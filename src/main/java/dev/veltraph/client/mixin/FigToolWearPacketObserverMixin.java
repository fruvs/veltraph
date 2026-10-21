package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigToolWearBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class FigToolWearPacketObserverMixin {
    private static final String DISPATCH="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";
    @Inject(method="handleBlockUpdate",at=@At("TAIL"),require=1)
    private void wear$block(ClientboundBlockUpdatePacket p,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.airApplied(this,p.getPos(),p.getBlockState(),-1));}
    @Inject(method="lambda$handleChunkBlocksUpdate$0",at=@At("TAIL"),require=1)
    private void wear$section(BlockPos pos,BlockState state,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.airApplied(this,pos,state,-1));}
    @Inject(method="handleSetPlayerInventory",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void wear$playerBefore(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.inventoryStarted(this,p));}
    @Inject(method="handleSetPlayerInventory",at=@At("TAIL"),require=1)
    private void wear$playerAfter(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.inventoryApplied(this,p));}
    @Inject(method="handleContainerSetSlot",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void wear$slotBefore(ClientboundContainerSetSlotPacket p,CallbackInfo ci){if(p.getContainerId()==0||p.getContainerId()==-2)McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.inventoryStarted(this,p));}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"),require=1)
    private void wear$slotAfter(ClientboundContainerSetSlotPacket p,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.inventoryApplied(this,p));}
    @Inject(method="handleContainerContent",at=@At(value="INVOKE",target=DISPATCH,shift=At.Shift.AFTER),require=1)
    private void wear$contentBefore(ClientboundContainerSetContentPacket p,CallbackInfo ci){if(p.containerId()==0)McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.inventoryStarted(this,p));}
    @Inject(method="handleContainerContent",at=@At("TAIL"),require=1)
    private void wear$contentAfter(ClientboundContainerSetContentPacket p,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.inventoryApplied(this,p));}
}
