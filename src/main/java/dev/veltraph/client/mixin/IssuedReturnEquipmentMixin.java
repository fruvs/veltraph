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
@Mixin(targets="net.minecraft.world.entity.EntityEquipment")
public abstract class IssuedReturnEquipmentMixin implements IssuedReturnNativeFields.Equipment {
    @Shadow @Final private java.util.EnumMap<net.minecraft.world.entity.EquipmentSlot,net.minecraft.world.item.ItemStack> items;
    public java.util.EnumMap<net.minecraft.world.entity.EquipmentSlot,net.minecraft.world.item.ItemStack> veltraph$returnEquipmentItems(){return items;}
    @Inject(method={"set(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"},at=@At("HEAD"),require=1)
    private void veltraph$returnMutation0(CallbackInfoReturnable<?> ci){IssuedReturnMutationJournal.mutation(this);}
    @Inject(method={"setAll(Lnet/minecraft/world/entity/EntityEquipment;)V","clear()V"},at=@At("HEAD"),require=1)
    private void veltraph$returnBulk(CallbackInfo ci){IssuedReturnMutationJournal.mutation(this);}
}
