package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PatchedDataComponentMap.class)
public abstract class RetainedNamedLoadoutComponentsMixin {
    @Inject(method={"set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;","set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;","remove(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"},at=@At("HEAD"),require=1)
    private void named$componentResult(CallbackInfoReturnable<Object> ci){McNamedLoadoutPort.protectedComponentMutation(this);}
    @Inject(method={"applyPatch(Lnet/minecraft/core/component/DataComponentPatch;)V","applyPatch(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)V","restorePatch(Lnet/minecraft/core/component/DataComponentPatch;)V","clearPatch()V","setAll(Lnet/minecraft/core/component/DataComponentMap;)V"},at=@At("HEAD"),require=1)
    private void named$componentVoid(CallbackInfo ci){McNamedLoadoutPort.protectedComponentMutation(this);}
}
