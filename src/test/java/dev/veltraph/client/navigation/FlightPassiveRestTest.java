package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FlightPassiveRestTest {
    private static final Vec3 GOAL=new Vec3(16.5,74,.5);
    // Immutable P2 native sample82: both instantaneous gates pass, but outward coast leaves .3.
    private static final Vec3 P2_POSITION=new Vec3(16.2239251013004,73.9240086392042,.40223970029240885);
    private static final Vec3 P2_VELOCITY=new Vec3(-.01166142802273605,0,-.01345343322328114);
    @Test void exactNativeLowMotionBoundaryDoesNotDeclareArrivalBeforePassiveDrift() {
        assertTrue(P2_POSITION.horizontalDistanceTo(GOAL)<.3);assertTrue(Math.hypot(P2_VELOCITY.x(),P2_VELOCITY.z())<.02);
        Vec3 coast=P2_POSITION,v=P2_VELOCITY;
        for(int tick=0;tick<10;tick++) { coast=new Vec3(coast.x()+v.x(),coast.y(),coast.z()+v.z());v=new Vec3(v.x()*.91f,0,v.z()*.91f); }
        assertTrue(coast.horizontalDistanceTo(GOAL)>.4,"actual native .91 passive tail can invalidate the immediate goal window");
        var c=new FlightController();c.start(List.of(GOAL),0,false);
        var out=c.tick(new FlightController.Input(P2_POSITION,P2_VELOCITY,-81.2996f,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.FLYING,out.state());
        assertTrue(out.forward()||out.back()||out.left()||out.right(),"ordinary input must recenter the outward low-motion boundary");
    }
    @Test void inwardLowMotionCanArriveWhenEntirePassiveTailStaysInsideTheGoalWindow() {
        var c=new FlightController();c.start(List.of(GOAL),0,false);
        var out=c.tick(new FlightController.Input(P2_POSITION,new Vec3(.011,0,.01),-81.2996f,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.ARRIVED,out.state());assertFalse(out.forward()||out.back()||out.left()||out.right()||out.up()||out.down());
    }
    @Test void unloadedOrObstructedRestingFootprintCannotBeReportedAsArrival() {
        var c=new FlightController();c.start(List.of(GOAL),0,false);c.clearance((from,to)->to.x()<=16.55);
        var out=c.tick(new FlightController.Input(GOAL,new Vec3(.01,0,0),-90,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.FLYING,out.state());
    }
}
