package dev.veltraph.client.mixin;
import dev.veltraph.client.chocolate.ChocolateClickNativeFields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
/** Direct original native field read; no observer, codec, callback or mutation. */
@Mixin(targets="net.minecraft.client.gui.screens.Screen")
public abstract class ChocolateClickScreenFieldsMixin implements ChocolateClickNativeFields.ScreenFields {
    @Shadow @org.spongepowered.asm.mixin.Final protected net.minecraft.network.chat.Component title;
    public net.minecraft.network.chat.Component chocolate$clickTitle(){return title;}
}
