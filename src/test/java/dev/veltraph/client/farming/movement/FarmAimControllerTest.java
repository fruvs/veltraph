package dev.veltraph.client.farming.movement;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Smooth bounded aim translated from RotationController — never snaps; deterministic when seeded. */
class FarmAimControllerTest {

    /** Deterministic settings (no jitter, no GCD): pure bounded LERP at the given max step. */
    private static FarmAimController.Settings lerp(float maxStep) {
        return new FarmAimController.Settings(maxStep, maxStep, 1.0, 3f, 25f,
                false, 0.5, false, 1.5f, 40, 140, 5);
    }

    @Test void preciseOptInRefinesActualHalfDegreeLadderPhaseAndLegacyKeepsItsDeadband() {
        var settings=new FarmAimController.Settings(6f,6f,.2,3f,25f,true,.5,false,1f,3600,12000,4);
        var legacy=new FarmAimController(settings,new Random(0));legacy.setTarget(0,-58.95f);
        assertEquals(-58.5f,legacy.step(0,-58.5f)[1],0,"ordinary constructor retains .75-degree behavior");
        for(float initial:new float[]{-58.5f,-10f}) {
            var reachable=dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.target(initial,true,.5);
            assertTrue(reachable.isPresent());float target=(float)reachable.getAsDouble();
            var precise=new FarmAimController(settings,new Random(0),.05f);precise.setTarget(0,target);
            float pitch=initial;int moves=0;
            for(int tick=0;tick<120;tick++) {
                float next=precise.step(0,pitch)[1];
                assertTrue(Math.abs(next-pitch)<=6.0001f,"precision retains bounded correction");
                if(next!=pitch)moves++;pitch=next;
            }
            assertTrue(moves>0,"actual phase must correct rather than freeze");
            if(initial==-10f)assertTrue(moves>3,"large return phase remains a multi-tick rotation");
            assertTrue(dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.ready(pitch),"actual pitch enters measured ladder/fruit window: "+pitch);
            assertEquals(target,pitch,.0001f,"reachable target is preserved");
            for(int tick=0;tick<120;tick++)assertEquals(pitch,precise.step(0,pitch)[1],0,"settled precision does not jitter");
        }
    }

    @Test void preciseDeadbandHasHalfConfiguredMouseStepFloorWithoutAlternating() {
        for(double sensitivity:new double[]{0,.5,1}) {
            var settings=new FarmAimController.Settings(6f,6f,.2,3f,25f,true,sensitivity,false,1f,3600,12000,4);
            float step=(float)(Math.pow(sensitivity*.6+.2,3)*1.2);
            var precise=new FarmAimController(settings,new Random(0),.0001f);precise.setTarget(0,-59f-step*.5f);
            float pitch=-59f;
            for(int tick=0;tick<240;tick++)assertEquals(pitch,precise.step(0,pitch)[1],0,"unreachable half-step gap must remain stable");
        }
    }

    @Test void precisePitchStaysAtNearestMouseAngleWhileOtherAxisStillTurns() {
        var settings=new FarmAimController.Settings(6f,6f,.2,3f,25f,true,.5,false,1f,3600,12000,4);
        var precise=new FarmAimController(settings,new Random(0),.05f);precise.setTarget(90,-59.075f);
        float yaw=0,pitch=-59;
        for(int tick=0;tick<80;tick++) {
            var out=precise.step(yaw,pitch);assertEquals(pitch,out[1],0,"settled pitch must not alternate by .15 while yaw corrects");yaw=out[0];
        }
        assertEquals(90,yaw,.0751f,"other axis still converges normally");
    }

    @Test void coarseUnreachablePhaseCannotAuthorizeLadderWorkAndPrecisionRejectsInvalidRequests() {
        assertTrue(dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.target(-58.75,true,1).isEmpty());
        assertFalse(dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.ready(-58.75f),"caller must hold controls when no safe mouse-reachable target exists");
        for(float requested:new float[]{0,-.05f,Float.NaN,Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new FarmAimController(lerp(6),new Random(0),requested));
    }

    @Test
    void neverSnaps_correctionTakesMultipleTicks() {
        FarmAimController.Settings ramp = new FarmAimController.Settings(18f, 12f, 0.35, 3f, 25f,
                false, 0.5, false, 1.5f, 40, 140, 5);
        FarmAimController aim = new FarmAimController(ramp, new Random(1));
        aim.setTarget(90f, 0f);
        float yaw = 0f;
        float first = aim.step(yaw, 0f)[0];
        assertTrue(first > 0f && first < 18f, "ease-in: first step below the per-tick cap (" + first + ")");
        // It converges over several ticks but never in one.
        int ticks = 0;
        yaw = first;
        while (Math.abs(Angles.signedDelta(yaw, 90f)) > 3f && ticks < 100) {
            yaw = aim.step(yaw, 0f)[0];
            ticks++;
        }
        assertTrue(ticks >= 3, "a 90° turn takes several ticks, not a snap (took " + (ticks + 1) + ")");
        assertEquals(90f, yaw, 3f);
    }

    @Test
    void boundedStepIsCappedPerTick() {
        FarmAimController aim = new FarmAimController(lerp(10f), new Random(0));
        aim.setTarget(100f, 0f);
        assertEquals(10f, aim.step(0f, 0f)[0], 0.001f, "capped to maxYaw per tick");
    }

    @Test
    void takesShortestPathAcrossTheSeam() {
        FarmAimController aim = new FarmAimController(lerp(10f), new Random(0));
        aim.setTarget(10f, 0f);
        // 350 -> 10 is +20 (short way); a +10 step lands on 0 (wrapping), not 340.
        assertEquals(0f, aim.step(350f, 0f)[0], 0.001f);
    }

    @Test
    void toleranceAndLargeErrorThresholds() {
        FarmAimController aim = new FarmAimController(lerp(10f), new Random(0));
        aim.setTarget(0f, 0f);
        assertTrue(aim.withinTolerance(2f, 0f));
        assertFalse(aim.withinTolerance(10f, 0f));
        assertFalse(aim.isLargeError(10f, 0f), "10° is drift, not a large error");
        assertTrue(aim.isLargeError(40f, 0f), "40° exceeds the pause threshold");
    }

    @Test
    void mouseGcdDoesNotChaseSubDegreeNoiseWhenSettled() {
        // GCD on, sensitivity 0.5. A sub-degree target while already settled should not oscillate.
        FarmAimController.Settings gcd = new FarmAimController.Settings(10f, 10f, 1.0, 0.05f, 25f,
                true, 0.5, false, 1.5f, 40, 140, 5);
        FarmAimController aim = new FarmAimController(gcd, new Random(0));
        aim.setTarget(0.05f, 0f);
        float yaw = 0f;
        for (int i = 0; i < 5; i++) {
            yaw = aim.step(yaw, 0f)[0];
        }
        assertEquals(0f, yaw, 0.0001f, "settled aim holds still instead of sub-GCD jittering");
    }

    @Test
    void mouseGcdStillMovesForRealCorrections() {
        FarmAimController.Settings gcd = new FarmAimController.Settings(10f, 10f, 1.0, 0.05f, 25f,
                true, 0.5, false, 1.5f, 40, 140, 5);
        FarmAimController aim = new FarmAimController(gcd, new Random(0));
        aim.setTarget(2.0f, 0f);
        float yaw = aim.step(0f, 0f)[0];
        assertTrue(yaw > 0f, "larger correction still moves");
        float gcdStep = (float) (Math.pow(0.5 * 0.6 + 0.2, 3.0) * 1.2);
        assertEquals(Math.round(yaw / gcdStep), yaw / gcdStep, 0.01f, "applied yaw is a multiple of the GCD step");
    }

    @Test
    void jitterIsDeterministicForAFixedSeedAndReturnsToTarget() {
        FarmAimController.Settings j = new FarmAimController.Settings(10f, 10f, 1.0, 3f, 25f,
                false, 0.5, true, 1.5f, 2, 4, 3);
        FarmAimController a = new FarmAimController(j, new Random(42));
        FarmAimController b = new FarmAimController(j, new Random(42));
        a.setTarget(0f, 0f);
        b.setTarget(0f, 0f);
        float ya = 0f;
        float yb = 0f;
        float maxDeviation = 0f;
        for (int i = 0; i < 40; i++) {
            ya = a.step(ya, 0f)[0];
            yb = b.step(yb, 0f)[0];
            assertEquals(ya, yb, 0.0001f, "same seed -> identical jitter sequence (tick " + i + ")");
            maxDeviation = Math.max(maxDeviation, Math.abs(ya));
        }
        assertTrue(maxDeviation > 0f, "jitter actually perturbs the view");
        assertTrue(maxDeviation <= 1.5f + 0.001f, "jitter stays within its bound");
    }

    @Test
    void jitterIsRareAndNeverDuringSetupOrCorrection() {
        // Minutes-scale interval (3600–12000 ticks) per live feedback.
        FarmAimController.Settings rare = new FarmAimController.Settings(10f, 10f, 1.0, 3f, 25f,
                false, 0.5, true, 1.0f, 3600, 12000, 4);
        FarmAimController aim = new FarmAimController(rare, new Random(5));
        aim.setTarget(0f, 0f);
        // Start far from target (a setup turn). Jitter must not fire while correcting.
        float yaw = 90f;
        for (int i = 0; i < 50; i++) {
            yaw = aim.step(yaw, 0f)[0];
            assertFalse(aim.jittering(), "no jitter during the setup/correction turn (tick " + i + ")");
        }
        assertEquals(0f, yaw, 0.001f, "converged onto the target");
        // Steady on target: still no jitter for hundreds of ticks (interval is minutes-scale).
        for (int i = 0; i < 300; i++) {
            yaw = aim.step(yaw, 0f)[0];
            assertEquals(0f, yaw, 0.001f, "no jitter within the minutes-scale interval (tick " + i + ")");
        }
    }

    @Test
    void jitterDisabledHoldsTheTargetExactly() {
        FarmAimController aim = new FarmAimController(lerp(10f), new Random(7));
        aim.setTarget(0f, 0f);
        float yaw = 0f;
        for (int i = 0; i < 20; i++) {
            yaw = aim.step(yaw, 0f)[0];
        }
        assertEquals(0f, yaw, 0.0001f, "no jitter -> the view stays exactly on target");
    }

    @Test
    void jitterEnabledStillHoldsSubDegreeTargetNoiseUntilRarePulse() {
        FarmAimController.Settings rare = new FarmAimController.Settings(10f, 10f, 1.0, 3f, 25f,
                true, 0.5, true, 1.0f, 3600, 12000, 4);
        FarmAimController aim = new FarmAimController(rare, new Random(11));
        aim.setTarget(0f, 0f);
        float yaw = 0f;
        float pitch = 4.6f;
        for (int i = 0; i < 120; i++) {
            aim.retarget(0f, (i % 2 == 0) ? 4.3f : 4.9f);
            float[] out = aim.step(yaw, pitch);
            yaw = out[0];
            pitch = out[1];
            assertFalse(aim.jittering(), "minutes-scale jitter must not pulse in the first few seconds");
            assertEquals(0f, yaw, 0.0001f, "yaw stays still while settled");
            assertEquals(4.6f, pitch, 0.0001f, "pitch does not chase alternating sub-degree target noise");
        }
    }
}
