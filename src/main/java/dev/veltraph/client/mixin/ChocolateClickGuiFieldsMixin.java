package dev.veltraph.client.mixin;
import dev.veltraph.client.chocolate.ChocolateClickNativeFields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
/** Direct original native field read; no observer, codec, callback or mutation. */
@Mixin(targets="net.minecraft.client.gui.Gui")
public abstract class ChocolateClickGuiFieldsMixin implements ChocolateClickNativeFields.GuiFields {
    @Shadow private net.minecraft.client.gui.screens.Screen screen;
    public net.minecraft.client.gui.screens.Screen chocolate$clickScreen(){return screen;}
}
