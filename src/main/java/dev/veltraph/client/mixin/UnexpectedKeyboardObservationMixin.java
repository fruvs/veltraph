package dev.veltraph.client.mixin;
import dev.veltraph.client.observation.unexpected.UnexpectedActions;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Passive native UI callback; never infers a human identity or captures typed text. */
@Mixin(KeyboardHandler.class)
public class UnexpectedKeyboardObservationMixin {
    @Inject(method="keyPress(JILnet/minecraft/client/input/KeyEvent;)V",at=@At("HEAD"))
    private void veltraph$nativeKeyboard(long window,int action,KeyEvent event,CallbackInfo ci) {UnexpectedActions.keyboard(window,action,event);}
    @Inject(method="keyPress(JILnet/minecraft/client/input/KeyEvent;)V",at=@At("TAIL"))
    private void veltraph$appliedNativeKeyboard(long window,int action,KeyEvent event,CallbackInfo ci) {UnexpectedActions.keyboardApplied(window,action,event);}
}
