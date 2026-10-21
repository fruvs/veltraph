package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.*;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class IssuedFarmAppliedLandingMixin {
 @WrapMethod(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",require=1)
 private void veltraph$farmAppliedHandler(ClientboundPlayerPositionPacket packet,Operation<Void> original){var self=(ClientPacketListener)(Object)this;var i=IssuedFarmAppliedLanding.entered(self,packet);boolean normal=false;try{original.call(packet);normal=true;}finally{IssuedFarmAppliedLanding.exited(i,self,packet,normal);}}
 @WrapOperation(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"),require=1)
 private boolean veltraph$farmAppliedPosition(PositionMoveRotation change,Set<?> relatives,Entity player,boolean interpolate,Operation<Boolean> original){boolean normal=false;try{var result=original.call(change,relatives,player,interpolate);normal=true;return result;}finally{IssuedFarmAppliedLanding.applied((ClientPacketListener)(Object)this,player,normal);}}
 @WrapOperation(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1)
 private void veltraph$farmAckOperation(Connection connection,Packet<?> packet,Operation<Void> original){IssuedFarmAppliedLanding.ackRequested(connection,packet);boolean normal=false;try{original.call(connection,packet);normal=true;}finally{IssuedFarmAppliedLanding.acknowledged((ClientPacketListener)(Object)this,connection,packet,normal);}}
}
