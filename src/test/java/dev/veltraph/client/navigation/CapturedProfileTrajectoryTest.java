package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
import static org.junit.jupiter.api.Assertions.*;

/** All poses/velocities here are synthetic arithmetic fixtures, never observed native evidence. */
class CapturedProfileTrajectoryTest {
    static GroundNativePhysicsProfile profile(float base, boolean sprint) {
        var effects = List.of(new GroundCapturedEffects.Effect("minecraft:haste",0,true,true,false),
                new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,true,true,false),
                new GroundCapturedEffects.Effect("minecraft:strength",7,true,true,false),
                new GroundCapturedEffects.Effect("minecraft:night_vision",0,true,true,false),
                new GroundCapturedEffects.Effect("minecraft:regeneration",8,true,true,false),
                new GroundCapturedEffects.Effect("minecraft:absorption",0,true,true,false),
                new GroundCapturedEffects.Effect("minecraft:jump_boost",5,true,true,false));
        double effective = (double)base * (sprint ? 1D + (double).3F : 1D);
        return GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(base, effective,
                (float)effective, sprint ? List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",(double).3F,2))
                    : List.of(), (double).42F,.08D,1D,1D,5,effects,0D,1F)).orElseThrow();
    }
    private static Trace trace(float base, boolean sprint, int count, Input input, Phase phase, Vec3 v) {
        return forecast(profile(base,sprint),new Vec3(0,0,0),v,.6F,0F,phase,
                Collections.nCopies(count,input),100).orElseThrow();
    }
    @Test void cardinalInputHasNativeFloatScaleWhileDiagonalReturnsNearUnitLength() {
        assertArrayEquals(new float[]{0,.98F},localInput(Input.FORWARD));
        float[] diagonal=localInput(Input.FORWARD_LEFT);
        assertEquals(1D,Math.hypot(diagonal[0],diagonal[1]),2E-7);
        assertTrue(Math.hypot(diagonal[0],diagonal[1])>.999999D);
    }
    @Test void inputHandednessMatchesNativePositiveLeftAndForwardAtZeroYaw() {
        for (Input input : Input.values()) {
            Vec3 a=inputAcceleration(input,1F,0F);
            assertEquals(Integer.signum(input.left()),(int)Math.signum(a.x()));
            assertEquals(Integer.signum(input.forward()),(int)Math.signum(a.z()));
        }
    }
    @Test void nativeYawQuarterTurnMapsForwardToNegativeXAndLeftToPositiveZ() {
        Vec3 forward=inputAcceleration(Input.FORWARD,1F,90F);
        Vec3 left=inputAcceleration(Input.LEFT,1F,90F);
        assertEquals(-(double).98F,forward.x(),1E-7);
        assertEquals(0,forward.z(),1E-7);
        assertEquals((double).98F,left.z(),1E-7);
    }
    @Test void walkingAndSprintUseCapturedFloatSpeedAndExactModifierOperation() {
        assertEquals(.3528000208854678,trace(.36F,false,1,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0)).samples().getFirst().intendedMove().z(),1E-15);
        assertEquals(.45864003299236344,trace(.36F,true,1,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0)).samples().getFirst().intendedMove().z(),1E-15);
        assertEquals(.39200001347064983,trace(.4F,false,1,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0)).samples().getFirst().intendedMove().z(),1E-15);
        assertEquals(.5096000496387489,trace(.4F,true,1,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0)).samples().getFirst().intendedMove().z(),1E-15);
    }
    @Test void twentyNativeFramesStayInsideIndependentlyDerivedNumericalIntervals() {
        double[][] intervals={{14.60729306575,14.60729306577},{18.98948122733,18.98948122735},
                {16.23032522553,16.23032522555},{21.09942412337,21.09942412339}};
        int row=0;
        for(float base:new float[]{.36F,.4F}) for(boolean sprint:new boolean[]{false,true}) {
            double distance=trace(base,sprint,20,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0)).samples().getLast().position().z();
            assertTrue(distance>=intervals[row][0]&&distance<=intervals[row++][1]);
        }
    }
    @Test void groundAndAirDoNotShareAccelerationOrDragBranches() {
        var p=profile(.4F,false);var zero=new Vec3(0,0,0);
        var ground=frame(p,zero,zero,.6F,0,Input.FORWARD,Phase.SUPPORTED_GROUND).orElseThrow();
        var air=frame(p,zero,zero,.6F,0,Input.FORWARD,Phase.OBSERVED_AIRBORNE).orElseThrow();
        assertEquals((double).98F*(double).02F,air.acceleration().z(),1E-15);
        assertTrue(ground.acceleration().z()>air.acceleration().z()*19);
        assertEquals(air.intendedMove().z()*(double).91F,air.velocity().z());
        assertEquals(ground.intendedMove().z()*(double)(.6F*.91F),ground.velocity().z());
    }
    @Test void sprintAirAccelerationUsesNativeFixedPlayerValueAcrossBothCapturedSpeeds() {
        for(float base:new float[]{.36F,.4F}) {
            var a=trace(base,true,1,Input.FORWARD,Phase.OBSERVED_AIRBORNE,new Vec3(0,0,0)).samples().getFirst();
            assertEquals((double).98F*(double).025999999F,a.acceleration().z());
        }
    }
    @Test void neutralTakeoffUsesGroundDragOnceAndNeverAddsAnotherJumpImpulse() {
        var t=trace(.4F,false,2,Input.NEUTRAL,Phase.GROUNDED_TAKEOFF,new Vec3(0,0,.1));
        var first=t.samples().getFirst();var second=t.samples().getLast();
        assertEquals((double)(.42F+.1F*6F),first.position().y());
        assertEquals(.1D*(double)(.6F*.91F),first.velocity().z());
        assertEquals(first.velocity().z()*(double).91F,second.velocity().z());
        assertTrue(second.intendedMove().y()<first.intendedMove().y());
        assertEquals(JumpIntent.PRESS,first.controls().jump());
        assertEquals(JumpIntent.RELEASE,second.controls().jump());
    }
    @Test void observedAirborneFrameNeverSynthesizesTakeoff() {
        var t=trace(.4F,false,1,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE,new Vec3(0,-.2,0));
        assertEquals(-.2D,t.samples().getFirst().position().y());
    }
    @Test void supportedGroundProjectionIsExplicitAndRetainsActualDownwardAttempt() {
        var sample=trace(.4F,false,1,Input.NEUTRAL,Phase.SUPPORTED_GROUND,new Vec3(0,-.0784,.3)).samples().getFirst();
        assertEquals(0,sample.position().y());assertEquals(-.0784,sample.intendedMove().y());
        assertEquals(Condition.FRESH_FLAT_SUPPORT_CLAMP,sample.condition());
        assertEquals(-.08D*(double).98F,sample.velocity().y());
    }
    @Test void combinedHorizontalPlayerDeadZoneDoesNotZeroEachAxisIndependently() {
        var retained=playerMomentum(new Vec3(.0025,.002,.0025));
        assertEquals(.0025,retained.x());assertEquals(.0025,retained.z());assertEquals(0,retained.y());
        assertEquals(new Vec3(0,0,0),playerMomentum(new Vec3(.002,.002,.002)));
        assertEquals(.003,playerMomentum(new Vec3(.003,.003,0)).x());
    }
    @Test void neutralGroundStopIsFiniteAndWithinCoastBoundForAllFourProfiles() {
        for(float base:new float[]{.36F,.4F}) for(boolean sprint:new boolean[]{false,true}) {
            var walk=trace(base,sprint,20,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0));
            var end=walk.samples().getLast();
            var brake=forecast(profile(base,sprint),end.position(),end.velocity(),.6F,0F,Phase.SUPPORTED_GROUND,
                    Collections.nCopies(32,Input.NEUTRAL),100).orElseThrow();
            var bound=groundCoastBound(end.position(),end.velocity(),.6F).orElseThrow();
            assertEquals(0,brake.samples().getLast().velocity().z());
            assertTrue(brake.samples().getLast().position().z()<bound.z());
            assertEquals(brake.samples().get(10).position(),brake.samples().getLast().position());
        }
    }
    @Test void snapshotAndControlsRemainImmutableAndPrivatelyConstructed() {
        var inputs=new ArrayList<>(List.of(Input.FORWARD,Input.NEUTRAL));
        var t=forecast(profile(.4F,false),new Vec3(0,0,0),new Vec3(0,0,0),.6F,0F,Phase.SUPPORTED_GROUND,inputs,100).orElseThrow();
        inputs.clear();assertEquals(2,t.samples().size());
        assertThrows(UnsupportedOperationException.class,()->t.samples().clear());
        assertEquals(SprintIntent.RELEASE,t.samples().getFirst().controls().sprint());
        for(Class<?> type:List.of(Sample.class,Trace.class,Controls.class))
            for(var constructor:type.getDeclaredConstructors()) assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void boundedPreviewRequiresEffectsToOutlastAllFrames() {
        for(int duration:new int[]{0,1,2}) assertTrue(forecast(profile(.4F,false),new Vec3(0,0,0),new Vec3(0,0,0),.6F,0F,
                Phase.SUPPORTED_GROUND,List.of(Input.NEUTRAL,Input.NEUTRAL),duration).isEmpty());
        assertTrue(forecast(profile(.4F,false),new Vec3(0,0,0),new Vec3(0,0,0),.6F,0F,
                Phase.SUPPORTED_GROUND,Collections.nCopies(65,Input.NEUTRAL),-1).isEmpty());
    }
    @Test void invalidFrictionNonFiniteObservationOrSprintTakeoffProducesNoAdvice() {
        var zero=new Vec3(0,0,0);var p=profile(.4F,false);
        assertTrue(frame(p,zero,zero,Float.NaN,0,Input.FORWARD,Phase.SUPPORTED_GROUND).isEmpty());
        assertTrue(frame(p,zero,zero,.98F,0,Input.FORWARD,Phase.SUPPORTED_GROUND).isEmpty());
        assertTrue(frame(p,zero,new Vec3(Double.NaN,0,0),.6F,0,Input.FORWARD,Phase.OBSERVED_AIRBORNE).isEmpty());
        assertTrue(frame(profile(.4F,true),zero,zero,.6F,0,Input.NEUTRAL,Phase.GROUNDED_TAKEOFF).isEmpty());
    }
    @Test void explicitIntentMismatchCannotSilentlyBecomeAnotherPhase() {
        var zero=new Vec3(0,0,0);
        assertTrue(frame(profile(.4F,false),zero,zero,.6F,0,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE,
                JumpIntent.PRESS,SprintIntent.RELEASE).isEmpty());
        assertTrue(frame(profile(.4F,false),zero,zero,.6F,0,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE,
                JumpIntent.RELEASE,SprintIntent.PRESS).isEmpty());
    }
    @Test void ordinaryMotionCapsRemainOrdinaryAndSevenEffectSnapshotSurvivesForecast() {
        assertFalse(GroundLocomotionMotion.speedAttribute((double).4F,false));
        assertFalse(GroundLocomotionMotion.speedAttribute((double).36F*(1+(double).3F),true));
        var t=trace(.4F,false,1,Input.FORWARD,Phase.SUPPORTED_GROUND,new Vec3(0,0,0));
        assertEquals(7,t.profileState().effects().size());
    }
    @Test void sprintEnableGroundFrameUsesBeforeWalkingTravelSpeedAndPreservesPressIntent() {
        var zero=new Vec3(0,0,0);
        var enable=frame(profile(.4F,false),zero,zero,.6F,0,Input.FORWARD,Phase.SUPPORTED_GROUND,
                JumpIntent.RELEASE,SprintIntent.PRESS).orElseThrow();
        assertEquals(.39200001347064983,enable.intendedMove().z(),1E-15);
        assertEquals(SprintIntent.PRESS,enable.controls().sprint());
        assertEquals(JumpIntent.RELEASE,enable.controls().jump());
        var normalSprint=frame(profile(.4F,true),enable.position(),enable.velocity(),.6F,0,
                Input.FORWARD,Phase.SUPPORTED_GROUND).orElseThrow();
        assertEquals(enable.velocity().z()+.5096000496387489,normalSprint.intendedMove().z(),1E-15);
        assertEquals(Condition.FRESH_FLAT_SUPPORT_CLAMP,enable.condition());
    }
    @Test void sprintReleaseGroundFrameUsesBeforeSprintTravelSpeedAndPreservesReleaseIntent() {
        var zero=new Vec3(0,0,0);
        var release=frame(profile(.4F,true),zero,zero,.6F,0,Input.FORWARD,Phase.SUPPORTED_GROUND,
                JumpIntent.RELEASE,SprintIntent.RELEASE).orElseThrow();
        assertEquals(.5096000496387489,release.intendedMove().z(),1E-15);
        assertEquals(SprintIntent.RELEASE,release.controls().sprint());
        var normalWalk=frame(profile(.4F,false),release.position(),release.velocity(),.6F,0,
                Input.FORWARD,Phase.SUPPORTED_GROUND).orElseThrow();
        assertEquals(release.velocity().z()+.39200001347064983,normalWalk.intendedMove().z(),1E-15);
    }
    @Test void sprintTransitionMismatchCannotExtendToAirborneOrTakeoffMath() {
        var zero=new Vec3(0,0,0);
        for(boolean sprint:new boolean[]{false,true}) {
            var intent=sprint ? SprintIntent.RELEASE : SprintIntent.PRESS;
            assertTrue(frame(profile(.4F,sprint),zero,zero,.6F,0,Input.FORWARD,Phase.OBSERVED_AIRBORNE,
                    JumpIntent.RELEASE,intent).isEmpty());
            assertTrue(frame(profile(.4F,sprint),zero,zero,.6F,0,Input.FORWARD,Phase.GROUNDED_TAKEOFF,
                    JumpIntent.PRESS,intent).isEmpty());
            assertTrue(frame(profile(.4F,sprint),zero,zero,.6F,0,Input.FORWARD,Phase.SUPPORTED_GROUND,
                    JumpIntent.PRESS,intent).isEmpty());
        }
    }
    @Test void neutralSprintEnableAndReleaseRetainObservedMomentumInsteadOfInjectingAcceleration() {
        var origin=new Vec3(2,3,4);var observed=new Vec3(0,-.0784,.4);
        for(boolean sprint:new boolean[]{false,true}) {
            var result=frame(profile(.36F,sprint),origin,observed,.6F,0,Input.NEUTRAL,Phase.SUPPORTED_GROUND,
                    JumpIntent.RELEASE,sprint ? SprintIntent.RELEASE : SprintIntent.PRESS).orElseThrow();
            assertEquals(new Vec3(0,0,0),result.acceleration());
            assertEquals(new Vec3(2,3,4.4),result.position());
            assertEquals(.4D*(double)(.6F*.91F),result.velocity().z());
            assertEquals(observed,result.intendedMove());
        }
    }
}
