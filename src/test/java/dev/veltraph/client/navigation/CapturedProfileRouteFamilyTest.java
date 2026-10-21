package dev.veltraph.client.navigation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapturedProfileRouteFamilyTest {
    private static GroundNativePhysicsProfile.State ordinary(double base,int jump){
        return new GroundNativePhysicsProfile.State(base,base,(float)base,List.of(),(double).42F,.08,1,1,jump);
    }
    @Test void originalUncertifiedPointThreeFlatWalkIsNotCapturedFamily(){
        assertFalse(CapturedProfileRouteFamily.supported(ordinary(.3D,-1)));
    }
    @Test void capturedBaseWithoutCapturedEffectsKeepsOriginalBranch(){
        assertTrue(GroundNativePhysicsProfile.classify(ordinary((double).4F,-1)).isPresent());
        assertFalse(CapturedProfileRouteFamily.supported(ordinary((double).4F,-1)));
        assertFalse(CapturedProfileRouteFamily.supported(ordinary((double).36F,-1)));
    }
    @Test void ordinaryPointOneJumpBoostDoesNotReceiveCapturedExemption(){
        var s=ordinary(.1D,5);assertTrue(GroundNativePhysicsProfile.classify(s).isPresent());assertFalse(CapturedProfileRouteFamily.supported(s));
    }
    @Test void realSupportedCapturedFamiliesStillSelectActiveProducer(){
        for(float base:new float[]{.36F,.4F})for(boolean sprint:new boolean[]{false,true})
            assertTrue(CapturedProfileRouteFamily.supported(CapturedProfileTrajectoryTest.profile(base,sprint).state()));
    }
    @Test void fullFamilyWithUnknownGravityOrModifierIsInitialUnsupportedPhysics(){
        var s=CapturedProfileTrajectoryTest.profile(.4F,false).state();
        var gravity=new GroundNativePhysicsProfile.State(s.baseSpeed(),s.effectiveSpeed(),s.travelSpeed(),s.movementModifiers(),s.jumpStrength(),.09,
                s.frictionModifier(),s.airDragModifier(),s.jumpAmplifier(),s.effects(),s.movementEfficiency(),s.blockSpeedFactor());
        assertFalse(CapturedProfileRouteFamily.supported(gravity));
        var modifier=new GroundNativePhysicsProfile.State(s.baseSpeed(),s.effectiveSpeed(),s.travelSpeed(),List.of(new GroundNativePhysicsProfile.Modifier("mod:unknown",0,0)),
                s.jumpStrength(),s.gravity(),s.frictionModifier(),s.airDragModifier(),s.jumpAmplifier(),s.effects(),s.movementEfficiency(),s.blockSpeedFactor());
        assertFalse(CapturedProfileRouteFamily.supported(modifier));
    }
}
