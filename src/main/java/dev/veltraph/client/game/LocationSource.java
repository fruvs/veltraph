package dev.veltraph.client.game;

/**
 * Which client-visible signal determined the resolved {@link GameContext}. Mirrors the original mod's
 * layered model (scoreboard first, then tab overlay as a fallback) and is surfaced by the
 * {@code /veltraphdebug location} command so a live formatting mismatch can be diagnosed.
 */
public enum LocationSource {
    /** No usable signal (not on Hypixel, or no scoreboard/tab data). */
    NONE,
    /** Server brand / address only (on Hypixel, area still undetermined). */
    BRAND,
    /** The scoreboard sidebar objective title (e.g. {@code SKYBLOCK}). */
    SCOREBOARD_TITLE,
    /** A scoreboard sidebar row (the location/area line, or a strong SkyBlock signal line). */
    SCOREBOARD_LINE,
    /** The player-list (tab) footer text. */
    TAB_FOOTER,
    /** A player-list (tab) display name / header line. */
    TAB_PLAYER;
}
