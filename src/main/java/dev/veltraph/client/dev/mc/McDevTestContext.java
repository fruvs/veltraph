package dev.veltraph.client.dev.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.HydraConfig;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.dev.DevTestContext;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.hydra.HydraAntiStaff;
import dev.veltraph.client.hydra.HydraEvent;
import dev.veltraph.client.hydra.response.ResponseTemplates;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.rewarp.RewarpWarpExecutor;
import dev.veltraph.client.service.ChatService;
import dev.veltraph.client.service.ServerChatService;
import dev.veltraph.client.service.ServerCommandService;
import dev.veltraph.client.service.mc.McServerChatService;
import dev.veltraph.client.service.mc.McServerCommandService;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Live-Minecraft implementation of the {@link DevTestContext} seam. It routes each dev-test action to the
 * real Veltraph systems: the module manager (stop), the input lock (release), the activation guard (gate),
 * the anti-staff orchestrator (alert/classification/fix-angle), the local chat HUD, and — only for the
 * explicitly-allowed SERVER_BOUND actions — the vanilla server chat/command path the rewarp command uses.
 * No network beyond those two named server methods.
 */
public final class McDevTestContext implements DevTestContext {

    private final Minecraft client;           // for live look angles (currentPitch/Yaw)
    private final ModuleManager moduleManager;
    private final GameContextService gameContext;
    private final HydraAntiStaff hydra;
    private final ConfigService config;
    private final ChatService localChat;            // local client chat HUD
    private final ServerChatService serverChat;     // vanilla in-game chat (server-bound)
    private final ServerCommandService serverCommand; // vanilla in-game command (server-bound)

    public McDevTestContext(Minecraft client, ModuleManager moduleManager,
                            GameContextService gameContext, HydraAntiStaff hydra,
                            ConfigService config, ChatService localChat) {
        this.client = client;
        this.moduleManager = moduleManager;
        this.gameContext = gameContext;
        this.hydra = hydra;
        this.config = config;
        this.localChat = localChat;
        this.serverChat = new McServerChatService(client);
        this.serverCommand = new McServerCommandService(client);
    }

    @Override
    public String areaName() {
        GameContext ctx = gameContext == null ? GameContext.NONE : gameContext.current();
        return ctx.area().name();
    }

    @Override
    public String contextSummary() {
        GameContext ctx = gameContext == null ? GameContext.NONE : gameContext.current();
        return "Hypixel=" + ctx.onHypixel() + " SkyBlock=" + ctx.inSkyBlock()
                + " area=" + ModuleActivationGuard.describe(ctx);
    }

    @Override
    public String moduleStateSummary() {
        if (moduleManager == null) {
            return "module manager unavailable";
        }
        List<Module> running = moduleManager.enabledModules();
        if (running.isEmpty()) {
            return "no macro running";
        }
        StringBuilder sb = new StringBuilder("running: ");
        for (int i = 0; i < running.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(running.get(i).id());
        }
        return sb.toString();
    }

    @Override
    public int stopAllMacros() {
        if (moduleManager == null) {
            return 0;
        }
        int n = moduleManager.enabledModules().size();
        moduleManager.stopAll();
        return n;
    }

    @Override
    public void releaseInput() {
        ScriptingInputLock.releaseAll();
    }

    @Override
    public String gateDecision() {
        if (moduleManager == null || gameContext == null) {
            return "gate unavailable";
        }
        GameContext ctx = gameContext.current();
        Module representative = null;
        for (Module m : moduleManager.all()) {
            if (m.requiredArea().isPresent()) {
                representative = m;
                break;
            }
        }
        if (representative == null) {
            return "no area-gated module registered (context: " + ModuleActivationGuard.describe(ctx) + ")";
        }
        return ModuleActivationGuard.evaluate(representative, ctx)
                .map(reason -> "BLOCKED — " + reason)
                .orElse("would allow " + representative.id() + " (context: "
                        + ModuleActivationGuard.describe(ctx) + ")");
    }

    @Override
    public String simulateHydraAlert(HydraEvent.Type type) {
        return hydra == null ? "anti-staff unavailable" : hydra.simulateAlert(type);
    }

    @Override
    public String classifyHydra(HydraEvent.Type type) {
        return hydra == null ? "anti-staff unavailable" : hydra.describeClassification(type);
    }

    @Override
    public void fixAngle(double yaw, double pitch) {
        if (hydra != null) {
            hydra.runFixAngle(yaw, pitch);
        }
    }

    @Override
    public double currentPitch() {
        try {
            return client != null && client.player != null ? client.player.getXRot() : Double.NaN;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Override
    public double currentYaw() {
        try {
            return client != null && client.player != null ? client.player.getYRot() : Double.NaN;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Override
    public double fixAngleMinPitch() {
        return config.get(HydraConfig.FIX_ANGLE_MIN_PITCH);
    }

    @Override
    public double fixAngleMaxPitch() {
        return config.get(HydraConfig.FIX_ANGLE_MAX_PITCH);
    }

    @Override
    public String runReaction(dev.veltraph.client.hydra.response.HydraReaction reaction) {
        return hydra == null ? "anti-staff unavailable" : hydra.runReaction(reaction);
    }

    @Override
    public String runFailsafeResponse(dev.veltraph.client.failsafe.response.FailsafeResponseType type) {
        return hydra == null ? "anti-staff unavailable" : hydra.runFailsafeResponse(type);
    }

    @Override
    public String runFailsafeRoutine(dev.veltraph.client.failsafe.response.FailsafeRoutine routine) {
        return hydra == null ? "anti-staff unavailable" : hydra.runFailsafeRoutine(routine);
    }

    @Override
    public boolean parityMode() {
        return config.get(HydraConfig.HYDRA_PARITY_MODE);
    }

    @Override
    public String firstReplyTemplate() {
        String configured = config.get(HydraConfig.AUTO_REPLY_FIRST_MESSAGES);
        if (configured != null && !configured.isBlank()) {
            for (String part : configured.split("\\|")) {
                if (!part.isBlank()) {
                    return part.trim();
                }
            }
        }
        return ResponseTemplates.DEFAULT_FIRST.get(0);
    }

    @Override
    public String afterDisconnectCommand() {
        String cmd = config.get(HydraConfig.AFTER_DISCONNECT_COMMAND);
        return cmd == null || cmd.isBlank() ? "lobby" : cmd.trim();
    }

    @Override
    public String avoidEvacuationCommand() {
        String cmd = config.get(HydraConfig.AVOID_EVACUATION_COMMAND);
        return cmd == null || cmd.isBlank() ? "lobby" : cmd.trim();
    }

    @Override
    public String rewarpCommand() {
        return RewarpWarpExecutor.normalizeCommand(config.get(FarmingConfig.REWARP_COMMAND));
    }

    @Override
    public void localChat(String message) {
        if (localChat != null) {
            localChat.send(message);
        }
    }

    @Override
    public boolean serverChat(String message) {
        return serverChat.sendChat(message);
    }

    @Override
    public boolean serverCommand(String command) {
        return serverCommand.sendCommand(command);
    }
}
