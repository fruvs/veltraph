package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundCapturedEffectsTest {
    private static GroundCapturedEffects.Effect effect(String id, int amplifier) {
        return new GroundCapturedEffects.Effect("minecraft:" + id, amplifier, true, true, false);
    }
    static List<GroundCapturedEffects.Effect> capturedSeven() {
        return List.of(effect("haste", 0), effect("mining_fatigue", 255), effect("strength", 7),
                effect("night_vision", 0), effect("regeneration", 8), effect("absorption", 0), effect("jump_boost", 5));
    }
    static GroundNativePhysicsProfile.State state(double base, boolean sprint, List<GroundCapturedEffects.Effect> effects) {
        var s = GroundNativePhysicsProfileTest.state(base, sprint, 5);
        return new GroundNativePhysicsProfile.State(s.baseSpeed(), s.effectiveSpeed(), s.travelSpeed(),
                s.movementModifiers(), s.jumpStrength(), s.gravity(), s.frictionModifier(), s.airDragModifier(), 5, effects);
    }
    @Test void actualCapturedSevenEffectsAreCompatibleWithBothObservedSpeedFamilies() {
        for (double base : new double[]{(double).36F, (double).4F}) for (boolean sprint : new boolean[]{false, true}) {
            var p = GroundNativePhysicsProfile.classify(state(base, sprint, capturedSeven())).orElseThrow();
            assertEquals(7, p.state().effects().size());
            assertEquals(base * (sprint ? 1 + (double).3F : 1), p.state().effectiveSpeed());
            assertEquals(Float.floatToIntBits(.42F + .1F * 6), Float.floatToIntBits(p.jumpImpulse(1)));
        }
    }
    @Test void effectIterationOrderCannotLookLikeForeignMutation() {
        var reversed = new ArrayList<>(capturedSeven()); java.util.Collections.reverse(reversed);
        var p = GroundNativePhysicsProfile.classify(state((double).4F, false, capturedSeven())).orElseThrow();
        assertTrue(p.matches(state((double).4F, false, reversed)));
    }
    @Test void flagsAreCapturedExactlyAndCannotChangeMidJourney() {
        var p = GroundNativePhysicsProfile.classify(state((double).4F, false, capturedSeven())).orElseThrow();
        var changed = new ArrayList<>(capturedSeven());
        changed.set(0, new GroundCapturedEffects.Effect("minecraft:haste", 0, false, true, false));
        assertFalse(p.matches(state((double).4F, false, changed)));
    }
    @Test void capturedHasteStrengthDoNotBlanketExemptUnknownMovementEffects() {
        for (String id : List.of("speed", "slowness", "levitation", "slow_falling", "dolphins_grace", "mod_unknown")) {
            var e = new ArrayList<>(capturedSeven()); e.removeLast(); e.add(effect(id, 5));
            assertFalse(GroundCapturedEffects.supported(e, 5));
        }
    }
    @Test void duplicateKnownEffectDoesNotMaskMissingOrForeignEffect() {
        var effects = new ArrayList<>(capturedSeven()); effects.set(1, effects.getFirst());
        assertFalse(GroundCapturedEffects.supported(effects, 5));
    }
    @Test void differentKnownAmplifierStillDeniesFamilyAdmission() {
        var effects = new ArrayList<>(capturedSeven()); effects.set(1, effect("mining_fatigue", 254));
        assertFalse(GroundCapturedEffects.supported(effects, 5));
    }
    @Test void signedNbtByteIsNotNativeAmplifierProof() {
        var effects = new ArrayList<>(capturedSeven()); effects.set(1, effect("mining_fatigue", -1));
        assertFalse(GroundCapturedEffects.supported(effects, 5));
    }
    @Test void nativeEffectRemovalEvenNonMovementIsForeignToCapturedJourney() {
        var p = GroundNativePhysicsProfile.classify(state((double).4F, false, capturedSeven())).orElseThrow();
        var fewer = new ArrayList<>(capturedSeven()); fewer.remove(0);
        assertFalse(p.matches(state((double).4F, false, fewer)));
    }
    @Test void finiteCapturedDurationMayTickWithoutRefreshOrReplacement() {
        assertTrue(GroundCapturedEffects.durationCurrent(100, 100, 101, 666900, 666899));
        assertTrue(GroundCapturedEffects.durationCurrent(100, 200, 200, 666900, 666800));
        assertFalse(GroundCapturedEffects.durationCurrent(100, 100, 101, 666900, 666901));
        assertFalse(GroundCapturedEffects.durationCurrent(100, 100, 101, 666900, 666000));
    }
    @Test void infiniteCannotBecomeFiniteAndExpiryCannotLookCurrent() {
        assertTrue(GroundCapturedEffects.durationCurrent(100, 200, 300, -1, -1));
        assertFalse(GroundCapturedEffects.durationCurrent(100, 200, 300, -1, 666900));
        assertFalse(GroundCapturedEffects.durationCurrent(100, 100, 200, 100, 0));
    }
    @Test void clockRollbackAndInvalidOriginalDurationsDeny() {
        assertFalse(GroundCapturedEffects.durationCurrent(100, 150, 149, 666900, 666851));
        assertFalse(GroundCapturedEffects.durationCurrent(100, 99, 101, 666900, 666899));
        assertFalse(GroundCapturedEffects.durationCurrent(100, 100, 100, 0, 0));
    }
    @Test void sameValueEffectRefreshCannotReviveOriginalMutationRevision() {
        var instance = new Object(); var original = GroundPhysicsNativeRevisions.watch(instance);
        GroundPhysicsNativeRevisions.mutation(instance);
        assertTrue(GroundCapturedEffects.durationCurrent(100, 100, 101, 666900, 666900));
        assertFalse(original.current());
    }
}
