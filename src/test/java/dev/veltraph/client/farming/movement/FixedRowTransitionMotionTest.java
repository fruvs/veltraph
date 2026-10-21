package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual-position and observed-momentum controls; no native physics or acceptance is supplied. */
class FixedRowTransitionMotionTest {
    private final Vec3 goal = new Vec3(.5, 64, .5);
    private final Optional<Vec3> still = Optional.of(new Vec3(0, 0, 0));

    @Test void nearbyApproachKeepsNeededKeysWithoutClockParity() {
        for (float yaw : new float[]{0, 90, 180, -90, -70}) {
            var pose = new Vec3(.1, 64, .1);
            var expected = FixedViewKeySteering.toward(pose, goal, yaw);
            for (int observation = 0; observation < 6; observation++)
                assertEquals(expected, FixedRowTransitionMotion.coastingKeys(pose, goal, yaw, still));
            assertTrue(expected.forward() || expected.back() || expected.left() || expected.right());
        }
    }

    @Test void coastOnlyTheAxisWhoseObservedMomentumClosesTheGap() {
        var pose = new Vec3(.4, 64, -.5);
        var keys = FixedRowTransitionMotion.coastingKeys(pose, goal, 0,
                Optional.of(new Vec3(.1, 0, .1)));
        assertTrue(keys.forward(), "The long corridor axis still needs forward input");
        assertFalse(keys.left() || keys.right(), "Lateral momentum closes the lateral correction");
        assertFalse(keys.back());
    }

    @Test void projectedOvershootCoastsWithoutInventingAnOppositeKey() {
        var pose = new Vec3(.5, 64, .35);
        var keys = FixedRowTransitionMotion.coastingKeys(pose, goal, 0,
                Optional.of(new Vec3(0, 0, .3)));
        assertFalse(keys.forward() || keys.back() || keys.left() || keys.right());
        var overshot = new Vec3(.5, 64, .65);
        assertTrue(FixedRowTransitionMotion.coastingKeys(overshot, goal, 0,
                Optional.of(new Vec3(0, 0, .1))).back(), "An observed overshoot needs a real correction");
    }

    @Test void momentumAwayFromTheGoalCannotSuppressNeededCorrection() {
        var pose = new Vec3(.5, 64, .35);
        assertTrue(FixedRowTransitionMotion.coastingKeys(pose, goal, 0,
                Optional.of(new Vec3(0, 0, -.1))).forward());
    }

    @Test void actualCrossingAndProjectedEntryCannotManufactureArrival() {
        var outside = new Vec3(.5, 64, .2);
        var inside = new Vec3(.5, 64, .4);
        assertFalse(FixedRowTransitionMotion.arrivalObserved(null, inside, goal, still));
        assertFalse(FixedRowTransitionMotion.arrivalObserved(outside, inside, goal, still));
        assertFalse(FixedRowTransitionMotion.arrivalObserved(inside, outside, goal,
                Optional.of(new Vec3(0, 0, .3))));
        assertFalse(FixedRowTransitionMotion.arrivalObserved(inside, inside, goal,
                Optional.of(new Vec3(.3, 0, 0))), "Cross-corridor momentum also refuses impending drift");
        assertTrue(FixedRowTransitionMotion.arrivalObserved(inside, goal, goal, still));
    }

    @Test void invalidMomentumFailsClosedAndUnknownMomentumIsNeverGuessed() {
        var bad = Optional.of(new Vec3(Double.NaN, 0, 0));
        assertEquals(new FixedViewKeySteering.Keys(false, false, false, false),
                FixedRowTransitionMotion.coastingKeys(goal, goal, 0, bad));
        assertFalse(FixedRowTransitionMotion.arrivalObserved(goal, goal, goal, bad));
        var outside = new Vec3(.5, 64, -.5);
        assertEquals(FixedViewKeySteering.toward(outside, goal, 0),
                FixedRowTransitionMotion.coastingKeys(outside, goal, 0, Optional.empty()));
        assertFalse(FixedRowTransitionMotion.arrivalObserved(outside, goal, goal, Optional.empty()));
    }
}
