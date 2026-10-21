package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
public abstract class RetainedNamedLoadoutCursorMixin {
    @Inject(method="setCarried(Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),require=1)
    private void named$cursor(ItemStack next,CallbackInfo ci){McNamedLoadoutPort.cursorMutation(this,next);}
}
