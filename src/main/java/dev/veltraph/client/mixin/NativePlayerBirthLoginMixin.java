package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class NativePlayerBirthLoginMixin {
 @WrapMethod(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",require=1)
 private void veltraph$birthLogin(ClientboundLoginPacket packet,Operation<Void> original){NativePlayerBirthProof.Episode e=null;try{e=NativePlayerBirthProof.loginEntered((ClientPacketListener)(Object)this,packet);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(packet);normal=true;}finally{try{NativePlayerBirthProof.loginExited(e,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;resetPos()V"),require=1,allow=1)
 private void veltraph$birthInitialReset(LocalPlayer originalPlayer,Operation<Void> original){
  NativePlayerBirthProof.LoginResetScope scope=null;try{scope=NativePlayerBirthProof.loginResetEntered((ClientPacketListener)(Object)this,originalPlayer);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(originalPlayer);normal=true;}
  finally{try{NativePlayerBirthProof.loginResetExited(scope,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="FIELD",target="Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;",opcode=181),require=1)
 private void veltraph$birthAssignedPlayer(Minecraft c,LocalPlayer player,Operation<Void> original){original.call(c,player);try{NativePlayerBirthProof.installed(c,player);}catch(RuntimeException|Error unavailable){}}
 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;addEntity(Lnet/minecraft/world/entity/Entity;)V"),require=1)
 private void veltraph$birthAddedPlayer(ClientLevel level,Entity player,Operation<Void> original){boolean normal=false;try{original.call(level,player);normal=true;}finally{try{NativePlayerBirthProof.added(level,player,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;setYRot(F)V"),require=1)
 private void veltraph$birthInitialYaw(LocalPlayer player,float yaw,Operation<Void> original){NativePlayerBirthProof.PoseAttempt a=null;try{a=NativePlayerBirthProof.poseEntered(player,true,yaw);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(player,yaw);normal=true;}finally{try{NativePlayerBirthProof.poseExited(a,normal);}catch(RuntimeException|Error unavailable){}}}

 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="FIELD",target="Lnet/minecraft/client/Minecraft;gameMode:Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;",opcode=181),require=1)
 private void veltraph$birthAssignedGameMode(Minecraft client,MultiPlayerGameMode gameMode,Operation<Void> original){
  NativePlayerBirthProof.InstallationAttempt attempt=null;try{attempt=NativePlayerBirthProof.gameModeInstallationEntered(client,gameMode);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(client,gameMode);normal=true;}finally{try{NativePlayerBirthProof.installationReturned(attempt,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V"),require=1)
 private void veltraph$birthInstalledWorld(Minecraft client,ClientLevel world,Operation<Void> original){
  NativePlayerBirthProof.InstallationAttempt attempt=null;try{attempt=NativePlayerBirthProof.worldInstallationEntered(client,world);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(client,world);normal=true;}finally{try{NativePlayerBirthProof.installationReturned(attempt,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
