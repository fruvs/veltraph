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
@Mixin(targets="net.minecraft.world.entity.player.Inventory")
public abstract class IssuedReturnInventoryMixin implements IssuedReturnNativeFields.Items {
    @Shadow @Final private net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items;
    public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> veltraph$returnItems(){return items;}
    @Shadow @Final private net.minecraft.world.entity.EntityEquipment equipment;
    public net.minecraft.world.entity.EntityEquipment veltraph$returnInventoryEquipment(){return equipment;}
    @Shadow private int selected;
    public int veltraph$returnSelected(){return selected;}
    @Inject(method="setSelectedSlot(I)V",at=@At("HEAD"),require=1)
    private void veltraph$returnSelectedMutation(int slot,CallbackInfo ci){IssuedReturnMutationJournal.selectedSlotMutation(this,slot);}
    @Inject(method={"setItem(ILnet/minecraft/world/item/ItemStack;)V","clearContent()V","removeItem(Lnet/minecraft/world/item/ItemStack;)V"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation0(CallbackInfo ci){IssuedReturnMutationJournal.mutation(this);}
    @Inject(method={"removeItem(II)Lnet/minecraft/world/item/ItemStack;","removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation1(CallbackInfoReturnable<?> ci){IssuedReturnMutationJournal.mutation(this);}
}
