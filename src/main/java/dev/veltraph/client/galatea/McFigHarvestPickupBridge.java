package dev.veltraph.client.galatea;

import dev.veltraph.client.combat.slayer.SlayerPickupSignal;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.service.BlockBreakingService;
import dev.veltraph.client.service.mc.McBlockBreakingService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import static dev.veltraph.client.galatea.FigHarvestPickupLedger.*;

/** STAGED correlated receipt collector. It never commits/adopts a caller inventory fence. */
public final class McFigHarvestPickupBridge implements AutoCloseable {
    public record Binding(Scope scope,FigHarvestPickupLedger ledger,Set<BlockPos> originalTreeBlocks,
            long originalDeadlineMillis,BlockBreakingService breaking,BooleanSupplier revalidate) {
        public Binding{originalTreeBlocks=Set.copyOf(originalTreeBlocks);}
    }
    public record Event(long serial,String kind,long nativeMillis,String detail) {}
    private record Armed(BlockState original,BlockBreakingService.TargetGuardBinding guard) {}
    private static final class Operation {final long serial,at;final int sequence;final BlockPos target;final BlockState original;long stop,ack,ackAt;int stopSequence=-1;
        Operation(long s,long t,int q,BlockPos p,BlockState state){serial=s;at=t;sequence=q;target=p;original=state;}}
    private static final class Spawn {final UUID uuid;final int id;final Vec3 position;final long serial,at;long data,dataAt;
        NativeStackIdentity.Snapshot unit;int count;boolean ambiguous;
        Spawn(UUID u,int i,Vec3 p,long s,long t){uuid=u;id=i;position=p;serial=s;at=t;}}
    private record SpawnBefore(boolean absent) {}
    private record RemoveBefore(List<Spawn> tracked) {}
    private record TakeBefore(UUID uuid,int entityId,int amount,long afterSignal) {}
    private record NativeTake(Pickup pickup,NativeStackIdentity.Snapshot unit) {}
    private static final class InventoryBatch {final long serial,at;final List<Cell> before,after;final List<?> beforeFull,afterFull;boolean staged;
        InventoryBatch(long s,long t,List<Cell> b,List<Cell> a,List<?> bf,List<?> af){serial=s;at=t;before=b;after=a;beforeFull=bf;afterFull=af;}}
    private record InventoryBefore(List<Cell> cells,List<?> full) {}
    private static McFigHarvestPickupBridge installed;
    private record AckScope(Object listener,int sequence) {}
    private static final ThreadLocal<AckScope> ACK=new ThreadLocal<>();
    private final Minecraft client;private final Supplier<Binding> binding;private final Supplier<?> actualRootOwner;
    private Binding active;private long serial,lastNow;
    private final Map<BlockPos,Armed> armed=new HashMap<>();private final Map<BlockPos,Operation> operations=new HashMap<>();
    private final Map<UUID,Spawn> spawns=new LinkedHashMap<>();private final Map<UUID,NativeTake> takes=new LinkedHashMap<>();
    private final Set<UUID> foreignTakes=new HashSet<>(),mergedDonors=new HashSet<>();
    private final Map<UUID,FigHarvestMergeCorrelation.Removal> removals=new HashMap<>();
    private final Map<UUID,FigHarvestMergeCorrelation.Metadata> pendingMerges=new HashMap<>();
    private final ArrayDeque<FigHarvestMergeCorrelation.Proof> mergeProofs=new ArrayDeque<>();
    private final Set<UUID> used=new HashSet<>();private final Map<Object,Object> ingress=new IdentityHashMap<>();
    private final List<InventoryBatch> batches=new ArrayList<>();private final ArrayDeque<Event> events=new ArrayDeque<>();
    private McFigHarvestPickupBridge(Minecraft c,Supplier<Binding> b,Supplier<?> owner){client=Objects.requireNonNull(c);binding=Objects.requireNonNull(b);actualRootOwner=Objects.requireNonNull(owner);}
    public static McFigHarvestPickupBridge install(Minecraft c,Supplier<Binding> b,Supplier<?> actualSoleRootInputOwner){
        if(installed!=null)throw new IllegalStateException("Harvest bridge already installed");return installed=new McFigHarvestPickupBridge(c,b,actualSoleRootInputOwner);
    }
    /** Unsupported observation must not throw into or change vanilla packet/application behavior. */
    public static void observeSafely(Runnable observation){try{observation.run();}catch(RuntimeException|LinkageError unsupported){
        var self=installed;if(self!=null&&self.client.isSameThread())self.clear();}}
    private Binding current(){
        if(!client.isSameThread())return null;
        try{var b=binding.get();long now=System.currentTimeMillis();
            if(b==null||b.scope()==null||b.ledger()==null||b.breaking()==null||b.revalidate()==null||!b.revalidate().getAsBoolean()
                    ||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()
                    ||client.player.level()!=client.level||b.scope().player()!=client.player||b.scope().world()!=client.level
                    ||b.scope().connection()!=client.getConnection()||b.scope().owner()!=actualRootOwner.get()
                    ||b.originalDeadlineMillis()==Long.MAX_VALUE||now>b.originalDeadlineMillis()||now<lastNow){clear();return null;}
            if(active!=null&&active.scope().same(b.scope())&&(active.originalDeadlineMillis()!=b.originalDeadlineMillis()
                    ||active.ledger()!=b.ledger()||active.breaking()!=b.breaking()||!active.originalTreeBlocks().equals(b.originalTreeBlocks()))){clear();return null;}
            if(active==null||!active.scope().same(b.scope())){clear();active=b;b.ledger().bind(b.scope(),b.originalTreeBlocks(),b.originalDeadlineMillis());}
            lastNow=now;return active;
        }catch(RuntimeException unsupported){clear();return null;}
    }
    private void clear(){if(active!=null){event("SCOPE_RETIRED","");active.ledger().clear();}active=null;armed.clear();operations.clear();spawns.clear();takes.clear();used.clear();foreignTakes.clear();mergedDonors.clear();removals.clear();pendingMerges.clear();ingress.clear();batches.clear();}
    private long event(String kind,String detail){long n=++serial;String scope=active==null?"unbound":"activation="+active.scope().activationGeneration()+" tree="+active.scope().treeGeneration();
        events.addLast(new Event(n,kind,System.currentTimeMillis(),scope+" "+detail));
        while(events.size()>512)events.removeFirst();return n;}
    public List<FigHarvestMergeCorrelation.Proof> mergeProofs(){if(!client.isSameThread())throw new IllegalStateException("Client thread required");return List.copyOf(mergeProofs);}
    public List<Event> events(){if(!client.isSameThread())throw new IllegalStateException("Client thread required");return List.copyOf(events);}
    private static BlockPos pos(net.minecraft.core.BlockPos p){return new BlockPos(p.getX(),p.getY(),p.getZ());}
    private static net.minecraft.core.BlockPos nativePos(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    /** Before prediction: actual owned held breaking and exact target-guard context, not a submission. */
    public static void attempted(MultiPlayerGameMode gameMode,net.minecraft.core.BlockPos target){var self=installed;if(self==null)return;var b=self.current();if(b==null)return;
        var p=pos(target);var guard=b.breaking().targetGuardBinding(b.scope().owner()).orElse(null);
        if(gameMode!=self.client.gameMode||!b.originalTreeBlocks().contains(p)||!b.breaking().isBreaking()
                ||!self.client.options.keyAttack.isDown()||guard==null||!b.breaking().targetGuardBoundToCurrentContext(b.scope().owner(),guard)
                ||!McBlockBreakingService.allowsDestroyBlock(gameMode,target)||!self.client.level.hasChunkAt(target))return;
        var state=self.client.level.getBlockState(target);if(!state.isAir())self.armed.putIfAbsent(p,new Armed(state,guard));
    }
    /** Actual Connection.send(Packet) returned. Native submission is not server acceptance. */
    public static void submitted(Connection transport,Packet<?> packet){var self=installed;if(self==null||!(packet instanceof ServerboundPlayerActionPacket p))return;
        var b=self.current();if(b==null||transport!=self.client.getConnection().getConnection())return;var target=pos(p.getPos());
        if(p.getAction()==ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK){self.operations.remove(target);self.armed.remove(target);self.event("ABORT_SUBMITTED",target.toString());return;}
        if(p.getAction()!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK&&p.getAction()!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)return;
        var arm=self.armed.get(target);if(arm==null||!b.breaking().targetGuardBoundToCurrentContext(b.scope().owner(),arm.guard()))return;
        long stamp=self.event(p.getAction().name()+"_SUBMITTED",target+" sequence="+p.getSequence());long now=System.currentTimeMillis();
        if(p.getAction()==ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK){
            if(self.operations.containsKey(target)){self.operations.remove(target);self.armed.remove(target);return;} // Ambiguous restart never creates loot origin.
            self.operations.put(target,new Operation(stamp,now,p.getSequence(),target,arm.original()));
        }else{var op=self.operations.get(target);if(op!=null){op.stop=stamp;op.stopSequence=p.getSequence();}}
    }
    /** Authoritative server payload after setServerVerifiedBlockState; predicted client AIR is unused. */
    public static void blockApplied(Object listener,net.minecraft.core.BlockPos target,BlockState serverState){var self=installed;if(self==null)return;
        var b=self.current();if(b==null||listener!=self.client.getConnection())return;var op=self.operations.get(pos(target));
        if(op==null)return;String kind=ACK.get()==null?"SERVER_BLOCK":"SERVER_VERIFIED";
        long receipt=self.event(kind+(serverState.isAir()?"_AIR":"_STATE"),target.toString());
        if(!serverState.isAir()||op.ack!=0)return;op.ack=receipt;op.ackAt=System.currentTimeMillis();self.join();
    }
    public static void withinAck(Object listener,int sequence,Runnable vanillaApply){var previous=ACK.get();ACK.set(new AckScope(listener,sequence));
        try{observeSafely(()->{var self=installed;if(self!=null&&self.current()!=null)self.event("BLOCK_CHANGED_ACK","sequence="+sequence);});vanillaApply.run();}
        finally{if(previous==null)ACK.remove();else ACK.set(previous);}}
    /** State passed by native endPredictionsUpTo to syncBlockState, not the predicted world readout. */
    public static void serverVerifiedApplied(Object world,net.minecraft.core.BlockPos pos,BlockState verified){var scope=ACK.get();var self=installed;
        if(scope!=null&&self!=null&&world==self.client.level){
            McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.verifiedApplied(world,pos,verified,scope.listener(),scope.sequence()));
            var op=self.operations.get(pos(pos));
            int required=op==null?-1:op.stopSequence>=0?op.stopSequence:op.sequence;
            if(op!=null&&required>=0&&scope.sequence()>=required)blockApplied(scope.listener(),pos,verified);}}
    public static void addStarted(Object listener,Object packet,int entityId){var self=installed;if(self==null)return;
        var b=self.current();if(b!=null&&listener==self.client.getConnection())self.ingress.put(packet,new SpawnBefore(self.client.level.getEntity(entityId)==null));}
    public static void addApplied(Object listener,ClientboundAddEntityPacket packet){var self=installed;if(self==null)return;
        var b=self.current();var before=self.ingress.remove(packet);if(b==null||listener!=self.client.getConnection()||!(before instanceof SpawnBefore s)||!s.absent())return;
        if(!(self.client.level.getEntity(packet.getId()) instanceof ItemEntity item)||!item.getUUID().equals(packet.getUUID()))return;
        if(self.spawns.containsKey(item.getUUID())){self.spawns.get(item.getUUID()).ambiguous=true;return;}
        var point=new Vec3(packet.getX(),packet.getY(),packet.getZ());if(!Double.isFinite(point.x())||!Double.isFinite(point.y())||!Double.isFinite(point.z()))return;
        var voxel=new BlockPos((int)Math.floor(point.x()),(int)Math.floor(point.y()),(int)Math.floor(point.z()));
        if(!b.originalTreeBlocks().contains(voxel)||!self.operations.containsKey(voxel))return;
        long n=self.event("ITEM_SPAWN",item.getUUID()+" at="+point);
        self.spawns.put(item.getUUID(),new Spawn(item.getUUID(),item.getId(),point,n,System.currentTimeMillis()));
    }
    /** Called only for a packet containing the actual ItemEntity DATA_ITEM accessor id. */
    public static void itemDataApplied(Object listener,int entityId){var self=installed;if(self==null)return;var b=self.current();
        if(b==null||listener!=self.client.getConnection()||!(self.client.level.getEntity(entityId) instanceof ItemEntity item))return;
        var spawn=self.spawns.get(item.getUUID());if(spawn==null||spawn.id!=entityId)return;
        if(item.getItem().isEmpty()){spawn.ambiguous=true;self.event("ITEM_DATA_REJECT",spawn.uuid+" reason=empty-or-zero-count");return;}
        try{var unit=NativeStackIdentity.capture(item.getItem().copyWithCount(1),self.client.level.registryAccess());
            int observed=item.getItem().getCount();
            if(spawn.data!=0){
                boolean equal=Objects.equals(spawn.unit,unit);var pending=self.pendingMerges.get(spawn.uuid);
                self.event("ITEM_DATA_REPLAY",spawn.uuid+" cached="+spawn.count+" observed="+observed+" unitEqual="+equal+" tainted="+spawn.ambiguous);
                if(!equal||observed<spawn.count||pending!=null&&observed<pending.afterCount()){
                    spawn.ambiguous=true;self.pendingMerges.remove(spawn.uuid);self.event("ITEM_DATA_REJECT",spawn.uuid+" reason=unit-or-count-regression");return;}
                if(observed>spawn.count){
                    if(pending==null||pending.afterCount()!=observed){long serial=self.event("MERGE_METADATA_INCREASE",spawn.uuid+" before="+spawn.count+" after="+observed);
                        self.pendingMerges.put(spawn.uuid,new FigHarvestMergeCorrelation.Metadata(spawn.uuid,serial,System.currentTimeMillis(),unit,spawn.count,observed,item.getItem().getMaxStackSize()));}
                    self.join();
                }return;
            }
            spawn.unit=unit;spawn.count=observed;spawn.data=self.event("ITEM_STACK_DATA",spawn.uuid+" count="+spawn.count);spawn.dataAt=System.currentTimeMillis();self.join();
        }catch(RuntimeException unsupported){spawn.ambiguous=true;self.event("ITEM_DATA_REJECT",spawn.uuid+" reason=unsupported-full-codec");}
    }
    public static void takeStarted(Object listener,ClientboundTakeItemEntityPacket packet){var self=installed;if(self==null)return;var b=self.current();
        if(b==null||listener!=self.client.getConnection()||packet.getAmount()<=0)return;
        var currentEntity=self.client.level.getEntity(packet.getItemId());
        if(self.client.level.getEntity(packet.getPlayerId())!=self.client.player){
            var tracked=self.spawns.values().stream().filter(s->s.id==packet.getItemId()).toList();
            if(tracked.size()==1){var uuid=tracked.getFirst().uuid;self.foreignTakes.add(uuid);self.event("FOREIGN_TAKE_OBSERVED",uuid+" recipientId="+packet.getPlayerId()+" amount="+packet.getAmount());}return;}
        if(!(currentEntity instanceof ItemEntity item))return;

        self.ingress.put(packet,new TakeBefore(item.getUUID(),packet.getItemId(),packet.getAmount(),SlayerPickupSignal.serial(self.client.level,listener)));
    }
    /** Reuse the existing real pre-shrink SlayerPickupSignal receipt; no replacement capture or clearing. */
    public static void takeApplied(Object listener,ClientboundTakeItemEntityPacket packet){var self=installed;if(self==null)return;
        var b=self.current();var before=self.ingress.remove(packet);if(b==null||listener!=self.client.getConnection()||!(before instanceof TakeBefore t))return;
        var matches=SlayerPickupSignal.read(self.client.level,listener,self.client.player.getUUID(),t.afterSignal()).stream()
                .filter(r->r.item().equals(t.uuid())&&r.entityId()==t.entityId()&&r.amount()==t.amount()).toList();
        if(matches.size()!=1)return;var receipt=matches.getFirst();if(self.takes.containsKey(t.uuid())){var spawn=self.spawns.get(t.uuid());if(spawn!=null)spawn.ambiguous=true;return;}
        self.event("TAKE_ITEM",t.uuid()+" nativeSerial="+receipt.serial()+" amount="+receipt.amount());
        self.takes.put(t.uuid(),new NativeTake(new Pickup(t.uuid(),receipt.serial(),self.client.player,receipt.amount(),System.currentTimeMillis()),receipt.content()));self.join();
    }
    public static void removeStarted(Object listener,ClientboundRemoveEntitiesPacket packet){var self=installed;if(self==null)return;var b=self.current();
        if(b==null||listener!=self.client.getConnection())return;var tracked=new ArrayList<Spawn>();
        for(int id:packet.entityIds()){var entity=self.client.level.getEntity(id);if(!(entity instanceof ItemEntity item))continue;
            var spawn=self.spawns.get(item.getUUID());if(spawn!=null&&spawn.id==id)tracked.add(spawn);}
        self.ingress.put(packet,new RemoveBefore(List.copyOf(tracked)));
    }
    public static void removeApplied(Object listener,ClientboundRemoveEntitiesPacket packet){var self=installed;if(self==null)return;var b=self.current();var before=self.ingress.remove(packet);
        if(b==null||listener!=self.client.getConnection()||!(before instanceof RemoveBefore removed))return;
        for(var spawn:removed.tracked()){boolean absent=self.client.level.getEntity(spawn.id)==null;
            long serial=self.event("ITEM_REMOVED",spawn.uuid+" entityId="+spawn.id+" absent="+absent+" foreignTake="+self.foreignTakes.contains(spawn.uuid)+" localTake="+self.takes.containsKey(spawn.uuid));
            self.removals.put(spawn.uuid,new FigHarvestMergeCorrelation.Removal(spawn.uuid,spawn.id,serial,System.currentTimeMillis(),absent,self.foreignTakes.contains(spawn.uuid)||self.takes.containsKey(spawn.uuid)));}
        self.join();
    }
    private FigHarvestMergeCorrelation.Carrier carrier(Spawn spawn,boolean donor){var op=origin(spawn);if(op==null)return null;
        return new FigHarvestMergeCorrelation.Carrier(active.scope(),spawn.uuid,spawn.id,new Break(op.serial,op.target,op.at,op.ack,op.ackAt,op.original),
                spawn.position,spawn.serial,spawn.at,spawn.data,spawn.dataAt,spawn.unit,spawn.count,spawn.ambiguous,
                used.contains(spawn.uuid)||mergedDonors.contains(spawn.uuid)||foreignTakes.contains(spawn.uuid)||donor&&takes.containsKey(spawn.uuid));
    }
    private void tryMerges(){for(var entry:new ArrayList<>(pendingMerges.entrySet())){var destination=spawns.get(entry.getKey());
        if(destination==null)continue;var meta=entry.getValue();var donorList=new ArrayList<FigHarvestMergeCorrelation.Donor>();
        for(var spawn:spawns.values())if(!spawn.uuid.equals(destination.uuid)&&removals.containsKey(spawn.uuid)){
            var nativeCarrier=carrier(spawn,true);if(nativeCarrier!=null)donorList.add(new FigHarvestMergeCorrelation.Donor(nativeCarrier,removals.get(spawn.uuid)));}
        var proof=FigHarvestMergeCorrelation.correlate(carrier(destination,false),meta,donorList,active.scope(),active.originalDeadlineMillis());
        if(proof.isEmpty()){event("MERGE_PENDING_OR_REJECT",destination.uuid+" before="+meta.beforeCount()+" observed="+meta.afterCount()+" donorCandidates="+donorList.size());continue;}
        var original=active;var evidence=proof.orElseThrow();
        if(!evidence.commit(original.scope(),()->readonlyCurrent()!=null&&pendingMerges.get(destination.uuid)==meta&&!destination.ambiguous))continue;
        for(var donor:evidence.donors()){mergedDonors.add(donor.carrier().uuid());event("MERGE_DONOR_CORRELATED",donor.carrier().uuid()+" destination="+destination.uuid+" quantity="+donor.carrier().count()+" removalSerial="+donor.removal().nativeSerial());}
        destination.count=meta.afterCount();destination.data=meta.nativeSerial();destination.dataAt=meta.at();pendingMerges.remove(destination.uuid);
        mergeProofs.addLast(evidence);while(mergeProofs.size()>64)mergeProofs.removeFirst();event("MERGE_CORRELATED_NATIVE_CHAIN",destination.uuid+" available="+destination.count+" donorCount="+evidence.donors().size());
    }}
    private List<Cell> cells(){var result=new ArrayList<Cell>();for(int i=0;i<36;i++){var s=client.player.getInventory().getItem(i);
        result.add(new Cell(i,NativeStackIdentity.capture(s.isEmpty()?ItemStack.EMPTY:s.copyWithCount(1),client.level.registryAccess()),s.getCount(),NativeStackIdentity.capture(s,client.level.registryAccess())));}return List.copyOf(result);}
    private List<?> full(){var result=new ArrayList<NativeStackIdentity.Snapshot>();for(int i=0;i<client.player.getInventory().getContainerSize();i++)
        result.add(NativeStackIdentity.capture(client.player.getInventory().getItem(i),client.level.registryAccess()));return List.copyOf(result);}
    public static void inventoryStarted(Object listener,Object packet){var self=installed;if(self==null)return;var b=self.current();
        if(b==null||listener!=self.client.getConnection())return;try{self.ingress.put(packet,new InventoryBefore(self.cells(),self.full()));}catch(RuntimeException unsupported){/* Unknown identity denies adaptation. */}}
    public static void inventoryApplied(Object listener,Object packet){var self=installed;if(self==null)return;var b=self.current();var before=self.ingress.remove(packet);
        if(b==null||listener!=self.client.getConnection()||!(before instanceof InventoryBefore start))return;
        try{var after=self.cells();var afterFull=self.full();if(start.full().equals(afterFull))return;
            long n=self.event("INVENTORY_APPLIED",packet.getClass().getSimpleName());self.batches.add(new InventoryBatch(n,System.currentTimeMillis(),start.cells(),after,start.full(),afterFull));self.join();
        }catch(RuntimeException unsupported){/* No unsupported partial codec adoption. */}}
    private List<CellDelta> deltas(InventoryBatch batch){var values=new ArrayList<CellDelta>();for(int i=0;i<36;i++){
        var a=batch.before.get(i);var z=batch.after.get(i);if(a.canonicalFullIdentity().equals(z.canonicalFullIdentity()))continue;
        if(z.count()<=a.count()||a.count()>0&&!a.canonicalUnitIdentity().equals(z.canonicalUnitIdentity()))return List.of();values.add(new CellDelta(i,a,z));}return values;}
    private Operation origin(Spawn drop){if(drop.ambiguous||drop.data<=0||drop.unit==null||drop.count<=0)return null;
        var voxel=new BlockPos((int)Math.floor(drop.position.x()),(int)Math.floor(drop.position.y()),(int)Math.floor(drop.position.z()));
        var matches=operations.values().stream().filter(o->o.ack>0&&o.target.equals(voxel)&&o.at<=drop.at).toList();return matches.size()==1?matches.getFirst():null;}
    private boolean within(Operation op,long time){return op!=null&&time>=op.at&&time<=active.originalDeadlineMillis();}
    /** Unique receipt-set closure; old unrelated/oversized takes do not poison a newer exact batch. */
    private List<Spawn> unique(List<Spawn> candidates,long amount,ToIntFunction<Spawn> count){
        return FigHarvestReceiptAllocation.unique(candidates,amount,s->count.applyAsInt(s)).orElse(null);
    }
    private void join(){if(active==null)return;tryMerges();for(var batch:batches){if(batch.staged)continue;var changes=deltas(batch);if(changes.isEmpty())continue;
        var candidates=new ArrayList<Spawn>();for(var drop:spawns.values()){var op=origin(drop);var take=takes.get(drop.uuid);
            if(used.contains(drop.uuid)||mergedDonors.contains(drop.uuid)||foreignTakes.contains(drop.uuid)||take==null||!within(op,batch.at)||!within(op,drop.at)||!within(op,take.pickup().takenMillis())
                    ||!Objects.equals(drop.unit,take.unit())||take.pickup().amount()>drop.count)continue;
            if(changes.stream().anyMatch(c->Objects.equals(c.after().canonicalUnitIdentity(),drop.unit)))candidates.add(drop);}
        Map<Object,Long> needed=new HashMap<>();for(var d:changes)needed.merge(d.after().canonicalUnitIdentity(),(long)d.after().count()-d.before().count(),Long::sum);
        var selected=new ArrayList<Spawn>();boolean uniqueClosure=true;for(var entry:needed.entrySet()){
            var exact=unique(candidates.stream().filter(d->Objects.equals(d.unit,entry.getKey())).toList(),entry.getValue(),d->takes.get(d.uuid).pickup().amount());
            if(exact==null){uniqueClosure=false;break;}selected.addAll(exact);}
        if(!uniqueClosure){event("ALLOCATION_NO_UNIQUE_CLOSURE","inventorySerial="+batch.serial+" candidates="+candidates.size());continue;}candidates=selected;
        var remaining=new HashMap<Integer,Integer>();for(var d:changes)remaining.put(d.index(),d.after().count()-d.before().count());
        var chains=new ArrayList<Correlation>();for(var drop:candidates){int amount=takes.get(drop.uuid).pickup().amount();var allocated=new ArrayList<CellDelta>();
            for(var d:changes)if(Objects.equals(d.after().canonicalUnitIdentity(),drop.unit)&&amount>0){int n=Math.min(amount,remaining.get(d.index()));if(n>0){allocated.add(new CellDelta(d.index(),d.before(),d.after(),n));remaining.put(d.index(),remaining.get(d.index())-n);amount-=n;}}
            var op=origin(drop);chains.add(new Correlation(Evidence.CORRELATED_NATIVE_CHAIN,active.scope(),new Break(op.serial,op.target,op.at,op.ack,op.ackAt,op.original),
                    new Drop(drop.uuid,drop.id,op.target,drop.position,drop.unit,drop.count,drop.serial,drop.data,drop.at,1),takes.get(drop.uuid).pickup(),new Delivery(batch.serial,allocated,batch.at)));}
        boolean staged=active.ledger().stageBatch(chains);
        if(staged){batch.staged=true;for(var drop:candidates)used.add(drop.uuid);event("CORRELATED_NATIVE_CHAIN",Integer.toString(chains.size()));}
    }}
    /** Read-only staged contract. Root holds its production caller branch until native ordering review. */
    private Binding readonlyCurrent(){
        if(!client.isSameThread()||active==null)return null;
        try{var b=binding.get();long now=System.currentTimeMillis();return b!=null&&b.scope().same(active.scope())&&b.ledger()==active.ledger()
                &&b.originalDeadlineMillis()==active.originalDeadlineMillis()&&now>=lastNow&&now<=b.originalDeadlineMillis()
                &&b.breaking()==active.breaking()&&b.originalTreeBlocks().equals(active.originalTreeBlocks())
                &&b.revalidate().getAsBoolean()&&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level
                &&client.player==b.scope().player()&&client.level==b.scope().world()
                &&client.getConnection()==b.scope().connection()&&actualRootOwner.get()==b.scope().owner()?active:null;
        }catch(RuntimeException unsupported){return null;}
    }
    public boolean pendingExactDelivery(Scope scope,List<?> beforeFull,List<?> actualFull,Set<Integer> protectedSlots){var b=readonlyCurrent();
        if(b==null||!b.scope().same(scope)||beforeFull==null||actualFull==null||beforeFull.size()!=actualFull.size())return false;
        for(var batch:batches){if(batch.staged||!batch.beforeFull.equals(beforeFull)||!batch.afterFull.equals(actualFull))continue;
            var changes=deltas(batch);if(changes.isEmpty()||changes.stream().anyMatch(d->protectedSlots.contains(d.index())))continue;
            var expected=new ArrayList<Object>(beforeFull);for(var d:changes)expected.set(d.index(),d.after().canonicalFullIdentity());if(!expected.equals(actualFull))continue;
            Map<Object,Long> needed=new HashMap<>();for(var d:changes)needed.merge(d.after().canonicalUnitIdentity(),(long)d.after().count()-d.before().count(),Long::sum);
            var candidates=new ArrayList<Spawn>();for(var drop:spawns.values()){var op=origin(drop);var take=takes.get(drop.uuid);
                if(used.contains(drop.uuid)||mergedDonors.contains(drop.uuid)||!within(op,batch.at)||!within(op,drop.at)||take!=null
                        &&(!within(op,take.pickup().takenMillis())||!Objects.equals(take.unit(),drop.unit)||take.pickup().amount()>drop.count))continue;
                if(needed.containsKey(drop.unit))candidates.add(drop);}
            boolean exact=true,missingTake=false;for(var entry:needed.entrySet()){
                var selected=unique(candidates.stream().filter(d->Objects.equals(d.unit,entry.getKey())).toList(),entry.getValue(),d->takes.containsKey(d.uuid)?takes.get(d.uuid).pickup().amount():d.count);
                if(selected==null){exact=false;break;}missingTake|=selected.stream().anyMatch(d->!takes.containsKey(d.uuid));}
            if(exact&&missingTake)return true;
        }return false;
    }
    @Override public void close(){if(!client.isSameThread())throw new IllegalStateException("Client thread required");clear();if(installed==this)installed=null;}
}
