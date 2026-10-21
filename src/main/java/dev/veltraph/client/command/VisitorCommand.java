package dev.veltraph.client.command;

import dev.veltraph.client.garden.visitor.VisitorAcceptFilter;

import java.util.Locale;

/**
 * Pure parser for the {@code /vdebug visitor …} (and {@code /veltraphdebug visitor …}) argument tail. No
 * Minecraft dependency, so every form is unit-testable; {@code VeltraphCommands} applies the parsed intent
 * against the live {@code VisitorAdvisor} / config.
 *
 * <pre>
 *   (blank) | status                         -> print the advisor status line
 *   on                                       -> enable the advisory watcher
 *   off                                      -> disable the advisory watcher
 *   debug                                    -> print the detailed diagnostic dump
 *   filter &lt;name&gt; [on|off]                   -> turn one accept filter on/off (default on)
 *       name ∈ ALL | VALUABLE_DROPS | NEW_VISITORS | PROFITABLE | BETH | SEYMOUR (aliases accepted)
 * </pre>
 *
 * The accept filters are the recovered six independent toggles (see {@link VisitorAcceptFilter}); this
 * command flips one at a time. Client-side only — the advisor never sends, clicks, or serves.
 */
public record VisitorCommand(boolean valid, String error, Action action,
                             VisitorAcceptFilter filter, boolean enable) {

    public enum Action {
        STATUS,
        ON,
        OFF,
        DEBUG,
        FILTER
    }

    public static final String USAGE =
            "Usage: /vdebug visitor <status|on|off|debug|filter <ALL|VALUABLE_DROPS|NEW_VISITORS|PROFITABLE|BETH|SEYMOUR> [on|off]>";

    private static VisitorCommand error(String message) {
        return new VisitorCommand(false, message + " " + USAGE, null, null, false);
    }

    private static VisitorCommand ok(Action action) {
        return new VisitorCommand(true, null, action, null, false);
    }

    private static VisitorCommand filter(VisitorAcceptFilter f, boolean enable) {
        return new VisitorCommand(true, null, Action.FILTER, f, enable);
    }

    public static VisitorCommand parse(String argTail) {
        String trimmed = argTail == null ? "" : argTail.trim();
        if (trimmed.isEmpty()) {
            return ok(Action.STATUS);
        }
        String[] tokens = trimmed.split("\\s+");
        String head = tokens[0].toLowerCase(Locale.ROOT);
        if (head.equals("filter")) {
            if (tokens.length < 2 || tokens.length > 3) {
                return error("filter needs a name and optional on/off.");
            }
            VisitorAcceptFilter f = VisitorAcceptFilter.parse(tokens[1]);
            if (f == null) {
                return error("Unknown filter '" + tokens[1] + "'.");
            }
            boolean enable = true;
            if (tokens.length == 3) {
                String v = tokens[2].toLowerCase(Locale.ROOT);
                switch (v) {
                    case "on", "enable", "true" -> enable = true;
                    case "off", "disable", "false" -> enable = false;
                    default -> {
                        return error("filter state must be on/off, got '" + tokens[2] + "'.");
                    }
                }
            }
            return filter(f, enable);
        }
        if (tokens.length != 1) {
            return error("Too many arguments.");
        }
        return switch (head) {
            case "status" -> ok(Action.STATUS);
            case "on", "enable", "true" -> ok(Action.ON);
            case "off", "disable", "false" -> ok(Action.OFF);
            case "debug", "dump", "diag" -> ok(Action.DEBUG);
            default -> error("Unknown sub-command '" + tokens[0] + "'.");
        };
    }
}
