package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The smooth fix-angle stepper: bounded per tick (no snap), eases to the target, then deactivates. */
class HydraLookTest {

    private static HydraLook look() {
        return new HydraLook(new HydraLook.Settings(14f, 10f, 0.34, 0.6f));
    }

    @Test
    void firstStepNeverSnapsToALargeTarget() {
        HydraLook l = look();
        l.begin(120f, 60f);
        float[] next = l.step(0f, 0f);
        // A 120° yaw / 60° pitch correction cannot complete in one tick — it advances at most the per-tick cap.
        assertTrue(Math.abs(next[0]) <= 14f + 1e-4, "yaw step within per-tick cap, got " + next[0]);
        assertTrue(Math.abs(next[1]) <= 10f + 1e-4, "pitch step within per-tick cap, got " + next[1]);
        assertTrue(l.active(), "still easing after one tick");
    }

    @Test
    void easesAllTheWayToTheTargetOverManyTicks() {
        HydraLook l = look();
        l.begin(90f, 40f);
        float yaw = 0f;
        float pitch = 0f;
        for (int i = 0; i < 200 && l.active(); i++) {
            float[] n = l.step(yaw, pitch);
            yaw = n[0];
            pitch = n[1];
        }
        assertFalse(l.active(), "the move completes");
        assertEquals(90f, yaw, 1.0f);
        assertEquals(40f, pitch, 1.0f);
    }

    @Test
    void pitchIsClampedToVerticalLimits() {
        HydraLook l = look();
        l.begin(0f, 300f); // absurd target → clamps to 90
        assertEquals(90f, l.targetPitch(), 1e-4);
    }

    @Test
    void inactiveStepReturnsCurrentUnchanged() {
        HydraLook l = look();
        float[] n = l.step(12f, 34f);
        assertEquals(12f, n[0], 1e-4);
        assertEquals(34f, n[1], 1e-4);
        assertFalse(l.active());
    }

    @Test
    void takesTheShortWayAroundTheYawWrap() {
        HydraLook l = look();
        // From +170 to -170 the short path is +20° (across the ±180 wrap), not -340°.
        l.begin(-170f, 0f);
        float[] n = l.step(170f, 0f);
        float delta = HydraLook.signedDelta(170f, n[0]);
        assertTrue(delta > 0f && delta <= 14f + 1e-4, "moves the short way (positive, bounded), got " + delta);
    }
}
