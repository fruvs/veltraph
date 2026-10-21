package dev.veltraph.client.hydra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure forward-collision BARRIER edge detector: rising edge while eligible, re-arm on clear. */
class BarrierWatcherTest {

    @Test
    void firesOnceWhenABarrierNewlyAppears() {
        BarrierWatcher w = new BarrierWatcher();
        assertFalse(w.observe(false, true), "clear path, no fire");
        assertTrue(w.observe(true, true), "block newly appeared while eligible → fire");
        assertFalse(w.observe(true, true), "still blocked → latched, no repeat");
    }

    @Test
    void reArmsAfterThePathClears() {
        BarrierWatcher w = new BarrierWatcher();
        w.observe(false, true);
        assertTrue(w.observe(true, true));
        assertFalse(w.observe(false, true), "path cleared → re-arm, no fire on clear");
        assertTrue(w.observe(true, true), "a second barrier fires again");
    }

    @Test
    void neverFiresWhileIneligible() {
        BarrierWatcher w = new BarrierWatcher();
        // Even a clear→blocked transition does not fire when ineligible (moving / not scripting).
        assertFalse(w.observe(false, false));
        assertFalse(w.observe(true, false));
        assertFalse(w.observe(true, false));
    }

    @Test
    void blockedOnTheVeryFirstObservationDoesNotFire() {
        // No baseline yet → a block already in front when we start watching is not a "new" barrier.
        BarrierWatcher w = new BarrierWatcher();
        assertFalse(w.observe(true, true), "no prior baseline → no false edge");
        assertFalse(w.observe(true, true));
    }

    @Test
    void becomingIneligibleDropsTheBaselineSoNoFalseEdge() {
        BarrierWatcher w = new BarrierWatcher();
        w.observe(false, true);          // baseline: clear
        w.observe(false, false);         // ineligible → reset baseline
        // Now blocked on the first eligible tick again: no baseline → no false fire.
        assertFalse(w.observe(true, true));
    }

    @Test
    void resetClearsState() {
        BarrierWatcher w = new BarrierWatcher();
        w.observe(false, true);
        assertTrue(w.observe(true, true));
        w.reset();
        // After reset, blocked-first-tick must not fire (baseline gone).
        assertFalse(w.observe(true, true));
    }
}
