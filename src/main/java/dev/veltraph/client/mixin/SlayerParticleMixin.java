package dev.veltraph.client.mixin;

import dev.veltraph.client.combat.slayer.SlayerParticleSignal;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the source-supported Ichor association signal after ordinary client packet handling. */
@Mixin(ClientPacketListener.class)
public final class SlayerParticleMixin {
    @Shadow private ClientLevel level;
    @Inject(method="tryAddParticle(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;DDDDDD)Z",at=@At("RETURN"))
    private void veltraph$observeIchorOwner(ClientboundLevelParticlesPacket packet,double x,double y,double z,
                                          double vx,double vy,double vz,CallbackInfoReturnable<Boolean> ci) {
        var client=Minecraft.getInstance();
        // This private helper is called only after handleParticleEvent's main-thread guard.
        // Its XYZ arguments are the actual vanilla spawn point, including randomized bursts.
        if(client.player!=null&&level!=null&&client.level==level&&packet.particle().getType()==ParticleTypes.ENCHANT)
            SlayerParticleSignal.receive(level,new Vec3(x,y,z),client.player.tickCount*50L);
    }
}
