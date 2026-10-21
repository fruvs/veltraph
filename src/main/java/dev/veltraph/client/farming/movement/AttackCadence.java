package dev.veltraph.client.farming.movement;

/**
 * Pure attack-key cadence policy for the farming break. The recovered executor holds left-click
 * continuously to break the crop in front ({@code reports/_spec_parts/FarmingForwardExecutor.md});
 * that is the default ({@code cadenceTicks == 0} → always held). Some servers only register a fresh
 * break on a new click rather than a held one, so a positive cadence re-taps: the key is released for
 * one tick every {@code cadenceTicks} ticks, forcing the client to re-issue a swing.
 *
 * <p>No Minecraft dependency, so it is unit-testable on a plain JVM.
 */
public final class AttackCadence {

    private AttackCadence() {
    }

    /**
     * Whether the attack key should be HELD this tick while breaking is desired.
     *
     * @param tick         a monotonically increasing tick counter
     * @param cadenceTicks 0 = hold continuously; N &gt; 0 = release one tick every N (a re-tap)
     * @return true to hold the attack key, false to release it for this single re-tap tick
     */
    public static boolean shouldHold(long tick, int cadenceTicks) {
        if (cadenceTicks <= 0) {
            return true; // continuous hold (recovered default)
        }
        // Release on the tick that begins each cadence window so the next tick is a fresh click.
        return Math.floorMod(tick, cadenceTicks) != 0L;
    }
}
