package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure movement sequencer: each step held for its duration, then advance, then inactive. */
class HydraMovementRunnerTest {

    @Test
    void holdsEachStepForItsDurationThenAdvances() {
        HydraMovementRunner r = new HydraMovementRunner();
        HydraMovementStep back = HydraMovementStep.back(2, false);
        HydraMovementStep jump = HydraMovementStep.jump(1);
        r.start(List.of(back, jump));
        assertTrue(r.active());

        assertSame(back, r.advance()); // tick 1 — back
        assertSame(back, r.advance()); // tick 2 — back (2 ticks total)
        assertTrue(r.active());
        assertSame(jump, r.advance()); // tick 3 — jump (1 tick), sequence ends on this step
        assertFalse(r.active(), "inactive after the last step's last tick");
        assertNull(r.advance(), "nothing left to play");
    }

    @Test
    void emptyOrNullSequenceIsInactive() {
        HydraMovementRunner r = new HydraMovementRunner();
        r.start(List.of());
        assertFalse(r.active());
        assertNull(r.advance());
        r.start(null);
        assertFalse(r.active());
    }

    @Test
    void zeroDurationStepStillHeldOneTick() {
        HydraMovementRunner r = new HydraMovementRunner();
        HydraMovementStep s = HydraMovementStep.of(true, false, false, false, false, false, 0);
        r.start(List.of(s));
        assertSame(s, r.advance(), "a 0-tick step is clamped to 1 tick");
        assertFalse(r.active());
    }

    @Test
    void stopAbortsTheSequence() {
        HydraMovementRunner r = new HydraMovementRunner();
        r.start(List.of(HydraMovementStep.forward(10, false)));
        assertTrue(r.active());
        r.stop();
        assertFalse(r.active());
        assertEquals(0, r.remainingSteps());
        assertNull(r.advance());
    }
}
