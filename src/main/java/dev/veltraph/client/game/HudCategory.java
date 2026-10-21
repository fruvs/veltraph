package dev.veltraph.client.game;

/**
 * Categories of HUD overlay, each with a documented context in which it may show (see
 * {@code docs/LOCATION_GATES.md}). {@link HudVisibilityService} maps these to visibility rules.
 */
public enum HudCategory {
    /** General farming HUD — Garden only. */
    GARDEN,
    /** Pest HUD — Garden only. */
    PEST,
    /** Visitor HUD — Garden only. */
    VISITOR,
    /** Foraging HUD — island/park/galatea/hub. */
    FORAGING,
    /** Ghost HUD — Dwarven Mines (CONFIRMED gate for the ghost macro). */
    GHOST,
    /** Fishing HUD — anywhere in SkyBlock (exact area gate UNKNOWN). */
    FISHING,
    /** Slayer HUD — anywhere in SkyBlock (exact area gate UNKNOWN). */
    SLAYER,
    /** Script-only HUD — only while a module is actively running. */
    SCRIPT,
    /** Generic Veltraph HUD — anywhere in SkyBlock. */
    GENERIC
}
