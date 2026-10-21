package dev.veltraph.client.hud;

/**
 * One rendered line of the in-world HUD: its {@code text} and a semantic {@link Role} the renderer maps
 * to a {@link dev.veltraph.client.ui.Theme} color. Pure value type (no Minecraft, no color int) so the
 * "what to show" decision in {@link HudModelBuilder} stays unit-testable independent of the skin.
 *
 * @param text the line text (already short)
 * @param role the semantic color role
 */
public record HudLine(String text, Role role) {

    /** Semantic color buckets the renderer maps to concrete {@link dev.veltraph.client.ui.Theme} colors. */
    public enum Role {
        /** The panel title ("Veltraph"). */
        TITLE,
        /** A running / good state (e.g. macro RUNNING, anti-staff IDLE). */
        ACTIVE,
        /** A muted / secondary detail (area, counts). */
        MUTED,
        /** A warning / attention state (e.g. PAUSED, anti-staff ALERTED, pending return). */
        WARNING,
        /** A stopped / off state. */
        STOPPED,
        /** Plain body text. */
        BODY
    }

    public static HudLine of(String text, Role role) {
        return new HudLine(text, role);
    }

    public static HudLine title(String text) {
        return new HudLine(text, Role.TITLE);
    }

    public static HudLine body(String text) {
        return new HudLine(text, Role.BODY);
    }

    public static HudLine muted(String text) {
        return new HudLine(text, Role.MUTED);
    }

    public static HudLine active(String text) {
        return new HudLine(text, Role.ACTIVE);
    }

    public static HudLine warning(String text) {
        return new HudLine(text, Role.WARNING);
    }

    public static HudLine stopped(String text) {
        return new HudLine(text, Role.STOPPED);
    }
}
