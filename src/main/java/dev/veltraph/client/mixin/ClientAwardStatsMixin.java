package dev.veltraph.client.mixin;

import dev.veltraph.client.metrics.mc.ClientAwardStatsSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TAIL runs after vanilla's main-thread guard and actual received statistics application. */
@Mixin(ClientPacketListener.class)
public abstract class ClientAwardStatsMixin {
    @Shadow private ClientLevel level;
    @Inject(method="handleAwardStats",at=@At("TAIL"))
    private void veltraph$receivedStats(ClientboundAwardStatsPacket packet,CallbackInfo ci){
        ClientAwardStatsSignal.capture(Minecraft.getInstance(),level,this,packet);
    }
}
