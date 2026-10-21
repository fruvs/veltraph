package dev.veltraph.client.rewarp.mc;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

/** Local intent and status only. Configured mode/enable preference remains the user's existing choice. */
public final class FarmReturnCommands {
    private FarmReturnCommands() {}
    public static void register(McSuspendedFarmReturnCoordinator original){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            ClientCommands.literal("vfarmreturn")
                .executes(c->{c.getSource().sendFeedback(Component.literal(original.diagnostics()));return Command.SINGLE_SUCCESS;})
                .then(ClientCommands.literal("start").executes(c->{
                    if(original.queueUserIntent())c.getSource().sendFeedback(Component.literal("Return requested for the current farm after chat closes."));
                    else c.getSource().sendError(Component.literal("Return request refused. Check the active farm and configured return settings."));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("cancel").executes(c->{
                    if(original.cancelUserIntentFromCommand())c.getSource().sendFeedback(Component.literal("Return cancelled."));
                    else c.getSource().sendError(Component.literal("The original return controller is unavailable."));
                    return Command.SINGLE_SUCCESS;
                }))));
    }
}
