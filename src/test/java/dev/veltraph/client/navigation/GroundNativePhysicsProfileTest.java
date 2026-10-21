package dev.veltraph.client.navigation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundNativePhysicsProfileTest {
    static GroundNativePhysicsProfile.State state(double base, boolean sprint, int jump) {
        double amount = GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT;
        double effective = sprint ? base * (1 + amount) : base;
        return new GroundNativePhysicsProfile.State(base, effective, (float)effective,
                sprint ? List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting", amount, 2)) : List.of(),
                (double).42F, .08, 1, 1, jump);
    }
    @Test void actualCaptureBaseAndTotalMultiplicationRemainDistinct() {
        for (double base : new double[]{(double).36F, (double).4F}) {
            var walking = GroundNativePhysicsProfile.classify(state(base, false, -1)).orElseThrow();
            var running = GroundNativePhysicsProfile.classify(state(base, true, -1)).orElseThrow();
            assertEquals(base, walking.state().effectiveSpeed());
            assertEquals(base * (1 + (double).3F), running.state().effectiveSpeed());
            assertNotEquals(walking.state(), running.state());
        }
    }
    @Test void noGuiPercentOrRoundedDecimalSubstitution() {
        assertTrue(GroundNativePhysicsProfile.classify(state(360, false, -1)).isEmpty());
        assertTrue(GroundNativePhysicsProfile.classify(state(.36D, false, -1)).isEmpty());
        assertTrue(GroundNativePhysicsProfile.classify(state(.4D, false, -1)).isEmpty());
    }
    @Test void existingOrdinaryAttributeGateIsNotWidened() {
        assertTrue(GroundLocomotionMotion.speedAttribute(.1, false));
        assertFalse(GroundLocomotionMotion.speedAttribute((double).36F, false));
        assertFalse(GroundLocomotionMotion.speedAttribute((double).4F * (1 + (double).3F), true));
    }
    @Test void currentBaseReplacementEvenKnownCaptureFamilyIsForeign() {
        var profile = GroundNativePhysicsProfile.classify(state((double).36F, false, 5)).orElseThrow();
        assertFalse(profile.matches(state((double).4F, false, 5)));
    }
    @Test void currentEffectReplacementIsForeign() {
        var profile = GroundNativePhysicsProfile.classify(state((double).4F, false, 5)).orElseThrow();
        assertFalse(profile.matches(state((double).4F, false, -1)));
    }
    @Test void unknownModifierDoesNotMasqueradeAsNativeSprint() {
        var s = state((double).4F, true, -1);
        for (var m : List.of(new GroundNativePhysicsProfile.Modifier("mod:boost", (double).3F, 2),
                new GroundNativePhysicsProfile.Modifier("minecraft:sprinting", (double).3F, 1),
                new GroundNativePhysicsProfile.Modifier("minecraft:sprinting", .31, 2))) {
            assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(),
                    s.travelSpeed(), List.of(m), s.jumpStrength(), s.gravity(), 1, 1, -1)).isEmpty());
        }
    }
    @Test void sameValueWithoutMatchingModifierOriginFails() {
        var s = state((double).4F, true, -1);
        assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(),
                s.travelSpeed(), List.of(), s.jumpStrength(), s.gravity(), 1, 1, -1)).isEmpty());
    }
    @Test void unknownDragGravityAndAmplifierDenied() {
        var s = state((double).4F, false, 5);
        assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(),
                s.travelSpeed(), List.of(), s.jumpStrength(), .07, 1, 1, 5)).isEmpty());
        assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(),
                s.travelSpeed(), List.of(), s.jumpStrength(), .08, 1, .5, 5)).isEmpty());
        assertTrue(GroundNativePhysicsProfile.classify(state((double).4F, false, 4)).isEmpty());
    }
    @Test void actualTravelFloatMustAgreeWithAttribute() {
        var s = state((double).4F, false, -1);
        assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(),
                .1F, List.of(), s.jumpStrength(), .08, 1, 1, -1)).isEmpty());
    }
    @Test void nativeFloatJumpBoostVIFormula() {
        var p = GroundNativePhysicsProfile.classify(state((double).36F, false, 5)).orElseThrow();
        assertEquals(Float.floatToIntBits(.42F + .1F * 6F), Float.floatToIntBits(p.jumpImpulse(1)));
        assertTrue(Float.isNaN(p.jumpImpulse(.5F)));
    }
    @Test void soulSandSlowdownIsNotMistakenForOrdinaryFrictionAndJumpFactor() {
        var s = state((double).4F, false, 5);
        var slow = new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(), s.travelSpeed(),
                s.movementModifiers(), s.jumpStrength(), s.gravity(), s.frictionModifier(), s.airDragModifier(),
                s.jumpAmplifier(), s.effects(), 0, .4F);
        assertTrue(GroundNativePhysicsProfile.classify(slow).isEmpty());
    }
    @Test void stableNativeEfficiencyIsTrackedOnDeclaredUnitSpeedFactorSurfaces() {
        var s = state((double).4F, false, 5);
        var original = new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(), s.travelSpeed(),
                s.movementModifiers(), s.jumpStrength(), s.gravity(), s.frictionModifier(), s.airDragModifier(),
                s.jumpAmplifier(), s.effects(), .5, 1F);
        var p = GroundNativePhysicsProfile.classify(original).orElseThrow();
        var changed = new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(), s.travelSpeed(),
                s.movementModifiers(), s.jumpStrength(), s.gravity(), s.frictionModifier(), s.airDragModifier(),
                s.jumpAmplifier(), s.effects(), .6, 1F);
        assertFalse(p.matches(changed));
    }
    @Test void unknownOrNonFiniteEfficiencyAndSpeedFactorDeny() {
        var s = state((double).4F, false, 5);
        for (double efficiency : new double[]{Double.NaN, -.1, 1.1}) {
            assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(),
                    s.effectiveSpeed(), s.travelSpeed(), s.movementModifiers(), s.jumpStrength(), s.gravity(),
                    s.frictionModifier(), s.airDragModifier(), s.jumpAmplifier(), s.effects(), efficiency, 1F)).isEmpty());
        }
        assertTrue(GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(),
                s.effectiveSpeed(), s.travelSpeed(), s.movementModifiers(), s.jumpStrength(), s.gravity(),
                s.frictionModifier(), s.airDragModifier(), s.jumpAmplifier(), s.effects(), 0, Float.NaN)).isEmpty());
    }
    @Test void capturedSevenEffectsStillAdmitAfterSpeedFactorAndEfficiencyChecks() {
        var s = GroundCapturedEffectsTest.state((double).4F, false, GroundCapturedEffectsTest.capturedSeven());
        assertTrue(GroundNativePhysicsProfile.classify(s).isPresent());
    }
    @Test void inputSnapshotCannotBeMutatedAfterClassification() {
        var list = new java.util.ArrayList<GroundNativePhysicsProfile.Modifier>();
        var s = new GroundNativePhysicsProfile.State((double).36F, (double).36F, .36F, list, (double).42F, .08, 1, 1, -1);
        list.add(new GroundNativePhysicsProfile.Modifier("foreign", 100, 0));
        assertTrue(GroundNativePhysicsProfile.classify(s).isPresent());
        assertThrows(UnsupportedOperationException.class, () -> s.movementModifiers().clear());
    }
}
