package dev.veltraph.client.mixin;

import dev.veltraph.client.galatea.McFigOwnedUseBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ordinal 12 is the inspected 26.3 normal queued-use loop; held/manual use has no armed ticket. */
@Mixin(Minecraft.class)
public abstract class FigOwnedUseMinecraftMixin {
    @Redirect(method="handleKeybinds",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;consumeClick()Z",ordinal=12),require=1)
    private boolean veltraph$figNormalUse(KeyMapping key){return McFigOwnedUseBridge.consumeNormal(key);}
    @Redirect(method="handleKeybinds",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;consumeClick()Z",ordinal=9),require=1)
    private boolean veltraph$figDrainUse(KeyMapping key){return McFigOwnedUseBridge.consumeDrain(key);}
    @Redirect(method="handleKeybinds",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;startUseItem()V",ordinal=0),require=1)
    private void veltraph$figQueuedUse(Minecraft client){McFigOwnedUseBridge.runQueuedUse(client,
            ()->((FigOwnedUseMinecraftInvoker)(Object)client).veltraph$figVanillaUse());}
    @Inject(method="handleKeybinds",at=@At("RETURN"),require=1)
    private void veltraph$figKeybindsReturn(CallbackInfo ci){McFigOwnedUseBridge.endKeybinds((Minecraft)(Object)this);}
}
