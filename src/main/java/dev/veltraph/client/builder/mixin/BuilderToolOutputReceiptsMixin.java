package dev.veltraph.client.builder.mixin;
import dev.veltraph.client.builder.BuilderToolOutputReceipts;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class BuilderToolOutputReceiptsMixin {
 @Inject(method="handleBlockUpdate",at=@At("TAIL"))
 private void veltraph$toolBlock(ClientboundBlockUpdatePacket packet,CallbackInfo ci){var p=packet.getPos();BuilderToolOutputReceipts.received(this,new BlockPos(p.getX(),p.getY(),p.getZ()),BuiltInRegistries.BLOCK.getKey(packet.getBlockState().getBlock()).toString());}
 @Inject(method="handleChunkBlocksUpdate",at=@At("TAIL"))
 private void veltraph$toolSection(ClientboundSectionBlocksUpdatePacket packet,CallbackInfo ci){packet.runUpdates((p,s)->BuilderToolOutputReceipts.received(this,new BlockPos(p.getX(),p.getY(),p.getZ()),BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString()));}
}
