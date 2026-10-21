package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.fishing.worm.WormOwnedCatch;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded local evidence of owned reel -> new native item -> local pickup -> actual inventory packet. */
public final class WormCatchPacketBridge {
    private static WormCatchPacketBridge installed;
    private final Minecraft client;private final WormOwnedCatch.Scope scope;private final WormOwnedCatch ledger;
    private final int rod;
    private final BooleanSupplier valid;private final long issued,deadline;private final net.minecraft.world.phys.Vec3 hookPoint;
    private final Set<UUID> existing=new HashSet<>();private final Map<UUID,WormOwnedCatch.Spawn> spawns=new HashMap<>();
    private final Map<UUID,WormOwnedCatch.Take> takes=new HashMap<>();private final IdentityHashMap<Object,InventoryBefore> before=new IdentityHashMap<>();
    private final List<BatchWithStacks> batches=new ArrayList<>();private boolean ambiguous,closed;
    private record InventoryBefore(List<WormOwnedCatch.Cell> cells,dev.veltraph.client.fishing.worm.WormInventoryPayload.Payload payload){}
    private record BatchWithStacks(WormOwnedCatch.Batch batch,List<ItemStack> items){}
    private WormCatchPacketBridge(Minecraft client,WormOwnedCatch.Scope scope,long issued,long deadline,int rod,int tool,net.minecraft.world.phys.Vec3 point,BooleanSupplier valid){this.client=client;this.scope=scope;this.issued=issued;this.deadline=deadline;this.hookPoint=point;this.valid=valid;this.rod=rod;ledger=new WormOwnedCatch(scope,issued,deadline,rod,tool);for(var entity:client.level.entitiesForRendering())if(entity instanceof ItemEntity)existing.add(entity.getUUID());}
    public static WormCatchPacketBridge begin(Minecraft client,WormOwnedCatch.Scope scope,long issued,long deadline,int rod,int tool,net.minecraft.world.phys.Vec3 hook,BooleanSupplier valid){var next=new WormCatchPacketBridge(client,scope,issued,deadline,rod,tool,hook,valid);if(installed!=null)installed.close();installed=next;return next;}
    private boolean current(Object listener){if(closed||!client.isSameThread()||client.player!=scope.player()||client.level!=scope.world()||client.getConnection()!=scope.connection()||listener!=scope.connection()||System.currentTimeMillis()>deadline)return false;try{return valid.getAsBoolean();}catch(Throwable unavailable){return false;}}
    public void close(){closed=true;spawns.clear();takes.clear();before.clear();batches.clear();if(installed==this)installed=null;}
    private static void safely(java.util.function.Consumer<WormCatchPacketBridge> action){var self=installed;if(self==null)return;try{action.accept(self);}catch(Throwable unavailable){self.ambiguous=true;}}
    public static void spawnApplied(Object listener,ClientboundAddEntityPacket packet){safely(self->{if(!self.current(listener)||!(self.client.level.getEntity(packet.getId()) instanceof ItemEntity item)||self.existing.contains(item.getUUID()))return;
        if(item.position().distanceTo(self.hookPoint)>1.5)return;if(self.spawns.size()>=8){self.ambiguous=true;return;}
        if(!self.spawns.isEmpty())self.ambiguous=true;self.spawns.put(item.getUUID(),new WormOwnedCatch.Spawn(item.getUUID(),item.getId(),"pending",0,System.currentTimeMillis(),true,true,false,item.getOwner()!=null&&item.getOwner()!=self.client.player));});}
    public static void takeStarted(Object listener,ClientboundTakeItemEntityPacket packet){safely(self->{if(!self.current(listener)||!(self.client.level.getEntity(packet.getItemId()) instanceof ItemEntity item))return;var spawn=self.spawns.get(item.getUUID());if(spawn==null)return;
        if(self.client.level.getEntity(packet.getPlayerId())!=self.client.player||packet.getAmount()<=0||item.getItem().isEmpty()||packet.getAmount()>item.getItem().getCount()){self.ambiguous=true;return;}
        var unit=NativeStackIdentity.capture(item.getItem().copyWithCount(1),self.client.level.registryAccess());self.spawns.put(item.getUUID(),new WormOwnedCatch.Spawn(item.getUUID(),item.getId(),unit,item.getItem().getCount(),spawn.at(),true,true,!self.ambiguous,item.getOwner()!=null&&item.getOwner()!=self.client.player));
        self.takes.put(item.getUUID(),new WormOwnedCatch.Take(item.getUUID(),item.getId(),unit,packet.getAmount(),System.currentTimeMillis(),true,false));});}
    public static void takeApplied(Object listener,ClientboundTakeItemEntityPacket packet){safely(self->{if(!self.current(listener))return;for(var entry:new ArrayList<>(self.takes.entrySet())){var take=entry.getValue();if(take.id()==packet.getItemId()&&take.count()==packet.getAmount())self.takes.put(entry.getKey(),new WormOwnedCatch.Take(take.uuid(),take.id(),take.unit(),take.count(),take.at(),true,true));}});}
    private List<WormOwnedCatch.Cell> cells(List<ItemStack> items){var cells=new ArrayList<WormOwnedCatch.Cell>();for(var item:items)cells.add(new WormOwnedCatch.Cell(NativeStackIdentity.capture(item.isEmpty()?ItemStack.EMPTY:item.copyWithCount(1),client.level.registryAccess()),item.getCount(),NativeStackIdentity.capture(item,client.level.registryAccess())));return List.copyOf(cells);}
    private List<ItemStack> items(){var items=new ArrayList<ItemStack>();for(int i=0;i<36;i++){var item=client.player.getInventory().getItem(i).copy();if(i==rod)item.remove(net.minecraft.core.component.DataComponents.DAMAGE);items.add(item);}return List.copyOf(items);}
    public static void inventoryStarted(Object listener,Object packet){safely(self->{if(!self.current(listener))return;if(self.before.size()>=8){self.ambiguous=true;return;}int target=-1;ItemStack contents=null;
        if(packet instanceof ClientboundSetPlayerInventoryPacket p){target=p.slot();contents=p.contents();}
        else if(packet instanceof ClientboundContainerSetSlotPacket p){target=dev.veltraph.client.fishing.worm.WormInventoryPayload.inventoryIndexForMenuSlot(p.getContainerId(),p.getSlot());contents=p.getItem();}
        if(target<0||target>=36||contents==null)return;var payload=contents.copy();if(target==self.rod)payload.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        var identity=NativeStackIdentity.capture(payload,self.client.level.registryAccess());
        self.before.put(packet,new InventoryBefore(self.cells(self.items()),new dev.veltraph.client.fishing.worm.WormInventoryPayload.Payload(target,identity,true)));});}
    public static void inventoryApplied(Object listener,Object packet){safely(self->{if(!self.current(listener))return;var start=self.before.remove(packet);if(start==null)return;var items=self.items();var after=self.cells(items);if(!dev.veltraph.client.fishing.worm.WormInventoryPayload.applied(start.payload(),start.cells(),after))return;if(self.batches.size()>=8){self.ambiguous=true;return;}self.batches.add(new BatchWithStacks(new WormOwnedCatch.Batch(self.scope,start.cells(),after,System.currentTimeMillis(),true),items));});}
    /** Updates only the exact correlated cell; unrelated reservation entries remain untouched. */
    public Optional<List<ItemStack>> adopt(List<ItemStack> reserved,boolean hookRemoved){if(!current(scope.connection())||ambiguous||spawns.size()!=1||takes.size()!=1)return Optional.empty();var spawn=spawns.values().iterator().next();var take=takes.values().iterator().next();var reservation=cells(reserved);
        for(var batch:batches){var proof=ledger.correlate(scope,reservation,spawn,take,batch.batch,hookRemoved);if(proof.isEmpty())continue;if(!ledger.consume(scope,proof.get()))return Optional.empty();var result=new ArrayList<>(reserved);result.set(proof.get().slot(),batch.items.get(proof.get().slot()).copy());return Optional.of(List.copyOf(result));}return Optional.empty();}
}
