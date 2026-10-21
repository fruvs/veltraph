package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchGraphFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.world.item.component.CustomData")
public abstract class VeinWatchedCustomDataMixin implements VeinWatchGraphFields.Custom {
    @Shadow @Final private net.minecraft.nbt.CompoundTag tag;
    public net.minecraft.nbt.CompoundTag veltraph$watchTag(){return tag;}
    @Inject(method={"update(Ljava/util/function/Consumer;)Lnet/minecraft/world/item/component/CustomData;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation0(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method="update(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Consumer;)V",at=@At("HEAD"),require=1)
    private static void veltraph$watchStaticUpdate(net.minecraft.core.component.DataComponentType<?> type,net.minecraft.world.item.ItemStack stack,java.util.function.Consumer<?> update,CallbackInfo ci){VeinWatchedRevisions.mutation(stack);}
    @Inject(method="set(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/nbt/CompoundTag;)V",at=@At("HEAD"),require=1)
    private static void veltraph$watchStaticSet(net.minecraft.core.component.DataComponentType<?> type,net.minecraft.world.item.ItemStack stack,net.minecraft.nbt.CompoundTag tag,CallbackInfo ci){VeinWatchedRevisions.mutation(stack);}
}
