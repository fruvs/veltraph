package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(KeyMapping.class)
public abstract class NativeManualGuiReleaseKeyMixin {
 @WrapOperation(method="releaseAll()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;release()V"),require=1)
 private static void veltraph$manualGuiReleaseKey(KeyMapping key,Operation<Void> original){NativeGuiKeyResetLineage.Release observed=null;try{observed=NativeGuiKeyResetLineage.beforeRelease(key);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(key);normal=true;}finally{try{NativeGuiKeyResetLineage.afterRelease(observed,normal);}catch(RuntimeException|Error unavailable){}}}
}
