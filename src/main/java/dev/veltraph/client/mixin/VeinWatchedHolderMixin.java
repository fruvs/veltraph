package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
@Mixin(Holder.Reference.class)
public abstract class VeinWatchedHolderMixin implements VeinWatchNativeFields.HolderFields {
    @Shadow private ResourceKey<?> key;
    public ResourceKey<?> veltraph$watchKey(){return key;}
    @Inject(method={"bindKey(Lnet/minecraft/resources/ResourceKey;)V","bindValue(Ljava/lang/Object;)V",
            "bindComponents(Lnet/minecraft/core/component/DataComponentMap;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchHolder(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
