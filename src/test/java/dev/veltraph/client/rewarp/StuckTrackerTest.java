package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure no-position-change timer used by the auto-rewarp stuck trigger. */
class StuckTrackerTest {

    @Test
    void notStuckWhileMoving() {
        StuckTracker t = new StuckTracker();
        t.sample(true, 0, 64, 0, 0L);
        t.sample(true, 1, 64, 0, 50L);
        t.sample(true, 2, 64, 0, 100L);
        assertFalse(t.isStuck());
    }

    @Test
    void becomesStuckWhenStationaryAndRecordsStreakStart() {
        StuckTracker t = new StuckTracker();
        t.sample(true, 10, 64, 10, 1000L); // first sample, not stuck yet
        assertFalse(t.isStuck());
        t.sample(true, 10, 64, 10, 1050L); // no movement -> stuck, streak began at 1050
        assertTrue(t.isStuck());
        assertEquals(1050L, t.stuckSinceMs());
        // Streak start does not advance while still stuck.
        t.sample(true, 10.0, 64, 10.0, 5000L);
        assertTrue(t.isStuck());
        assertEquals(1050L, t.stuckSinceMs());
    }

    @Test
    void movingResetsTheStreak() {
        StuckTracker t = new StuckTracker();
        t.sample(true, 0, 64, 0, 0L);
        t.sample(true, 0, 64, 0, 100L); // stuck from 100
        assertTrue(t.isStuck());
        t.sample(true, 5, 64, 5, 200L); // moved -> not stuck
        assertFalse(t.isStuck());
    }

    @Test
    void notTrackingResets() {
        StuckTracker t = new StuckTracker();
        t.sample(true, 0, 64, 0, 0L);
        t.sample(true, 0, 64, 0, 100L);
        assertTrue(t.isStuck());
        t.sample(false, 0, 64, 0, 200L); // paused/idle -> reset
        assertFalse(t.isStuck());
        // A fresh start after re-tracking begins a new "first sample".
        t.sample(true, 0, 64, 0, 300L);
        assertFalse(t.isStuck());
    }

    @Test
    void subEpsilonDriftCountsAsStuck() {
        StuckTracker t = new StuckTracker();
        t.sample(true, 0, 64, 0, 0L);
        // Tiny drift below the movement epsilon should still count as no progress.
        t.sample(true, 0.01, 64, 0.01, 100L);
        assertTrue(t.isStuck());
    }
}
