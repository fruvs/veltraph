package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.observation.nativeinput.NativeKeyBirthProof;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Options.class)
public abstract class NativeManualOptionsBindingMixin {
 @WrapOperation(method="processOptions(Lnet/minecraft/client/Options$FieldAccess;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"),require=2)
 private void veltraph$manualConfiguredBinding(KeyMapping key,InputConstants.Key value,Operation<Void> original){NativeKeyBirthProof.ConfiguredBinding observed=null;try{observed=NativeKeyBirthProof.beginConfiguredBinding((Options)(Object)this,key,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(key,value);normal=true;}finally{try{NativeKeyBirthProof.finishConfiguredBinding(observed,normal);}catch(RuntimeException|Error unavailable){}}}
}
