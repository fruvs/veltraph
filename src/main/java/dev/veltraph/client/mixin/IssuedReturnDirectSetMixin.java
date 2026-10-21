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
@Mixin(targets="net.minecraft.core.HolderSet$Direct")
public abstract class IssuedReturnDirectSetMixin implements IssuedReturnNativeFields.DirectSet {
    @Shadow @Final private java.util.List<?> contents;
    public java.util.List<?> veltraph$returnContents(){return contents;}
}
