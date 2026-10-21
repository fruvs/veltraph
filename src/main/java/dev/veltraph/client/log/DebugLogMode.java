package dev.veltraph.client.log;

import java.util.Locale;
import java.util.Optional;

/**
 * Verbosity levels for Veltraph's local debug log. Ordered least → most verbose; a message tagged at
 * level {@code L} is emitted when the active mode {@link #includes(DebugLogMode) includes} {@code L}.
 *
 * <ul>
 *   <li>{@link #BASIC} — major events only (init, GUI open/close, macro start/stop/block, gate
 *       failures, command usage).</li>
 *   <li>{@link #DETAILS} — BASIC + macro state changes (movement transitions, profile/crop/tool,
 *       area/context, held keys, aim target vs current, row-end/geometry decisions, block/break).</li>
 *   <li>{@link #VERBOSE} — DETAILS + near-everything (per-tick diagnostics, method-level decisions,
 *       UI clicks, guard checks, tool scans, input set/release, rotation retarget/jitter, world
 *       probes).</li>
 * </ul>
 */
public enum DebugLogMode {
    BASIC,
    DETAILS,
    VERBOSE;

    /** Whether the active mode (this) should emit a message tagged at {@code level}. */
    public boolean includes(DebugLogMode level) {
        return level.ordinal() <= this.ordinal();
    }

    /** Parse {@code basic|details|verbose} (case-insensitive); empty if unrecognised. */
    public static Optional<DebugLogMode> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "basic" -> Optional.of(BASIC);
            case "details", "detail" -> Optional.of(DETAILS);
            case "verbose" -> Optional.of(VERBOSE);
            default -> Optional.empty();
        };
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
