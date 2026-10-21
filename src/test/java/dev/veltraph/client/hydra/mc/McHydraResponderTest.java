package dev.veltraph.client.hydra.mc;

import dev.veltraph.client.hydra.response.HydraReaction;
import dev.veltraph.client.hydra.response.HydraReactionPlanner;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MC seam drives the smooth fix-angle ease and the movement reaction over ticks against fake services
 * (no Minecraft). Proves the headline fix: fix-angle no longer snaps.
 */
class McHydraResponderTest {

    private static McHydraResponder responder(FakeRotationService rot, RecordingInputService in) {
        return new McHydraResponder(msg -> true, cmd -> true, rot, in);
    }

    @Test
    void fixAngleEasesOverTicksInsteadOfSnapping() {
        FakeRotationService rot = new FakeRotationService(0f, 0f);
        RecordingInputService in = new RecordingInputService();
        McHydraResponder r = responder(rot, in);

        r.fixAngle(90.0, 40.0);
        // One tick must NOT jump straight to the target (that was the snapping bug).
        r.tick();
        assertTrue(Math.abs(rot.yaw) < 90f, "yaw did not snap in one tick, got " + rot.yaw);
        assertTrue(rot.aimCalls >= 1);

        // Over many ticks it converges on the target.
        for (int i = 0; i < 200; i++) {
            r.tick();
        }
        assertEquals(90f, rot.yaw, 1.5f);
        assertEquals(40f, rot.pitch, 1.5f);
        assertTrue(rot.aimCalls > 3, "smooth ease takes several aim() calls, not one");
    }

    @Test
    void nanAxisKeepsCurrentValueAsTarget() {
        FakeRotationService rot = new FakeRotationService(25f, -5f);
        McHydraResponder r = responder(rot, new RecordingInputService());
        r.fixAngle(Double.NaN, 30.0); // keep yaw, move pitch
        for (int i = 0; i < 200; i++) {
            r.tick();
        }
        assertEquals(25f, rot.yaw, 1.5f, "yaw unchanged (NaN target)");
        assertEquals(30f, rot.pitch, 1.5f);
    }

    @Test
    void movementReactionHoldsInputsThenReleases() {
        FakeRotationService rot = new FakeRotationService();
        RecordingInputService in = new RecordingInputService();
        McHydraResponder r = responder(rot, in);

        // BACKUP = back-pedal for backupTicks; use a short, explicit duration.
        var steps = HydraReactionPlanner.plan(HydraReaction.BACKUP, new HydraReactionPlanner.Settings(3, 6, 8, 6));
        r.move(HydraReaction.BACKUP, steps);

        r.tick(); // tick 1
        assertTrue(in.back, "back held while the reaction runs");
        assertFalse(in.forward);
        r.tick(); // tick 2
        r.tick(); // tick 3 (last held tick)
        assertTrue(in.back);
        r.tick(); // tick 4 — sequence finished → release
        assertFalse(in.back, "inputs released once the reaction finishes");
        assertTrue(in.releaseAllCount >= 1);
    }

    @Test
    void tickIsANoOpWhenNothingScheduled() {
        FakeRotationService rot = new FakeRotationService(7f, 8f);
        RecordingInputService in = new RecordingInputService();
        McHydraResponder r = responder(rot, in);
        r.tick();
        assertEquals(0, rot.aimCalls, "no ease in progress → no aim");
        assertEquals(7f, rot.yaw, 1e-4);
    }

    @Test
    void hasActiveLocalReactionTracksLookLifecycle() {
        FakeRotationService rot = new FakeRotationService(0f, 0f);
        McHydraResponder r = responder(rot, new RecordingInputService());
        assertFalse(r.hasActiveLocalReaction(), "idle → no active reaction");

        r.fixAngle(90.0, 40.0);
        assertTrue(r.hasActiveLocalReaction(), "queued ease → active (the dev tick path will advance it)");
        for (int i = 0; i < 200 && r.hasActiveLocalReaction(); i++) {
            r.tick();
        }
        assertFalse(r.hasActiveLocalReaction(), "completed ease → inactive (dev tick path stops)");
        assertEquals(90f, rot.yaw, 1.5f);
    }

    @Test
    void hasActiveLocalReactionTracksMovementLifecycle() {
        RecordingInputService in = new RecordingInputService();
        McHydraResponder r = responder(new FakeRotationService(), in);
        r.move(HydraReaction.BACKUP, HydraReactionPlanner.plan(HydraReaction.BACKUP,
                new HydraReactionPlanner.Settings(2, 6, 8, 6)));
        assertTrue(r.hasActiveLocalReaction(), "queued movement → active");
        // advance past the sequence; once inactive the dev tick path stops calling tick().
        for (int i = 0; i < 10 && r.hasActiveLocalReaction(); i++) {
            r.tick();
        }
        assertFalse(r.hasActiveLocalReaction());
        r.tick(); // the stop tick releases input
        assertFalse(in.back, "movement input released after completion");
        assertTrue(in.releaseAllCount >= 1);
    }

    @Test
    void resetAbortsInFlightMovementAndLook() {
        FakeRotationService rot = new FakeRotationService(0f, 0f);
        RecordingInputService in = new RecordingInputService();
        McHydraResponder r = responder(rot, in);

        r.fixAngle(90.0, 40.0);
        r.move(HydraReaction.BACKUP,
                HydraReactionPlanner.plan(HydraReaction.BACKUP, new HydraReactionPlanner.Settings(20, 6, 8, 6)));
        r.tick();
        assertTrue(in.back);
        int aimCalls = rot.aimCalls;

        r.reset();
        assertFalse(in.back);
        assertTrue(in.releaseAllCount >= 1);
        r.tick();
        assertEquals(aimCalls, rot.aimCalls, "look ease was aborted");
    }

    @Test
    void finalHeldTickRemainsOwnedUntilReleaseTick() {
        var input = new RecordingInputService();
        var responder = responder(new FakeRotationService(), input);
        responder.move(HydraReaction.BACKUP, java.util.List.of(
                dev.veltraph.client.hydra.response.HydraMovementStep.back(1, false)));
        responder.tick();
        assertTrue(input.back);
        assertTrue(responder.hasActiveLocalReaction(), "final held input requires one cleanup tick");
        responder.tick();
        assertFalse(input.back);
        assertFalse(responder.hasActiveLocalReaction());
    }

    @Test
    void partialInputFailureAbortsSequenceAndUsesIndependentEmergencyRelease() {
        var nativeInput = new RecordingInputService();
        int[] emergency = {0};
        var broken = new dev.veltraph.client.service.InputService() {
            public void setForwardHeld(boolean held) { nativeInput.setForwardHeld(held); }
            public void setBackHeld(boolean held) { throw new IllegalStateException("broken setter"); }
            public void setLeftHeld(boolean held) { nativeInput.setLeftHeld(held); }
            public void setRightHeld(boolean held) { nativeInput.setRightHeld(held); }
            public void setJumpHeld(boolean held) { nativeInput.setJumpHeld(held); }
            public void setSneakHeld(boolean held) { nativeInput.setSneakHeld(held); }
            public void releaseAllMovement() { throw new IllegalStateException("broken release"); }
        };
        var responder = new McHydraResponder(s -> true, c -> true, new FakeRotationService(), broken,
                () -> { emergency[0]++; nativeInput.releaseAllMovement(); });
        responder.move(HydraReaction.BACKUP, java.util.List.of(
                new dev.veltraph.client.hydra.response.HydraMovementStep(true, false, true, false, false, false, 20)));
        responder.tick();
        assertFalse(nativeInput.forward);
        assertFalse(responder.hasActiveLocalReaction());
        assertEquals(1, emergency[0]);
        responder.tick();
        assertEquals(1, emergency[0], "failed sequence cannot run/reassert on later ticks");
    }
}
