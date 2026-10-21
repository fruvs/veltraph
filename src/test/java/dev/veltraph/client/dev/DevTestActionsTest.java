package dev.veltraph.client.dev;

import dev.veltraph.client.hydra.HydraEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure dispatch of the standard dev-test actions through a recording context (no Minecraft). */
class DevTestActionsTest {

    private final DevTestRegistry registry = DevTestActions.standard();

    private DevTestResult run(String id, RecordingDevTestContext ctx, boolean allowServerBound) {
        return registry.run(id, ctx, true, allowServerBound);
    }

    @Test
    void stopActionStopsMacrosAndReleasesInput() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        DevTestResult res = run("general.stop", ctx, false);
        assertEquals(DevTestResult.Status.LOCAL, res.status());
        assertEquals(1, ctx.stopAllCalls);
        assertEquals(1, ctx.releaseInputCalls);
    }

    @Test
    void failsafeAlertsDispatchTheRightEventType() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        run("failsafe.world_change", ctx, false);
        run("failsafe.floor_change", ctx, false);
        run("failsafe.player_nearby", ctx, false);
        assertTrue(ctx.alerts.contains(HydraEvent.Type.WORLD_ANOMALY));
        assertTrue(ctx.alerts.contains(HydraEvent.Type.FLOOR_CHANGE));
        assertTrue(ctx.alerts.contains(HydraEvent.Type.STAFF_NEARBY));
    }

    @Test
    void locationGateUsesContextDecision() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        DevTestResult res = run("failsafe.location_gate", ctx, false);
        assertEquals(DevTestResult.Status.LOCAL, res.status());
        assertTrue(res.message().contains("would allow"));
        assertFalse(ctx.localChats.isEmpty());
    }

    @Test
    void fixAngleChoosesVisibleNonMidpointTargetAwayFromCurrentPitch() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        ctx.curPitch = 30.0; // closer to max(38) than to min(-38) → far edge is min(-38)
        ctx.curYaw = 100.0;
        run("hydra.fix_angle", ctx, false);
        assertEquals(-38.0, ctx.fixPitch, 0.0001, "targets the FAR edge from current pitch, not the midpoint");
        assertEquals(145.0, ctx.fixYaw, 0.0001, "yaw sweeps +45° from current for a visible move");
    }

    @Test
    void fixAngleHelperPicksFarEdgeAndNeverMidpoint() {
        // current below midpoint → target the high edge; above → the low edge; never the midpoint (0).
        assertEquals(38.0, DevTestActions.visibleFixAnglePitch(-10.0, -38.0, 38.0), 1e-9);
        assertEquals(-38.0, DevTestActions.visibleFixAnglePitch(10.0, -38.0, 38.0), 1e-9);
        // unknown current pitch → deterministic edge (max), not midpoint.
        assertEquals(38.0, DevTestActions.visibleFixAnglePitch(Double.NaN, -38.0, 38.0), 1e-9);
        // degenerate window still yields a visible (non-zero-delta) move.
        assertEquals(-30.0, DevTestActions.visibleFixAnglePitch(0.0, 0.0, 0.0), 1e-9);
    }

    @Test
    void failsafeResponseActionsQueueEachTrigger() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        for (var type : dev.veltraph.client.failsafe.response.FailsafeResponseType.values()) {
            run("failsafe.response." + type.name().toLowerCase(java.util.Locale.ROOT), ctx, false);
        }
        assertEquals(dev.veltraph.client.failsafe.response.FailsafeResponseType.values().length,
                ctx.failsafeResponses.size(), "every failsafe trigger has a dev response action");
    }

    @Test
    void failsafeRoutineActionsQueueEachRoutine() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        for (var ro : dev.veltraph.client.failsafe.response.FailsafeRoutine.values()) {
            run("failsafe.routine." + ro.name().toLowerCase(java.util.Locale.ROOT), ctx, false);
        }
        assertEquals(dev.veltraph.client.failsafe.response.FailsafeRoutine.values().length,
                ctx.failsafeRoutines.size(), "every focused routine has a dev action");
    }

    @Test
    void movementReactionActionsQueueEachReaction() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        run("hydra.reaction_cage", ctx, false);
        run("hydra.reaction_barrier", ctx, false);
        run("hydra.reaction_floor_change", ctx, false);
        run("hydra.reaction_backup", ctx, false);
        assertEquals(java.util.List.of(
                        dev.veltraph.client.hydra.response.HydraReaction.CAGE,
                        dev.veltraph.client.hydra.response.HydraReaction.BARRIER,
                        dev.veltraph.client.hydra.response.HydraReaction.FLOOR_CHANGE,
                        dev.veltraph.client.hydra.response.HydraReaction.BACKUP),
                ctx.reactions);
    }

    @Test
    void rareParityClassifies() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        DevTestResult res = run("hydra.rare_parity", ctx, false);
        assertEquals(DevTestResult.Status.LOCAL, res.status());
        assertTrue(ctx.classifies.contains(HydraEvent.Type.FLOOR_CHANGE));
    }

    @Test
    void localChatWritesOnlyLocally() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        run("chat.local", ctx, false);
        assertEquals(1, ctx.localChats.size());
        assertTrue(ctx.serverChats.isEmpty(), "local chat must never reach the server");
    }

    @Test
    void dryRunChatDoesNotSend() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        DevTestResult res = run("chat.server_dryrun", ctx, true); // even when allowed, dry-run never sends
        assertEquals(DevTestResult.Status.DRY_RUN, res.status());
        assertTrue(ctx.serverChats.isEmpty());
    }

    @Test
    void serverChatExecuteSendsOnlyWhenAllowed() {
        RecordingDevTestContext blocked = new RecordingDevTestContext();
        assertEquals(DevTestResult.Status.BLOCKED, run("chat.server_execute", blocked, false).status());
        assertTrue(blocked.serverChats.isEmpty());

        RecordingDevTestContext allowed = new RecordingDevTestContext();
        DevTestResult res = run("chat.server_execute", allowed, true);
        assertEquals(DevTestResult.Status.EXECUTED, res.status());
        assertEquals(1, allowed.serverChats.size());
    }

    @Test
    void rewarpDryRunVsExecute() {
        RecordingDevTestContext dry = new RecordingDevTestContext();
        assertEquals(DevTestResult.Status.DRY_RUN, run("failsafe.rewarp_dryrun", dry, true).status());
        assertTrue(dry.serverCommands.isEmpty());

        RecordingDevTestContext exec = new RecordingDevTestContext();
        DevTestResult res = run("failsafe.rewarp_execute", exec, true);
        assertEquals(DevTestResult.Status.EXECUTED, res.status());
        assertEquals(1, exec.serverCommands.size());
        assertEquals("warp garden", exec.serverCommands.get(0));
    }

    @Test
    void executeReportsNoOpWhenNotConnected() {
        RecordingDevTestContext ctx = new RecordingDevTestContext();
        ctx.connected = false;
        DevTestResult res = run("chat.server_execute", ctx, true);
        assertEquals(DevTestResult.Status.NO_OP, res.status());
    }
}
