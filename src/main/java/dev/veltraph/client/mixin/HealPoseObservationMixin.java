package dev.veltraph.client.mixin;

import dev.veltraph.client.combat.heal.HealSourcePosePacketContract;
import dev.veltraph.client.combat.heal.mc.NativeHealPoseSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public final class HealPoseObservationMixin {
    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private void veltraph$position(ClientboundPlayerPositionPacket packet,CallbackInfo ci){
        NativeHealPoseSignal.observe(Minecraft.getInstance(),this,HealSourcePosePacketContract.Kind.PLAYER_POSITION);
    }
    @Inject(method="handleRotatePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;setYRot(F)V"))
    private void veltraph$rotation(ClientboundPlayerRotationPacket packet,CallbackInfo ci){
        NativeHealPoseSignal.observe(Minecraft.getInstance(),this,HealSourcePosePacketContract.Kind.PLAYER_ROTATION);
    }
}
