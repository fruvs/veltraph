package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundProfileForecastTest {
    static GroundNativePhysicsProfile profile(double base, boolean sprint, int jump) {
        return GroundNativePhysicsProfile.classify(GroundNativePhysicsProfileTest.state(base, sprint, jump)).orElseThrow();
    }
    @Test void ordinaryVerticalSequenceRemainsByteValueCompatible() {
        var f = GroundProfileForecast.groundedNeutralTakeoff(profile(.1, false, -1), new Vec3(0, 0, 0),
                new Vec3(0, -.0784, 0), 1, .6F, 12, 0).orElseThrow();
        for (var step : f.samples()) {
            var old = GroundJumpLandingForecast.ordinaryVertical(step.nativeStep()).orElseThrow();
            assertEquals(old.height(), step.position().y());
            assertEquals(old.velocity(), step.velocity().y());
        }
    }
    @Test void boostApexRequiresNewGeometryBeyondOrdinaryJumpEnvelope() {
        var f = GroundProfileForecast.groundedNeutralTakeoff(profile((double).36F, false, 5), new Vec3(0, 70, 0),
                new Vec3(0, -.0784, 0), 1, .6F, 32, 100).orElseThrow();
        assertTrue(f.apex() > 75);
        assertTrue(f.samples().getLast().position().y() < 70);
        assertFalse(GroundLocomotionMotion.airborneCurrent(f.samples().get(6).position(),
                f.samples().get(6).velocity(), new Vec3(0, 70, 0), new Vec3(0, 71, 1), (double).36F));
    }
    @Test void firstBoostStepUsesNativeFloatRoundedImpulse() {
        var f = GroundProfileForecast.groundedNeutralTakeoff(profile((double).4F, false, 5), new Vec3(0, 0, 0),
                new Vec3(.12, -.0784, -.08), 1, .6F, 1, 100).orElseThrow();
        var sample = f.samples().getFirst();
        assertEquals((double)(.42F + .1F * 6F), sample.position().y());
        assertEquals(((double)(.42F + .1F * 6F) - .08) * (double).98F, sample.velocity().y());
        assertEquals(.12 * (double)(.6F * .91F), sample.velocity().x());
    }
    @Test void sprintJumpIsUnknownUntilActualSprintImpulseModelAndOwnershipExists() {
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(profile((double).4F, true, 5),
                new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1, .6F, 32, 100).isEmpty());
    }
    @Test void fasterObservedCoastDemandsLongerSupportedSweep() {
        var slow = GroundProfileForecast.groundBrake(profile(.1, false, -1), new Vec3(0, 70, 0),
                new Vec3(0, -.0784, .1), .6F, 16).orElseThrow();
        var fast = GroundProfileForecast.groundBrake(profile((double).4F, true, -1), new Vec3(0, 70, 0),
                new Vec3(0, -.0784, .5), .6F, 16).orElseThrow();
        assertEquals(slow.asymptoticCoast().z() * 5, fast.asymptoticCoast().z(), 1e-14);
        assertTrue(fast.asymptoticCoast().z() > fast.samples().getLast().position().z());
    }
    @Test void iceAndUnknownFrictionAreNotNormalizedIntoOrdinaryFamily() {
        var p = profile((double).4F, false, -1);
        for (float friction : new float[]{.98F, 0, Float.NaN}) {
            assertTrue(GroundProfileForecast.groundBrake(p, new Vec3(0, 70, 0),
                    new Vec3(0, -.0784, .5), friction, 16).isEmpty());
        }
    }
    @Test void invalidForecastInputsAndUnboundedHorizonDeny() {
        var p = profile((double).4F, false, 5);
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(0, 0, 0), new Vec3(0, .5, 0), 1, .6F, 32, 100).isEmpty());
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1, .6F, 65, 100).isEmpty());
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(Double.NaN, 0, 0), new Vec3(0, 0, 0), 1, .6F, 32, 100).isEmpty());
        assertTrue(GroundProfileForecast.groundBrake(p, new Vec3(0, 0, 0), new Vec3(0, 0, 0), .6F, 0).isEmpty());
    }
    @Test void observedAirborneNeverSynthesizesTakeoffAgain() {
        var f = GroundProfileForecast.observedAirborneNeutral(profile((double).4F, false, 5),
                new Vec3(0, 75, 0), new Vec3(.12, -.2, -.08), 1, 100).orElseThrow();
        assertEquals(74.8, f.samples().getFirst().position().y());
        assertEquals(.12 * (double).91F, f.samples().getFirst().velocity().x());
        assertTrue(Float.isNaN(f.impulse()));
        assertEquals(GroundProfileForecast.StartPhase.OBSERVED_AIRBORNE, f.phase());
    }
    @Test void groundedFirstStepAndSecondAirborneDragAreDistinct() {
        var f = GroundProfileForecast.groundedNeutralTakeoff(profile((double).4F, false, 5),
                new Vec3(0, 70, 0), new Vec3(.12, -.0784, 0), 1, .6F, 2, 100).orElseThrow();
        assertEquals(.12 * (double)(.6F * .91F), f.samples().getFirst().velocity().x());
        assertEquals(.12 * (double)(.6F * .91F) * (double).91F, f.samples().getLast().velocity().x());
        assertEquals(GroundProfileForecast.StartPhase.GROUNDED_TAKEOFF, f.phase());
    }
    @Test void playerCombinedHorizontalThresholdKeepsBothSmallAxesWhenVectorIsLargeEnough() {
        var f = GroundProfileForecast.observedAirborneNeutral(profile(.1, false, -1),
                new Vec3(0, 70, 0), new Vec3(.0025, -.2, .0025), 1, 0).orElseThrow();
        assertEquals(.0025, f.samples().getFirst().position().x());
        var stopped = GroundProfileForecast.observedAirborneNeutral(profile(.1, false, -1),
                new Vec3(0, 70, 0), new Vec3(.002, -.2, .002), 1, 0).orElseThrow();
        assertEquals(0, stopped.samples().getFirst().position().x());
    }
    @Test void unknownGroundFrictionCannotUseAirborneDefaultsForTakeoff() {
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(profile((double).4F, false, 5),
                new Vec3(0, 70, 0), new Vec3(.12, -.0784, 0), 1, .98F, 2, 100).isEmpty());
    }
    @Test void finiteBoostExpiryCannotBeForecastBeyondItsLiveDuration() {
        var p = profile((double).4F, false, 5);
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1, .6F, 32, 32).isEmpty());
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1, .6F, 32, 33).isPresent());
        assertTrue(GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(0, 0, 0), new Vec3(0, 0, 0), 1, .6F, 32, -1).isPresent());
    }
    @Test void fullCapturedSevenEffectsPreserveCorrectedGroundedForecast() {
        var p = GroundNativePhysicsProfile.classify(GroundCapturedEffectsTest.state((double).4F, false,
                GroundCapturedEffectsTest.capturedSeven())).orElseThrow();
        var f = GroundProfileForecast.groundedNeutralTakeoff(p, new Vec3(0, 70, 0),
                new Vec3(.12, -.0784, -.08), 1, .6F, 2, 666900).orElseThrow();
        assertEquals(.12 * (double)(.6F * .91F), f.samples().getFirst().velocity().x());
        assertEquals(p.state(), f.profileState());
    }
    @Test void forecastCollectionsCannotAcquireMutableCachedPermission() {
        var f = GroundProfileForecast.groundBrake(profile(.1, false, -1), new Vec3(0, 70, 0),
                new Vec3(0, -.0784, .1), .6F, 16).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> f.samples().clear());
    }
}
