package dev.veltraph.client.mixin;

import dev.veltraph.client.observation.unexpected.UnexpectedActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes a native chat-screen submission category without retaining/logging submitted text. */
@Mixin(ChatScreen.class)
public class UnexpectedCommandObservationMixin {
    @Inject(method="handleChatInput(Ljava/lang/String;Z)V",at=@At("HEAD"))
    private void veltraph$nativeNavigationSubmission(String submitted,boolean addToHistory,CallbackInfo ci) {
        if(Minecraft.getInstance().gui.screen()==(Object)this)UnexpectedActions.nativeNavigationCommand(submitted);
    }
}
