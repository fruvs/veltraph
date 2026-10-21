package dev.veltraph.client.mixin;
import dev.veltraph.client.rewarp.mc.IssuedReturnNativeFields;
import dev.veltraph.client.rewarp.mc.IssuedReturnMutationJournal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.core.NonNullList")
public abstract class IssuedReturnListMixin implements IssuedReturnNativeFields.ListFields {
    @Shadow @Final private java.util.List<?> list;
    public java.util.List<?> veltraph$returnList(){return list;}
    @Shadow @Final private Object defaultValue;
    public Object veltraph$returnDefault(){return defaultValue;}
    @Inject(method={"set(ILjava/lang/Object;)Ljava/lang/Object;","remove(I)Ljava/lang/Object;"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation0(CallbackInfoReturnable<?> ci){IssuedReturnMutationJournal.mutation(this);}
    @Inject(method={"add(ILjava/lang/Object;)V","clear()V"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation1(CallbackInfo ci){IssuedReturnMutationJournal.mutation(this);}
}
