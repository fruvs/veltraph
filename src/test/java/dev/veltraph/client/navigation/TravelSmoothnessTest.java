package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TravelSmoothnessTest {
    private GroundPathfinder.Terrain terrain(boolean corridor) {
        return new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1 && (!corridor || p.z()==0 && p.y()<3); }
            public boolean floor(BlockPos p) { return p.y()==0; }
        };
    }
    @Test void repeatedOpenRoutesVaryAndStillEndAtExactGoal() {
        var start=new Vec3(.5,1,.5);var goal=new Vec3(12.5,1,.5);
        var routes=new HashSet<List<Vec3>>();var t=terrain(false);
        for(int seed=0;seed<30;seed++) {
            var route=TravelPath.shape(start,List.of(goal),(a,b)->TravelPath.walkClear(t,a,b),seed);
            routes.add(route);assertEquals(goal,route.getLast());
            Vec3 previous=start;
            for(Vec3 p:route) { assertTrue(TravelPath.walkClear(t,previous,p));previous=p; }
        }
        assertEquals(30,routes.size());
    }
    @Test void oneWideTwoHighPassageKeepsFullBodyInside() {
        var start=new Vec3(.5,1,.5);var goal=new Vec3(12.5,1,.5);var t=terrain(true);
        for(int seed=0;seed<30;seed++) {
            var route=TravelPath.shape(start,List.of(goal),(a,b)->TravelPath.walkClear(t,a,b),seed);
            Vec3 previous=start;
            for(Vec3 p:route) { assertTrue(TravelPath.walkClear(t,previous,p));assertEquals(.5,p.z(),1e-8);previous=p; }
        }
    }
    @Test void turnsAreGradualAndConvergeAcrossYawWrap() {
        var heading=new TravelHeading();float yaw=170;
        for(int tick=0;tick<100;tick++) {
            float next=heading.step(yaw,-100,1);
            assertTrue(Math.abs(WalkController.wrap(next-yaw))<=6.001);yaw=next;
        }
        assertEquals(0,WalkController.wrap(-100-yaw),.02);
    }
    @Test void pitchCrossesBothLimitsWithoutWrappingOrReversing() {
        var heading=new TravelHeading();float pitch=-90,lastRate=0;
        for(int tick=0;tick<100;tick++) {
            float next=heading.stepLinear(pitch,90,1),rate=next-pitch;
            assertTrue(rate>=0&&rate<=6.001);
            assertTrue(Math.abs(rate-lastRate)<=1.201);
            lastRate=rate;pitch=next;
        }
        assertEquals(90,pitch,.02);
    }
    @Test void intermediateFlightWaypointDoesNotDemandStop() {
        var flight=new FlightController();flight.start(List.of(new Vec3(0,5,1),new Vec3(0,5,5)),0);
        var out=flight.tick(new FlightController.Input(new Vec3(0,5,.9),new Vec3(0,0,.15),0,true,true,true,50),.2,4000);
        assertEquals(FlightController.State.FLYING,out.state());assertTrue(out.forward());assertFalse(out.back());
    }
    @Test void walkingDoesNotReleaseAtIntermediateBlockCenters() {
        var walk=new WalkController();walk.start(List.of(new BlockPos(0,1,1),new BlockPos(0,1,2),new BlockPos(0,1,3)),0);
        var out=walk.tick(new WalkController.Input(new Vec3(.5,1,1.5),0,true,50),terrain(false),4000);
        assertEquals(WalkController.State.WALKING,out.state());assertTrue(out.forward());
    }
}
