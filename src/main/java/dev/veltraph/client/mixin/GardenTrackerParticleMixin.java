package dev.veltraph.client.mixin;

import com.mojang.serialization.JsonOps;
import dev.veltraph.client.garden.pests.tracker.GardenTrackerParticles;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records actual vanilla spawn XYZ/velocity and serialized particle options after native handling. */
@Mixin(ClientPacketListener.class)
public final class GardenTrackerParticleMixin {
    @Shadow private ClientLevel level;
    @Inject(method="tryAddParticle(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;DDDDDD)Z",at=@At("RETURN"))
    private void veltraph$observeGardenTracker(ClientboundLevelParticlesPacket packet,double x,double y,double z,double vx,double vy,double vz,CallbackInfoReturnable<Boolean> ci){
        var client=Minecraft.getInstance();if(client.player==null||client.level!=level||level==null||client.getConnection()!=(Object)this)return;
        var options=ParticleTypes.CODEC.encodeStart(JsonOps.INSTANCE,packet.particle()).result();if(options.isEmpty())return;
        GardenTrackerParticles.receive(client.player,level,client.getConnection(),BuiltInRegistries.PARTICLE_TYPE.getKey(packet.particle().getType()).toString(),options.get().toString(),
            new Vec3(x,y,z),new Vec3(vx,vy,vz),client.player.tickCount*50L,Boolean.TRUE.equals(ci.getReturnValue()));
    }
}
