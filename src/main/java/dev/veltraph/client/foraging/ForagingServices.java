package dev.veltraph.client.foraging;

/**
 * The Minecraft seam the tree-chop macro needs: resolve the crosshair log target + reach + movement, and
 * hold/release the break (attack) key. An interface so the module glue is unit-testable with a fake; the
 * MC implementation is {@code foraging/mc/McForagingServices}.
 *
 * <p>Read-only except {@link #setBreaking(boolean)} (the attack-key hold, the same mechanism the farming
 * engine uses). No block-progress packets — see {@code docs/FORAGING_NOTES.md}.
 */
public interface ForagingServices {

    /** Whether a world + local player are present. */
    boolean available();

    /** Axe readiness; headless seams may supply an ordinary axe by default. */
    default boolean hasAxe() { return true; }

    /** Whether the crosshair is on a block whose id matches the configured log keywords. */
    boolean lookingAtLog();

    /** The crosshair log's coordinate, or null when {@link #lookingAtLog()} is false. */
    BlockCoord targetLog();

    /** Distance from the player's eye to the targeted block (blocks); {@code NaN} when no target. */
    double eyeDistance();

    /** Whether the player is moving (for the "not in reach and not moving" failsafe). */
    boolean moving();

    /** Hold (true) or release (false) the break/attack key. */
    void setBreaking(boolean breaking);

    /** Explicit native crosshair-cache flush; unsupported implementations deny scheduled rest. */
    default boolean rebindForScheduledRest(){return false;}
    /** Explicit observed cooldown support; unsupported seams cannot admit an ability rest. */
    default boolean scheduledAbilityIdle(){return false;}

    /** No-op seam for tests/headless. */
    ForagingServices NONE = new ForagingServices() {
        @Override public boolean available() {
            return false;
        }
        @Override public boolean lookingAtLog() {
            return false;
        }
        @Override public BlockCoord targetLog() {
            return null;
        }
        @Override public double eyeDistance() {
            return Double.NaN;
        }
        @Override public boolean moving() {
            return false;
        }
        @Override public void setBreaking(boolean breaking) {
        }
    };
}
