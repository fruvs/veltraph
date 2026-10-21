package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic fixtures test math/controller feasibility; they do not prove native motion or custody. */
class CapturedAdjacentJumpControllerTest {
    private static CapturedAdjacentJumpController.Plan plan(float base,double rise,double distance,double vz) {
        double z=distance==1.65D ? .1D : .5D;
        return CapturedAdjacentJumpController.plan(CapturedProfileTrajectoryTest.profile(base,false),
                new Vec3(0,0,z),new Vec3(0,0,vz),new Vec3(0,rise,z+distance),.6F,0,100).orElseThrow();
    }
    @Test void bothCapturedProfilesFindBoundedNeutralTakeoffAndAirControlAtActualAdjacentTargets() {
        for(float base:new float[]{.36F,.4F}) for(double rise:new double[]{.600001D,1D})
            for(double distance:new double[]{.6D,1D,1.65D}) for(double vz:new double[]{0D,.05D}) {
                var p=plan(base,rise,distance,vz);var controls=p.expectedControls();
                assertEquals(Input.NEUTRAL,controls.getFirst().input());
                assertEquals(JumpIntent.PRESS,controls.getFirst().jump());
                assertTrue(controls.stream().anyMatch(c->c.input()==Input.FORWARD));
                assertTrue(controls.stream().anyMatch(c->c.input()==Input.BACKWARD));
                for(var c:controls.subList(1,controls.size())) assertEquals(JumpIntent.RELEASE,c.jump());
                assertTrue(p.trace().samples().size()<=40);assertTrue(p.candidatesExpanded()<=23040);
                assertTrue(p.crossing().interpolated().horizontalDistanceTo(p.target())<=.075D);
                assertTrue(p.crossing().freeAfter().horizontalDistanceTo(p.target())<=.075D);
                var contact=p.trace().samples().getLast();
                assertTrue(Math.hypot(contact.intendedMove().x(),contact.intendedMove().z())<=.05D);
                assertEquals(7,p.trace().profileState().effects().size());
            }
    }
    @Test void descendingCrossingRetainsFreeMotionAndDoesNotInventGroundedLanding() {
        var p=plan(.4F,1,1,0);var crossing=p.crossing();
        assertEquals(24,crossing.nativeStep());assertTrue(crossing.before().y()>1D);
        assertTrue(crossing.freeAfter().y()<1D);assertEquals(1D,crossing.interpolated().y());
        var last=p.trace().samples().getLast();
        assertEquals(Condition.UNCLAMPED_FLIGHT,last.condition());
        assertTrue(last.velocity().y()<-.7D);
        assertNotEquals(-.08D*(double).98F,last.velocity().y());
    }
    @Test void lowerRisingPlaneCrossesOneNativeFrameLaterThanFullBlockPlane() {
        assertEquals(25,plan(.4F,.600001D,1,0).crossing().nativeStep());
        assertEquals(24,plan(.4F,1D,1,0).crossing().nativeStep());
    }
    @Test void lowCorridorCannotContainBoostVIStandingBodyEvenWithHorizontalControl() {
        var p=plan(.4F,1,1,0);
        assertEquals(6.134969761058981,p.apex(),1E-12);
        assertEquals(7.934969761058981,p.requiredCeilingY(1.8D),1E-12);
        assertEquals(CapturedAdjacentJumpController.Headroom.REQUIRED_CLEARANCE_EXCEEDS_CEILING,p.compareCeiling(3D,1.8D));
        assertEquals(CapturedAdjacentJumpController.Headroom.CLEARANCE_ONLY_POSSIBLE,p.compareCeiling(9D,1.8D));
        assertEquals(CapturedAdjacentJumpController.Headroom.UNKNOWN,p.compareCeiling(Double.NaN,1.8D));
    }
    @Test void nativeForwardHeldTakeoffCannotSubstituteForNeutralAndAirborneController() {
        var profile=CapturedProfileTrajectoryTest.profile(.4F,false);
        var trace=forecast(profile,new Vec3(0,0,.5),new Vec3(0,0,0),.6F,0,Phase.GROUNDED_TAKEOFF,
                Collections.nCopies(24,Input.FORWARD),100).orElseThrow();
        assertTrue(trace.samples().getLast().position().z()>2.5D);
        assertTrue(trace.samples().getLast().position().horizontalDistanceTo(new Vec3(0,1,1.5))>.075D);
    }
    @Test void actualResidualVelocityChangesControlCandidateAndStaysBoundToTrace() {
        var stationary=plan(.4F,1,1,0);var residual=plan(.4F,1,1,.05);
        assertNotEquals(stationary.expectedControls().stream().map(Controls::input).toList(),
                residual.expectedControls().stream().map(Controls::input).toList());
        assertEquals(new Vec3(0,0,.05),residual.trace().observedVelocity());
        assertEquals(new Vec3(0,1,1.5),residual.target());
    }
    @Test void misalignedYawAndLargeLateralMomentumAreUnresolvedByForwardBackOnlyController() {
        var profile=CapturedProfileTrajectoryTest.profile(.4F,false);
        assertTrue(CapturedAdjacentJumpController.plan(profile,new Vec3(0,0,.5),new Vec3(0,0,0),
                new Vec3(0,1,1.5),.6F,90,100).isEmpty());
        assertTrue(CapturedAdjacentJumpController.plan(profile,new Vec3(0,0,.5),new Vec3(.5,0,0),
                new Vec3(0,1,1.5),.6F,0,100).isEmpty());
    }
    @Test void sprintTakeoffAndNonAdjacentOrNonRisingDestinationsProduceNoAdvice() {
        var zero=new Vec3(0,0,0);var profile=CapturedProfileTrajectoryTest.profile(.4F,false);
        assertTrue(CapturedAdjacentJumpController.plan(CapturedProfileTrajectoryTest.profile(.4F,true),zero,zero,
                new Vec3(0,1,1),.6F,0,100).isEmpty());
        for(Vec3 target:List.of(new Vec3(0,.6,1),new Vec3(0,1.01,1),new Vec3(0,1,2),
                new Vec3(1,1,1),new Vec3(0,1,.6)))
            assertTrue(CapturedAdjacentJumpController.plan(profile,zero,zero,target,.6F,0,100).isEmpty());
    }
    @Test void previewRequiresCurrentEffectsToOutlastActualCandidateContactStep() {
        var zero=new Vec3(0,0,0);var profile=CapturedProfileTrajectoryTest.profile(.4F,false);
        assertTrue(CapturedAdjacentJumpController.plan(profile,zero,zero,new Vec3(0,1,1),.6F,0,24).isEmpty());
        assertTrue(CapturedAdjacentJumpController.plan(profile,zero,zero,new Vec3(0,1,1),.6F,0,25).isPresent());
        assertTrue(CapturedAdjacentJumpController.plan(profile,zero,zero,new Vec3(0,1,1),.6F,0,-1).isPresent());
    }
    @Test void plansCrossingsAndPublishedControlListCannotBeCallerForgedOrMutated() {
        var p=plan(.4F,1,1,0);
        assertThrows(UnsupportedOperationException.class,()->p.expectedControls().clear());
        for(Class<?> type:List.of(CapturedAdjacentJumpController.Plan.class,CapturedAdjacentJumpController.Crossing.class))
            for(var constructor:type.getDeclaredConstructors()) assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
}
