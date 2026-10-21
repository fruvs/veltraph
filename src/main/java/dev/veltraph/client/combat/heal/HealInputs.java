package dev.veltraph.client.combat.heal;

/**
 * One tick of input to the pure {@link HyperionAutoHeal}. No Minecraft, no clock read — so the whole heal
 * FSM is unit-testable.
 *
 * @param enabled     whether the auto-heal assist is enabled
 * @param available   whether a world + player are present (and in SkyBlock)
 * @param hasHyperion whether a Hyperion (or configured heal weapon) is held / selectable
 * @param healthPct   the player's current health as a percentage of max (0–100)
 * @param clearAbove  whether the space above the player is clear (the "blocks in the way" check)
 * @param nowMillis   wall clock in epoch millis (for the post-cast wait, cooldown, and 15 s window)
 */
public record HealInputs(
        boolean enabled,
        boolean available,
        boolean hasHyperion,
        double healthPct,
        boolean clearAbove,
        long nowMillis, boolean cameraReady, boolean restorationComplete) {
    public HealInputs(boolean enabled, boolean available, boolean hasHyperion, double healthPct,
                      boolean clearAbove, long nowMillis) {
        this(enabled, available, hasHyperion, healthPct, clearAbove, nowMillis, true, true);
    }
}
