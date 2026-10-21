package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Each SDK query and optional restore remains exactly once, with its actual return or exception. */
@Mixin(KeyMapping.class)
public abstract class NativeManualGuiRestoreKeyMixin {
 @WrapOperation(method="restoreToggleStatesOnScreenClosed()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/ToggleKeyMapping;shouldRestoreStateOnScreenClosed()Z"),require=1)
 private static boolean veltraph$manualGuiRestoreQuery(ToggleKeyMapping key,Operation<Boolean> original){
  NativeGuiKeyResetLineage.Restore observed=null;try{observed=NativeGuiKeyResetLineage.beforeRestoreQuery(key);}catch(RuntimeException|Error unavailable){}
  boolean normal=false,result=false;try{result=original.call(key);normal=true;return result;}finally{try{NativeGuiKeyResetLineage.afterRestoreQuery(observed,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="restoreToggleStatesOnScreenClosed()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/ToggleKeyMapping;setDown(Z)V"),require=1)
 private static void veltraph$manualGuiRestoreSetter(ToggleKeyMapping key,boolean down,Operation<Void> original){
  NativeGuiKeyResetLineage.Restore observed=null;try{observed=NativeGuiKeyResetLineage.beforeRestoreSetter(key,down);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(key,down);normal=true;}finally{try{NativeGuiKeyResetLineage.afterRestoreSetter(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
