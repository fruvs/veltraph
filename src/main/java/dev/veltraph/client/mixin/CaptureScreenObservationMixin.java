package dev.veltraph.client.mixin;

import dev.veltraph.client.capture.CaptureGuiTools;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the installed native screen boundary, including transitions between sampling ticks. */
@Mixin(Gui.class)
public abstract class CaptureScreenObservationMixin {
    @Inject(method="setScreen", at=@At("RETURN"))
    private void veltraph$captureScreen(Screen screen, CallbackInfo ci) { CaptureGuiTools.nativeScreenChanged(); }
}
