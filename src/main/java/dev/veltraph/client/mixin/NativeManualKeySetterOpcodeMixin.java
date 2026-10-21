package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
/** Independent observation: existing NormalIdle/McHeld claims and primitive semantics remain unchanged. */
@Mixin(KeyMapping.class)
public abstract class NativeManualKeySetterOpcodeMixin {
 @WrapMethod(method="setDown(Z)V")
 private void veltraph$manualKeySetter(boolean down,Operation<Void> original){
  Object wart=dev.veltraph.client.service.mc.McHeldKeys.beginWartNativeMethod((KeyMapping)(Object)this,down,true);
  McNativeManualInputLineage.NativeKeyAttempt observed=null;try{observed=McNativeManualInputLineage.beginNativeKeySetter((KeyMapping)(Object)this,down);}catch(RuntimeException|Error unavailable){}McNativeManualInputLineage.MouseKeyAttempt mouseObserved=null;try{mouseObserved=McNativeManualInputLineage.beginNativeMouseKeySetter((KeyMapping)(Object)this,down);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(down);normal=true;}finally{try{McNativeManualInputLineage.finishNativeMouseKeySetter(mouseObserved,normal);}catch(RuntimeException|Error unavailable){}try{McNativeManualInputLineage.finishNativeKeySetter(observed,normal);}catch(RuntimeException|Error unavailable){}dev.veltraph.client.service.mc.McHeldKeys.endWartNativeMethod(wart);}
 }
 @WrapOperation(method="setDown(Z)V",at=@At(value="FIELD",target="Lnet/minecraft/client/KeyMapping;isDown:Z",opcode=Opcodes.PUTFIELD),require=1)
 private void veltraph$manualActualKeyWrite(KeyMapping key,boolean down,Operation<Void> original){
  Object token=null;try{token=McNativeManualInputLineage.claimNativeKeySetter(key,down);if(token==null)token=McNativeManualInputLineage.claimNativeToggleKeySetter(key,down);if(token==null)token=McNativeManualInputLineage.claimNativeMouseKeySetter(key,down);if(token==null)token=dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage.claimField(key,down);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{dev.veltraph.client.service.mc.McHeldKeys.beforeWartNativeField(key,down);original.call(key,down);normal=true;}finally{try{McNativeManualInputLineage.nativeKeyFieldReturned(token,key,down,normal);}catch(RuntimeException|Error unavailable){}try{McNativeManualInputLineage.nativeToggleKeyFieldReturned(token,key,down,normal);}catch(RuntimeException|Error unavailable){}try{McNativeManualInputLineage.nativeMouseKeyFieldReturned(token,key,down,normal);}catch(RuntimeException|Error unavailable){}try{dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage.fieldReturned(token,key,down,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
