package dev.veltraph.client.rewarp;

import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.ServerCommandService;

import java.util.Objects;

/**
 * Executes the original rewarp action: run the configured in-game warp command. The original recovered
 * default is {@code /warp garden}; Veltraph normalizes and sends it through the vanilla client command
 * path. Saved rewarp points still exist as the macro's "where/when should I rewarp?" marker, but the
 * physical action is just this command.
 */
public final class RewarpWarpExecutor {

    public static final String DEFAULT_COMMAND = "warp garden";

    private final ServerCommandService commands;

    public RewarpWarpExecutor(ServerCommandService commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    /** Normalize a user/config command to the no-leading-slash form required by sendChatCommand. */
    public static String normalizeCommand(String raw) {
        String c = raw == null || raw.isBlank() ? DEFAULT_COMMAND : raw.trim();
        while (c.startsWith("/")) {
            c = c.substring(1).trim();
        }
        return c.isBlank() ? DEFAULT_COMMAND : c;
    }

    /**
     * Run the rewarp command. Returns false only when no network handler/command path is available.
     */
    public boolean execute(String rawCommand) {
        String command = normalizeCommand(rawCommand);
        VeltraphLog.basic("Rewarp", "sending server command /" + command);
        boolean sent = commands.sendCommand(command);
        if (!sent) {
            VeltraphLog.basic("Rewarp", "failed to send /" + command + " (not connected)");
        }
        return sent;
    }
}
