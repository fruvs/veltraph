package dev.veltraph.client.hydra.response;

import java.util.Locale;

















public enum HydraResponsePolicy {

    /** No automated responses at all (default). The passive proximity/anomaly alert still runs. */
    OFF,

    /** Send the automated chat reply chain ("LOL" / "?" / "nice check" / …) on a staff check. */
    REPLY,

    /** Reply <i>and</i> normalize the look pitch into the configured band (fix-angle / auto-angle). */
    REPLY_AND_FIX_ANGLE,

    /** Reply + fix-angle + run the afterdisconnect / avoid-evacuation re-route command. */
    FULL;

    /** Whether this policy sends the automated chat reply chain. */
    public boolean allowsReply() {
        return this != OFF;
    }

    /** Whether this policy normalizes the look pitch (fix-angle / auto-angle). */
    public boolean allowsFixAngle() {
        return this == REPLY_AND_FIX_ANGLE || this == FULL;
    }

    /** Whether this policy runs the afterdisconnect / avoid-evacuation re-route command. */
    public boolean allowsReroute() {
        return this == FULL;
    }

    /**
     * Parse a policy name case-insensitively with a few friendly synonyms; unknown/blank → {@code null}
     * (callers decide whether that is an error). Used by the {@code /vdebug hydra policy <value>} command.
     */
    public static HydraResponsePolicy parse(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "off", "none", "alert", "alert_only" -> OFF;
            case "reply", "chat" -> REPLY;
            case "reply_fix", "reply_and_fix_angle", "fix", "fixangle", "reply+fix" -> REPLY_AND_FIX_ANGLE;
            case "full", "all" -> FULL;
            default -> null;
        };
    }
}
