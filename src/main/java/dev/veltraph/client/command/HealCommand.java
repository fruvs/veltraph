package dev.veltraph.client.command;

import java.util.Locale;

/**
 * Pure parser for {@code /vdebug heal …} (and {@code /veltraphdebug heal …}): {@code status | on | off}.
 * No Minecraft dependency; {@code VeltraphCommands} applies it to the live {@code CombatAutoHeal}. The
 * auto-heal is a local survival assist (look down → cast Hyperion → restore); client-side only.
 */
public record HealCommand(boolean valid, String error, Action action) {

    public enum Action {
        STATUS,
        ON,
        OFF
    }

    public static final String USAGE = "Usage: /vdebug heal <status|on|off>";

    private static HealCommand error(String message) {
        return new HealCommand(false, message + " " + USAGE, null);
    }

    private static HealCommand ok(Action action) {
        return new HealCommand(true, null, action);
    }

    public static HealCommand parse(String argTail) {
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
