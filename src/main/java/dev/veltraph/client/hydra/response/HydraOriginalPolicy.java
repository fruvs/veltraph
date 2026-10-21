package dev.veltraph.client.hydra.response;

import java.util.Locale;
















public enum HydraOriginalPolicy {
    REACT,
    STOP_ALL,
    IGNORE;

    /**
     * Map a Veltraph UI policy onto an original-policy concept (used when parity mode is OFF):
     * {@code OFF → IGNORE} (no response — the safe default), every reply-capable policy ({@code REPLY},
     * {@code REPLY_AND_FIX_ANGLE}, {@code FULL}) {@code → REACT}. {@code STOP_ALL} is reachable only via
     * parity mode + {@code HYDRA_ORIGINAL_POLICY} (the existing auto-stop failsafe already covers stopping).
     */
    public static HydraOriginalPolicy fromUiPolicy(HydraResponsePolicy ui) {
        if (ui == null || ui == HydraResponsePolicy.OFF) {
            return IGNORE;
        }
        return REACT;
    }

    /** Parse case-insensitively with friendly synonyms; unknown/blank → null. */
    public static HydraOriginalPolicy parse(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "react", "reply", "respond" -> REACT;
            case "stop_all", "stopall", "stop", "stop-all" -> STOP_ALL;
            case "ignore", "off", "none" -> IGNORE;
            default -> null;
        };
    }
}
