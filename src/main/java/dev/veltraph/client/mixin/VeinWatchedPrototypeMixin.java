package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
@Mixin(targets="net.minecraft.core.component.DataComponentMap$Builder$SimpleMap")
public abstract class VeinWatchedPrototypeMixin implements VeinWatchNativeFields.PrototypeFields {
    @Shadow @Final private Reference2ObjectMap<?,?> map;
    public Reference2ObjectMap<?,?> veltraph$watchMap(){return map;}
}
