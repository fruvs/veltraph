package dev.veltraph.client.failsafe.response;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure planner sequences: per-trigger plans + focused routines, honoring profile + action toggles. */
class FailsafeResponsePlannerTest {

    private static FailsafeResponseSettings settings(FailsafeResponseProfile profile, boolean jump, boolean sneak,
                                                     boolean attack, boolean use, boolean move) {
        return new FailsafeResponseSettings(true, profile, 3, 35.0, 8, jump, sneak, attack, use, move, 6,
                30_000L, 80, EnumSet.allOf(FailsafeResponseType.class));
    }

    private static boolean has(FailsafeResponsePlan p, FailsafeResponseAction.Kind kind) {
        return p.actions().stream().anyMatch(a -> a.kind() == kind);
    }

    @Test
    void everyTriggerProducesANonEmptyPlanThatLooksAround() {
        FailsafeResponseSettings s = settings(FailsafeResponseProfile.LOCAL_SAFE, true, true, true, true, true);
        for (FailsafeResponseType type : FailsafeResponseType.values()) {
            FailsafeResponsePlan p = FailsafeResponsePlanner.plan(type, s);
            assertFalse(p.isEmpty(), type + " has a plan");
            assertTrue(has(p, FailsafeResponseAction.Kind.LOOK), type + " looks around");
            assertEquals(type.name(), p.label());
        }
    }

    @Test
    void minimalProfileIsLookOnly() {
        FailsafeResponseSettings s = settings(FailsafeResponseProfile.MINIMAL, true, true, true, true, true);
        FailsafeResponsePlan p = FailsafeResponsePlanner.plan(FailsafeResponseType.BOSS_TELEPORTED, s);
        assertTrue(has(p, FailsafeResponseAction.Kind.LOOK));
        assertFalse(has(p, FailsafeResponseAction.Kind.MOVE), "MINIMAL → no move");
        assertFalse(has(p, FailsafeResponseAction.Kind.JUMP), "MINIMAL → no jump");
    }

    @Test
    void clicksOnlyInFullProfileAndWhenToggled() {
        // LOCAL_SAFE never clicks even if attack/use toggled.
        FailsafeResponsePlan safe = FailsafeResponsePlanner.plan(FailsafeResponseType.MENTION,
                settings(FailsafeResponseProfile.LOCAL_SAFE, true, true, true, true, true));
        assertFalse(has(safe, FailsafeResponseAction.Kind.ATTACK_CLICK), "LOCAL_SAFE → no attack click");
        // FULL with attack/use on → clicks present.
        FailsafeResponsePlan full = FailsafeResponsePlanner.plan(FailsafeResponseType.MENTION,
                settings(FailsafeResponseProfile.FULL, true, true, true, true, true));
        assertTrue(has(full, FailsafeResponseAction.Kind.ATTACK_CLICK));
        assertTrue(has(full, FailsafeResponseAction.Kind.USE_CLICK));
        // FULL with attack/use OFF → no clicks (toggle respected).
        FailsafeResponsePlan fullNoClick = FailsafeResponsePlanner.plan(FailsafeResponseType.MENTION,
                settings(FailsafeResponseProfile.FULL, true, true, false, false, true));
        assertFalse(has(fullNoClick, FailsafeResponseAction.Kind.ATTACK_CLICK));
    }

    @Test
    void moveToggleOffRemovesMovement() {
        FailsafeResponsePlan p = FailsafeResponsePlanner.plan(FailsafeResponseType.BARRIER,
                settings(FailsafeResponseProfile.FULL, true, true, true, true, /*move*/ false));
        assertFalse(has(p, FailsafeResponseAction.Kind.MOVE), "move toggle off → no MOVE actions");
    }

    @Test
    void teleportIntoBlockBacksOff() {
        FailsafeResponsePlan p = FailsafeResponsePlanner.plan(FailsafeResponseType.TELEPORTED_INTO_BLOCK,
                settings(FailsafeResponseProfile.LOCAL_SAFE, true, true, false, false, true));
        assertTrue(p.actions().stream().anyMatch(a -> a.kind() == FailsafeResponseAction.Kind.MOVE && a.back()),
                "teleport-into-block backs off");
    }

    @Test
    void routinesIsolateOneSlice() {
        FailsafeResponseSettings s = settings(FailsafeResponseProfile.FULL, true, true, true, true, true);
        assertTrue(has(FailsafeResponsePlanner.routine(FailsafeRoutine.LOOK_SWEEP, s),
                FailsafeResponseAction.Kind.LOOK));
        FailsafeResponsePlan js = FailsafeResponsePlanner.routine(FailsafeRoutine.JUMP_SNEAK, s);
        assertTrue(has(js, FailsafeResponseAction.Kind.JUMP) && has(js, FailsafeResponseAction.Kind.SNEAK));
        FailsafeResponsePlan clicks = FailsafeResponsePlanner.routine(FailsafeRoutine.CLICKS, s);
        assertTrue(has(clicks, FailsafeResponseAction.Kind.ATTACK_CLICK)
                && has(clicks, FailsafeResponseAction.Kind.USE_CLICK));
        assertEquals(4, FailsafeResponsePlanner.routine(FailsafeRoutine.MOVEMENT_SHUFFLE, s).actions().size());
        assertTrue(FailsafeResponsePlanner.routine(FailsafeRoutine.FULL_PLAN, s).actions().size() >= 5);
    }

    @Test
    void nullsAreEmpty() {
        FailsafeResponseSettings s = settings(FailsafeResponseProfile.FULL, true, true, true, true, true);
        assertTrue(FailsafeResponsePlanner.plan(null, s).isEmpty());
        assertTrue(FailsafeResponsePlanner.routine(null, s).isEmpty());
    }
}
