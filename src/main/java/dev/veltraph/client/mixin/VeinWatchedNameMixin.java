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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import java.util.List;
@Mixin(MutableComponent.class)
public abstract class VeinWatchedNameMixin implements VeinWatchNativeFields.NameFields {
    @Shadow @Final private ComponentContents contents;
    @Shadow @Final private List<?> siblings;
    @Shadow private Style style;
    public ComponentContents veltraph$watchContents(){return contents;}
    public List<?> veltraph$watchSiblings(){return siblings;}
    public Style veltraph$watchStyle(){return style;}
    @Inject(method={"setStyle(Lnet/minecraft/network/chat/Style;)Lnet/minecraft/network/chat/MutableComponent;",
            "append(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
            "append(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;",
            "withStyle(Ljava/util/function/UnaryOperator;)Lnet/minecraft/network/chat/MutableComponent;",
            "withStyle(Lnet/minecraft/network/chat/Style;)Lnet/minecraft/network/chat/MutableComponent;",
            "withStyle([Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;",
            "withStyle(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;",
            "withColor(I)Lnet/minecraft/network/chat/MutableComponent;",
            "withColor(Lnet/minecraft/network/chat/TextColor;)Lnet/minecraft/network/chat/MutableComponent;",
            "withoutShadow()Lnet/minecraft/network/chat/MutableComponent;"},at=@At("HEAD"),require=1)
    private void veltraph$watchName(CallbackInfoReturnable<MutableComponent> ci){VeinWatchedRevisions.mutation(this);}
}
