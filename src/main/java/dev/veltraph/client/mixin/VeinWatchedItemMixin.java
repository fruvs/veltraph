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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.syncher.EntityDataAccessor;
@Mixin(ItemEntity.class)
public abstract class VeinWatchedItemMixin implements VeinWatchNativeFields.ItemFields {
    @Shadow @Final private static EntityDataAccessor<ItemStack> DATA_ITEM;
    public EntityDataAccessor<ItemStack> veltraph$watchItemAccessor(){return DATA_ITEM;}
    @Inject(method="setItem(Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),require=1)
    private void veltraph$watchItem(ItemStack item,CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
