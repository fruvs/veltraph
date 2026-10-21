package dev.veltraph.client.greenhouse;

import com.google.gson.*;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import dev.veltraph.client.service.FarmServices;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;
import static dev.veltraph.client.greenhouse.GreenhouseDiagnosticAssociation.*;

/** Explicit native-use adapter. Ordinary diagnostics use actual isolated server open/content; legacy echoes are opt-in. */
public final class McGreenhouseDiagnosticAdapter {
    private static volatile McGreenhouseDiagnosticAdapter installed;
    // Connection-local unresolved response origin survives module/adapter replacement. No timer declares it drained.
    private static Object unresolvedListener;
    private void retainUnresolvedOrigin(){if(client.getConnection()!=null&&association.hasUnresolvedNativeResponse(client.getConnection()))unresolvedListener=client.getConnection();}
    private final Minecraft client;private final GreenhouseModule owner;private final ConfigService config;private final FarmServices services;
    private final BooleanSupplier persist;private final McMenuAccess menus;private final GreenhouseDiagnosticAssociation association=new GreenhouseDiagnosticAssociation();
    private final Map<Cell,Associated> live=new HashMap<>();private final List<Evidence> archive=new ArrayList<>();
    private record WaterIntegrity(GreenhouseRuntime.Plan plan,Associated plant,GreenhouseNativeInventory before,GreenhouseNativeInventory after,int toolSlot,int resourceSlot) {}
    private WaterIntegrity water;private int originalStartSlot=-1;
    private Selection selected;private UUID token;private long epoch,menuEpoch;private Object lastNativeMenu;private boolean issuing;private volatile boolean foreignAction;
    public McGreenhouseDiagnosticAdapter(Minecraft c,GreenhouseModule owner,ConfigService config,FarmServices services,BooleanSupplier persist){
        this.client=c;this.owner=owner;this.config=config;this.services=services;this.persist=persist;menus=new McMenuAccess(c);
        // Restored scalar provenance is historical only, never a current plant permission.
        try{String text=config.get(GreenhouseConfig.GREENHOUSE_OBSERVATIONS);if(!text.isBlank()){var json=JsonParser.parseString(text).getAsJsonObject();if(json.has("diagnosticAssociations")){var rows=new Gson().fromJson(json.get("diagnosticAssociations"),Evidence[].class);if(rows!=null&&rows.length<=100)archive.addAll(Arrays.asList(rows));}}}catch(RuntimeException ignored){archive.clear();}
    }
    public void select(Selection selection){if(owner.isEnabled())throw new IllegalStateException("Stop Greenhouse before selecting diagnostics");selected=Objects.requireNonNull(selection);live.remove(selection.cell());association.cancel();}
    public void selectForActiveAction(Selection s){if(!owner.isEnabled()||association.active())throw new IllegalStateException("One owned diagnostic request at a time");selected=Objects.requireNonNull(s);live.remove(s.cell());}
    public Optional<Selection> selected(){return Optional.ofNullable(selected);}
    public void activation(){token=UUID.randomUUID();epoch++;foreignAction=false;}
    private long now(){return services.clock().nowMillis();}
    private void sampleMenuEpoch(){Object m=client.player==null?null:client.player.containerMenu;if(m!=lastNativeMenu){lastNativeMenu=m;menuEpoch++;}}
    private Cell cell(net.minecraft.core.BlockPos p){return new Cell(p.getX(),p.getY(),p.getZ());}
    private String fingerprint(Cell c){return !loaded(c)?"":client.level.getBlockState(new net.minecraft.core.BlockPos(c.x(),c.y(),c.z())).toString();}
    private boolean loaded(Cell c){return McGreenhouseLoadedCell.readable(client.level,c);}
    private GreenhouseNativeInventory inventory(){
        var slots=new ArrayList<ItemStack>();for(int i=0;i<client.player.getInventory().getContainerSize();i++)slots.add(client.player.getInventory().getItem(i));
        var gear=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);for(var e:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))gear.put(e,client.player.getItemBySlot(e));
        return GreenhouseNativeInventory.capture(slots,gear,client.player.containerMenu.getCarried(),services.inventory().selectedSlot(),client.level.registryAccess());
    }
    GreenhouseNativeInventory currentNativeInventory(){if(client.player==null||client.level==null||client.getConnection()==null)throw new IllegalArgumentException("Loaded native actor/world/connection required");return inventory();}
    public void prepareWaterIntegrity(GreenhouseRuntime.Plan plan,int toolSlot,int resourceSlot,int units){
        var actual=live.get(plan.actions().getFirst().cell());if(actual==null||fresh(actual.evidence().cell(),30000).isEmpty())throw new IllegalArgumentException("No fresh native association");
        var before=inventory();var selection=GreenhouseSelectedWaterResources.select(before,toolSlot,resourceSlot,units);
        var remaining=client.player.getInventory().getItem(resourceSlot).copy();remaining.setCount(selection.resourceAfter());
        var after=before.replacingResource(resourceSlot,remaining,client.level.registryAccess());
        water=new WaterIntegrity(plan,actual,before,after,toolSlot,resourceSlot);originalStartSlot=-1;
    }
    private boolean samePreparedScope(){
        if(water==null||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive())return false;
        var a=water.plant();var c=a.custody();var e=a.evidence();
        return c.player()==client.player&&c.world()==client.level&&c.connection()==client.getConnection()
                &&e.context().equals(new Context(config.get(GreenhouseConfig.GREENHOUSE_PROFILE),config.get(GreenhouseConfig.GREENHOUSE_PLOT),client.level.dimension().identifier().toString()))
                &&loaded(e.cell())&&fingerprint(e.cell()).equals(e.plantFingerprint());
    }
    public boolean preparedStartMatches(GreenhouseRuntime.Plan plan){
        if(!samePreparedScope()||!water.plan().equals(plan)||plan.actions().size()!=1||plan.actions().getFirst().kind()!=GreenhouseRuntime.Kind.WATER)return false;
        var current=inventory();if(!current.sameSlotsAndCursor(water.before()))return false;originalStartSlot=current.selectedSlot();return true;
    }
    public boolean preparedBeforeIssue(GreenhouseRuntime.Action action){
        if(!samePreparedScope()||!water.plan().actions().getFirst().equals(action))return false;
        var current=inventory();return current.selectedSlot()==water.toolSlot()&&current.sameSlotsAndCursor(water.before());
    }
    public boolean preparedWaitingMatches(GreenhouseRuntime.Action action,boolean returning){
        if(!samePreparedScope()||!water.plan().actions().getFirst().equals(action))return false;
        var current=inventory();boolean slot=current.selectedSlot()==water.toolSlot()||current.selectedSlot()==selected.bindings().diagnosticToolSlot()||(returning&&current.selectedSlot()==originalStartSlot);
        return slot&&(current.sameSlotsAndCursor(water.before())||current.sameSlotsAndCursor(water.after()));
    }
    public boolean preparedAfterMatches(GreenhouseRuntime.Action action){return samePreparedScope()&&water.plan().actions().getFirst().equals(action)&&inventory().sameSlotsAndCursor(water.after());}
    public String canonicalSlot(int slot){return GreenhouseNativeInventory.captureCell(client.player.getInventory().getItem(slot),client.level.registryAccess()).persistentEncoding();}

    private Custody custody(){
        if(foreignAction||selected==null||client.player==null||client.level==null||client.player.level()!=client.level||client.getConnection()==null||!owner.isEnabled()||token==null)return null;
        Object rootOwner=VeltraphBootstrap.moduleManager().inputOwner().orElse(null);if(rootOwner!=owner||client.isPaused()||client.getCameraEntity()!=client.player)return null;
        if(VeltraphBootstrap.autoHeal()!=null&&VeltraphBootstrap.autoHeal().ownsControl())return null;
        if(VeltraphBootstrap.hydra()!=null&&(VeltraphBootstrap.hydra().hasActiveLocalReaction()||VeltraphBootstrap.hydra().hasActiveGeneralResponse()))return null;
        var picked=client.player.pick(3.5,1f,false);if(!(picked instanceof BlockHitResult h)||h.getType()!=HitResult.Type.BLOCK)return null;
        Cell ray=cell(h.getBlockPos()),plant=selected.cell();
        return new Custody(client.player,client.level,client.getConnection(),rootOwner,token,epoch,
            new Context(config.get(GreenhouseConfig.GREENHOUSE_PROFILE),config.get(GreenhouseConfig.GREENHOUSE_PLOT),client.level.dimension().identifier().toString()),
            new Position(client.player.getX(),client.player.getY(),client.player.getZ()),client.player.getYRot(),client.player.getXRot(),services.inventory().selectedSlot(),NativeStackIdentity.capture(client.player.getMainHandItem(),client.level.registryAccess()),inventory(),
            ray,h.getDirection().name(),new Position(h.getLocation().x,h.getLocation().y,h.getLocation().z),fingerprint(ray),fingerprint(plant),loaded(ray)&&loaded(plant),client.player.isAlive(),client.player.containerMenu.getCarried().isEmpty());
    }
    public Optional<String> readiness(){
        if(selected==null)return Optional.of("Select an explicit native crop/tool/target inspection");
        retainUnresolvedOrigin();
        if(client.getConnection() instanceof NativeReceiveCarrier carrier&&!carrier.greenhouseObserverHealthy())return Optional.of("Native diagnostic observer failed on this connection; reconnect before inspection");
        if(selected.isolatedNativeMenu()&&unresolvedListener==client.getConnection())return Optional.of("Unresolved earlier native diagnostic response; reconnect before another inspection");
        if(unresolvedListener!=null&&unresolvedListener!=client.getConnection())unresolvedListener=null;
        if(client.player==null||client.level==null||client.gameMode==null||client.getConnection()==null||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty())return Optional.of("Loaded actor, empty cursor and closed menu required");
        if(!config.get(GreenhouseConfig.GREENHOUSE_PROFILE).equals(selected.bindings().profileId())||!config.get(GreenhouseConfig.GREENHOUSE_PLOT).equals(selected.bindings().plotId()))return Optional.of("Select the exact expected profile/plot before inspection");
        if(services.inventory().selectedSlot()!=selected.bindings().diagnosticToolSlot()||!canonicalSlot(services.inventory().selectedSlot()).equals(selected.bindings().diagnosticToolIdentity()))return Optional.of("Hold the exact observed Diagnostic Tool");
        if(!(client.hitResult instanceof BlockHitResult observedRay)||observedRay.getType()!=HitResult.Type.BLOCK||!cell(observedRay.getBlockPos()).equals(selected.binding().diagnosticTargetCell()))return Optional.of("The observed native interaction ray must identify that exact block");
        var hit=client.player.pick(3.5,1f,false);if(!(hit instanceof BlockHitResult h)||h.getType()!=HitResult.Type.BLOCK||!cell(h.getBlockPos()).equals(selected.binding().diagnosticTargetCell())||!loaded(selected.cell()))return Optional.of("Look at the selected loaded actual diagnostic target; no proximity inference");
        return Optional.empty();
    }
    public boolean issue(){
        if(!client.isSameThread())throw new IllegalStateException("Native inspection requires client thread");sampleMenuEpoch();
        if(readiness().isPresent())return false;
        services.rotation().cancelRotation(); // Freeze the observed user ray before recording; no new camera endpoint is requested.
        Custody c=custody();if(!association.begin(selected,c,now(),menuEpoch,true))return false;
        installed=this;issuing=true;foreignAction=false;
        try{var hit=(BlockHitResult)client.player.pick(3.5,1f,false);client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);association.nativeCallReturned(true);}
        catch(RuntimeException error){association.nativeCallReturned(false);}finally{issuing=false;}
        return association.active();
    }
    /** Passive hook at actual submission. No packet is emitted, suppressed, retried or rewritten here. */
    public static void beforeSend(Object nativeConnection,Packet<?> packet){
        var self=installed;if(self==null||(!self.association.active()&&!self.association.awaitingDurability()))return;
        boolean action=packet instanceof ServerboundUseItemOnPacket||packet instanceof ServerboundUseItemPacket||packet instanceof ServerboundPlayerActionPacket
            ||packet instanceof ServerboundContainerClickPacket||packet instanceof ServerboundContainerClosePacket
            ||packet instanceof ServerboundChatCommandPacket||packet instanceof ServerboundSetCarriedItemPacket;
        if(!action)return;
        if(!self.client.isSameThread()){self.foreignAction=true;return;}
        if(self.client.getConnection()==null||nativeConnection!=self.client.getConnection().getConnection()){self.foreignAction=true;return;}
        if(packet instanceof ServerboundUseItemOnPacket p&&self.issuing&&p.hand()==InteractionHand.MAIN_HAND){var hit=p.hitResult();self.association.submitted(self.custody(),p.sequence(),self.cell(hit.getBlockPos()),hit.getDirection().name(),new Position(hit.getLocation().x,hit.getLocation().y,hit.getLocation().z),self.now());}
        else self.association.foreignNativeAction();
    }
    public interface NativeReceiveCarrier {
        boolean greenhouseObserverHealthy();
        Object greenhouseClaim(Object packet,int kind);
        boolean greenhouseCurrent(Object claim,Object packet,int kind,boolean normal);
    }
    public static final class ReceiveInvocation {
        private final McGreenhouseDiagnosticAdapter self;private final Object listener,packet,claim,request;
        private final NativeReceiveCarrier carrier;private final Selection selection;private final UUID activation;private final long epoch;
        private final int kind,container,revision;private final String title;private final List<ItemStack> items;private final ItemStack carried;
        private ReceiveInvocation(McGreenhouseDiagnosticAdapter self,Object listener,Object packet,Object claim,NativeReceiveCarrier carrier,
                int kind,int container,int revision,String title,List<ItemStack> items,ItemStack carried){
            this.self=self;this.listener=listener;this.packet=packet;this.claim=claim;this.request=self.association.pendingRequestIdentity();this.carrier=carrier;this.selection=self.selected;
            this.activation=self.token;this.epoch=self.epoch;this.kind=kind;this.container=container;this.revision=revision;
            this.title=title;this.items=items;this.carried=carried;
        }
    }
    /** Best-effort immediate revocation. The target-held listener latch remains effective even if this method cannot link/initialize. */
    public static void observerFailed(Object listener){
        if(!(listener instanceof NativeReceiveCarrier carrier)||carrier.greenhouseObserverHealthy())return;
        unresolvedListener=listener;
        var self=installed;if(self!=null&&self.client.getConnection()==listener){self.foreignAction=true;self.association.foreignNativeAction();}
    }
    /** Entry is admitted only inside the installed native handler frame. Its packet values are copied before application. */
    public static ReceiveInvocation receiveEntry(Object listener,Packet<?> packet){
        var self=installed;
        if(self==null||self.selected==null||!self.selected.isolatedNativeMenu()||!self.association.active())return null;
        if(!self.client.isSameThread())return null; // Thread dispatch re-enters the actual handler on the client thread.
        if(listener!=self.client.getConnection()||!(listener instanceof NativeReceiveCarrier carrier)||!carrier.greenhouseObserverHealthy()){self.foreignAction=true;return null;}
        int kind=packet instanceof ClientboundOpenScreenPacket?1:packet instanceof ClientboundContainerSetContentPacket?2:3;
        Object claim=carrier.greenhouseClaim(packet,kind);
        if(claim==null){self.foreignAction=true;return null;}
        if(packet instanceof ClientboundOpenScreenPacket p){
            if(self.client.player==null||self.client.gui.screen()!=null||self.client.player.containerMenu!=self.client.player.inventoryMenu
                    ||p.getType()!=net.minecraft.world.inventory.MenuType.GENERIC_9x5){self.association.foreignNativeAction();return null;}
            return new ReceiveInvocation(self,listener,packet,claim,carrier,kind,p.getContainerId(),-1,MenuSnapshot.clean(p.getTitle().getString()),List.of(),ItemStack.EMPTY);}
        if(packet instanceof ClientboundContainerSetContentPacket p)return new ReceiveInvocation(self,listener,packet,claim,carrier,kind,p.containerId(),p.stateId(),"",p.items().stream().map(ItemStack::copy).toList(),p.carriedItem().copy());
        return new ReceiveInvocation(self,listener,packet,claim,carrier,kind,-1,-1,"",List.of(),ItemStack.EMPTY);
    }
    /** Normal-return witness plus exact applied packet rows; no ACK is treated as a target/menu echo. */
    public static void receiveExit(ReceiveInvocation call,boolean normal){
        if(call==null)return;var self=call.self;
        try {
            if(!normal||!call.carrier.greenhouseObserverHealthy()||!call.carrier.greenhouseCurrent(call.claim,call.packet,call.kind,normal)
                    ||installed!=self||call.selection!=self.selected||call.request==null||call.request!=self.association.pendingRequestIdentity()
                    ||!Objects.equals(call.activation,self.token)||call.epoch!=self.epoch
                    ||!self.client.isSameThread()||call.listener!=self.client.getConnection()||self.foreignAction){self.association.foreignNativeAction();return;}
            self.sampleMenuEpoch();
            if(call.kind==1){
                if(!(self.client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)
                        ||self.client.player==null||screen.getMenu()!=self.client.player.containerMenu
                        ||screen.getMenu().containerId!=call.container||screen.getMenu().slots.size()!=81
                        ||screen.getMenu().getType()!=net.minecraft.world.inventory.MenuType.GENERIC_9x5
                        ||!MenuSnapshot.clean(screen.getTitle().getString()).equals(call.title)){
                    self.association.foreignNativeAction();return;
                }
                self.association.nativeMenuOpened(call.listener,screen,screen.getMenu(),call.container,call.title,self.custody(),self.now());
            }else if(call.kind==2){
                if(self.client.player==null||self.client.level==null||call.container!=self.client.player.containerMenu.containerId
                        ||call.revision!=self.client.player.containerMenu.getStateId()||call.items.size()!=81||!call.carried.isEmpty()
                        ||!self.client.player.containerMenu.getCarried().isEmpty()){self.association.foreignNativeAction();return;}
                var menu=self.client.player.containerMenu;
                for(var slot:menu.slots){
                    if(slot.index<0||slot.index>=call.items.size()
                            ||!NativeStackIdentity.capture(slot.getItem(),self.client.level.registryAccess()).equals(NativeStackIdentity.capture(call.items.get(slot.index),self.client.level.registryAccess()))
                            ||slot.getItem().getCount()!=call.items.get(slot.index).getCount()){
                        self.association.foreignNativeAction();return;
                    }
                }
                var token=self.receiptToken();
                if(token==null||token.allRows().size()!=45){self.association.foreignNativeAction();return;}
                self.association.nativeMenuContents(call.listener,token,self.custody(),self.now());
            }else self.association.foreignNativeAction();
        }catch(RuntimeException unsupported){self.association.foreignNativeAction();}
        finally{self.retainUnresolvedOrigin();}
    }
    private ReceiptToken receiptToken(){try{return captureReceiptToken();}catch(RuntimeException unsupported){return null;}}
    private ReceiptToken captureReceiptToken(){
        if(selected==null||client.player==null||client.level==null||!(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu)return null;
        sampleMenuEpoch();var menu=client.player.containerMenu;var nativeRows=menu.slots.stream().filter(s->s.index==selected.receiptSlot()&&s.container!=client.player.getInventory()).toList();
        if(nativeRows.size()!=1)return null;var item=nativeRows.getFirst().getItem();var lore=item.get(net.minecraft.core.component.DataComponents.LORE);
        var row=new MenuSnapshot.Slot(selected.receiptSlot(),item.isEmpty()?"":MenuSnapshot.clean(item.getHoverName().getString()),lore==null?List.of():lore.lines().stream().map(c->MenuSnapshot.clean(c.getString())).toList(),false,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getCount(),item.hasFoil(),McMenuAccess.componentIdentity(item,client.level.registryAccess()));
        var fullRows=new ArrayList<MenuSnapshot.Slot>();var fullNative=new HashMap<Integer,NativeStackIdentity.Snapshot>();
        if(selected.fullCapturedDiagnostics())for(var slot:menu.slots)if(slot.container!=client.player.getInventory()){
            var value=slot.getItem();var lines=value.get(net.minecraft.core.component.DataComponents.LORE);
            fullRows.add(new MenuSnapshot.Slot(slot.index,value.isEmpty()?"":MenuSnapshot.clean(value.getHoverName().getString()),lines==null?List.of():lines.lines().stream().map(c->MenuSnapshot.clean(c.getString())).toList(),false,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.getItem()).toString(),value.getCount(),value.hasFoil(),McMenuAccess.componentIdentity(value,client.level.registryAccess())));
            if(fullNative.put(slot.index,NativeStackIdentity.capture(value,client.level.registryAccess()))!=null)return null;
        }
        return new ReceiptToken(screen,menu,menu.containerId,menu.getStateId(),menuEpoch,MenuSnapshot.clean(screen.getTitle().getString()),row,NativeStackIdentity.capture(item,client.level.registryAccess()),fullRows,fullNative);
    }
    public Optional<Associated> tick(){
        retainUnresolvedOrigin();
        if(!association.active())return Optional.empty();sampleMenuEpoch();if(foreignAction){association.foreignNativeAction();return Optional.empty();}
        var tokenBefore=receiptToken();var menu=menus.read();var tokenAfter=receiptToken();
        if(menu!=null&&(tokenBefore==null||!tokenBefore.same(tokenAfter))){association.refuseDurability();return Optional.empty();}
        var states=new HashMap<Cell,String>();if(selected!=null&&loaded(selected.cell()))states.put(selected.cell(),fingerprint(selected.cell()));
        var accepted=association.observe(menu,states,custody(),now(),menuEpoch,tokenAfter);retainUnresolvedOrigin();
        if(accepted.isPresent()){
            Associated a=accepted.get();archive.removeIf(e->e.context().equals(a.evidence().context())&&e.cell().equals(a.evidence().cell()));archive.add(a.evidence());while(archive.size()>100)archive.removeFirst();
        }
        return accepted;
    }
    /** Only freshly live same-P/W/C observations; restored provenance cannot satisfy this route. */
    public Optional<Plant> fresh(Cell c,long maxAge){
        if(client.getConnection() instanceof NativeReceiveCarrier carrier&&!carrier.greenhouseObserverHealthy())return Optional.empty();
        var a=live.get(c);if(a==null||client.player==null||!client.player.isAlive()||client.level==null||client.getConnection()==null||client.gui.screen()!=null)return Optional.empty();
        var scope=a.custody();var e=a.evidence();long n=now();
        if(scope.player()!=client.player||scope.world()!=client.level||scope.connection()!=client.getConnection()||n<e.receivedAt()||n-e.receivedAt()>maxAge
                ||!e.context().equals(new Context(config.get(GreenhouseConfig.GREENHOUSE_PROFILE),config.get(GreenhouseConfig.GREENHOUSE_PLOT),client.level.dimension().identifier().toString()))||!loaded(c)||!fingerprint(c).equals(e.plantFingerprint())||!client.player.inventoryMenu.getCarried().isEmpty())return Optional.empty();
        return Optional.of(e.plant());
    }
    public Map<Cell,Plant> freshCells(Set<Cell> cells,long maxAge){var result=new HashMap<Cell,Plant>();for(Cell c:cells){var p=fresh(c,maxAge);if(p.isEmpty())return Map.of();result.put(c,p.get());}return Map.copyOf(result);}
    public long oldestReceipt(Set<Cell> cells){return cells.stream().map(live::get).filter(Objects::nonNull).mapToLong(a->a.evidence().receivedAt()).min().orElse(-1);}
    public String addProvenance(String snapshotJson){var json=JsonParser.parseString(snapshotJson).getAsJsonObject();json.add("diagnosticAssociations",new Gson().toJsonTree(List.copyOf(archive)));return json.toString();}
    public boolean confirmSaved(Associated a){
        if(association.state()!=State.ASSOCIATED||association.associated().isEmpty()||!association.associated().get().equals(a))return false;
        var selectedBefore=selected;var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
        var tokenBefore=receiptToken();
        if(lifecycle==null||!lifecycle.known()||tokenBefore==null||!root.lifecycleCurrent(lifecycle)
                ||!association.confirmDurable(persist,this::custody,this::receiptToken,this::now,()->finalNative(a,selectedBefore,tokenBefore,root,lifecycle))){
            // Refusal of an older save may not erase a newly received record for the same cell.
            var current=live.get(a.evidence().cell());if(current!=null&&current.evidence().request().equals(a.evidence().request()))live.remove(a.evidence().cell());
            archive.removeIf(e->e.request().equals(a.evidence().request()));return false;
        }
        live.put(a.evidence().cell(),a);return true;
    }
    /** Direct native leaf: no public clock, input-owner supplier, persistence, configuration or inventory-service callbacks. */
    private boolean finalNative(Associated a,Selection expectedSelection,ReceiptToken expectedReceipt,dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle){
        try{
            var c=a.custody();
            if(!client.isSameThread()||foreignAction||selected!=expectedSelection||!owner.isEnabled()||token==null||!token.equals(c.token())||epoch!=c.epoch()
                    ||!(client.getConnection() instanceof NativeReceiveCarrier carrier)||!carrier.greenhouseObserverHealthy()
                    ||client.player!=c.player()||client.level!=c.world()||client.getConnection()!=c.connection()||client.player.level()!=client.level
                    ||client.getCameraEntity()!=client.player||client.isPaused()||!client.player.isAlive()||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(lifecycle))return false;
            var player=client.player;var level=client.level;var menu=player.containerMenu;
            if(player.getInventory().getSelectedSlot()!=c.selectedSlot()||!menu.getCarried().isEmpty()||!loaded(c.rayCell())||!loaded(expectedSelection.cell()))return false;
            var slots=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++)slots.add(player.getInventory().getItem(i));
            var gear=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);for(var e:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))gear.put(e,player.getItemBySlot(e));
            var inventory=GreenhouseNativeInventory.capture(slots,gear,menu.getCarried(),player.getInventory().getSelectedSlot(),level.registryAccess());
            if(!c.inventory().equals(inventory)||!c.toolIdentity().equals(NativeStackIdentity.capture(player.getMainHandItem(),level.registryAccess())))return false;
            var picked=player.pick(3.5,1f,false);if(!(picked instanceof BlockHitResult h)||h.getType()!=HitResult.Type.BLOCK||!cell(h.getBlockPos()).equals(c.rayCell())
                    ||!h.getDirection().name().equals(c.face())||!new Position(h.getLocation().x,h.getLocation().y,h.getLocation().z).equals(c.hitPosition()))return false;
            var actualReceipt=captureReceiptToken();
            return expectedReceipt.same(actualReceipt)&&new Position(player.getX(),player.getY(),player.getZ()).equals(c.position())
                    &&Float.compare(player.getYRot(),c.yaw())==0&&Float.compare(player.getXRot(),c.pitch())==0
                    &&fingerprint(c.rayCell()).equals(c.targetFingerprint())&&fingerprint(expectedSelection.cell()).equals(c.plantFingerprint())
                    &&client.player==player&&client.level==level&&client.getConnection()==c.connection()&&player.containerMenu==menu
                    &&selected==expectedSelection&&token.equals(c.token())&&epoch==c.epoch()&&!foreignAction&&owner.isEnabled()
                    &&VeltraphBootstrap.moduleManager()==root&&root.lifecycleCurrent(lifecycle);
        }catch(RuntimeException lost){return false;}
    }
    public State state(){return association.state();}public String status(){return association.status();}
    public void cancel(){association.cancel();retainUnresolvedOrigin();if(installed==this)installed=null;issuing=false;}
}
