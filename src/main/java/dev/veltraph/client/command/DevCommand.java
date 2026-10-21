package dev.veltraph.client.command;

import java.util.Locale;

/**
 * Pure parser for the {@code /vdebug dev …} (and {@code /veltraphdebug dev …}) argument tail. No Minecraft
 * dependency, so every form is unit-testable; {@code VeltraphCommands} applies the parsed intent against
 * the live {@code DeveloperModeController} + {@code DevTestRegistry} (the same registry the UI uses).
 *
 * <pre>
 *   (blank) | status     -> print Developer Mode + log status
 *   on                   -> enable Developer Mode (forces verbose logging)
 *   off                  -> disable Developer Mode (restores prior logging)
 *   list                 -> list the registered test action ids
 *   run &lt;actionId&gt;        -> run one local test action (blocked unless Dev Mode is on)
 * </pre>
 *
 * Client-side only. {@code status}/{@code on} work regardless of Dev Mode; {@code run} requires it on.
 */
public record DevCommand(boolean valid, String error, Action action, String actionId) {

    public enum Action {
        STATUS,
        ON,
        OFF,
        LIST,
        RUN
    }

    public static final String USAGE = "Usage: /vdebug dev <status|on|off|list|run <actionId>>";

    private static DevCommand error(String message) {
        return new DevCommand(false, message + " " + USAGE, null, null);
    }

    private static DevCommand ok(Action action) {
        return new DevCommand(true, null, action, null);
    }

    public static DevCommand parse(String argTail) {
        String trimmed = argTail == null ? "" : argTail.trim();
        if (trimmed.isEmpty()) {
            return ok(Action.STATUS);
        }
        String[] tokens = trimmed.split("\\s+");
        String head = tokens[0].toLowerCase(Locale.ROOT);
        if (head.equals("run")) {
            if (tokens.length != 2) {
                return error("run needs exactly one action id.");
            }
            return new DevCommand(true, null, Action.RUN, tokens[1]);
        }
        if (tokens.length != 1) {
            return error("Too many arguments.");
        }
        return switch (head) {
            case "status" -> ok(Action.STATUS);
            case "on", "enable", "true" -> ok(Action.ON);
            case "off", "disable", "false" -> ok(Action.OFF);
            case "list", "ls" -> ok(Action.LIST);
            default -> error("Unknown sub-command '" + tokens[0] + "'.");
        };
    }
}
