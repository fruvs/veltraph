package dev.veltraph.client.command;

import dev.veltraph.client.hydra.response.HydraReaction;
import dev.veltraph.client.hydra.response.HydraResponsePolicy;

import java.util.Locale;

/**
 * Pure parser for the {@code /vdebug hydra …} (and {@code /veltraphdebug hydra …}) argument tail. No
 * Minecraft dependency, so every form is unit-testable; the command in {@code VeltraphCommands} applies
 * the parsed intent against the live anti-staff feature.
 *
 * <pre>
 *   (blank) | status            -> print the watcher status line
 *   on                          -> enable the anti-staff watcher
 *   off                         -> disable the anti-staff watcher
 *   triggers                    -> print which defensive triggers are enabled + their thresholds
 *   whitelist [list]            -> print the local friend whitelist
 *   whitelist add <name>        -> add a name to the local friend whitelist
 *   whitelist remove <name>     -> remove a name from the local friend whitelist
 *   whitelist clear             -> clear the local friend whitelist
 *   response                    -> print the LOCAL_INCLUDE staff-response config (policy + toggles +
 *                                  realign-msgs / look-msgs counts)
 *   policy &lt;off|reply|reply_fix|full&gt; -> set the automated-response policy
 * </pre>
 *
 * The whitelist is a purely local friend list (it replaces the original C2-provided staff roster, which
 * is EXCLUDED). Part of the local Hydra/Anti-Staff rebuild ({@code docs/HYDRA_ANTISTAFF_NOTES.md}); the
 * command is client-side only. The automated response (when enabled) sends vanilla in-game chat/commands,
 * never anything to a backend/C2.
 */
public record HydraCommand(boolean valid, String error, Action action, WhitelistOp whitelistOp,
                           String whitelistName, HydraResponsePolicy policy, HydraReaction reaction) {

    /** The recognised sub-actions. */
    public enum Action {
        STATUS,
        ON,
        OFF,
        TRIGGERS,
        WHITELIST,
        RESPONSE,
        POLICY,
        REACTION,
        FAILSAFE
    }

    /** Whitelist mutation for {@link Action#WHITELIST}. */
    public enum WhitelistOp {
        LIST,
        ADD,
        REMOVE,
        CLEAR
    }

    public static final String USAGE =
            "Usage: /vdebug hydra <status|on|off|triggers|response|policy <off|reply|reply_fix|full>"
                    + "|reaction <cage|barrier|floor|backup>|failsafe"
                    + "|whitelist <list|add|remove|clear> [name]>";

    private static HydraCommand error(String message) {
        return new HydraCommand(false, message + " " + USAGE, null, null, null, null, null);
    }

    private static HydraCommand ok(Action action) {
        return new HydraCommand(true, null, action, null, null, null, null);
    }

    private static HydraCommand whitelist(WhitelistOp op, String name) {
        return new HydraCommand(true, null, Action.WHITELIST, op, name, null, null);
    }

    private static HydraCommand policy(HydraResponsePolicy policy) {
        return new HydraCommand(true, null, Action.POLICY, null, null, policy, null);
    }

    private static HydraCommand reaction(HydraReaction reaction) {
        return new HydraCommand(true, null, Action.REACTION, null, null, null, reaction);
    }

    /** Parse a raw argument tail (the text after {@code hydra}); null/blank → status. */
    public static HydraCommand parse(String argTail) {
        String trimmed = argTail == null ? "" : argTail.trim();
        if (trimmed.isEmpty()) {
            return ok(Action.STATUS);
        }
        String[] tokens = trimmed.split("\\s+");
        String head = tokens[0].toLowerCase(Locale.ROOT);
        if (head.equals("whitelist") || head.equals("wl")) {
            return parseWhitelist(tokens);
        }
        if (head.equals("policy")) {
            return parsePolicy(tokens);
        }
        if (head.equals("reaction") || head.equals("react")) {
            return parseReaction(tokens);
        }
        if (tokens.length != 1) {
            return error("Too many arguments.");
        }
        return switch (head) {
            case "status" -> ok(Action.STATUS);
            case "on", "enable", "true" -> ok(Action.ON);
            case "off", "disable", "false" -> ok(Action.OFF);
            case "triggers", "trigger" -> ok(Action.TRIGGERS);
            case "response", "responses" -> ok(Action.RESPONSE);
            case "failsafe", "failsafes" -> ok(Action.FAILSAFE);
            default -> error("Unknown sub-command '" + tokens[0] + "'.");
        };
    }

    private static HydraCommand parsePolicy(String[] tokens) {
        if (tokens.length == 1) {
            return ok(Action.RESPONSE); // `policy` with no value just prints the current response config
        }
        if (tokens.length != 2) {
            return error("Usage: policy <off|reply|reply_fix|full>.");
        }
        HydraResponsePolicy p = HydraResponsePolicy.parse(tokens[1]);
        if (p == null) {
            return error("Unknown policy '" + tokens[1] + "'.");
        }
        return policy(p);
    }

    private static HydraCommand parseReaction(String[] tokens) {
        if (tokens.length != 2) {
            return error("Usage: reaction <cage|barrier|floor|backup>.");
        }
        HydraReaction r = switch (tokens[1].toLowerCase(Locale.ROOT)) {
            case "cage" -> HydraReaction.CAGE;
            case "barrier" -> HydraReaction.BARRIER;
            case "floor", "floor_change", "floorchange" -> HydraReaction.FLOOR_CHANGE;
            case "backup", "back" -> HydraReaction.BACKUP;
            default -> null;
        };
        if (r == null) {
            return error("Unknown reaction '" + tokens[1] + "'.");
        }
        return reaction(r);
    }

    private static HydraCommand parseWhitelist(String[] tokens) {
        if (tokens.length == 1) {
            return whitelist(WhitelistOp.LIST, null);
        }
        String op = tokens[1].toLowerCase(Locale.ROOT);
        return switch (op) {
            case "list" -> tokens.length == 2 ? whitelist(WhitelistOp.LIST, null)
                    : error("Too many arguments.");
            case "clear" -> tokens.length == 2 ? whitelist(WhitelistOp.CLEAR, null)
                    : error("Too many arguments.");
            case "add" -> tokens.length == 3 ? whitelist(WhitelistOp.ADD, tokens[2])
                    : error("Usage: whitelist add <name>.");
            case "remove", "rm", "del" -> tokens.length == 3 ? whitelist(WhitelistOp.REMOVE, tokens[2])
                    : error("Usage: whitelist remove <name>.");
            default -> error("Unknown whitelist op '" + tokens[1] + "'.");
        };
    }
}
