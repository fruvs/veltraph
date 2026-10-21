package dev.veltraph.client.mixin;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Finalizes retained genuine initialization only; no fresh pose/key history is captured. */
@Mixin(Minecraft.class)
public abstract class NativePlayerBirthFinalizeMixin {
 @Inject(method="tick()V",at=@At("HEAD"),require=1)
 private void veltraph$birthFinalizeRetained(CallbackInfo ci){try{NativePlayerBirthProof.finalizeRetainedInitialization((Minecraft)(Object)this);}catch(RuntimeException|Error unavailable){}}
}
