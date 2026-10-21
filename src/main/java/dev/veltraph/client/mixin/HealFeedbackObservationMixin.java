package dev.veltraph.client.mixin;

import dev.veltraph.client.service.mc.NativeHealFeedbackSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual received system packet only; no cancellation, sending or packet mutation. */
@Mixin(ClientPacketListener.class)
public final class HealFeedbackObservationMixin {
    @Inject(method="handleSystemChat",at=@At("TAIL"))
    private void veltraph$healFeedback(ClientboundSystemChatPacket packet,CallbackInfo ci) {
        NativeHealFeedbackSignal.observeSystemChat(Minecraft.getInstance(),this,packet.content(),packet.overlay());
    }
}
