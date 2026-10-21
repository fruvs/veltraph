package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SavedAnchorRecoveryTest {
    static class Actions implements SavedAnchorRecovery.Actions {
        boolean walk=true,fly=true;int walks,flights,releases,aligns;
        SavedAnchorRecovery.Travel travel=SavedAnchorRecovery.Travel.ACTIVE;
        public boolean startWalk(){walks++;return walk;}
        public boolean startFlight(){flights++;return fly;}
        public SavedAnchorRecovery.Travel travel(){return travel;}
        public void align(){aligns++;}
        public void release(){releases++;}
    }
    private SavedAnchorRecovery controller(Actions a){return new SavedAnchorRecovery(a,new SavedAnchorRecovery.Settings(10000,500));}
    @Test void arrivalRequiresObservedPositionAngleAndContinuousSettling(){
        var a=new Actions();var r=controller(a);assertTrue(r.start(true,true,0));
        a.travel=SavedAnchorRecovery.Travel.ARRIVED;r.tick(true,true,false,false,100);
        assertEquals(SavedAnchorRecovery.State.ALIGNING,r.state());
        r.tick(true,true,true,true,200);r.tick(true,true,false,true,500);
        r.tick(true,true,true,true,700);r.tick(true,true,true,true,1199);assertTrue(r.active());
        r.tick(true,true,true,true,1200);assertEquals(SavedAnchorRecovery.State.ARRIVED,r.state());assertTrue(a.releases>=2);
    }
    @Test void walkFailureFallsBackToFlightOnlyWithPermission(){
        var a=new Actions();a.walk=false;var r=controller(a);assertFalse(r.start(true,false,0));assertEquals(0,a.flights);
        assertTrue(r.start(true,true,0));assertEquals(SavedAnchorRecovery.State.FLYING,r.state());assertEquals(1,a.flights);
    }
    @Test void movementFailureCanUseOnlyOneFlightFallback(){
        var a=new Actions();var r=controller(a);r.start(true,true,0);a.travel=SavedAnchorRecovery.Travel.FAILED;
        r.tick(true,false,false,false,1);assertEquals(SavedAnchorRecovery.State.FLYING,r.state());
        r.tick(true,false,false,false,2);assertEquals(SavedAnchorRecovery.State.FAILED,r.state());assertEquals(1,a.flights);
    }
    @Test void invalidContextCancelsAndTimeoutFailsWithoutResuming(){
        var a=new Actions();var r=controller(a);assertFalse(r.start(false,true,0));assertEquals(0,a.walks);
        r.start(true,true,0);r.tick(false,false,false,false,1);assertEquals(SavedAnchorRecovery.State.CANCELLED,r.state());
        r.start(true,true,0);r.tick(true,false,false,false,10000);assertEquals(SavedAnchorRecovery.State.FAILED,r.state());
        assertFalse(r.active());
    }
    @Test void navigatorArrivalDoesNotOverrideActualDistance(){
        var a=new Actions();var r=controller(a);r.start(true,true,0);a.travel=SavedAnchorRecovery.Travel.ARRIVED;
        r.tick(true,false,true,true,1);assertEquals(SavedAnchorRecovery.State.FAILED,r.state());
    }
    @Test void backwardClockCannotExtendTravelOrSettleDeadline(){
        var actions=new Actions();var recovery=controller(actions);recovery.start(true,false,100);
        recovery.tick(true,false,false,false,200);recovery.tick(true,false,false,false,199);
        assertEquals(SavedAnchorRecovery.State.FAILED,recovery.state());assertFalse(recovery.active());
        recovery.start(true,false,300);actions.travel=SavedAnchorRecovery.Travel.ARRIVED;
        recovery.tick(true,true,true,true,400);recovery.tick(true,true,true,true,500);
        recovery.tick(true,true,true,true,499);assertEquals(SavedAnchorRecovery.State.FAILED,recovery.state());
    }
    @Test void invalidTimingCannotStartRecovery(){
        assertThrows(IllegalArgumentException.class,()->new SavedAnchorRecovery.Settings(0,0));
        assertThrows(IllegalArgumentException.class,()->new SavedAnchorRecovery.Settings(100,-1));
        assertThrows(IllegalArgumentException.class,()->new SavedAnchorRecovery.Settings(100,100));
    }
}
