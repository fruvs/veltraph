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
@Mixin(targets="net.minecraft.network.chat.TextColor")
public abstract class VeinWatchedColorMixin implements VeinWatchGraphFields.Color {
    @Shadow @Final private int value;
    public int veltraph$watchColorValue(){return value;}
    @Shadow @Final private String name;
    public String veltraph$watchColorName(){return name;}
}
