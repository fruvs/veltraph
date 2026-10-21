package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Model-only contracts. These public advisory values cannot admit or publish a native Leg. */
class SupportedSharedFlightPolicyTest {
    private static final Vec3 GOAL=new Vec3(30,76,0);
    private static SharedMovementPolicy.Candidate candidate(String id,SharedMovementPolicy.Mode mode,double cost){
        return new SharedMovementPolicy.Candidate(id,mode,GOAL,cost,0,new Object(),new Object(),null);
    }
    private static SharedMovementPolicy.Options options(){return new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.FLIGHT),SharedMovementPolicy.Purpose.GENERAL_TRAVEL,0,10000,.35,1.25);}
    @Test void supportedFlightNeedsNoToolBudget(){var f=candidate("flight",SharedMovementPolicy.Mode.FLIGHT,32);assertSame(f,SharedMovementPolicy.select(List.of(f),options(),0,null,0).orElseThrow());}
    @Test void enumOutsideActualOfferedCandidatesCannotSelectFlight(){var w=candidate("walk",SharedMovementPolicy.Mode.WALK,30);assertEquals(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.select(List.of(w),options(),0,null,0).orElseThrow().mode());}
    @Test void flightDoesNotBypassComparableCost(){var w=candidate("walk",SharedMovementPolicy.Mode.WALK,30);var f=candidate("flight",SharedMovementPolicy.Mode.FLIGHT,50);assertSame(w,SharedMovementPolicy.select(List.of(w,f),options(),0,null,0).orElseThrow());}
    @Test void repeatedComparableJourneysCanAlternateModes(){var w=candidate("walk",SharedMovementPolicy.Mode.WALK,30);var f=candidate("flight",SharedMovementPolicy.Mode.FLIGHT,32);var first=SharedMovementPolicy.select(List.of(w,f),options(),0,null,0).orElseThrow();var next=SharedMovementPolicy.select(List.of(w,f),options(),0,first.id(),1).orElseThrow();assertNotEquals(first.mode(),next.mode());}
    @Test void exactReturnDoesNotAdmitSharedFlight(){assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.FLIGHT),SharedMovementPolicy.Purpose.EXACT_RETURN,0,10000,.35,1.25));}
    @Test void prescribedGroundTransitDoesNotAdmitFlight(){assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.FLIGHT),SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL,0,10000,.35,1.25));}
    @Test void hoveringAtSupportedGoalDoesNotCompleteLanding(){var c=new FlightController();c.start(List.of(GOAL),0,true);var out=c.tick(new FlightController.Input(GOAL,new Vec3(0,0,0),0,true,true,true,false,50),.2,1000);assertEquals(FlightController.State.FLYING,out.state());assertTrue(out.down());}
    @Test void actualSupportedStationaryGoalCompletesAndReleases(){var c=new FlightController();c.start(List.of(GOAL),0,true);var out=c.tick(new FlightController.Input(GOAL,new Vec3(0,0,0),0,true,true,true,true,50),.2,1000);assertEquals(FlightController.State.ARRIVED,out.state());assertFalse(out.forward()||out.back()||out.left()||out.right()||out.up()||out.down());}
    @Test void precisionFlightDoesNotCompleteAtOldPointThreeWindow(){var c=new FlightController();c.startSupported(List.of(GOAL),0,.15);var p=new Vec3(GOAL.x()+.2,GOAL.y(),GOAL.z());assertEquals(FlightController.State.FLYING,c.tick(new FlightController.Input(p,new Vec3(0,0,0),0,true,true,true,true,50),.2,1000).state());}
    @Test void precisionFlightCompletesActualSupportedSmallerGoal(){var c=new FlightController();c.startSupported(List.of(GOAL),0,.15);var p=new Vec3(GOAL.x()+.1,GOAL.y(),GOAL.z());assertEquals(FlightController.State.ARRIVED,c.tick(new FlightController.Input(p,new Vec3(0,0,0),0,true,true,true,true,50),.2,1000).state());}
    @Test void precisionPassiveRestCannotEscapeRequestedRadius(){var c=new FlightController();c.startSupported(List.of(GOAL),0,.15);var p=new Vec3(GOAL.x()+.1,GOAL.y(),GOAL.z());assertEquals(FlightController.State.FLYING,c.tick(new FlightController.Input(p,new Vec3(.015,0,0),0,true,true,true,true,50),.2,1000).state());}
    @Test void precisionCorrectionDoesNotStopAtOriginalDeadZone(){var c=new FlightController();c.startSupported(List.of(GOAL),0,.05);var p=new Vec3(GOAL.x()+.1,GOAL.y(),GOAL.z());var out=c.tick(new FlightController.Input(p,new Vec3(0,0,0),0,true,true,true,false,50),.2,1000);assertEquals(FlightController.State.FLYING,out.state());assertTrue(out.left()||out.right());}
    @Test void originalAuthoredFlightRetainsOriginalArrivalTolerance(){var c=new FlightController();c.start(List.of(GOAL),0,true);var p=new Vec3(GOAL.x()+.2,GOAL.y(),GOAL.z());assertEquals(FlightController.State.ARRIVED,c.tick(new FlightController.Input(p,new Vec3(0,0,0),0,true,true,true,true,50),.2,1000).state());}
    @Test void precisionRadiusRejectsNonfiniteOrWidenedRequests(){for(double radius:new double[]{0,-.1,.36,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new FlightController().startSupported(List.of(GOAL),0,radius));}
    @Test void actualSlowerAirDecayCannotUseOriginalPointNineOneRestTail(){var c=new FlightController();c.startSupported(List.of(GOAL),0,.15,.98);var p=new Vec3(GOAL.x()+.1,GOAL.y(),GOAL.z());assertEquals(FlightController.State.FLYING,c.tick(new FlightController.Input(p,new Vec3(.004,0,0),0,true,true,true,true,50),.2,1000).state());}
    @Test void actualWalkOnlySettingExcludesEverySharedTransportHint(){assertEquals(Set.of(SharedMovementPolicy.Mode.WALK),SharedMovementPolicy.allowedFor(SharedMovementPolicy.Purpose.GENERAL_TRAVEL,ApproachMode.WALK_ONLY));}
    @Test void actualAutoSettingRetainsSharedFlightAndBothInstantDevices(){assertEquals(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.FLIGHT,SharedMovementPolicy.Mode.AOTE,SharedMovementPolicy.Mode.AOTV),SharedMovementPolicy.allowedFor(SharedMovementPolicy.Purpose.GENERAL_TRAVEL,ApproachMode.AUTO));}
    @Test void returnApproachOffersOnlyRequestedInstantTransmissionHintsWhileEntryAndCropStayPrescribed(){
        assertEquals(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.AOTE,SharedMovementPolicy.Mode.AOTV),SharedMovementPolicy.allowedFor(SharedMovementPolicy.Purpose.EXACT_RETURN,ApproachMode.AUTO));
        assertEquals(Set.of(SharedMovementPolicy.Mode.WALK),SharedMovementPolicy.allowedFor(SharedMovementPolicy.Purpose.EXACT_RETURN,ApproachMode.WALK_ONLY));
        assertEquals(Set.of(SharedMovementPolicy.Mode.WALK),SharedMovementPolicy.allowedFor(SharedMovementPolicy.Purpose.CROP_WORK,ApproachMode.AUTO));
        assertThrows(IllegalArgumentException.class,()->new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK,SharedMovementPolicy.Mode.ETHERWARP),SharedMovementPolicy.Purpose.EXACT_RETURN,1,10000,.18,1.25));
    }
}
