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
@Mixin(targets="net.minecraft.core.HolderSet$Named")
public abstract class IssuedReturnNamedSetMixin implements IssuedReturnNativeFields.NamedSet {
    @Shadow private java.util.List<?> contents;
    public java.util.List<?> veltraph$returnContents(){return contents;}
    @Shadow @Final private net.minecraft.tags.TagKey<?> key;
    public net.minecraft.tags.TagKey<?> veltraph$returnTag(){return key;}
    @Inject(method={"bind(Ljava/util/List;)V"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation0(CallbackInfo ci){IssuedReturnMutationJournal.mutation(this);}
}
