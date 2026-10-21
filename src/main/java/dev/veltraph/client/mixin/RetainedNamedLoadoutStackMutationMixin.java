package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Refusal-only actual native count/component setter history for protected live stack aliases. */
@Mixin(ItemStack.class)
public abstract class RetainedNamedLoadoutStackMutationMixin {
    @Inject(method={"setCount(I)V","applyComponentsAndValidate(Lnet/minecraft/core/component/DataComponentPatch;)V","applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V","applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V"},at=@At("HEAD"),require=1)
    private void named$stackVoid(CallbackInfo ci){McNamedLoadoutPort.protectedStackMutation((ItemStack)(Object)this);}
    @Inject(method={"set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;","set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;","remove(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;","update(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;","update(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;Ljava/util/function/UnaryOperator;)Ljava/lang/Object;"},at=@At("HEAD"),require=1)
    private void named$stackComponent(CallbackInfoReturnable<Object> ci){McNamedLoadoutPort.protectedStackMutation((ItemStack)(Object)this);}
}
