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
import net.minecraft.network.chat.contents.TranslatableContents;
@Mixin(TranslatableContents.class)
public abstract class VeinWatchedTranslationMixin implements VeinWatchNativeFields.TranslationFields {
    @Shadow @Final private String key;
    @Shadow @Final private String fallback;
    @Shadow @Final private Object[] args;
    public String veltraph$watchKey(){return key;}
    public String veltraph$watchFallback(){return fallback;}
    public Object[] veltraph$watchArgs(){return args;}
}
