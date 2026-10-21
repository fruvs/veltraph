package dev.veltraph.client.mixin;

import dev.veltraph.client.input.ScriptingInputLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scripting input ownership: while a Veltraph macro is running, gameplay mouse-look is suppressed so
 * the deterministic farming aim isn't fought by physical mouse movement. It cancels the in-game look
 * update at HEAD — but only in-world and only when no screen is open, so Veltraph UI screens (and any
 * vanilla screen) still receive full mouse input. The suppression releases automatically the moment
 * {@link ScriptingInputLock#isActive()} becomes false (stop / disable / disconnect).
 */
@Mixin(MouseHandler.class)
public class MouseLookSuppressMixin {

    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
    private void veltraph$suppressLook(double timeDelta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (ScriptingInputLock.shouldSuppressLook(mc.gui.screen() != null, mc.level != null)) {
            ci.cancel();
        }
    }
}
