package dev.veltraph.client.combat.slayer;
import dev.veltraph.client.core.ModuleActivationGuard;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SlayerCommands {
    private SlayerCommands() {}
    public static void register(SlayerModule module,ModuleActivationGuard guard) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(ClientCommands.literal("vslayer")
                .executes(c->{c.getSource().sendFeedback(Component.literal(module.diagnostics()+" | /vslayer start | cancel | stop | status"));return 1;})
                .then(ClientCommands.literal("status").executes(c->{c.getSource().sendFeedback(Component.literal(module.diagnostics()));return 1;}))
                .then(ClientCommands.literal("start").executes(c->start(module,guard,false,c.getSource())))
                .then(ClientCommands.literal("cancel").executes(c->start(module,guard,true,c.getSource())))
                .then(ClientCommands.literal("stop").executes(c->{module.setEnabled(false);return 1;}))));
    }
    private static int start(SlayerModule module,ModuleActivationGuard guard,boolean cancel,FabricClientCommandSource source) {
        Minecraft.getInstance().execute(()->{
            try {
                if(module.isEnabled()) module.setEnabled(false);
                module.cancellationOnly(cancel);
                if(guard.start(module)) Minecraft.getInstance().gui.setScreen(null);
                else module.cancellationOnly(false);
            } catch(RuntimeException e) { source.sendError(Component.literal("Slayer: "+e.getMessage())); }
        });return 1;
    }
}
