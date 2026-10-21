package dev.veltraph.client.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.veltraph.client.combat.ghost.GhostSourceWalkTerrainEpochs;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
/** Only geometry mutation position is observed; no NBT/account/packet content is retained or modified. */
@Mixin(ClientPacketListener.class)
public abstract class GhostTerrainBlockEntityDataObservationMixin {
    @Shadow private ClientLevel level;
    @Inject(method="handleBlockEntityData",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",shift=At.Shift.AFTER))
    private void veltraph$blockEntityData(ClientboundBlockEntityDataPacket packet,CallbackInfo ci){if(level!=null&&net.minecraft.client.Minecraft.getInstance().isSameThread()){var p=packet.getPos();GhostSourceWalkTerrainEpochs.changedCell(level,new Cell(p.getX(),p.getY(),p.getZ()));}}
}
