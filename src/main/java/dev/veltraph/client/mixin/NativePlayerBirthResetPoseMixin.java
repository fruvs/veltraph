package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Only the original resetPos setXRot opcode. Reset outside an authenticated login receives no birth envelope. */
@Mixin(LocalPlayer.class)
public abstract class NativePlayerBirthResetPoseMixin {
 @WrapOperation(method="resetPos()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;setXRot(F)V"),require=1,allow=1)
 private void veltraph$birthResetPitch(LocalPlayer originalPlayer,float value,Operation<Void> original){
  NativePlayerBirthProof.PoseAttempt actual=null;try{actual=NativePlayerBirthProof.loginResetPitchEntered(originalPlayer,value);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(originalPlayer,value);normal=true;}
  finally{try{NativePlayerBirthProof.loginResetPitchExited(actual,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
