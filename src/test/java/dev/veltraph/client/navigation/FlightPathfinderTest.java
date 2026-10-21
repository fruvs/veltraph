package dev.veltraph.client.navigation;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightPathfinderTest {
    @Test void climbsAndGoesAroundAnObstacle() {
        var result=FlightPathfinder.find(p->p.y()>=0&&!(p.x()==2&&p.z()==0&&p.y()<4),new Vec3(.5,0,.5),new Vec3(4.5,2,.5),8,1000);
        assertTrue(result.found());assertEquals(new Vec3(4.5,2,.5),result.nodes().getLast());
        assertTrue(result.nodes().stream().anyMatch(p->p.z()!=.5||p.y()>=4));
    }
    @Test void boundsSearchAndRejectsUnloadedDestinations() {
        assertFalse(FlightPathfinder.find(p->p.x()<3,new Vec3(.5,0,.5),new Vec3(4.5,0,.5),8,1000).found());
        assertEquals("Flight search budget reached",FlightPathfinder.find(p->true,new Vec3(.5,0,.5),new Vec3(30.5,30,30.5),32,5).reason());
    }
    @Test void keepsValidatedEdgesUntilClearanceAwareShaping() {
        var result=FlightPathfinder.find(p->true,new Vec3(.5,0,.5),new Vec3(8.5,0,.5),16,1000);
        assertEquals(8,result.nodes().size());assertEquals(new Vec3(8.5,0,.5),result.nodes().getLast());
    }
    @Test void validatesFractionalStartAndDestinationConnections() {
        Vec3 start=new Vec3(.9,.8,.9),goal=new Vec3(3.2,.4,.9);
        var result=FlightPathfinder.find(p->true,start,goal,8,1000,(a,b)->a.z()>=.8 && b.z()>=.8);
        assertTrue(result.found());
        Vec3 previous=start;
        for(Vec3 point:result.nodes()) { assertTrue(previous.z()>=.8 && point.z()>=.8);previous=point; }
        assertEquals(goal,result.nodes().getLast());
    }
    @Test void fractionalEndpointsUseActualPoseInsteadOfBlockedCellCenters() {
        var air=new FlightPathfinder.Airspace() {
            public boolean clear(BlockPos p) { return p.y()>=1; }
            public boolean poseClear(Vec3 p) { return p.y()>=.5; }
        };
        var start=new Vec3(.5,.5,.5);var goal=new Vec3(3.5,.5,.5);
        var result=FlightPathfinder.find(air,start,goal,8,1000,(a,b)->a.y()>=.5&&b.y()>=.5);
        assertTrue(result.found());assertEquals(goal,result.nodes().getLast());
        assertTrue(result.nodes().stream().allMatch(p->p.y()>=.5));
    }

}
