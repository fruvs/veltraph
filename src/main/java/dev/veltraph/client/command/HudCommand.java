package dev.veltraph.client.command;

import java.util.Locale;

/**
 * Pure parser for the {@code /vdebug hud …} (and {@code /veltraphdebug hud …}) argument tail. No
 * Minecraft dependency, so every form is unit-testable; the command in {@code VeltraphCommands} applies
 * the parsed intent against the live HUD-enabled config flag (client-side only, never reaches the server).
 *
 * <pre>
 *   (blank) | status   -> print the HUD status line
 *   on                 -> enable the in-world HUD overlay
 *   off                -> disable the in-world HUD overlay
 * </pre>
 */
public record HudCommand(boolean valid, String error, Action action) {

    /** The recognised sub-actions. */
    public enum Action {
        STATUS,
        ON,
        OFF
    }

    public static final String USAGE = "Usage: /vdebug hud <status|on|off>";

    private static HudCommand error(String message) {
        return new HudCommand(false, message + " " + USAGE, null);
    }

    private static HudCommand ok(Action action) {
        return new HudCommand(true, null, action);
    }

    /** Parse a raw argument tail (the text after {@code hud}); null/blank → status. */
    public static HudCommand parse(String argTail) {
        String trimmed = argTail == null ? "" : argTail.trim();
        if (trimmed.isEmpty()) {
            return ok(Action.STATUS);
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length != 1) {
            return error("Too many arguments.");
        }
        return switch (tokens[0].toLowerCase(Locale.ROOT)) {
            case "status" -> ok(Action.STATUS);
            case "on", "enable", "true" -> ok(Action.ON);
            case "off", "disable", "false" -> ok(Action.OFF);
            default -> error("Unknown sub-command '" + tokens[0] + "'.");
        };
    }
}
