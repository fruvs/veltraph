package dev.veltraph.client.mixin;

import dev.veltraph.client.service.mc.NativeActionBarSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plain 26.3 server title-actionbar receipt; no cancellation, packet mutation or native payload loading. */
@Mixin(ClientPacketListener.class)
public final class ActionBarObservationMixin {
    @Inject(method="setActionBarText",at=@At("TAIL"))
    private void veltraph$observeActionBar(ClientboundSetActionBarTextPacket packet,CallbackInfo ci) {
        NativeActionBarSignal.observeTitle(Minecraft.getInstance(),this,packet.text());
    }
}
