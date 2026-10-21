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
@Mixin(targets="net.minecraft.world.item.enchantment.ItemEnchantments")
public abstract class VeinWatchedEnchantmentsMixin implements VeinWatchGraphFields.Enchantments {
    @Shadow @Final private it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<?> enchantments;
    public it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<?> veltraph$watchEnchantments(){return enchantments;}
}
