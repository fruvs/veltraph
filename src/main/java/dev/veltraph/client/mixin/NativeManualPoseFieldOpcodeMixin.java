package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;
/** Exact normal PUTFIELD return. Entry claims stay mod first, UI second, authenticated birth third. */
@Mixin(Entity.class)
public abstract class NativeManualPoseFieldOpcodeMixin {
 @WrapOperation(method="setYRot(F)V",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;yRot:F",opcode=Opcodes.PUTFIELD),require=1)
 private void veltraph$manualPoseYawField(Entity actor,float value,Operation<Void> original){
  var token=McNativeManualInputLineage.nativePoseFieldToken(actor,true);float requested=McNativeManualInputLineage.nativePoseFieldRequested(actor,true,value);boolean normal=false;
  try{original.call(actor,value);normal=true;}finally{McNativeManualInputLineage.nativePoseFieldReturned(token,actor,true,value,normal);try{NativePlayerBirthProof.nativePoseFieldReturned(token,actor,true,requested,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="setXRot(F)V",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/Entity;xRot:F",opcode=Opcodes.PUTFIELD),require=1)
 private void veltraph$manualPosePitchField(Entity actor,float value,Operation<Void> original){
  var token=McNativeManualInputLineage.nativePoseFieldToken(actor,false);float requested=McNativeManualInputLineage.nativePoseFieldRequested(actor,false,value);boolean normal=false;
  try{original.call(actor,value);normal=true;}finally{McNativeManualInputLineage.nativePoseFieldReturned(token,actor,false,value,normal);try{NativePlayerBirthProof.nativePoseFieldReturned(token,actor,false,requested,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
