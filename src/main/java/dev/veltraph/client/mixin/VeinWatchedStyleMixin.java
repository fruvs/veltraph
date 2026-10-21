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
@Mixin(targets="net.minecraft.network.chat.Style")
public abstract class VeinWatchedStyleMixin implements VeinWatchGraphFields.NativeStyle {
    @Shadow @Final private net.minecraft.network.chat.TextColor color;
    public net.minecraft.network.chat.TextColor veltraph$watchColor(){return color;}
    @Shadow @Final private Integer shadowColor;
    public Integer veltraph$watchShadow(){return shadowColor;}
    @Shadow @Final private Boolean bold;
    public Boolean veltraph$watchBold(){return bold;}
    @Shadow @Final private Boolean italic;
    public Boolean veltraph$watchItalic(){return italic;}
    @Shadow @Final private Boolean underlined;
    public Boolean veltraph$watchUnderlined(){return underlined;}
    @Shadow @Final private Boolean strikethrough;
    public Boolean veltraph$watchStrikethrough(){return strikethrough;}
    @Shadow @Final private Boolean obfuscated;
    public Boolean veltraph$watchObfuscated(){return obfuscated;}
    @Shadow @Final private net.minecraft.network.chat.ClickEvent clickEvent;
    public net.minecraft.network.chat.ClickEvent veltraph$watchClick(){return clickEvent;}
    @Shadow @Final private net.minecraft.network.chat.HoverEvent hoverEvent;
    public net.minecraft.network.chat.HoverEvent veltraph$watchHover(){return hoverEvent;}
    @Shadow @Final private String insertion;
    public String veltraph$watchInsertion(){return insertion;}
    @Shadow @Final private net.minecraft.network.chat.FontDescription font;
    public net.minecraft.network.chat.FontDescription veltraph$watchFont(){return font;}
}
