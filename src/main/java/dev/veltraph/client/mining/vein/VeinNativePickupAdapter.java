package dev.veltraph.client.mining.vein;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.galatea.FigHarvestPickupLedger;
import dev.veltraph.client.galatea.FigToolWearLedger.Action;
import dev.veltraph.client.galatea.FigToolWearLedger;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.*;
import static dev.veltraph.client.mining.vein.VeinPickupCorrelation.*;

/** Native pre/post receipt producer. Never adopts inventory or invents server loot. Client thread only. */
public final class VeinNativePickupAdapter implements AutoCloseable {
    public record Binding(Context context,BooleanSupplier freshCustody) {}
    public record Event(long serial,String kind,long at,String detail) {}
    private record Spawn(int id,UUID uuid,Vec3 position,long serial,long at) {}
    private record AddBefore(boolean absent) {}
    private record TakeBefore(UUID uuid,int id,int amount,Object unit,int beforeCount,Object recipient) {}
    private record InventoryBefore(List<FigHarvestPickupLedger.Cell> cells,Set<Integer> covered) {}
    private record InventoryApplied(long serial,long at,List<FigHarvestPickupLedger.Cell> before,List<FigHarvestPickupLedger.Cell> after) {}
    private static volatile VeinNativePickupAdapter active;
    private final Minecraft client;private final Supplier<Binding> binding;private final Binding original;
    private final VeinPickupCorrelation correlation;
    private final Map<Object,Object> ingress=new IdentityHashMap<>();
    private final Map<Integer,Spawn> spawns=new HashMap<>();
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private final List<InventoryApplied> inventoryBatches=new ArrayList<>();
    private final Set<Long> fedInventories=new HashSet<>();
    private final Set<UUID> appliedTakes=new HashSet<>();
    private String retirement="none",retirementEvents="none";private String lastFailure="none";private boolean pickupCommitted;
    private FigHarvestPickupLedger.Cell projectedTool;
    private FigToolWearLedger.Permit wearPermit;private VeinNativeWearBridge.AdoptedWear adoptedWear;
    private List<?> wearBefore,wearAfter;
    private CollectionRequest collectionRequest;private boolean collectionMovementRevoked;
    private ItemEntity collectionRestItem;private net.minecraft.world.phys.Vec3 collectionRestPosition;private int collectionRestTick=-1;
    private long serial,lastNow=-1,issueSerial;private dev.veltraph.client.world.BlockPos target;private boolean issued,air,retired;

    private VeinNativePickupAdapter(Minecraft client,Supplier<Binding> binding){
        this.client=Objects.requireNonNull(client);this.binding=Objects.requireNonNull(binding);original=Objects.requireNonNull(binding.get());
        Objects.requireNonNull(original.context());Objects.requireNonNull(original.freshCustody());
        correlation=new VeinPickupCorrelation(original.context(),this::fresh);
    }
    public static VeinNativePickupAdapter bind(Minecraft client,Supplier<Binding> binding){
        if(!client.isSameThread())throw new IllegalStateException("Client thread required");
        if(active!=null)active.close();var next=new VeinNativePickupAdapter(client,binding);active=next;
        if(!next.current())next.close();return next;
    }
    private static long now(){return System.nanoTime()/1_000_000;}
    private boolean fresh(){
        if(!observed("fresh-not-retired",!retired)||!observed("fresh-active",active==this)||!observed("fresh-thread",client.isSameThread()))return false;
        try{var latest=binding.get();var context=original.context();var scope=context.scope();
            return observed("fresh-binding",latest!=null)&&observed("fresh-context",context.same(latest.context()))&&observed("fresh-custody-present",latest.freshCustody()!=null)&&observed("fresh-owner-custody",latest.freshCustody().getAsBoolean())
                    &&observed("fresh-player",client.player==scope.player())&&observed("fresh-world",client.level==scope.world())&&observed("fresh-connection",client.getConnection()==scope.connection())
                    &&observed("fresh-player-present",client.player!=null)&&observed("fresh-world-present",client.level!=null)&&observed("fresh-alive",client.player.isAlive())&&observed("fresh-player-level",client.player.level()==client.level)
                    &&observed("fresh-screen",client.gui.screen()==null)&&observed("fresh-menu",client.player.containerMenu==client.player.inventoryMenu)&&observed("fresh-cursor",client.player.containerMenu.getCarried().isEmpty())
                    &&observed("fresh-slot",client.player.getInventory().getSelectedSlot()==context.slot())
                    &&observed("fresh-manager",VeltraphBootstrap.moduleManager()!=null)&&observed("fresh-input-owner",VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==scope.owner())
                    &&observed("fresh-camera-installed",McOwnedRotation.installed(client))&&observed("fresh-camera-owner",McOwnedRotation.authority(client)==scope.owner())&&observed("fresh-camera-epoch",McOwnedRotation.epoch(client)==context.cameraEpoch());
        }catch(RuntimeException unsupported){observed("fresh-exception-"+unsupported.getClass().getSimpleName(),false);return false;}
    }
    private boolean current(){long at=now();if(!observed("current-clock",at>=lastNow)||!observed("current-deadline",at<=original.context().originalDeadline())||!fresh()){if(client.isSameThread())retire("current-refused:"+lastFailure);return false;}lastNow=at;return true;}
    private long event(String kind,String detail){long next=++serial;events.addLast(new Event(next,kind,now(),detail));while(events.size()>128)events.removeFirst();return next;}
    public static void observeSafely(Runnable observation){var self=active;if(self==null||!self.client.isSameThread())return;try{observation.run();}catch(Throwable unsupported){self.retire("observer-exception:"+unsupported.getClass().getSimpleName());}}
    /** Parent passes only the native guarded action after actual STOP submission; no synthetic issue. */
    public boolean issued(Action action){
        if(!current()||action==null||issued||action.stopSerial()<=action.issueSerial()||action.stopSequence()<0)return false;
        var c=original.context();var s=c.scope();
        if(action.scope().owner()!=s.owner()||action.scope().player()!=s.player()||action.scope().world()!=s.world()||action.scope().connection()!=s.connection()
                ||action.attemptGeneration()!=s.activationGeneration()||action.epoch()!=c.cameraEpoch()||action.slot()!=c.slot()
                ||!Objects.equals(action.settings(),c.configuration())||!Objects.equals(action.before().full(),c.tool())
                ||action.originalDeadline()!=c.originalDeadline()||action.targetGuard()==null||action.originalLogState()==null)return false;
        long start=event("START_SUBMITTED","sequence="+action.startSequence());long stop=event("STOP_SUBMITTED","sequence="+action.stopSequence());
        target=action.target();issueSerial=start;
        issued=correlation.issued(c,new Issued(start,stop,target,action.originalLogState(),action.targetGuard(),c.tool(),c.slot(),c.cameraEpoch(),c.configuration(),action.issued(),now()),now());return issued;
    }
    public static void airApplied(Object listener,net.minecraft.core.BlockPos pos,BlockState state){var self=active;
        if(self==null||!self.current()||!self.issued||self.air||listener!=self.client.getConnection()||!state.isAir()
                ||!self.target.equals(new dev.veltraph.client.world.BlockPos(pos.getX(),pos.getY(),pos.getZ()))
                ||!self.client.level.getBlockState(pos).equals(state))return;
        long stamp=self.event("SERVER_AIR_APPLIED",pos.toString());self.air=self.correlation.air(self.original.context(),self.issueSerial,new Air(stamp,now(),true),now());
    }
    public static void addBefore(Object listener,ClientboundAddEntityPacket packet){var self=active;if(self==null||!self.current()||!self.issued||listener!=self.client.getConnection())return;
        self.capture(packet,new AddBefore(self.client.level.getEntity(packet.getId())==null));}
    public static void addAfter(Object listener,ClientboundAddEntityPacket packet){var self=active;if(self==null||!self.current()||listener!=self.client.getConnection())return;
        var before=self.ingress.remove(packet);if(!(before instanceof AddBefore add)||!add.absent()||!(self.client.level.getEntity(packet.getId()) instanceof ItemEntity item)||!item.getUUID().equals(packet.getUUID()))return;
        var point=new Vec3(packet.getX(),packet.getY(),packet.getZ());if(!Double.isFinite(point.x())||!Double.isFinite(point.y())||!Double.isFinite(point.z())||!point.toBlockPos().equals(self.target))return;
        if(self.spawns.containsKey(packet.getId())||self.spawns.values().stream().anyMatch(s->s.uuid().equals(item.getUUID()))){self.retire("duplicate-spawn");return;}
        if(self.spawns.size()>=MAX_ENTITIES){self.retire("spawn-overflow");return;}
        long stamp=self.event("ITEM_ADD_APPLIED",item.getUUID().toString());self.spawns.put(item.getId(),new Spawn(item.getId(),item.getUUID(),point,stamp,now()));
    }
    public static void dataAfter(Object listener,int entityId){var self=active;if(self==null||!self.current()||listener!=self.client.getConnection())return;
        var spawn=self.spawns.get(entityId);if(spawn==null||!(self.client.level.getEntity(entityId) instanceof ItemEntity item)||!item.getUUID().equals(spawn.uuid()))return;
        var stack=item.getItem();if(stack.isEmpty()){self.retire("item-data-empty");return;}var unit=VeinPickupNativeSnapshots.unit(stack,self.client.level.registryAccess());
        long stamp=self.event("ITEM_DATA_APPLIED",spawn.uuid()+" count="+stack.getCount());
        if(!self.correlation.entity(self.original.context(),new Entity(spawn.uuid(),entityId,spawn.position(),spawn.serial(),spawn.at(),unit,stack.getCount(),stamp,now()),now()))self.retire("correlation-item-data-denied");
    }
    public static void takeBefore(Object listener,ClientboundTakeItemEntityPacket packet){var self=active;if(self==null||!self.current()||listener!=self.client.getConnection()||packet.getAmount()<=0)return;
        var spawn=self.spawns.get(packet.getItemId());if(spawn==null||!(self.client.level.getEntity(packet.getItemId()) instanceof ItemEntity item)||!spawn.uuid().equals(item.getUUID()))return;
        var recipient=self.client.level.getEntity(packet.getPlayerId());if(recipient!=self.client.player){self.retire("take-foreign-recipient");return;}
        if(item.getItem().isEmpty()||packet.getAmount()>item.getItem().getCount()){self.retire("take-pre-count");return;}
        self.capture(packet,new TakeBefore(item.getUUID(),item.getId(),packet.getAmount(),VeinPickupNativeSnapshots.unit(item.getItem(),self.client.level.registryAccess()),item.getItem().getCount(),recipient));
    }
    public static void takeAfter(Object listener,ClientboundTakeItemEntityPacket packet){var self=active;if(self==null||!self.current()||listener!=self.client.getConnection())return;
        var before=self.ingress.remove(packet);if(!(before instanceof TakeBefore take))return;
        var remaining=self.client.level.getEntity(take.id());int expected=take.beforeCount()-take.amount();
        if(expected>0){if(!(remaining instanceof ItemEntity item)||!item.getUUID().equals(take.uuid())||item.getItem().getCount()!=expected
                    ||!Objects.equals(take.unit(),VeinPickupNativeSnapshots.unit(item.getItem(),self.client.level.registryAccess()))){self.retire("take-post-partial-state");return;}}
        else if(remaining!=null&&(!remaining.isRemoved()||remaining instanceof ItemEntity item&&!item.getItem().isEmpty())){self.retire("take-post-complete-state");return;}
        long stamp=self.event("TAKE_APPLIED",take.uuid()+" count="+take.amount());
        if(!self.correlation.taken(self.original.context(),new Taken(take.uuid(),take.id(),stamp,take.recipient(),take.unit(),take.amount(),now()),now()))self.retire("correlation-take-denied");else self.appliedTakes.add(take.uuid());
    }
    private void capture(Object packet,Object before){if(ingress.size()>=32||ingress.putIfAbsent(packet,before)!=null)retire("ingress-overflow-or-duplicate");}
    private static Set<Integer> covered(Object packet){
        if(packet instanceof ClientboundSetPlayerInventoryPacket p)return p.slot()>=0&&p.slot()<36?Set.of(p.slot()):Set.of();
        if(packet instanceof ClientboundContainerSetSlotPacket p){int slot=p.getSlot();if(p.getContainerId()==-2)return slot>=0&&slot<36?Set.of(slot):Set.of();if(p.getContainerId()==0){if(slot>=36&&slot<=44)return Set.of(slot-36);if(slot>=9&&slot<=35)return Set.of(slot);}}
        if(packet instanceof ClientboundContainerSetContentPacket p&&p.containerId()==0){var cells=new HashSet<Integer>();for(int i=0;i<36;i++)cells.add(i);return Set.copyOf(cells);}return Set.of();
    }
    public static void inventoryBefore(Object listener,Object packet){var self=active;if(self==null||!self.current()||listener!=self.client.getConnection())return;var coverage=covered(packet);if(coverage.isEmpty())return;
        self.capture(packet,new InventoryBefore(VeinPickupNativeSnapshots.inventory(self.client.player.getInventory(),self.client.level.registryAccess()),coverage));}
    public static void inventoryAfter(Object listener,Object packet){var self=active;if(self==null||!self.current()||listener!=self.client.getConnection())return;
        var before=self.ingress.remove(packet);if(!(before instanceof InventoryBefore inventory))return;var after=VeinPickupNativeSnapshots.inventory(self.client.player.getInventory(),self.client.level.registryAccess());
        boolean changed=false;for(int i=0;i<36;i++)if(!inventory.cells().get(i).equals(after.get(i))){changed=true;if(!inventory.covered().contains(i)){self.retire("inventory-uncovered-cell:"+i);return;}}
        if(!changed)return;if(self.inventoryBatches.size()>=MAX_INVENTORIES){self.retire("inventory-overflow");return;}
        long stamp=self.event("INVENTORY_APPLIED",packet.getClass().getSimpleName());self.inventoryBatches.add(new InventoryApplied(stamp,now(),inventory.cells(),after));self.feedInventories();
    }
    /** Only a ledger-issued, still-unspent exact wear permit may project the native tool cell. */
    public boolean projectWear(FigToolWearLedger.Permit permit,List<?> before,List<?> projected){
        if(!observed("project-current",current())||!observed("project-permit",permit!=null)||!observed("project-before",before!=null)||!observed("project-after",projected!=null)||!observed("project-before36",before.size()==36)||!observed("project-after36",projected.size()==36))return false;
        var c=original.context();var s=c.scope();
        if(!observed("project-bound",permit.boundTo(s.activationGeneration(),c.cameraEpoch(),String.valueOf(c.configuration()),c.originalDeadline()))
                ||!observed("project-matches",permit.matches(s.owner(),s.player(),s.world(),s.connection(),s.activationGeneration(),before,projected))
                ||!observed("project-original-tool",Objects.equals(before.get(c.slot()),c.tool()))||!observed("project-permit-after",projected.equals(permit.after())))return false;
        for(int i=0;i<36;i++)if(i!=c.slot()&&!observed("project-other-cell-"+i,Objects.equals(before.get(i),projected.get(i))))return false;
        var actual=VeinPickupNativeSnapshots.cell(c.slot(),client.player.getInventory().getItem(c.slot()),client.level.registryAccess());
        if(!observed("project-native-tool",Objects.equals(actual.canonicalFullIdentity(),projected.get(c.slot()))))return false;
        if(projectedTool!=null&&!observed("project-repeated-tool",projectedTool.equals(actual)))return false;
        projectedTool=actual;wearPermit=permit;wearBefore=List.copyOf(before);wearAfter=List.copyOf(projected);feedInventories();return !retired;
    }
    public boolean retainAdoptedWear(VeinNativeWearBridge.AdoptedWear receipt){var c=original.context();var s=c.scope();if(receipt==null||!receipt.fresh(s.owner(),s.player(),s.world(),s.connection(),s.activationGeneration(),c.cameraEpoch(),String.valueOf(c.configuration()),c.originalDeadline())||wearAfter==null||!receipt.after().equals(wearAfter))return false;adoptedWear=receipt;return true;}
    private boolean wearFresh(){var c=original.context();var s=c.scope();return wearPermit==null||adoptedWear!=null&&adoptedWear.fresh(s.owner(),s.player(),s.world(),s.connection(),s.activationGeneration(),c.cameraEpoch(),String.valueOf(c.configuration()),c.originalDeadline())||wearPermit.boundTo(s.activationGeneration(),c.cameraEpoch(),String.valueOf(c.configuration()),c.originalDeadline())
            &&wearPermit.matches(s.owner(),s.player(),s.world(),s.connection(),s.activationGeneration(),wearBefore,wearAfter);}
    private void feedInventories(){
        for(var batch:inventoryBatches){if(fedInventories.contains(batch.serial()))continue;int slot=original.context().slot();
            var beforeTool=batch.before().get(slot);var afterTool=batch.after().get(slot);
            if(!Objects.equals(beforeTool.canonicalFullIdentity(),afterTool.canonicalFullIdentity())&&projectedTool==null)continue;
            var before=new ArrayList<>(batch.before());var after=new ArrayList<>(batch.after());
            if(projectedTool!=null){
                Object originalTool=original.context().tool(),qualified=projectedTool.canonicalFullIdentity();
                if(!wearFresh()||!Objects.equals(beforeTool.canonicalFullIdentity(),originalTool)&&!Objects.equals(beforeTool.canonicalFullIdentity(),qualified)
                        ||!Objects.equals(afterTool.canonicalFullIdentity(),originalTool)&&!Objects.equals(afterTool.canonicalFullIdentity(),qualified)){retire("inventory-tool-projection-chain");return;}
                before.set(slot,projectedTool);after.set(slot,projectedTool);
            }
            correlation.inventory(original.context(),new Inventory(batch.serial(),batch.at(),before,after),now());fedInventories.add(batch.serial());
        }
    }
    private boolean observed(String operand,boolean value){if(!value&&"none".equals(lastFailure))lastFailure=operand;return value;}
    public String cachedDiagnostic(){return "retirement="+retirement+" events="+retirementEvents+" pickup="+lastFailure+" retired="+retired+" serial="+serial+" issued="+issued+" issueSerial="+issueSerial+" air="+air+" spawns="+spawns.size()+" inventoryBatches="+inventoryBatches.size()+" projected="+(projectedTool!=null);}
    /** Private handout binds the unique applied native loot chain. It grants movement only. */
    public static final class CollectionRequest {
        private final VeinNativePickupAdapter adapter;private final dev.veltraph.client.mining.mc.McMiningServices service;
        private final net.minecraft.world.phys.Vec3 initialMotion;private final Entity entity;private final Spawn spawn;private final net.minecraft.world.phys.AABB nativeItemBox;private final ItemEntity item;private final VeinWatchedItemHistory.Snapshot itemHistory;
        private final net.minecraft.client.player.LocalPlayer player;private final net.minecraft.client.multiplayer.ClientLevel world;
        private final net.minecraft.client.multiplayer.ClientPacketListener connection;private final Object gameMode,options,inventory,menu;
        private final dev.veltraph.client.core.ModuleManager root;private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot store;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot controls;
        private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection;
        private final VeinMiningModule.CollectionSourceSnapshot source;
        private final long deadlineFloor;private final java.util.List<net.minecraft.world.item.ItemStack> stacks;private final net.minecraft.world.item.ItemStack nativeItem;
        private CollectionRequest(VeinNativePickupAdapter adapter,dev.veltraph.client.mining.mc.McMiningServices service,Entity entity,ItemEntity item,VeinWatchedItemHistory.Snapshot itemHistory){
            this.adapter=adapter;this.service=service;deadlineFloor=adapter.lastNow;source=((VeinMiningModule)adapter.original.context().scope().owner()).collectionSourceSnapshot(adapter,adapter.original.context().originalDeadline());this.entity=entity;initialMotion=item.getDeltaMovement();spawn=adapter.spawns.get(entity.id());nativeItemBox=item.getBoundingBox();this.item=item;this.itemHistory=itemHistory;
            var c=adapter.client;player=c.player;world=c.level;connection=c.getConnection();gameMode=c.gameMode;options=c.options;
            inventory=player.getInventory();menu=player.inventoryMenu;root=VeltraphBootstrap.moduleManager();lifecycle=root.lifecycleSnapshot();
            var cfg=VeltraphBootstrap.config();store=cfg instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot():cfg instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
            controls=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();installation=McOwnedRotation.installationSnapshot(c);
            selection=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(player.getInventory());
            var copies=new ArrayList<net.minecraft.world.item.ItemStack>(36);for(int i=0;i<36;i++)copies.add(player.getInventory().getItem(i).copy());stacks=List.copyOf(copies);nativeItem=item.getItem().copy();
        }
        public dev.veltraph.client.world.BlockPos source(){return adapter.target;}
        public Object owner(){return adapter.original.context().scope().owner();}
        public ItemEntity item(){return item;}
        public Vec3 itemPosition(){return itemHistory.position();}
        public net.minecraft.world.phys.AABB itemBox(){return nativeItemBox;}
        public long deadline(){return adapter.original.context().originalDeadline();}public long deadlineFloor(){return deadlineFloor;}
        public boolean sourceCurrent(){return source!=null&&source.current();}
        /** Private source identity and native selection history are cleanup vetoes only. */
        public boolean cleanupSourceCurrent(){var c=adapter.client;return source!=null&&source.cleanupCurrent()&&c.isSameThread()
            &&c.player==player&&c.level==world&&c.getConnection()==connection&&c.gameMode==gameMode&&c.options==options
            &&VeltraphBootstrap.moduleManager()==root&&player.getInventory()==inventory&&player.inventoryMenu==menu&&player.containerMenu==menu
            &&menu!=null&&player.inventoryMenu.getCarried().isEmpty();}
        public boolean cleanupNativeContext(){var c=adapter.client;return c.isSameThread()&&c.player==player&&c.level==world&&c.getConnection()==connection
            &&c.gameMode==gameMode&&c.options==options&&VeltraphBootstrap.moduleManager()==root&&player.getInventory()==inventory
            &&player.inventoryMenu==menu&&player.containerMenu==menu&&menu!=null&&player.inventoryMenu.getCarried().isEmpty();}
        public boolean cleanupActivationMatches(VeinMiningModule.CollectionActivation exact){return source!=null&&source.activationMatches(exact)&&cleanupSourceCurrent();}
        /** Private actual source handout only; selection/tool/all36 follow accepted native receipts. */
        public VeinMiningModule.CollectionCleanupSlots cleanupSlots(){return cleanupSourceCurrent()?source.cleanupSlots():null;}
        public boolean belongsToAdapter(VeinNativePickupAdapter exact){return adapter==exact&&adapter.collectionRequest==this;}
        public boolean belongsTo(dev.veltraph.client.mining.mc.McMiningServices exact){return service==exact&&adapter.collectionRequest==this;}
        /** Callback-capable original custody first, then callback-free immutable/native final leaf. */
        public boolean current(){boolean valid=adapter.current()&&nativeLeaf();if(!valid&&adapter.client.isSameThread())adapter.collectionMovementRevoked=true;return valid;}
        public boolean controlLeaf(){var c=adapter.client;return c.isSameThread()&&VeltraphBootstrap.moduleManager()==root&&lifecycle!=null&&lifecycle.known()&&root.lifecycleCurrent(lifecycle)
            &&source!=null&&source.current()&&store!=null&&store.current()&&controls!=null&&controls.active()&&controls.current()&&installation!=null&&installation.current()
            &&selection!=null&&selection.current()&&c.player==player&&c.level==world&&c.getConnection()==connection&&c.gameMode==gameMode&&c.options==options
            &&player.getInventory()==inventory&&player.inventoryMenu==menu&&player.containerMenu==menu&&menu!=null&&player.inventoryMenu.getCarried().isEmpty()
            &&player.getInventory().getSelectedSlot()==adapter.original.context().slot()&&player.level()==world&&player.isAlive()&&!player.isSpectator()&&!player.isPassenger()&&!player.getAbilities().flying&&player.getPose()==net.minecraft.world.entity.Pose.STANDING
            &&c.gui.screen()==null&&!c.isPaused()&&c.getCameraEntity()==player&&McOwnedRotation.epoch(c)==adapter.original.context().cameraEpoch();}
        /** No authority supplier, caller predicate, config getter or codec runs at this final leaf. */
        public boolean observationLeaf(){var c=adapter.client;
            if(adapter.collectionMovementRevoked||adapter.retired||active!=adapter||adapter.collectionRequest!=this||!controlLeaf()
                ||!adapter.issued||!adapter.air||adapter.spawns.size()!=1||adapter.spawns.get(entity.id())!=spawn||adapter.appliedTakes.contains(entity.uuid())||!adapter.correlation.samePendingCollection(entity)
                ||c.level.getEntity(entity.id())!=item||item.level()!=world||item.isRemoved()||!item.getUUID().equals(entity.uuid())
                ||!item.getBoundingBox().equals(nativeItemBox)||!item.onGround()||!motionCurrent()||!VeinWatchedItemHistory.current(itemHistory)
                ||!net.minecraft.world.item.ItemStack.matches(nativeItem,item.getItem()))return false;
            for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(stacks.get(i),player.getInventory().getItem(i)))return false;
            return controlLeaf()&&adapter.collectionRequest==this&&!adapter.collectionMovementRevoked&&VeinWatchedItemHistory.current(itemHistory);
        }
        private boolean motionCurrent(){var now=item.getDeltaMovement();return VeinCollectionMotion.unchangedOrDecaying(initialMotion.x,initialMotion.y,initialMotion.z,now.x,now.y,now.z);}
        /** Retains only the authentic already-published origin after an actual applied native TAKE. */
        public boolean takenObservationLeaf(){return adapter.collectionRequest==this&&!adapter.retired&&active==adapter&&controlLeaf()
            &&adapter.spawns.get(entity.id())==spawn&&adapter.appliedTakes.contains(entity.uuid())&&adapter.correlation.observedTakeFor(entity);}
        public boolean nativeLeaf(){
            if(!observationLeaf())return false;long at=System.nanoTime()/1_000_000;
            return VeinCollectionWindow.current(at,adapter.lastNow,deadline())&&observationLeaf();
        }
    }
    public Optional<CollectionRequest> collectionRequest(dev.veltraph.client.mining.mc.McMiningServices service){
        if(collectionMovementRevoked||!current()||!issued||!air||!wearFresh()||!(original.context().scope().owner() instanceof VeinMiningModule owner)||!owner.ownsCollectionService(service))return Optional.empty();
        if(collectionRequest!=null)return collectionRequest.belongsTo(service)&&collectionRequest.current()?Optional.of(collectionRequest):Optional.empty();
        var exact=correlation.singlePendingCollection(original.context(),now()).orElse(null);if(exact==null||exact.count()!=1||spawns.size()!=1||appliedTakes.contains(exact.uuid()))return Optional.empty();
        var spawn=spawns.get(exact.id());if(spawn==null||!spawn.uuid().equals(exact.uuid())||!(client.level.getEntity(exact.id()) instanceof ItemEntity item)
            ||!item.getUUID().equals(exact.uuid())||item.isRemoved()||!item.onGround()||item.getItem().getCount()!=exact.count()
            ||!Objects.equals(VeinPickupNativeSnapshots.unit(item.getItem(),client.level.registryAccess()),exact.unit()))return Optional.empty();
        // Real native pose stability across two ordinary client ticks, not exact-zero velocity.
        var position=item.position();int tick=client.player.tickCount;
        if(collectionRestItem!=item||!position.equals(collectionRestPosition)){collectionRestItem=item;collectionRestPosition=position;collectionRestTick=tick;return Optional.empty();}
        if(tick<=collectionRestTick)return Optional.empty();
        var history=VeinWatchedItemHistory.watch(item);if(history==null)return Optional.empty();
        var next=new CollectionRequest(this,service,exact,item,history);collectionRequest=next;
        if(!next.current())return Optional.empty();return Optional.of(next);
    }
    public Context context(){return original.context();}
    public long originalDeadline(){return original.context().originalDeadline();}
    public int observedItemCount(){return spawns.size();}
    public boolean settled(List<?> expected,List<?> actual){return current()&&pickupCommitted&&!spawns.isEmpty()
            &&spawns.values().stream().allMatch(s->appliedTakes.contains(s.uuid()))&&expected!=null&&expected.equals(actual);}
    /** Observational only. Absence of a spawn is not proof that no delayed server loot will arrive. */
    public boolean hasPendingItems(){return current()&&!spawns.isEmpty()&&(!pickupCommitted||spawns.values().stream().anyMatch(s->!appliedTakes.contains(s.uuid())));}
    public boolean pendingExact(List<?> expected,List<?> actual){return current()&&wearFresh()&&correlation.pendingExact(original.context(),expected,actual,now());}
    public Optional<FigHarvestPickupLedger.Permit> review(long attempt,List<?> expected,List<?> actual){return current()&&wearFresh()?correlation.review(original.context(),attempt,expected,actual,now()):Optional.empty();}
    public boolean commit(FigHarvestPickupLedger.Permit permit,BooleanSupplier inventoryFenceCas){boolean result=current()&&wearFresh()&&correlation.commit(original.context(),permit,now(),()->fresh()&&wearFresh()&&inventoryFenceCas.getAsBoolean());if(result)pickupCommitted=true;return result;}
    /** Public interface carries a concrete privately issued ledger permit; callers cannot forge native proof. */
    public Optional<VeinInventoryAdoption.Permission> permission(long attempt,List<?> expected,List<?> actual){
        var permit=review(attempt,expected,actual).orElse(null);if(permit==null)return Optional.empty();
        var before=List.copyOf(expected);var after=List.copyOf(actual);var c=original.context();var s=c.scope();
        return Optional.of(new VeinInventoryAdoption.Permission(){
            public boolean fresh(){return current()&&wearFresh()&&permit.matches(s.owner(),s.player(),s.world(),s.connection(),attempt,before,after)
                    &&VeinPickupNativeSnapshots.inventory(client.player.getInventory(),client.level.registryAccess()).stream().map(FigHarvestPickupLedger.Cell::canonicalFullIdentity).toList().equals(after);}
            public List<?> before(){return before;}public List<?> after(){return after;}
            public boolean commit(BooleanSupplier cas){return fresh()&&VeinNativePickupAdapter.this.commit(permit,cas);}
        });
    }
    public Optional<VeinInventoryAdoption.Permission> asPermission(List<?> expected,List<?> actual,long attempt){return permission(attempt,expected,actual);}
    public List<Event> events(){return List.copyOf(events);}
    private void retire(String cause){if(client.isSameThread()&&"none".equals(retirement)){retirement=cause;try{retirementEvents=events.stream().map(e->e.serial()+":"+e.kind()+":"+e.detail()).collect(java.util.stream.Collectors.joining("|"));}catch(Throwable unavailable){retirementEvents="diagnostic-unavailable";}}close();}
    @Override public void close(){if(!client.isSameThread())return;retired=true;collectionMovementRevoked=true;correlation.retire();ingress.clear();spawns.clear();inventoryBatches.clear();fedInventories.clear();appliedTakes.clear();projectedTool=null;wearPermit=null;wearBefore=null;wearAfter=null;if(active==this)active=null;}
}
