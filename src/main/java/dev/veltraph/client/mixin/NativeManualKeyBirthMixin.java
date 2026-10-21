package dev.veltraph.client.mixin;
import dev.veltraph.client.observation.nativeinput.NativeKeyBirthProof;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Actual mapped constructors; descriptor pins show no vanilla setDown call in their bodies. */
@Mixin(KeyMapping.class)
public abstract class NativeManualKeyBirthMixin {
 // Name-only selectors default to one member; each actual constructor must be selected explicitly.
 @Inject(method={
     "<init>(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;)V",
     "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)V",
     "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V"
 },at=@At("RETURN"),require=3,allow=3)
 private void veltraph$manualKeyBorn(CallbackInfo ci){NativeKeyBirthProof.nativeConstructorReturned((KeyMapping)(Object)this);}
}
