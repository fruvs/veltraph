package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Existing setter histories record these privately pending real SDK opcode tokens only. */
@Mixin(Entity.class)
public abstract class NativeManualEntityTurnOpcodeMixin {
 @WrapOperation(method="turn(DD)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setYRot(F)V"),require=1)
 private void veltraph$manualTurnYaw(Entity player,float value,Operation<Void> original){
  McNativeManualInputLineage.NativePoseAttempt observed=null;try{observed=McNativeManualInputLineage.beginNativePoseOpcode(player,true,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(player,value);normal=true;}finally{try{McNativeManualInputLineage.finishNativePoseOpcode(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="turn(DD)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setXRot(F)V"),require=2)
 private void veltraph$manualTurnPitch(Entity player,float value,Operation<Void> original){
  McNativeManualInputLineage.NativePoseAttempt observed=null;try{observed=McNativeManualInputLineage.beginNativePoseOpcode(player,false,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(player,value);normal=true;}finally{try{McNativeManualInputLineage.finishNativePoseOpcode(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
