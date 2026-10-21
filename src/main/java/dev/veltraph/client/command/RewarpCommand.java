package dev.veltraph.client.command;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * Pure parser for the {@code /veltraph farming rewarp …} (and {@code /vdebug rewarp …}) argument tail.
 * No Minecraft dependency, so every form is unit-testable; the command handler applies the parsed
 * intent against {@code RewarpService} + the live capture helper.
 *
 * <pre>
 *   add                  -> capture current location, append
 *   clear                -> clear all saved points
 *   replace              -> replace the LAST saved point with the current location
 *   replace &lt;index&gt;       -> replace the 0-based point at &lt;index&gt; with the current location
 *   list                 -> print saved points
 *   warp                 -> run the configured rewarp command (default /warp garden)
 * </pre>
 */
public record RewarpCommand(boolean valid, String error, Action action, OptionalInt index) {

    /** The recognised sub-actions. */
    public enum Action {
        ADD,
        CLEAR,
        REPLACE,
        LIST,
        WARP
    }

    public static final String USAGE =
            "Usage: /veltraph farming rewarp <add|clear|replace [index]|list|warp>";

    private static RewarpCommand error(String message) {
        return new RewarpCommand(false, message + " " + USAGE, null, OptionalInt.empty());
    }

    private static RewarpCommand ok(Action action, OptionalInt index) {
        return new RewarpCommand(true, null, action, index);
    }

    /** Parse a raw argument tail (the text after {@code rewarp}); null/blank → error. */
    public static RewarpCommand parse(String argTail) {
        String trimmed = argTail == null ? "" : argTail.trim();
        if (trimmed.isEmpty()) {
            return error("Missing rewarp sub-command.");
        }
        return parseTokens(trimmed.split("\\s+"));
    }

    /** Parse pre-split tokens (the words after {@code rewarp}). */
    public static RewarpCommand parseTokens(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return error("Missing rewarp sub-command.");
        }
        String head = tokens[0].toLowerCase(Locale.ROOT);
        switch (head) {
            case "add" -> {
                return tokens.length == 1 ? ok(Action.ADD, OptionalInt.empty())
                        : error("'add' takes no arguments.");
            }
            case "clear" -> {
                return tokens.length == 1 ? ok(Action.CLEAR, OptionalInt.empty())
                        : error("'clear' takes no arguments.");
            }
            case "list" -> {
                return tokens.length == 1 ? ok(Action.LIST, OptionalInt.empty())
                        : error("'list' takes no arguments.");
            }
            case "warp" -> {
                return tokens.length == 1 ? ok(Action.WARP, OptionalInt.empty())
                        : error("'warp' takes no arguments.");
            }
            case "replace" -> {
                if (tokens.length == 1) {
                    return ok(Action.REPLACE, OptionalInt.empty());
                }
                if (tokens.length != 2) {
                    return error("'replace' takes at most one index.");
                }
                Integer idx = parseIndex(tokens[1]);
                if (idx == null) {
                    return error("Invalid index '" + tokens[1] + "'.");
                }
                return ok(Action.REPLACE, OptionalInt.of(idx));
            }
            default -> {
                return error("Unknown sub-command '" + tokens[0] + "'.");
            }
        }
    }

    /** A non-negative integer, or null. */
    private static Integer parseIndex(String token) {
        try {
            int v = Integer.parseInt(token.trim());
            return v < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
