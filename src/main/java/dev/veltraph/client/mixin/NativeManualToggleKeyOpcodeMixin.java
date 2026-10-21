package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import net.minecraft.client.*;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
/** Observes genuine native query/optional inner field semantics without changing McHeld or history primitives. */
@Mixin(ToggleKeyMapping.class)
public abstract class NativeManualToggleKeyOpcodeMixin {
 @Shadow @Final private BooleanSupplier needsToggle;
 @WrapMethod(method="setDown(Z)V")
 private void veltraph$manualToggleOuter(boolean down,Operation<Void> original){Object wart=dev.veltraph.client.service.mc.McHeldKeys.beginWartNativeMethod((KeyMapping)(Object)this,down,false);McNativeManualInputLineage.NativeToggleKeyAttempt observed=null;try{observed=McNativeManualInputLineage.beginNativeToggleKeySetter((KeyMapping)(Object)this,down,needsToggle);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(down);normal=true;}finally{try{McNativeManualInputLineage.finishNativeToggleKeySetter(observed,normal);}catch(RuntimeException|Error unavailable){}dev.veltraph.client.service.mc.McHeldKeys.endWartNativeMethod(wart);}}
 @WrapOperation(method="setDown(Z)V",at=@At(value="INVOKE",target="Ljava/util/function/BooleanSupplier;getAsBoolean()Z"),require=1)
 private boolean veltraph$manualToggleMode(BooleanSupplier source,Operation<Boolean> original){McNativeManualInputLineage.ToggleMode observed=null;try{observed=McNativeManualInputLineage.beginNativeToggleMode((KeyMapping)(Object)this,source);}catch(RuntimeException|Error unavailable){}boolean normal=false,result=false;try{result=original.call(source);normal=true;return result;}finally{try{McNativeManualInputLineage.finishNativeToggleMode(observed,result,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="setDown(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;setDown(Z)V"),require=2)
 private void veltraph$manualToggleSuper(ToggleKeyMapping receiver,boolean down,Operation<Void> original){Object wart=dev.veltraph.client.service.mc.McHeldKeys.beginWartNativeSuper(receiver,down);McNativeManualInputLineage.ToggleSuper observed=null;try{observed=McNativeManualInputLineage.beginNativeToggleSuper(receiver,down);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(receiver,down);normal=true;}finally{try{McNativeManualInputLineage.finishNativeToggleSuper(observed,normal);}catch(RuntimeException|Error unavailable){}dev.veltraph.client.service.mc.McHeldKeys.endWartNativeSuper(wart);}}
}
