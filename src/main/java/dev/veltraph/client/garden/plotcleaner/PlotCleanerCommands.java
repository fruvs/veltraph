package dev.veltraph.client.garden.plotcleaner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import dev.veltraph.client.core.ModuleActivationGuard;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

/** Local explicit preparation. Only start enters the existing shared activation guard. */
public final class PlotCleanerCommands {
    private PlotCleanerCommands() {}

    public static void register(PlotCleanerModule module, ModuleActivationGuard guard) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommands.literal("vplotcleaner")
                        .executes(c -> inspect(module, c.getSource()))
                        .then(ClientCommands.literal("inspect").executes(c -> inspect(module, c.getSource())))
                        .then(ClientCommands.literal("clear").executes(c -> clear(module, c.getSource())))
                        .then(prepareArguments(module))
                        .then(ClientCommands.literal("start").executes(c -> guard.start(module) ? 1 : 0))
                        .then(ClientCommands.literal("stop").executes(c -> { module.setEnabled(false); return 1; }))));
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> prepareArguments(PlotCleanerModule module) {
        var last = ClientCommands.argument("maxZ", IntegerArgumentType.integer(-240, 239))
                .executes(c -> {
                    try {
                        var p = new PlotCleanerPlan(integer(c,"plot"), integer(c,"minX"), integer(c,"minY"),
                                integer(c,"minZ"), integer(c,"maxX"), integer(c,"maxY"), integer(c,"maxZ"));
                        var invalid = module.preparationCheck(p);
                        if (invalid.isPresent()) return error(c.getSource(), invalid.get());
                        module.request(p);
                        c.getSource().sendFeedback(Component.literal("Prepared " + p.plotMarker() + " | " + p.scopeMarker()
                                + ". No operation started. Verify the ordinary mining counterpart settings, then use /vplotcleaner start or its module card."));
                        return 1;
                    } catch (RuntimeException invalid) { return error(c.getSource(), invalid.getMessage()); }
                });
        return ClientCommands.literal("prepare")
                .then(ClientCommands.argument("plot", IntegerArgumentType.integer(1,24))
                .then(ClientCommands.argument("minX", IntegerArgumentType.integer(-240,239))
                .then(ClientCommands.argument("minY", IntegerArgumentType.integer(66,319))
                .then(ClientCommands.argument("minZ", IntegerArgumentType.integer(-240,239))
                .then(ClientCommands.argument("maxX", IntegerArgumentType.integer(-240,239))
                .then(ClientCommands.argument("maxY", IntegerArgumentType.integer(66,319)).then(last)))))));
    }

    private static int integer(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c, String name) {
        return IntegerArgumentType.getInteger(c,name);
    }
    private static int inspect(PlotCleanerModule module, FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(module.preparedPlan()
                .map(p -> "Prepared " + p.plotMarker() + " | " + p.scopeMarker() + " | " + p.volume() + " cells")
                .orElse("No prepared cleaner bounds")));
        source.sendFeedback(Component.literal("/vplotcleaner prepare <plot> <minX> <minY> <minZ> <maxX> <maxY> <maxZ> | inspect | clear | start | stop. Explicit ordinary mining counterpart; native bulk-row ability is unrecovered."));
        return 1;
    }
    private static int clear(PlotCleanerModule module, FabricClientCommandSource source) {
        try { module.clearRequest(); source.sendFeedback(Component.literal("Inactive prepared cleaner bounds cleared; no operation started.")); return 1; }
        catch (RuntimeException invalid) { return error(source,invalid.getMessage()); }
    }
    private static int error(FabricClientCommandSource source, String message) {
        source.sendError(Component.literal("Plot cleaner: " + message)); return 0;
    }
}
