package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.Input;
class CapturedProfileSprintEligibilityTest {
    private static CapturedProfileSprintEligibility.Traits traits(int food,boolean nativeCapability,boolean slow,
            boolean fall,boolean collision,boolean minor,boolean using,boolean water,boolean flying,boolean blind) {
        return new CapturedProfileSprintEligibility.Traits(true,false,false,using,false,water,false,flying,blind,food,nativeCapability,slow,fall,collision,minor,true);
    }
    private static CapturedProfileSprintEligibility.Traits valid(int food){return traits(food,true,false,false,false,false,false,false,false,false);}
    @Test void wideAndNarrowWalkingFoodSixNeverPublishSprintIntentEvenWithNativeMayflyBypass(){
        for(var input:new Input[]{Input.FORWARD,Input.FORWARD_LEFT,Input.FORWARD_RIGHT}) {
            assertFalse(CapturedProfileSprintEligibility.mayPress(valid(6),input));
            assertFalse(CapturedProfileSprintEligibility.mayPress(valid(0),input));
        }
        assertFalse(CapturedProfileSprintEligibility.mayRequest(valid(6)));
    }
    @Test void exactOriginalFoodBoundaryPermitsSevenAndRefusesSix(){
        assertTrue(CapturedProfileSprintEligibility.mayPress(valid(7),Input.FORWARD));
        assertFalse(CapturedProfileSprintEligibility.mayPress(valid(6),Input.FORWARD));
    }
    @Test void actualCandidateForwardReplacesStalePriorInputForFirstAndOngoingSprint(){
        assertTrue(CapturedProfileSprintEligibility.mayPress(valid(20),Input.FORWARD));
        assertTrue(CapturedProfileSprintEligibility.mayPress(valid(20),Input.FORWARD_LEFT));
        assertTrue(CapturedProfileSprintEligibility.mayPress(valid(20),Input.FORWARD_RIGHT));
        for(var input:new Input[]{Input.NEUTRAL,Input.BACKWARD,Input.LEFT,Input.RIGHT,Input.BACKWARD_LEFT,Input.BACKWARD_RIGHT})
            assertFalse(CapturedProfileSprintEligibility.mayPress(valid(20),input));
    }
    @Test void nativeCapabilityRefusalCannotBeOverriddenByLongStraightGeometry(){
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,false,false,false,false,false,false,false,false,false),Input.FORWARD));
    }
    @Test void nativeDrySlowFallAndMajorCollisionTraitsRefusePress(){
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,true,false,false,false,false,false,false,false),Input.FORWARD));
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,true,false,false,false,false,false,false),Input.FORWARD));
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,false,true,false,false,false,false,false),Input.FORWARD));
        assertTrue(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,false,true,true,false,false,false,false),Input.FORWARD));
    }
    @Test void originalItemWaterFlyingBlindnessExclusionsStayAdditionalRefusals(){
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,false,false,false,true,false,false,false),Input.FORWARD));
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,false,false,false,false,true,false,false),Input.FORWARD));
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,false,false,false,false,false,true,false),Input.FORWARD));
        assertFalse(CapturedProfileSprintEligibility.mayPress(traits(20,true,false,false,false,false,false,false,false,true),Input.FORWARD));
    }
    @Test void absentTraitsAndNonGroundedProfileCannotBecomeSprintAuthority(){
        assertFalse(CapturedProfileSprintEligibility.mayPress(null,Input.FORWARD));assertFalse(CapturedProfileSprintEligibility.mayPress(valid(20),null));
        var t=new CapturedProfileSprintEligibility.Traits(true,false,false,false,false,false,false,false,false,20,true,false,false,false,false,false);
        assertFalse(CapturedProfileSprintEligibility.mayPress(t,Input.FORWARD));
    }
    @Test void exactVersionStartupAndSustainUsePositiveNormalizedScalarNotOlderPointEightThreshold(){
        assertEquals(1F,CapturedProfileSprintEligibility.nativeForwardScalar(Input.FORWARD));
        assertEquals(1F/(float)Math.sqrt(2F),CapturedProfileSprintEligibility.nativeForwardScalar(Input.FORWARD_LEFT));
        assertTrue(CapturedProfileSprintEligibility.nativeForwardScalar(Input.FORWARD_LEFT)<.8F);
        assertTrue(CapturedProfileSprintEligibility.mayPress(valid(20),Input.FORWARD_LEFT));
        assertEquals(0F,CapturedProfileSprintEligibility.nativeForwardScalar(Input.NEUTRAL));
    }
}
