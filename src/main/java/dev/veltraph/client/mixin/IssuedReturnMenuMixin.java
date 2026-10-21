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
@Mixin(targets="net.minecraft.world.inventory.AbstractContainerMenu")
public abstract class IssuedReturnMenuMixin implements IssuedReturnNativeFields.Menu {
    @Shadow private net.minecraft.world.item.ItemStack carried;
    public net.minecraft.world.item.ItemStack veltraph$returnCursor(){return carried;}
    @Inject(method={"setCarried(Lnet/minecraft/world/item/ItemStack;)V","initializeContents(ILjava/util/List;Lnet/minecraft/world/item/ItemStack;)V"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation0(CallbackInfo ci){IssuedReturnMutationJournal.mutation(this);}
}
