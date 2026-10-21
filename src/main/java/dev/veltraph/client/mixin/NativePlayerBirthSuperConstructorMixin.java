package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LivingEntity.class)
public abstract class NativePlayerBirthSuperConstructorMixin {
 @WrapOperation(method="<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setYRot(F)V"),require=1)
 private void veltraph$birthSuperclassYaw(LivingEntity receiver,float value,Operation<Void> original){NativePlayerBirthProof.PoseAttempt a=null;try{a=NativePlayerBirthProof.superConstructorYawEntered(receiver,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(receiver,value);normal=true;}finally{try{NativePlayerBirthProof.poseExited(a,normal);}catch(RuntimeException|Error unavailable){}}}
}
