package dev.veltraph.client.command;

import dev.veltraph.client.log.DebugLogMode;

import java.util.Locale;
import java.util.Optional;

/**
 * Pure parser for the {@code /vdebug log …} and {@code /veltraph debug log …} argument tails. Handles
 * every documented form (and the {@code ture} typo for {@code true}):
 *
 * <pre>
 *   (empty)                         -> status (show current state)
 *   status                          -> status
 *   true|false|ture                 -> enable/disable
 *   true|false|ture mode &lt;m&gt;        -> enable/disable + set mode
 *   mode &lt;m&gt;                         -> set mode only
 * </pre>
 *
 * where {@code <m>} is {@code basic|details|verbose}. Returns a parsed intent (enable flag, mode,
 * status) or an error message; the command handler applies it against the config/logger. No Minecraft
 * dependency, so it is fully unit-testable.
 */
public record DebugLogCommand(boolean valid, String error, Boolean enable, DebugLogMode mode, boolean status) {

    public static final String USAGE =
            "Usage: /vdebug log <true|false> [mode <basic|details|verbose>] | log mode <m> | log status";

    private static DebugLogCommand error(String message) {
        return new DebugLogCommand(false, message + " " + USAGE, null, null, false);
    }

    private static DebugLogCommand ok(Boolean enable, DebugLogMode mode, boolean status) {
        return new DebugLogCommand(true, null, enable, mode, status);
    }

    /** Parse a raw argument tail (the text after {@code log}); null/blank → status. */
    public static DebugLogCommand parse(String argTail) {
        String trimmed = argTail == null ? "" : argTail.trim();
        if (trimmed.isEmpty()) {
            return ok(null, null, true);
        }
        return parseTokens(trimmed.split("\\s+"));
    }

    /** Parse pre-split tokens (the words after {@code log}). */
    public static DebugLogCommand parseTokens(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return ok(null, null, true);
        }
        String head = tokens[0].toLowerCase(Locale.ROOT);

        if (head.equals("status")) {
            return tokens.length == 1 ? ok(null, null, true) : error("Unexpected arguments after 'status'.");
        }

        if (head.equals("mode")) {
            if (tokens.length != 2) {
                return error("'mode' needs exactly one value.");
            }
            Optional<DebugLogMode> m = DebugLogMode.parse(tokens[1]);
            return m.map(mode -> ok(null, mode, false)).orElseGet(() -> error("Unknown mode '" + tokens[1] + "'."));
        }

        Boolean enable = parseBool(head);
        if (enable == null) {
            return error("Expected true/false/mode/status, got '" + tokens[0] + "'.");
        }
        if (tokens.length == 1) {
            return ok(enable, null, false);
        }
        // <bool> mode <m>
        if (tokens.length == 3 && tokens[1].equalsIgnoreCase("mode")) {
            Optional<DebugLogMode> m = DebugLogMode.parse(tokens[2]);
            return m.map(mode -> ok(enable, mode, false))
                    .orElseGet(() -> error("Unknown mode '" + tokens[2] + "'."));
        }
        return error("Expected 'mode <basic|details|verbose>' after " + head + ".");
    }

    /** {@code true}/{@code on}/{@code 1}/{@code ture} → true; {@code false}/{@code off}/{@code 0} → false; else null. */
    private static Boolean parseBool(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "true", "ture", "on", "1", "enable", "enabled" -> Boolean.TRUE;
            case "false", "off", "0", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }
}
