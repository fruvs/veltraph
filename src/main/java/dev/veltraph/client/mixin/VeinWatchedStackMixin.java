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
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.PatchedDataComponentMap;
@Mixin(ItemStack.class)
public abstract class VeinWatchedStackMixin implements VeinWatchNativeFields.StackFields {
    @Shadow private int count;
    @Shadow @Final private Holder<?> item;
    @Shadow @Final private PatchedDataComponentMap components;
    public int veltraph$watchCount(){return count;}
    public Holder<?> veltraph$watchItem(){return item;}
    public PatchedDataComponentMap veltraph$watchComponents(){return components;}
    @Inject(method={"setCount(I)V","applyComponentsAndValidate(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchVoid(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
            "set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;",
            "remove(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
            "update(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;",
            "update(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;Ljava/util/function/UnaryOperator;)Ljava/lang/Object;"},at=@At("HEAD"),require=1)
    private void veltraph$watchComponent(CallbackInfoReturnable<Object> ci){VeinWatchedRevisions.mutation(this);}
}
