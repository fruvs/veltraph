package dev.veltraph.client.combat.heal;

/**
 * The single side effect the pure {@link HyperionAutoHeal} asks the MC layer to perform this tick.
 */
public enum HealAction {
    /** Do nothing. */
    NONE,
    /** Save the current pitch and look straight down. */
    LOOK_DOWN,
    /** Right-click (use) the held Hyperion to cast Wither Impact (the AOE heal). */
    CAST,
    /** Restore the saved pitch (after the heal, or after aborting on a blocked cast). */
    RESTORE
}
