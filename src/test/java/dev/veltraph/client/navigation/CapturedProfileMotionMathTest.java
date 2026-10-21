package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.navigation.mc.GroundProfileMotionCarrier.NativePose;
import dev.veltraph.client.navigation.mc.McCapturedProfileLocomotion;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
/** Synthetic arithmetic fixtures do not confer carrier or input authority. */
class CapturedProfileMotionMathTest {
    @Test void exactNativeLocalInputMustMatchIncludingDiagonalSquareTransform(){
        assertEquals(new Vec3(0,0,(double).98F),CapturedProfileMotionMath.nativeTravelInput(Input.FORWARD));
        var diagonal=CapturedProfileMotionMath.nativeTravelInput(Input.FORWARD_LEFT);
        assertEquals(1D,Math.hypot(diagonal.x(),diagonal.z()),2E-7D);
        assertEquals(CapturedProfileMotionMath.Match.INPUT_MISMATCH,CapturedProfileMotionMath.inputAndJump(Input.FORWARD_LEFT,
                Phase.OBSERVED_AIRBORNE,new Vec3(.98D/Math.sqrt(2),0,.98D/Math.sqrt(2)),0));
    }
    @Test void jumpCountsAreOneOnlyForActualTakeoffAndZeroForAllContinuationFrames(){
        var neutral=new Vec3(0,0,0);
        assertEquals(CapturedProfileMotionMath.Match.MATCHED,CapturedProfileMotionMath.inputAndJump(Input.NEUTRAL,Phase.GROUNDED_TAKEOFF,neutral,1));
        for(var phase:List.of(Phase.SUPPORTED_GROUND,Phase.GROUNDED_EXIT,Phase.OBSERVED_AIRBORNE))
            assertEquals(CapturedProfileMotionMath.Match.JUMP_COUNT_MISMATCH,CapturedProfileMotionMath.inputAndJump(Input.NEUTRAL,phase,neutral,1));
        assertEquals(CapturedProfileMotionMath.Match.JUMP_COUNT_MISMATCH,CapturedProfileMotionMath.inputAndJump(Input.NEUTRAL,Phase.GROUNDED_TAKEOFF,neutral,0));
    }
    @Test void sourceSupportedAndGroundedExitHaveDifferentRealFirstVerticalMoves(){
        var p=CapturedProfileTrajectoryTest.profile(.4F,false);var position=new Vec3(0,1,.5);var velocity=new Vec3(0,-.0784,.04);
        var support=frame(p,position,velocity,.6F,0,Input.NEUTRAL,Phase.SUPPORTED_GROUND).orElseThrow();
        var exit=frame(p,position,velocity,.6F,0,Input.NEUTRAL,Phase.GROUNDED_EXIT).orElseThrow();
        assertEquals(1D,support.position().y());assertEquals(.9216D,exit.position().y(),1E-15);
        assertEquals(.04D*(double)(.6F*.91F),exit.velocity().z());
        assertEquals((-.0784D-.08D)*(double).98F,exit.velocity().y());
        assertEquals(JumpIntent.RELEASE,exit.controls().jump());
        assertEquals(Condition.UNCLAMPED_FLIGHT,exit.condition());
    }
    @Test void groundExitDoesNotExtendGroundSprintTransitionException(){
        var zero=new Vec3(0,0,0);
        assertTrue(frame(CapturedProfileTrajectoryTest.profile(.4F,false),zero,zero,.6F,0,Input.NEUTRAL,
                Phase.GROUNDED_EXIT,JumpIntent.RELEASE,SprintIntent.PRESS).isEmpty());
    }
    @Test void nativeLandingKeepsFullXZAndClampsVerticalVelocityOnly(){
        var p=CapturedProfileTrajectoryTest.profile(.4F,false);var before=new Vec3(0,1.1,1.49);var target=new Vec3(0,1,1.5);
        var free=frame(p,before,new Vec3(0,-.2,.01),.6F,0,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE).orElseThrow();
        var actual=new NativePose(new Vec3(0,1,1.5),new Vec3(0,-.08D*(double).98F,.01D*(double).91F),0,true,false,7,.4F);
        assertEquals(CapturedProfileMotionMath.Match.MATCHED,CapturedProfileMotionMath.landingContact(before,free,actual,target,.075D));
        assertEquals(CapturedProfileMotionMath.Match.FREE_MOTION_MISMATCH,CapturedProfileMotionMath.freeMotion(free,actual));
    }
    @Test void landingRequiresDescendingSegmentAndRealGroundFlag(){
        var p=CapturedProfileTrajectoryTest.profile(.4F,false);var before=new Vec3(0,1.1,1.49);var target=new Vec3(0,1,1.5);
        var free=frame(p,before,new Vec3(0,-.2,.01),.6F,0,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE).orElseThrow();
        var falseGround=new NativePose(new Vec3(0,1,1.5),new Vec3(0,-.0784,0),0,false,false,7,.4F);
        assertEquals(CapturedProfileMotionMath.Match.CONTACT_MISMATCH,CapturedProfileMotionMath.landingContact(before,free,falseGround,target,.075D));
        var ascent=frame(p,before,new Vec3(0,.2,.01),.6F,0,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE).orElseThrow();
        assertEquals(CapturedProfileMotionMath.Match.CONTACT_MISMATCH,CapturedProfileMotionMath.landingContact(before,ascent,falseGround,target,.075D));
    }
    @Test void horizontalCollisionOrForeignVelocityCannotMasqueradeAsNativeLanding(){
        var p=CapturedProfileTrajectoryTest.profile(.4F,false);var before=new Vec3(0,1.1,1.49);var target=new Vec3(0,1,1.5);
        var free=frame(p,before,new Vec3(0,-.2,.01),.6F,0,Input.NEUTRAL,Phase.OBSERVED_AIRBORNE).orElseThrow();
        var changed=new NativePose(new Vec3(0,1,1.49),new Vec3(0,-.08D*(double).98F,.0091D),0,true,false,7,.4F);
        assertEquals(CapturedProfileMotionMath.Match.CONTACT_MISMATCH,CapturedProfileMotionMath.landingContact(before,free,changed,target,.075D));
    }
    @Test void originalDropBoundRemainsCardinalAdjacentAndAtMostOneBlock(){
        assertTrue(CapturedProfileMotionMath.originalDrop(new Vec3(.5,1,.5),new Vec3(.5,0,1.5)));
        for(Vec3 target:List.of(new Vec3(.5,-.01,1.5),new Vec3(.5,.4,1.5),new Vec3(1.5,0,1.5),new Vec3(.5,0,2.5)))
            assertFalse(CapturedProfileMotionMath.originalDrop(new Vec3(.5,1,.5),target));
    }
    @Test void yawToleranceHandlesWrapWithoutOpeningNonFiniteValues(){
        assertEquals(.2D,CapturedProfileMotionMath.yawDifference(179.9F,-179.9F),.00002D);
        assertEquals(Double.POSITIVE_INFINITY,CapturedProfileMotionMath.yawDifference(Float.NaN,0));
    }
    @Test void privateDecisionsCannotBeConstructedFromCallerBooleanPermission(){
        for(var constructor:McCapturedProfileLocomotion.Decision.class.getDeclaredConstructors())
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void ownedNeutralTurnAndNonSprintNeutralTakeoffDoNotChangePhysicalTrajectory(){
        var zero=new Vec3(0,0,0);var p=CapturedProfileTrajectoryTest.profile(.4F,false);var origin=new Vec3(0,0,0);var v=new Vec3(.04,0,.02);
        for(var phase:List.of(Phase.SUPPORTED_GROUND,Phase.GROUNDED_TAKEOFF,Phase.OBSERVED_AIRBORNE)) {
            assertTrue(CapturedProfileMotionMath.yawIndependent(Input.NEUTRAL,zero,phase,false));
            var a=frame(p,origin,v,.6F,0,Input.NEUTRAL,phase).orElseThrow();
            var b=frame(p,origin,v,.6F,90,Input.NEUTRAL,phase).orElseThrow();
            assertEquals(a.position(),b.position());assertEquals(a.velocity(),b.velocity());
        }
    }
    @Test void nonneutralOrSprintJumpCannotUseNeutralYawException(){
        var zero=new Vec3(0,0,0);
        assertFalse(CapturedProfileMotionMath.yawIndependent(Input.FORWARD,zero,Phase.OBSERVED_AIRBORNE,false));
        assertFalse(CapturedProfileMotionMath.yawIndependent(Input.NEUTRAL,new Vec3(0,0,.001),Phase.OBSERVED_AIRBORNE,false));
        assertFalse(CapturedProfileMotionMath.yawIndependent(Input.NEUTRAL,zero,Phase.GROUNDED_TAKEOFF,true));
    }
}
