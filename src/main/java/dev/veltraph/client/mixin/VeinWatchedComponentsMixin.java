package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.component.PatchedDataComponentMap;
@Mixin(PatchedDataComponentMap.class)
public abstract class VeinWatchedComponentsMixin implements VeinWatchNativeFields.ComponentsFields {
    @Shadow @Final private net.minecraft.core.component.DataComponentMap prototype;
    @Shadow private it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> patch;
    public net.minecraft.core.component.DataComponentMap veltraph$watchPrototype(){return prototype;}
    public it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> veltraph$watchPatch(){return patch;}
    @Inject(method={"set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
            "set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;",
            "remove(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"},at=@At("HEAD"),require=1)
    private void veltraph$watchReturned(CallbackInfoReturnable<Object> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"applyPatch(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyPatch(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)V",
            "restorePatch(Lnet/minecraft/core/component/DataComponentPatch;)V","clearPatch()V",
            "setAll(Lnet/minecraft/core/component/DataComponentMap;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchMap(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
