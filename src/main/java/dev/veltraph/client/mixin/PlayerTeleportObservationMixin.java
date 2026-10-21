package dev.veltraph.client.mixin;

import dev.veltraph.client.rewarp.mc.NativeTeleportSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class PlayerTeleportObservationMixin {
    @Inject(method="handleMovePlayer",at=@At("TAIL"))
    private void veltraph$observeAppliedTeleport(ClientboundPlayerPositionPacket packet,CallbackInfo ci) {
        NativeTeleportSignal.observe(Minecraft.getInstance(),this);
    }
}
