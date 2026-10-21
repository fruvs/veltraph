package dev.veltraph.client.mixin;
import dev.veltraph.client.chocolate.ChocolateClickNativeFields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
/** Original native menu field only; no screen provider or semantic getter. */
@Mixin(targets="net.minecraft.client.gui.screens.inventory.AbstractContainerScreen")
public abstract class ChocolateClickContainerFieldsMixin implements ChocolateClickNativeFields.ContainerFields {
    @Shadow @Final protected net.minecraft.world.inventory.AbstractContainerMenu menu;
    public net.minecraft.world.inventory.AbstractContainerMenu chocolate$clickMenu(){return menu;}
}
