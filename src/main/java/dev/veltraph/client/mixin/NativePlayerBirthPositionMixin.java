package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class NativePlayerBirthPositionMixin {
 @WrapMethod(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",require=1)
 private void veltraph$birthInitialPosition(ClientboundPlayerPositionPacket packet,Operation<Void> original){NativePlayerBirthProof.Episode e=null;try{e=NativePlayerBirthProof.positionEntered((ClientPacketListener)(Object)this,packet);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(packet);normal=true;}finally{try{NativePlayerBirthProof.positionExited(e,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setYRot(F)V"),require=1)
 private static void veltraph$birthServerYaw(Entity player,float value,Operation<Void> original){NativePlayerBirthProof.PoseAttempt a=null;try{a=NativePlayerBirthProof.poseEntered(player,true,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(player,value);normal=true;}finally{try{NativePlayerBirthProof.poseExited(a,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setXRot(F)V"),require=1)
 private static void veltraph$birthServerPitch(Entity player,float value,Operation<Void> original){NativePlayerBirthProof.PoseAttempt a=null;try{a=NativePlayerBirthProof.poseEntered(player,false,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(player,value);normal=true;}finally{try{NativePlayerBirthProof.poseExited(a,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setPos(Lnet/minecraft/world/phys/Vec3;)V"),require=1)
 private static void veltraph$birthServerPosition(Entity player,net.minecraft.world.phys.Vec3 position,Operation<Void> original){boolean normal=false;try{original.call(player,position);normal=true;}finally{try{NativePlayerBirthProof.actualPositionReturned(player,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1)
 private void veltraph$birthServerAck(net.minecraft.network.Connection connection,net.minecraft.network.protocol.Packet<?> packet,Operation<Void> original){try{NativePlayerBirthProof.ackRequested(connection,packet);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(connection,packet);normal=true;}finally{try{NativePlayerBirthProof.ackReturned(connection,packet,normal);}catch(RuntimeException|Error unavailable){}}}
}
