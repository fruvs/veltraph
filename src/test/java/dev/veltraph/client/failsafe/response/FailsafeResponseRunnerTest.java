package dev.veltraph.client.failsafe.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure runner executes a plan over ticks via a recording sink, releasing input on completion/reset. */
class FailsafeResponseRunnerTest {

    /** Records every sink effect so the runner's per-tick behavior is verifiable. */
    private static class RecordingSink implements FailsafeActionSink {
        float yaw = 0f, pitch = 0f;
        int lookApplies;
        boolean fwd, back, left, right, jump, sneak, attack, use;
        int releaseAllCount;
        final List<String> commands = new ArrayList<>();

        @Override public float currentYaw() { return yaw; }
        @Override public float currentPitch() { return pitch; }
        @Override public void applyLook(float y, float p) { yaw = y; pitch = p; lookApplies++; }
        @Override public void setMovement(boolean f, boolean b, boolean l, boolean r, boolean j, boolean s) {
            fwd = f; back = b; left = l; right = r; jump = j; sneak = s;
        }
        @Override public void setAttack(boolean held) { attack = held; }
        @Override public void setUse(boolean held) { use = held; }
        @Override public void sendCommand(String c) { commands.add(c); }
        @Override public void releaseAll() {
            fwd = back = left = right = jump = sneak = attack = use = false;
            releaseAllCount++;
        }
        boolean anyInputHeld() { return fwd || back || left || right || jump || sneak || attack || use; }
    }

    private static FailsafeResponseSettings full() {
        return new FailsafeResponseSettings(true, FailsafeResponseProfile.FULL, 2, 30.0, 4,
                true, true, true, true, true, 3, 30_000L, 200,
                java.util.EnumSet.allOf(FailsafeResponseType.class));
    }

    @Test
    void runsAPlanToCompletionThenReleasesInput() {
        RecordingSink sink = new RecordingSink();
        FailsafeResponseRunner runner = new FailsafeResponseRunner();
        FailsafeResponsePlan plan = FailsafeResponsePlanner.routine(FailsafeRoutine.FULL_PLAN, full());
        runner.start(plan, 14f, 200, sink);
        assertTrue(runner.active());

        int guard = 0;
        while (runner.active() && guard++ < 1000) {
            runner.tick(sink);
        }
        assertFalse(runner.active(), "plan completes");
        assertTrue(sink.lookApplies > 1, "smooth look advanced over multiple ticks");
        assertFalse(sink.anyInputHeld(), "all input released at completion");
        assertTrue(sink.releaseAllCount >= 1);
    }

    @Test
    void resetAbortsAndReleasesInput() {
        RecordingSink sink = new RecordingSink();
        FailsafeResponseRunner runner = new FailsafeResponseRunner();
        runner.start(FailsafeResponsePlanner.routine(FailsafeRoutine.MOVEMENT_SHUFFLE, full()), 14f, 200, sink);
        runner.tick(sink); // hold a movement step
        assertTrue(sink.anyInputHeld());
        runner.reset(sink);
        assertFalse(runner.active());
        assertFalse(sink.anyInputHeld(), "reset releases held input");
        assertTrue(sink.releaseAllCount >= 1);
    }

    @Test
    void maxTicksCapStopsARunawayPlan() {
        RecordingSink sink = new RecordingSink();
        FailsafeResponseRunner runner = new FailsafeResponseRunner();
        // A plan whose natural length far exceeds the cap; cap=3 must end it.
        FailsafeResponsePlan plan = FailsafeResponsePlanner.routine(FailsafeRoutine.FULL_PLAN, full());
        runner.start(plan, 14f, 3, sink);
        for (int i = 0; i < 50 && runner.active(); i++) {
            runner.tick(sink);
        }
        assertFalse(runner.active(), "max-ticks cap ends the plan");
        assertFalse(sink.anyInputHeld());
    }

    @Test
    void emptyPlanIsInactive() {
        RecordingSink sink = new RecordingSink();
        FailsafeResponseRunner runner = new FailsafeResponseRunner();
        runner.start(new FailsafeResponsePlan("none", List.of()), 14f, 80, sink);
        assertFalse(runner.active());
    }
    @Test
    void replacementReleasesPreviousInputsBeforeLookOrEmptyPlan() {
        var sink = new RecordingSink();
        var runner = new FailsafeResponseRunner();
        var move = new FailsafeResponsePlan("move", List.of(FailsafeResponseAction.move(true,false,false,false,20)));
        runner.start(move, 6, 50, sink);
        runner.tick(sink);
        assertTrue(sink.fwd);
        runner.start(new FailsafeResponsePlan("look", List.of(FailsafeResponseAction.look(40,0,20))),6,50,sink);
        assertFalse(sink.anyInputHeld());
        runner.start(move,6,50,sink);
        runner.tick(sink);
        runner.start(new FailsafeResponsePlan("empty",List.of()),6,50,sink);
        assertFalse(sink.anyInputHeld());
        assertFalse(runner.active());
    }

    @Test
    void budgetReleasesOnLastPermittedTickWithoutIssuingNextCommand() {
        var sink = new RecordingSink();
        var runner = new FailsafeResponseRunner();
        runner.start(new FailsafeResponsePlan("bounded",List.of(
                FailsafeResponseAction.move(true,false,false,false,1),
                FailsafeResponseAction.command("warp garden"))),6,1,sink);
        runner.tick(sink);
        assertFalse(runner.active());
        assertFalse(sink.anyInputHeld());
        assertTrue(sink.commands.isEmpty(),"no action may start after the tick budget");
    }

    @Test
    void adapterFailureAbortsAndReleasesOtherHeldInputs() {
        var sink = new RecordingSink() {
            @Override public void applyLook(float y,float p) { throw new IllegalStateException("lost player"); }
        };
        var runner = new FailsafeResponseRunner();
        runner.start(new FailsafeResponsePlan("look",List.of(FailsafeResponseAction.look(40,0,20))),6,50,sink);
        sink.attack = true;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->runner.tick(sink));
        assertFalse(runner.active());
        assertFalse(sink.anyInputHeld());
    }

}
