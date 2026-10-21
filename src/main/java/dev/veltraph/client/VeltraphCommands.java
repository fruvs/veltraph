package dev.veltraph.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.veltraph.client.command.CommandTarget;
import dev.veltraph.client.command.DebugLogCommand;
import dev.veltraph.client.command.HealCommand;
import dev.veltraph.client.command.HudCommand;
import dev.veltraph.client.command.HydraCommand;
import dev.veltraph.client.command.LocationDebugReport;
import dev.veltraph.client.command.RewarpCommand;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.HudConfig;
import dev.veltraph.client.config.HydraConfig;
import dev.veltraph.client.config.VisitorConfig;
import dev.veltraph.client.command.VisitorCommand;
import dev.veltraph.client.command.DevCommand;
import dev.veltraph.client.garden.visitor.VisitorAdvisor;
import dev.veltraph.client.inventory.selling.GuardedSellingModule;
import dev.veltraph.client.inventory.selling.SellingInspectorCommands;
import dev.veltraph.client.combat.heal.CombatAutoHeal;
import dev.veltraph.client.hydra.HydraAntiStaff;
import dev.veltraph.client.hydra.Whitelist;
import dev.veltraph.client.rewarp.RewarpCommandHandler;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.game.LocationResolution;
import dev.veltraph.client.game.LocationSignals;
import dev.veltraph.client.log.DebugLogController;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.ChatService;
import dev.veltraph.client.ui.VeltraphScreen;
import dev.veltraph.client.ui.VeltraphScreens;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-side commands. These are registered through Fabric's client command API and are
 * <b>never sent to the server</b> — they only open Veltraph screens or print local debug output.
 *
 * <ul>
 *   <li>{@code /veltraph} → Settings UI</li>
 *   <li>{@code /vmacro} and {@code /veltraphmacro} → Automations UI</li>
 *   <li>{@code /veltraphdebug location} and {@code /vdebug location} → print raw + normalized
 *       location evidence to chat and the client log</li>
 * </ul>
 */
public final class VeltraphCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("Veltraph");

    private VeltraphCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            if(VeltraphBootstrap.chocolateFactory()!=null)
                dev.veltraph.client.chocolate.ChocolateFactoryCommands.register(dispatcher,
                        VeltraphBootstrap.chocolateFactory(),VeltraphBootstrap.guard());
            dispatcher.register(ClientCommands.literal("veltraph")
                    .then(ClientCommands.literal("debug")
                            .then(logSubtree()))
                    .then(ClientCommands.literal("galatea")
                            .then(ClientCommands.literal("reset").executes(ctx -> {
                                ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                        VeltraphBootstrap.requestGalateaReset()?"[Veltraph] Galatea reset requested; awaiting ordinary guarded handoff.":"[Veltraph] Galatea reset unavailable: check idle parent, permission, exact bindings and active owners."));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(ClientCommands.literal("farming")
                            .then(rewarpSubtree()))
                    .executes(ctx -> openLater(() -> VeltraphScreens.settings(VeltraphBootstrap.guard()))));
            dispatcher.register(ClientCommands.literal("vmacro")
                    .executes(ctx -> openLater(() -> VeltraphScreens.automations(VeltraphBootstrap.guard()))));
            dispatcher.register(ClientCommands.literal("veltraphmacro")
                    .executes(ctx -> openLater(() -> VeltraphScreens.automations(VeltraphBootstrap.guard()))));

            dispatcher.register(ClientCommands.literal("veltraphdebug")
                    .then(ClientCommands.literal("location")
                            .executes(ctx -> printLocationDebug()))
                    .then(ClientCommands.literal("macro")
                            .executes(ctx -> printMacroDebug()))
                    .then(rewarpSubtree())
                    .then(hydraSubtree())
                    .then(visitorSubtree())
                    .then(sellingSubtree())
                    .then(healSubtree())
                    .then(hudSubtree())
                    .then(devSubtree())
                    .then(logSubtree()));
            dispatcher.register(ClientCommands.literal("vdebug")
                    .then(ClientCommands.literal("unusual").executes(ctx->{
                        for(String line:VeltraphBootstrap.unexpectedDiagnostics())
                            ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(line));
                        return 1;
                    }))
                    .then(ClientCommands.literal("location")
                            .executes(ctx -> printLocationDebug()))
                    .then(ClientCommands.literal("macro")
                            .executes(ctx -> printMacroDebug()))
                    .then(rewarpSubtree())
                    .then(hydraSubtree())
                    .then(visitorSubtree())
                    .then(sellingSubtree())
                    .then(healSubtree())
                    .then(hudSubtree())
                    .then(devSubtree())
                    .then(logSubtree()));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> sellingSubtree() {
        return SellingInspectorCommands.subtree(() -> {
            var manager = VeltraphBootstrap.moduleManager();
            return manager == null ? null : manager.get(GuardedSellingModule.ID)
                    .filter(GuardedSellingModule.class::isInstance)
                    .map(GuardedSellingModule.class::cast).orElse(null);
        });
    }

    /**
     * The shared {@code log [args...]} subtree used under {@code /vdebug}, {@code /veltraphdebug}, and
     * {@code /veltraph debug}. A greedy-string tail is routed through the pure {@link DebugLogCommand}
     * parser, so every documented form (true/false/ture, mode, status) is handled in one place.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> logSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("log")
                .executes(ctx -> applyLog(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyLog(StringArgumentType.getString(ctx, "args"))));
    }

    /**
     * The {@code rewarp <add|clear|replace [index]|list>} subtree, reused under {@code /veltraph
     * farming}, {@code /vdebug}, and {@code /veltraphdebug}. A greedy-string tail is routed through the
     * pure {@link RewarpCommand} parser + {@link RewarpCommandHandler}.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> rewarpSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("rewarp")
                .executes(ctx -> applyRewarp(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyRewarp(StringArgumentType.getString(ctx, "args"))));
    }

    /**
     * The shared {@code hydra [status|on|off]} subtree used under {@code /vdebug} and
     * {@code /veltraphdebug}. A greedy-string tail is routed through the pure {@link HydraCommand}
     * parser; the local anti-staff feature is read/toggled client-side only (never sent to the server).
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> hydraSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("hydra")
                .executes(ctx -> applyHydra(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyHydra(StringArgumentType.getString(ctx, "args"))));
    }

    /**
     * The shared {@code visitor [status|on|off|filter <…>]} subtree used under {@code /vdebug} and
     * {@code /veltraphdebug}. Routed through the pure {@link VisitorCommand} parser; the Garden-visitor
     * advisor is read/toggled client-side only (it never clicks/buys/serves — advisory recommendations).
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> visitorSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("visitor")
                .executes(ctx -> applyVisitor(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyVisitor(StringArgumentType.getString(ctx, "args"))));
    }

    /** Parse a {@code visitor} argument tail and apply it to the local Garden-visitor advisor (client-side). */
    private static int applyVisitor(String tail) {
        ChatService chat = VeltraphBootstrap.chat();
        VeltraphLog.basic("Visitors", "/vdebug visitor " + tail);
        VisitorAdvisor advisor = VeltraphBootstrap.visitor();
        ConfigService cfg = VeltraphBootstrap.config();
        if (advisor == null || cfg == null) {
            emit(chat, "[Veltraph] Garden Visitors is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        VisitorCommand parsed = VisitorCommand.parse(tail);
        if (!parsed.valid()) {
            emit(chat, "[Veltraph] " + parsed.error());
            return Command.SINGLE_SUCCESS;
        }
        switch (parsed.action()) {
            case STATUS -> { /* status printed below */ }
            case ON -> {
                cfg.set(VisitorConfig.VISITOR_ADVISOR_ENABLED, true);
                VeltraphBootstrap.saveConfig();
            }
            case OFF -> {
                cfg.set(VisitorConfig.VISITOR_ADVISOR_ENABLED, false);
                VeltraphBootstrap.saveConfig();
            }
            case FILTER -> {
                cfg.set(filterKey(parsed.filter()), parsed.enable());
                VeltraphBootstrap.saveConfig();
            }
            case DEBUG -> {
                for (String line : advisor.debugDump()) {
                    emit(chat, "[Veltraph] " + line);
                }
                return Command.SINGLE_SUCCESS;
            }
        }
        emit(chat, "[Veltraph] " + advisor.status());
        return Command.SINGLE_SUCCESS;
    }

    /** Map a recovered accept-filter dimension to its backing boolean {@link VisitorConfig} key. */
    private static dev.veltraph.client.config.ConfigKey<Boolean> filterKey(
            dev.veltraph.client.garden.visitor.VisitorAcceptFilter f) {
        return switch (f) {
            case ALL -> VisitorConfig.VISITOR_ACCEPT_FILTER_ALL;
            case VALUABLE_DROPS -> VisitorConfig.VISITOR_ACCEPT_FILTER_VALUABLE_DROPS;
            case NEW_VISITORS -> VisitorConfig.VISITOR_ACCEPT_FILTER_NEW_VISITORS;
            case PROFITABLE -> VisitorConfig.VISITOR_ACCEPT_FILTER_PROFITABLE;
            case BETH -> VisitorConfig.VISITOR_ACCEPT_FILTER_BETH;
            case SEYMOUR -> VisitorConfig.VISITOR_ACCEPT_FILTER_SEYMOUR;
        };
    }

    /**
     * The shared {@code dev [status|on|off|list|run <id>]} subtree (Target 1, local test harness). Routed
     * through the pure {@link DevCommand} parser and the shared {@code DevTestRegistry} — the same registry
     * the Settings Developer page uses. Client-side only; {@code run} is blocked unless Developer Mode is on.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> devSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("dev")
                .executes(ctx -> applyDev(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyDev(StringArgumentType.getString(ctx, "args"))));
    }

    /** Parse a {@code dev} argument tail and apply it against the live Developer Mode + dev-test registry. */
    private static int applyDev(String tail) {
        ChatService chat = VeltraphBootstrap.chat();
        VeltraphLog.basic("Dev", "/vdebug dev " + tail);
        dev.veltraph.client.dev.DeveloperModeController devMode = VeltraphBootstrap.developerMode();
        dev.veltraph.client.dev.DevTestRegistry registry = VeltraphBootstrap.devTests();
        dev.veltraph.client.dev.DevTestContext devCtx = VeltraphBootstrap.devTestContext();
        if (devMode == null || registry == null) {
            emit(chat, "[Veltraph] Developer Mode is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        DevCommand parsed = DevCommand.parse(tail);
        if (!parsed.valid()) {
            emit(chat, "[Veltraph] " + parsed.error());
            return Command.SINGLE_SUCCESS;
        }
        switch (parsed.action()) {
            case STATUS -> { /* status printed below */ }
            case ON -> {
                devMode.setEnabled(true);
                VeltraphBootstrap.saveConfig();
            }
            case OFF -> {
                devMode.setEnabled(false);
                VeltraphBootstrap.saveConfig();
            }
            case LIST -> {
                emit(chat, "[Veltraph] Dev test actions (" + registry.size() + "):");
                for (dev.veltraph.client.dev.DevTestAction a : registry.actions()) {
                    emit(chat, "[Veltraph]   " + a.id() + " [" + a.risk() + "] — " + a.label());
                }
                return Command.SINGLE_SUCCESS;
            }
            case RUN -> {
                dev.veltraph.client.dev.DevTestResult res =
                        registry.run(parsed.actionId(), devCtx, devMode.enabled(), devMode.allowServerBound());
                emit(chat, "[Veltraph] dev run " + parsed.actionId() + " -> "
                        + res.status() + ": " + res.message());
                return Command.SINGLE_SUCCESS;
            }
        }
        emit(chat, "[Veltraph] " + devMode.status());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The shared {@code heal [status|on|off]} subtree used under {@code /vdebug} and {@code /veltraphdebug}.
     * Routed through the pure {@link HealCommand} parser; toggles the local Hyperion auto-heal assist.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> healSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("heal")
                .executes(ctx -> applyHeal(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyHeal(StringArgumentType.getString(ctx, "args"))));
    }

    /** Parse a {@code heal} argument tail and apply it to the local auto-heal assist (client-side). */
    private static int applyHeal(String tail) {
        ChatService chat = VeltraphBootstrap.chat();
        VeltraphLog.basic("AutoHeal", "/vdebug heal " + tail);
        CombatAutoHeal heal = VeltraphBootstrap.autoHeal();
        if (heal == null) {
            emit(chat, "[Veltraph] auto-heal is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        HealCommand parsed = HealCommand.parse(tail);
        if (!parsed.valid()) {
            emit(chat, "[Veltraph] " + parsed.error());
            return Command.SINGLE_SUCCESS;
        }
        switch (parsed.action()) {
            case STATUS -> { /* status below */ }
            case ON -> {
                heal.setEnabled(true);
                VeltraphBootstrap.rearmSurvivalAssist();
                VeltraphBootstrap.saveConfig();
            }
            case OFF -> {
                heal.setEnabled(false);
                VeltraphBootstrap.saveConfig();
            }
        }
        emit(chat, "[Veltraph] " + heal.status());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The shared {@code hud [status|on|off]} subtree used under {@code /vdebug} and
     * {@code /veltraphdebug}. A greedy-string tail is routed through the pure {@link HudCommand} parser;
     * the in-world HUD overlay is toggled client-side only (the {@code HUD_ENABLED} flag is persisted).
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> hudSubtree() {
        return ClientCommands.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("hud")
                .executes(ctx -> applyHud(""))
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> applyHud(StringArgumentType.getString(ctx, "args"))));
    }

    /** Parse a {@code hud} argument tail and apply it to the {@code HUD_ENABLED} flag (client-side, persisted). */
    private static int applyHud(String tail) {
        ChatService chat = VeltraphBootstrap.chat();
        VeltraphLog.basic("Hud", "/vdebug hud " + tail);
        ConfigService cfg = VeltraphBootstrap.config();
        if (cfg == null) {
            emit(chat, "[Veltraph] HUD config is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        HudCommand parsed = HudCommand.parse(tail);
        if (!parsed.valid()) {
            emit(chat, "[Veltraph] " + parsed.error());
            return Command.SINGLE_SUCCESS;
        }
        switch (parsed.action()) {
            case ON -> {
                cfg.set(HudConfig.HUD_ENABLED, true);
                VeltraphBootstrap.saveConfig();
            }
            case OFF -> {
                cfg.set(HudConfig.HUD_ENABLED, false);
                VeltraphBootstrap.saveConfig();
            }
            case STATUS -> {
                // fall through to the status print below
            }
        }
        emit(chat, "[Veltraph] HUD overlay: " + (cfg.get(HudConfig.HUD_ENABLED) ? "ON" : "OFF")
                + " | corner=" + cfg.get(HudConfig.HUD_CORNER));
        return Command.SINGLE_SUCCESS;
    }

    /** Parse a {@code hydra} argument tail and apply it to the local anti-staff feature (client-side). */
    private static int applyHydra(String tail) {
        ChatService chat = VeltraphBootstrap.chat();
        VeltraphLog.basic("Hydra", "/vdebug hydra " + tail);
        HydraAntiStaff hydra = VeltraphBootstrap.hydra();
        if (hydra == null) {
            emit(chat, "[Veltraph] anti-staff is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        HydraCommand parsed = HydraCommand.parse(tail);
        if (!parsed.valid()) {
            emit(chat, "[Veltraph] " + parsed.error());
            return Command.SINGLE_SUCCESS;
        }
        switch (parsed.action()) {
            case STATUS -> emit(chat, "[Veltraph] " + hydra.status());
            case TRIGGERS -> emit(chat, "[Veltraph] " + hydra.triggerStatus());
            case RESPONSE -> emit(chat, "[Veltraph] " + hydra.responseStatus());
            case POLICY -> {
                hydra.setResponsePolicy(parsed.policy());
                VeltraphBootstrap.saveConfig();
                emit(chat, "[Veltraph] " + hydra.responseStatus());
            }
            case ON -> {
                hydra.setEnabled(true);
                VeltraphBootstrap.saveConfig();
                emit(chat, "[Veltraph] " + hydra.status());
            }
            case OFF -> {
                hydra.setEnabled(false);
                VeltraphBootstrap.saveConfig();
                emit(chat, "[Veltraph] " + hydra.status());
            }
            case WHITELIST -> applyHydraWhitelist(chat, parsed);
            case REACTION -> emit(chat, "[Veltraph] " + hydra.runReaction(parsed.reaction()));
            case FAILSAFE -> emit(chat, "[Veltraph] " + hydra.failsafeResponseStatus());
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Apply a {@code /vdebug hydra whitelist …} mutation to the local friend whitelist. Local only — the
     * whitelist is a comma-joined {@link HydraConfig#LOCAL_WHITELIST} string parsed by {@link Whitelist};
     * nothing is sent to the server. Add/remove/clear persist immediately; list just prints.
     */
    private static void applyHydraWhitelist(ChatService chat, HydraCommand parsed) {
        ConfigService cfg = VeltraphBootstrap.config();
        if (cfg == null) {
            emit(chat, "[Veltraph] config is not available yet.");
            return;
        }
        Whitelist wl = Whitelist.parse(cfg.get(HydraConfig.LOCAL_WHITELIST));
        switch (parsed.whitelistOp()) {
            case LIST -> { /* print below */ }
            case ADD -> {
                wl = wl.add(parsed.whitelistName());
                cfg.set(HydraConfig.LOCAL_WHITELIST, wl.toConfigString());
                VeltraphBootstrap.saveConfig();
            }
            case REMOVE -> {
                wl = wl.remove(parsed.whitelistName());
                cfg.set(HydraConfig.LOCAL_WHITELIST, wl.toConfigString());
                VeltraphBootstrap.saveConfig();
            }
            case CLEAR -> {
                wl = Whitelist.empty();
                cfg.set(HydraConfig.LOCAL_WHITELIST, "");
                VeltraphBootstrap.saveConfig();
            }
        }
        emit(chat, "[Veltraph] Anti-Staff whitelist (" + wl.size() + "): "
                + (wl.isEmpty() ? "(empty)" : String.join(", ", wl.names())));
    }

    /** Parse a {@code rewarp} argument tail and apply it through the handler (client-side only). */
    private static int applyRewarp(String tail) {
        VeltraphLog.basic("command", "/veltraph farming rewarp " + tail);
        // Batch 2: `rewarp auto` prints the automatic-rewarp runtime status (enabled + current state).
        // Intercepted here so the existing pure RewarpCommand parser stays untouched.
        if (tail != null && tail.trim().equalsIgnoreCase("auto")) {
            return printAutoRewarpStatus();
        }
        RewarpCommandHandler handler = VeltraphBootstrap.rewarpCommand();
        if (handler == null) {
            emit(VeltraphBootstrap.chat(), "[Veltraph] rewarp is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        handler.apply(tail);
        VeltraphBootstrap.saveConfig(); // persist rewarp add/clear/replace immediately
        return Command.SINGLE_SUCCESS;
    }

    /** Print the automatic-rewarp runtime status for {@code /vdebug rewarp auto} (client-side only). */
    private static int printAutoRewarpStatus() {
        ChatService chat = VeltraphBootstrap.chat();
        var runtime = VeltraphBootstrap.autoRewarp();
        ConfigService cfg = VeltraphBootstrap.config();
        if (runtime == null || cfg == null) {
            emit(chat, "[Veltraph] auto-rewarp is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        boolean enabled = cfg.get(dev.veltraph.client.config.FarmingConfig.AUTO_REWARP_ENABLED);
        emit(chat, "[Veltraph] " + runtime.statusLine(enabled));
        return Command.SINGLE_SUCCESS;
    }

    /** Parse a {@code log} argument tail and apply it to the debug-log controller (logging command usage). */
    private static int applyLog(String tail) {
        ChatService chat = VeltraphBootstrap.chat();
        DebugLogController controller = VeltraphBootstrap.debugLog();
        VeltraphLog.basic("command", "/vdebug log " + tail);
        if (controller == null) {
            emit(chat, "[Veltraph] debug logging is not available yet.");
            return Command.SINGLE_SUCCESS;
        }
        DebugLogCommand parsed = DebugLogCommand.parse(tail);
        if (!parsed.valid()) {
            emit(chat, "[Veltraph] " + parsed.error());
            return Command.SINGLE_SUCCESS;
        }
        if (parsed.status()) {
            emit(chat, "[Veltraph] " + controller.status());
            return Command.SINGLE_SUCCESS;
        }
        if (parsed.mode() != null) {
            controller.setMode(parsed.mode());
        }
        if (parsed.enable() != null) {
            controller.setEnabled(parsed.enable());
        }
        VeltraphBootstrap.saveConfig(); // persist the debug-log enabled/mode change immediately
        return Command.SINGLE_SUCCESS;
    }

    /** Command literal → target screen (delegates to the pure {@link CommandTarget} mapping). */
    public static CommandTarget targetFor(String commandLiteral) {
        return CommandTarget.forLiteral(commandLiteral);
    }

    /** Defer the screen open to the next client execute tick (the chat screen closes after a command). */
    private static int openLater(Supplier<VeltraphScreen> screen) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.setScreen(screen.get()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Print live macro diagnostics for {@code /vdebug macro}: per enabled module, the state, held keys,
     * breaking, target block, lane predicates, aim error, tool slot, and last pause reason. Client-side.
     */
    private static int printMacroDebug() {
        VeltraphLog.basic("command", "/vdebug macro");
        ChatService chat = VeltraphBootstrap.chat();
        var manager = VeltraphBootstrap.moduleManager();
        var enabled = manager == null ? java.util.List.<dev.veltraph.client.core.Module>of()
                : manager.enabledModules();
        if (enabled.isEmpty()) {
            emit(chat, "[Veltraph] /vdebug macro: no module running.");
            return Command.SINGLE_SUCCESS;
        }
        for (dev.veltraph.client.core.Module m : enabled) {
            for (String line : m.diagnostics().split("\n")) {
                emit(chat, "[Veltraph] " + line);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void emit(ChatService chat, String line) {
        if (chat != null) {
            chat.send(line);
        }
        LOGGER.info("{}", line);
    }

    /** Collect and print the current location evidence to chat + the client log. Client-side only. */
    private static int printLocationDebug() {
        VeltraphLog.basic("command", "/vdebug location");
        GameContextService context = VeltraphBootstrap.gameContext();
        ChatService chat = VeltraphBootstrap.chat();
        LocationResolution resolution = context.resolution();
        LocationSignals signals = context.signals();
        List<String> lines = LocationDebugReport.lines(signals, resolution);
        for (String line : lines) {
            if (chat != null) {
                chat.send(line);
            }
            LOGGER.info("[Veltraph] {}", line);
        }
        return Command.SINGLE_SUCCESS;
    }
}
