package dev.veltraph.client.combat.heal;

/**
 * The Minecraft seam for the Hyperion auto-heal: read health/weapon/clearance and perform the camera +
 * use-item actions. An interface so the orchestrator ({@code combat/heal/CombatAutoHeal}) is unit-testable
 * with a fake; the MC implementation is {@code combat/heal/mc/McHealServices}.
 */
public interface HealServices {

    /** Whether a world + local player are present. */
    boolean available();

    /** Whether the configured heal weapon (Hyperion) is in the hotbar (and selectable). */
    boolean hasHyperion();

    /** The player's current health as a percentage of max (0–100); {@code 100} when unknown. */
    double healthPct();

    /** Whether the space directly above the player is clear (the "blocks in the way" check). */
    boolean clearAbove();

    /** Native source readiness binds current action-bar raw HP/mana; pure legacy ports stay percentage based. */
    default boolean usesSourceReadiness() { return false; }
    default boolean sourceReady(boolean ignoreHealth, boolean emergency) { return false; }
    /** Read-only repeat decision chooses a prospective item; it must not change captured slot/type. */
    default boolean prospectiveSourceReady() { return sourceReady(false,false); }
    /** Select a new source heal item under the existing snapshot; no native use implied. */
    default boolean prepareSourceRepeat() { return false; }
    default boolean sourceHealthRecovered() { return healthPct() > 50; }
    /** Source buffer predicate is selected Hyperion AND the instance look-down permission. */
    default boolean sourceHyperionLookDown() { return false; }
    /** Read-only matching native player-position receipt newer than this ordinary use. */
    default boolean sourcePostUsePositionReceived() { return false; }
    /** Cancel only this attempt's current owned rotation before watchdog release/repeat. */
    default void cancelOwnedHealRotation() {}
    /** Native source dispatch cadence; legacy pure ports have no native frame serial. */
    default long sourceClientTick(){return Long.MIN_VALUE;}
    record SourceTickScope(Object player,Object world,Object connection) {}
    default SourceTickScope sourceTickScope(){return null;}
    default int sampleSourcePhaseDelay(){return java.util.concurrent.ThreadLocalRandom.current().nextInt(0,2);}
    /** Source FINISH selects its saved slot at SWITCH, separately from modern smooth view return. */
    default boolean restoreSourceSavedSlot(){return true;}

    /** Advance the owned camera request before reading completion. */
    default void tickCamera() {}
    /** Actual application callback has completed, not merely a target was published. */
    default boolean cameraReady() { return true; }
    default boolean restorationComplete() { return true; }
    /** Exact native snapshot still belongs to this attempt. */
    default boolean custodyValid() { return true; }
    default boolean ownsSnapshot() { return true; }

    /** Save the current view and request the conditional source look-down. */
    void lookDown();

    /** Select the heal weapon slot if needed and right-click (use) it. */
    void castHyperion();

    /** Native ports override this to report whether the ordinary use was actually invoked. */
    default HealCastOutcome castWithOutcome() {
        castHyperion();
        return HealCastOutcome.EMITTED;
    }

    /** Restore the pitch saved by {@link #lookDown()}. */
    void restoreLook();
    /** Discard saved camera/slot state when the world changes. */
    default void discardSnapshot() {}

    /** No-op seam for tests/headless. */
    HealServices NONE = new HealServices() {
        @Override public boolean available() {
            return false;
        }
        @Override public boolean hasHyperion() {
            return false;
        }
        @Override public double healthPct() {
            return 100.0;
        }
        @Override public boolean clearAbove() {
            return true;
        }
        @Override public void lookDown() {
        }
        @Override public void castHyperion() {
        }
        @Override public void restoreLook() {
        }
    };
}
