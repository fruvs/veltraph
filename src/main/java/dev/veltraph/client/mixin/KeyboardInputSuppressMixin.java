package dev.veltraph.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.input.ScriptingInputLock;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


















@Mixin(KeyMapping.class)
public class KeyboardInputSuppressMixin {

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void veltraph$ownKeyboard(InputConstants.Key key, boolean pressed, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (ScriptingInputLock.shouldSuppressKeyboard(mc.gui.screen() != null, mc.level != null)) {
            ci.cancel();
        }
    }
}
