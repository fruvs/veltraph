package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FlightControllerTest {
    @Test void verticalDirectionUsesCorrectPhysicalSign() {
        var c=new FlightController();c.start(List.of(new Vec3(.5,5,.5)),0);
        var up=c.tick(new FlightController.Input(new Vec3(.5,1,.5),new Vec3(0,0,0),0,true,true,true,100),.2,1000);
        assertTrue(up.up());assertFalse(up.down());
        var down=c.tick(new FlightController.Input(new Vec3(.5,8,.5),new Vec3(0,0,0),0,true,true,true,200),.2,1000);
        assertTrue(down.down());assertFalse(down.up());
    }
    @Test void obstructionOrPermissionLossReleasesEveryInput() {
        for(boolean permission:new boolean[]{true,false}) {
            var c=new FlightController();c.start(List.of(new Vec3(5,5,5)),0);
            var out=c.tick(new FlightController.Input(new Vec3(.5,1,.5),new Vec3(0,0,0),0,true,permission,false,100),.2,1000);
            assertEquals(FlightController.State.BLOCKED,out.state());assertFalse(out.forward()||out.back()||out.left()||out.right()||out.up()||out.down());
        }
    }
    @Test void arrivalRequiresVerticalToleranceAndLowMotion() {
        var c=new FlightController();c.start(List.of(new Vec3(.5,5,.5)),0);
        assertEquals(FlightController.State.FLYING,c.tick(new FlightController.Input(new Vec3(.5,4.7,.5),new Vec3(0,0,0),0,true,true,true,100),.2,1000).state());
        assertEquals(FlightController.State.ARRIVED,c.tick(new FlightController.Input(new Vec3(.5,4.9,.5),new Vec3(0,0,0),0,true,true,true,200),.2,1000).state());
    }
    @Test void smallOvershootBrakesWithoutTurningAround() {
        var c=new FlightController();c.start(List.of(new Vec3(0,5,0)),0);
        var out=c.tick(new FlightController.Input(new Vec3(0,5,.5),new Vec3(0,0,.15),0,true,true,true,50),.2,4000);
        assertTrue(out.back());assertFalse(out.forward());assertEquals(0,out.yaw(),.001);
    }
    @Test void nearGoalSidewaysMomentumGetsCounterSteering() {
        var c=new FlightController();c.start(List.of(new Vec3(0,5,0)),0);
        var out=c.tick(new FlightController.Input(new Vec3(.6,5,0),new Vec3(.15,0,0),0,true,true,true,50),.2,4000);
        assertTrue(out.right());assertFalse(out.left());assertEquals(0,out.yaw(),.001);
    }
    @Test void stallingIsTerminalUntilReplanned() {
        var c=new FlightController();c.start(List.of(new Vec3(5,5,5)),0);
        var position=new Vec3(.5,1,.5);
        c.tick(new FlightController.Input(position,new Vec3(0,0,0),0,true,true,true,100),.2,500);
        assertEquals(FlightController.State.BLOCKED,c.tick(new FlightController.Input(position,new Vec3(0,0,0),0,true,true,true,700),.2,500).state());
    }
    @Test void intermediateLegCountersteersSidewaysInertiaWhileCameraTurns() {
        var c=new FlightController();c.start(List.of(new Vec3(0,5,5),new Vec3(5,5,5)),0);
        var out=c.tick(new FlightController.Input(new Vec3(0,5,0),new Vec3(.15,0,0),0,true,true,true,50),.2,4000);
        assertTrue(out.right(),"lateral inertia must be braked before the final node too");
        assertFalse(out.left());
    }

    @Test void insideArrivalWindowBrakesInsteadOfChasingSubTolerancePosition() {
        var c=new FlightController();c.start(List.of(new Vec3(0,5,0)),0);
        var out=c.tick(new FlightController.Input(new Vec3(0,5,-.25),new Vec3(0,0,.03),0,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.FLYING,out.state());
        assertTrue(out.back());assertFalse(out.forward());
    }
    @Test void finalLandingColumnBrakesHorizontalMotionBeforeDescent() {
        var c=new FlightController();var above=new Vec3(0,6.25,0);var goal=new Vec3(0,5,0);
        c.start(List.of(above,goal),0);
        var moving=c.tick(new FlightController.Input(new Vec3(0,6.25,-.05),new Vec3(0,0,.1),0,true,true,true,50),.2,4000);
        assertEquals(above,c.target());assertTrue(moving.back());assertFalse(moving.down());
        var stopped=c.tick(new FlightController.Input(new Vec3(0,6.25,-.05),new Vec3(0,0,.01),0,true,true,true,100),.2,4000);
        assertEquals(goal,c.target());assertTrue(stopped.down());
    }
    @Test void finalLandingColumnCanCorrectOutsideItsTightCenteringGate() {
        var c=new FlightController();var above=new Vec3(0,6.25,0);
        c.start(List.of(above,new Vec3(0,5,0)),0);
        var out=c.tick(new FlightController.Input(new Vec3(0,6.25,-.13),new Vec3(0,0,0),0,true,true,true,50),.2,4000);
        assertEquals(above,c.target());assertTrue(out.forward());assertFalse(out.down());
    }
    @Test void landingColumnLetsSubInputMomentumDampInsteadOfAlternatingFullKeys() {
        var c=new FlightController();var above=new Vec3(0,6.25,0);
        c.start(List.of(above,new Vec3(0,5,0)),0);
        var out=c.tick(new FlightController.Input(new Vec3(0,6.25,-.05),new Vec3(0,0,.023),0,true,true,true,50),.2,4000);
        assertEquals(above,c.target());assertFalse(out.forward()||out.back());assertFalse(out.down());
    }
    @Test void offsetLandingNeedsExplicitPhysicalColumnProofAndOriginalLowMotion() {
        var above=new Vec3(-2.5,67.25,.5);var goal=new Vec3(-2.5,66,.5);
        for(double offset:new double[]{.11,.143,.24}) {
            var c=new FlightController();c.start(List.of(above,goal),0);
            var p=new Vec3(above.x()+offset,above.y(),above.z());
            c.tick(new FlightController.Input(p,new Vec3(0,0,0),0,true,true,true,50),.2,4000);
            assertEquals(above,c.target(),"no support proof must retain original tight centering");
            c.landingClearance((from,to)->to.equals(goal));
            var permitted=c.tick(new FlightController.Input(p,new Vec3(0,0,0),0,true,true,true,100),.2,4000);
            assertEquals(goal,c.target());assertTrue(permitted.down());assertFalse(permitted.up());
        }
        for(double velocity:new double[]{.02,.035}) {
            var c=new FlightController();c.start(List.of(above,goal),0);c.landingClearance((a,b)->true);
            c.tick(new FlightController.Input(new Vec3(-2.357,67.25,.5),new Vec3(velocity,0,0),0,true,true,true,50),.2,4000);
            assertEquals(above,c.target(),"offset landing still requires original lateral settling");
        }
    }
    @Test void offsetLandingRechecksPredictedFootprintAndNeverWidensFinalArrival() {
        var above=new Vec3(0,6.25,0);var goal=new Vec3(0,5,0);
        var c=new FlightController();c.start(List.of(above,goal),0);c.landingClearance((from,to)->from.x()<.2);
        c.tick(new FlightController.Input(new Vec3(.18,6.25,0),new Vec3(.01,0,0),0,true,true,true,50),.2,4000);
        assertEquals(above,c.target(),"predicted drift beyond supported footprint must deny descent");
        c.landingClearance((a,b)->true);
        c.tick(new FlightController.Input(new Vec3(.25,6.25,0),new Vec3(0,0,0),0,true,true,true,100),.2,4000);
        assertEquals(above,c.target(),"strict bounded handshake remains below .25");
        c.tick(new FlightController.Input(new Vec3(.24,6.25,0),new Vec3(0,0,0),0,true,true,true,150),.2,4000);
        assertEquals(goal,c.target());
        assertEquals(FlightController.State.FLYING,c.tick(new FlightController.Input(new Vec3(.24,5.21,0),new Vec3(0,0,0),0,true,true,true,200),.2,4000).state());
    }
    @Test void supportedColumnArrivalRequiresActualLandingInsteadOfHoveringInsideVerticalWindow() {
        var above=new Vec3(0,6.25,0);var goal=new Vec3(0,5,0);
        var c=new FlightController();c.start(List.of(above,goal),0);c.landingClearance((a,b)->true);
        c.tick(new FlightController.Input(new Vec3(.143,6.25,0),new Vec3(0,0,0),0,true,true,true,50),.2,4000);
        var hovering=c.tick(new FlightController.Input(new Vec3(.143,5.15,0),new Vec3(0,0,0),0,true,true,true,100),.2,4000);
        assertEquals(FlightController.State.FLYING,hovering.state());assertTrue(hovering.down());assertFalse(hovering.up());
        var landed=c.tick(new FlightController.Input(new Vec3(.143,5,0),new Vec3(0,-.0784,0),0,true,true,true,true,150),.2,4000);
        assertEquals(FlightController.State.ARRIVED,landed.state());assertFalse(landed.up()||landed.down());
    }
    @Test void verticalRouteShapeAloneDoesNotTurnFloatingGoalIntoGroundedLanding() {
        var c=new FlightController();var goal=new Vec3(0,5,0);c.start(List.of(new Vec3(0,6.25,0),goal),0);
        c.tick(new FlightController.Input(new Vec3(0,6.25,0),new Vec3(0,0,0),0,true,true,true,50),.2,4000);
        assertEquals(goal,c.target());
        assertEquals(FlightController.State.ARRIVED,c.tick(new FlightController.Input(goal,new Vec3(0,0,0),0,true,true,true,100),.2,4000).state());
        c.start(List.of(goal),0,true);
        var supported=c.tick(new FlightController.Input(new Vec3(0,5.15,0),new Vec3(0,0,0),0,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.FLYING,supported.state());assertTrue(supported.down());
    }
    @Test void knownFloatingGoalDoesNotAcquireLandingGateAtItsVerticalInterpolationWaypoint() {
        var c=new FlightController();var intermediate=new Vec3(-2.5,71.82031247299165,.5);var goal=new Vec3(-2.5,71,.5);
        c.start(List.of(intermediate,goal),0,false);
        var out=c.tick(new FlightController.Input(new Vec3(-2.5,71.97031247299165,.5),new Vec3(0,-.06,0),47,true,true,true,50),.2,4000);
        assertEquals(goal,c.target(),"explicitly floating route uses its ordinary swept intermediate transition instead of a ground landing velocity gate");
        assertTrue(out.down());assertFalse(out.up(),"must continue descending toward original goal, not brake upward around interpolation waypoint");
    }
    @Test void observedSupportedLandingAcceptsNormalGravityWithoutRearmingJump() {
        var c=new FlightController();var goal=new Vec3(.5,72,.5);c.start(List.of(goal),0);
        var out=c.tick(new FlightController.Input(goal,new Vec3(0,-.0784,0),0,true,true,true,true,100),.2,4000);
        assertEquals(FlightController.State.ARRIVED,out.state());assertFalse(out.up()||out.down());
        c.start(List.of(goal),0);
        var drifting=c.tick(new FlightController.Input(goal,new Vec3(.03,-.0784,0),0,true,true,true,true,100),.2,4000);
        assertEquals(FlightController.State.FLYING,drifting.state());assertFalse(drifting.up()||drifting.down());
        c.start(List.of(goal),0);
        assertEquals(FlightController.State.FLYING,c.tick(new FlightController.Input(new Vec3(.5,71.7,.5),new Vec3(0,-.0784,0),0,true,true,true,true,100),.2,4000).state());
    }
    @Test void nativeVerticalCoastingSettlesInsideGoalWithoutAlternatingKeys() {
        var c=new FlightController();var goal=new Vec3(32.05,74,.5);c.start(List.of(goal),0);
        var out=c.tick(new FlightController.Input(new Vec3(31.83,74.165,.58),new Vec3(0,-.05625,0),-90,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.FLYING,out.state());assertFalse(out.up()||out.down());
        var settled=c.tick(new FlightController.Input(new Vec3(31.83,74.10875,.58),new Vec3(0,-.03375,0),-90,true,true,true,100),.2,4000);
        assertEquals(FlightController.State.ARRIVED,settled.state());
        c.start(List.of(goal),0);
        assertTrue(c.tick(new FlightController.Input(new Vec3(31.83,74.165,.58),new Vec3(0,.05625,0),-90,true,true,true,50),.2,4000).down());
    }

}
