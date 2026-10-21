package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigHarvestPickupBridge;
import net.minecraft.client.multiplayer.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All preapplication captures follow native thread dispatch; applied captures run at native TAIL. */
@Mixin(ClientPacketListener.class)
public abstract class FigHarvestPacketObserverMixin {
    private static final String DISPATCH="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";
    @Inject(method="handleAddEntity",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void fig$addBefore(ClientboundAddEntityPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.addStarted(this,p,p.getId());});}
    @Inject(method="handleAddEntity",at=@At("TAIL"),require=1)
    private void fig$addAfter(ClientboundAddEntityPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.addApplied(this,p);});}
    @Inject(method="handleRemoveEntities",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void fig$removeBefore(ClientboundRemoveEntitiesPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->McFigHarvestPickupBridge.removeStarted(this,p));}
    @Inject(method="handleRemoveEntities",at=@At("TAIL"),require=1)
    private void fig$removeAfter(ClientboundRemoveEntitiesPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->McFigHarvestPickupBridge.removeApplied(this,p));}
    @Inject(method="handleSetEntityData",at=@At("TAIL"),require=1)
    private void fig$data(ClientboundSetEntityDataPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{if(p.packedItems().stream().anyMatch(v->v.id()==FigHarvestItemDataAccessor.fig$stackData().id()))McFigHarvestPickupBridge.itemDataApplied(this,p.id());});}
    @Inject(method="handleTakeItemEntity",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void fig$takeBefore(ClientboundTakeItemEntityPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.takeStarted(this,p);});}
    @Inject(method="handleTakeItemEntity",at=@At("TAIL"),require=1)
    private void fig$takeAfter(ClientboundTakeItemEntityPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.takeApplied(this,p);});}
    @Inject(method="handleBlockUpdate",at=@At("TAIL"),require=1)
    private void fig$block(ClientboundBlockUpdatePacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.blockApplied(this,p.getPos(),p.getBlockState());});}
    @Inject(method="lambda$handleChunkBlocksUpdate$0",at=@At("TAIL"),require=1)
    private void fig$section(BlockPos pos,BlockState state,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.blockApplied(this,pos,state);});}
    @Redirect(method="handleBlockChangedAck",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;handleBlockChangedAck(I)V"),require=1)
    private void fig$ack(ClientLevel level,int sequence){McFigHarvestPickupBridge.withinAck(this,sequence,()->level.handleBlockChangedAck(sequence));}
    @Inject(method="handleSetPlayerInventory",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void fig$playerBefore(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.inventoryStarted(this,p);});}
    @Inject(method="handleSetPlayerInventory",at=@At("TAIL"),require=1)
    private void fig$playerAfter(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.inventoryApplied(this,p);});}
    @Inject(method="handleContainerSetSlot",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void fig$slotBefore(ClientboundContainerSetSlotPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{if(p.getContainerId()==0||p.getContainerId()==-2)McFigHarvestPickupBridge.inventoryStarted(this,p);});}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"),require=1)
    private void fig$slotAfter(ClientboundContainerSetSlotPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.inventoryApplied(this,p);});}
    @Inject(method="handleContainerContent",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void fig$contentBefore(ClientboundContainerSetContentPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{if(p.containerId()==0)McFigHarvestPickupBridge.inventoryStarted(this,p);});}
    @Inject(method="handleContainerContent",at=@At("TAIL"),require=1)
    private void fig$contentAfter(ClientboundContainerSetContentPacket p,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->{McFigHarvestPickupBridge.inventoryApplied(this,p);});}
}
