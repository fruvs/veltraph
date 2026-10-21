package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reaction planner expands each situation into concrete, configurable local movement steps. */
class HydraReactionPlannerTest {

    private static final HydraReactionPlanner.Settings S =
            new HydraReactionPlanner.Settings(10, 6, 8, 6);

    @Test
    void backupBackPedals() {
        List<HydraMovementStep> steps = HydraReactionPlanner.plan(HydraReaction.BACKUP, S);
        assertEquals(1, steps.size());
        assertTrue(steps.get(0).back());
        assertFalse(steps.get(0).forward());
        assertEquals(10, steps.get(0).durationTicks());
    }

    @Test
    void cageJumpsThenBackHops() {
        List<HydraMovementStep> steps = HydraReactionPlanner.plan(HydraReaction.CAGE, S);
        assertEquals(2, steps.size());
        assertTrue(steps.get(0).jump(), "first step jumps to break the cage");
        assertTrue(steps.get(1).back() && steps.get(1).jump(), "then back-hops out");
        assertEquals(8, steps.get(0).durationTicks());
    }

    @Test
    void barrierBacksOffThenStrafes() {
        List<HydraMovementStep> steps = HydraReactionPlanner.plan(HydraReaction.BARRIER, S);
        assertEquals(2, steps.size());
        assertTrue(steps.get(0).back());
        assertTrue(steps.get(1).right() && steps.get(1).jump(), "strafe around the barrier, hopping");
    }

    @Test
    void floorChangeBackHopsToStableGround() {
        List<HydraMovementStep> steps = HydraReactionPlanner.plan(HydraReaction.FLOOR_CHANGE, S);
        assertEquals(1, steps.size());
        assertTrue(steps.get(0).back() && steps.get(0).jump());
        assertEquals(6, steps.get(0).durationTicks());
    }

    @Test
    void nullReactionIsEmpty() {
        assertTrue(HydraReactionPlanner.plan(null, S).isEmpty());
    }

    @Test
    void nullSettingsFallsBackToDefaults() {
        List<HydraMovementStep> steps = HydraReactionPlanner.plan(HydraReaction.BACKUP, null);
        assertEquals(1, steps.size());
        assertTrue(steps.get(0).durationTicks() >= 1);
    }
}
