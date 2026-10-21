package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory;
import net.minecraft.client.KeyMapping;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(KeyMapping.class)
public abstract class NativeManualKeyBindingOpcodeMixin {
 @WrapOperation(method="setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",at=@At(value="FIELD",target="Lnet/minecraft/client/KeyMapping;key:Lcom/mojang/blaze3d/platform/InputConstants$Key;",opcode=Opcodes.PUTFIELD),require=1)
 private void veltraph$manualBindingField(KeyMapping key,InputConstants.Key value,Operation<Void> original){NativeKeyBindingHistory.Write observed=null;try{observed=NativeKeyBindingHistory.beforeField(key,value);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(key,value);normal=true;}finally{try{NativeKeyBindingHistory.fieldReturned(observed,key,value,normal);}catch(RuntimeException|Error unavailable){}}}
}
