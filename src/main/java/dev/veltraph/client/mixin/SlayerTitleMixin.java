package dev.veltraph.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SlayerTitleMixin {
    @Inject(method="setTitleText",at=@At("TAIL"))
    private void veltraph$observeAttunement(ClientboundSetTitleTextPacket packet,CallbackInfo ci) {
        dev.veltraph.client.combat.slayer.ImpelSignal.receive(Minecraft.getInstance().level,packet.text().getString(),System.currentTimeMillis());
        dev.veltraph.client.combat.slayer.SlayerTitleSignal.receive(Minecraft.getInstance().level,packet.text().getString());
    }
    @Inject(method="setSubtitleText",at=@At("TAIL"))
    private void veltraph$observeImpel(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packet,CallbackInfo ci) {
        dev.veltraph.client.combat.slayer.ImpelSignal.receive(Minecraft.getInstance().level,packet.text().getString(),System.currentTimeMillis());
    }
    @Inject(method="handleTitlesClear",at=@At("TAIL"))
    private void veltraph$clearImpel(net.minecraft.network.protocol.game.ClientboundClearTitlesPacket packet,CallbackInfo ci) {
        dev.veltraph.client.combat.slayer.ImpelSignal.clear(Minecraft.getInstance().level);
    }
}
