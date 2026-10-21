package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.observation.nativeinput.NativeKeyBirthProof;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Options.class)
public abstract class NativeManualOptionsBirthMixin {
 @Shadow private Minecraft minecraft;
 @WrapOperation(method="<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Options;load()V"),require=1)
 private void veltraph$manualConstructorLoad(Options options,Operation<Void> original){NativeKeyBirthProof.OptionsLoad observed=null;try{observed=NativeKeyBirthProof.beginOptionsLoad(options,minecraft);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(options);normal=true;}finally{try{NativeKeyBirthProof.finishOptionsLoad(observed,normal);}catch(RuntimeException|Error unavailable){}}}
 @Inject(method="<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V",at=@At("RETURN"),require=1)
 private void veltraph$manualOptionsBorn(Minecraft client,File folder,CallbackInfo ci){try{NativeKeyBirthProof.nativeOptionsConstructorReturned((Options)(Object)this,client);}catch(RuntimeException|Error unavailable){}}
}
