package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.MiningBlockReceipts;
import dev.veltraph.client.mining.vein.VeinNativeWearBridge;
import dev.veltraph.client.mining.vein.VeinNativePickupAdapter;
import net.minecraft.network.protocol.game.*;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class MiningBlockReceiptMixin {
 @Inject(method="handleBlockUpdate",at=@At("TAIL"),require=1)
 private void vein$block(ClientboundBlockUpdatePacket packet,CallbackInfo ci){MiningBlockReceipts.observeSafely(()->{MiningBlockReceipts.applied(this,packet.getPos(),packet.getBlockState());VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.airApplied(this,packet.getPos(),packet.getBlockState()));VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.airApplied(this,packet.getPos(),packet.getBlockState()));});}
 @Inject(method="lambda$handleChunkBlocksUpdate$0",at=@At("TAIL"),require=1)
 private void vein$section(BlockPos pos,BlockState state,CallbackInfo ci){MiningBlockReceipts.observeSafely(()->{MiningBlockReceipts.applied(this,pos,state);VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.airApplied(this,pos,state));VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.airApplied(this,pos,state));});}
 private static final String DISPATCH="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";
 @Inject(method="handleSetPlayerInventory",at=@At("HEAD"),require=1)
 private void vein$inventoryBefore(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())return;VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.inventoryBefore(this,p));}
 @Inject(method="handleSetPlayerInventory",at=@At("TAIL"),require=1)
 private void vein$inventoryAfter(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.inventoryAfter(this,p));}
 @Inject(method="handleContainerSetSlot",at=@At("HEAD"),require=1)
 private void vein$slotBefore(ClientboundContainerSetSlotPacket p,CallbackInfo ci){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())return;if(p.getContainerId()==0||p.getContainerId()==-2)VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.inventoryBefore(this,p));}
 @Inject(method="handleContainerSetSlot",at=@At("TAIL"),require=1)
 private void vein$slotAfter(ClientboundContainerSetSlotPacket p,CallbackInfo ci){VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.inventoryAfter(this,p));}
}
