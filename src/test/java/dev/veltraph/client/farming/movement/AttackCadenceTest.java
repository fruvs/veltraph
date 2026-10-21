package dev.veltraph.client.farming.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attack-key cadence policy (Target 6): continuous hold by default, periodic re-tap when set. */
class AttackCadenceTest {

    @Test
    void zeroCadenceHoldsEveryTick() {
        for (long t = 0; t < 50; t++) {
            assertTrue(AttackCadence.shouldHold(t, 0), "cadence 0 = continuous hold at tick " + t);
        }
    }

    @Test
    void negativeCadenceAlsoHoldsContinuously() {
        for (long t = 0; t < 10; t++) {
            assertTrue(AttackCadence.shouldHold(t, -5));
        }
    }

    @Test
    void positiveCadenceReleasesOneTickEveryN() {
        int n = 4;
        int releases = 0;
        for (long t = 0; t < 4 * n; t++) {
            boolean hold = AttackCadence.shouldHold(t, n);
            if (!hold) {
                releases++;
                assertTrue(t % n == 0L, "release falls on the window boundary at tick " + t);
            }
        }
        assertEquals(4, releases);
    }

    @Test
    void cadenceMostlyHolds() {
        // With cadence 5, 4 of every 5 ticks hold (only the boundary tick re-taps).
        int holds = 0;
        for (long t = 0; t < 5; t++) {
            if (AttackCadence.shouldHold(t, 5)) {
                holds++;
            }
        }
        assertEquals(4, holds);
        assertFalse(AttackCadence.shouldHold(0, 5), "the window boundary re-taps");
    }
}
