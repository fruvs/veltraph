package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Input-controller trajectories; the native fixture separately verifies vanilla motion. */
final class OpenTravelTrajectoryTest {
    private static final Vec3 START=new Vec3(.5,1,.5),GOAL=new Vec3(16.5,1,.5);
    private static GroundPathfinder.Terrain terrain(int width) {
        return new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1&&(width==0||p.z()>=0&&p.z()<width); }
            public boolean floor(BlockPos p) { return p.y()==0; }
        };
    }
    private static List<Vec3> route(Vec3 start,Vec3 goal,GroundPathfinder.Terrain t,long seed) {
        return TravelPath.shapeOpen(start,List.of(goal),(a,b)->t.walkClear(a,b),seed);
    }
    @Test void broadTripsVaryOnBothSidesWithStraightDepartureAndArrival() {
        var routes=new HashSet<List<Vec3>>();boolean positive=false,negative=false;
        for(long seed=1;seed<=24;seed++) {
            var nodes=route(START,GOAL,terrain(0),seed*2654435761L);routes.add(nodes);
            double peak=0;Vec3 previous=START;
            for(Vec3 p:nodes) {
                assertTrue(terrain(0).walkClear(previous,p));previous=p;
                peak=Math.max(peak,Math.abs(p.z()-.5));
                positive|=p.z()>.9;negative|=p.z()<.1;
                if(p.x()<=2.5||p.x()>=14.5)assertEquals(.5,p.z(),1e-9);
            }
            assertTrue(peak>.4&&peak<1);assertEquals(GOAL,nodes.getLast());
        }
        assertEquals(24,routes.size());assertTrue(positive&&negative);
    }
    @Test void oneAndTwoBlockCorridorsKeepCenterlineEvenWhenABendWouldFit() {
        for(int width:new int[]{1,2})for(long seed=0;seed<20;seed++) {
            Vec3 start=new Vec3(.5,1,width/2d),goal=new Vec3(16.5,1,width/2d);
            for(Vec3 p:route(start,goal,terrain(width),seed))assertEquals(start.z(),p.z(),1e-9);
        }
    }
    @Test void missingSupportAtEitherSideDisablesVariationAcrossLoadedMainLine() {
        var t=new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1; }
            public boolean floor(BlockPos p) { return p.y()==0&&p.z()>=0&&p.z()<2; }
        };
        assertTrue(t.walkClear(START,GOAL));
        assertTrue(route(START,GOAL,t,99).stream().allMatch(p->p.z()==.5));
    }
    @Test void nearbyObstacleDisablesWideBendWithoutInvalidatingOrdinaryPath() {
        var t=new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1&&!(p.x()==8&&p.z()==1&&p.y()<3); }
            public boolean floor(BlockPos p) { return p.y()==0; }
        };
        assertTrue(t.walkClear(START,GOAL));
        for(Vec3 p:route(START,GOAL,t,97))assertEquals(.5,p.z(),1e-9);
    }
    @Test void shortInteractionApproachHasNoVariationAndPreservesFractionalEndpoint() {
        var goal=new Vec3(5.137,1,.62);
        for(Vec3 p:route(START,goal,terrain(0),14)) {
            assertEquals((p.x()-.5)/(goal.x()-.5),(p.z()-.5)/(goal.z()-.5),1e-8);
        }
        assertEquals(goal,route(START,goal,terrain(0),14).getLast());
    }
    @Test void leadIsContinuousAcrossWaypointAndFallsBackOnNewObstacleOrNearGoal() {
        var path=List.of(new Vec3(2,1,0),new Vec3(4,1,1),new Vec3(10,1,1));
        var origin=new Vec3(0,1,0);Vec3 before=new Vec3(1.999,1,0),after=new Vec3(2.001,1,.0005);
        var a=OpenTravelSteering.target(origin,path,0,before,1.25,(x,y)->true);
        var b=OpenTravelSteering.target(origin,path,1,after,1.25,(x,y)->true);
        assertTrue(a.distanceTo(b)<.01,"waypoint handoff must not switch the steering lead abruptly");
        var earlyA=OpenTravelSteering.target(origin,path,0,new Vec3(1.34,1,0),1.25,(x,y)->true);
        var earlyB=OpenTravelSteering.target(origin,path,1,new Vec3(1.36,1,0),1.25,(x,y)->true);
        assertTrue(earlyA.distanceTo(earlyB)<.03,"actual .65-radius early node handoff must retain continuous projected lead");
        assertEquals(path.get(0),OpenTravelSteering.target(origin,path,0,before,1.25,(x,y)->y.z()<.2));
        assertEquals(path.get(1),OpenTravelSteering.target(origin,path,1,new Vec3(8,1,1),1.25,(x,y)->true));
    }
    @Test void actualWalkingInputsProduceDifferentBroadTrajectoriesAndStrictArrival() {
        var trajectories=new HashSet<List<Vec3>>();
        for(long seed:new long[]{1,14,31,67,99999}) {
            var walk=new WalkController(()->seed);var t=terrain(0);
            walk.start(List.of(new BlockPos(16,1,0)),GOAL,0,.14);
            Vec3 position=START;float yaw=-90,lastRate=0;double peak=0;int pauses=0;var trace=new ArrayList<Vec3>();
            WalkController.Output out=null;
            for(int tick=0;tick<300;tick++) {
                out=walk.tick(new WalkController.Input(position,yaw,true,tick*50L),t,4000);
                float rate=WalkController.wrap(out.yaw()-yaw);
                assertTrue(Math.abs(rate)<=6.001);assertTrue(Math.abs(rate-lastRate)<=1.201);
                lastRate=rate;yaw=out.yaw();
                if(out.state()!=WalkController.State.WALKING)break;
                if(!out.forward()) { if(position.distanceTo(GOAL)>=.14)pauses++; }
                else { double rad=Math.toRadians(yaw);var next=new Vec3(position.x()-Math.sin(rad)*.17,1,position.z()+Math.cos(rad)*.17);assertTrue(t.walkClear(position,next));position=next; }
                peak=Math.max(peak,Math.abs(position.z()-.5));trace.add(position);
            }
            assertEquals(WalkController.State.ARRIVED,out.state(),walk.diagnostics());
            assertTrue(position.distanceTo(GOAL)<.14);assertTrue(peak>.2);assertEquals(0,pauses);
            assertFalse(out.forward()||out.jump());trajectories.add(trace);
        }
        assertEquals(5,trajectories.size());
    }
    @Test void strictWalkingArrivalDeceleratesLastHeadingStepWithoutMovingPastThePose() {
        var target=new Vec3(1.5,1,.5);
        for(float initial:new float[]{-80,-100}) {
            var walk=new WalkController(()->1L);walk.start(List.of(),target,0,.14);
            float yaw=initial,lastRate=0;WalkController.Output out=null;
            for(int tick=0;tick<8;tick++) {
                Vec3 p=new Vec3(tick==0?.5:tick==1?1.2:1.4,1,.5);
                out=walk.tick(new WalkController.Input(p,yaw,true,tick*50L),terrain(0),4000);
                float rate=WalkController.wrap(out.yaw()-yaw);
                assertTrue(Math.abs(rate-lastRate)<=1.21,"arrival rate discontinuity: previous="+lastRate+" next="+rate+" state="+out.state());
                yaw=out.yaw();lastRate=rate;
                if(tick>=2)assertFalse(out.forward()||out.jump(),"angular settling must release movement inside strict pose");
                if(out.state()==WalkController.State.ARRIVED)break;
            }
            assertEquals(WalkController.State.ARRIVED,out.state());assertEquals(0,lastRate,.001);
        }
    }
    @Test void ordinaryFlightInputsFollowDistinctBendsThenSettleAtUnchangedTolerance() {
        var trajectories=new HashSet<List<Vec3>>();Vec3 start=new Vec3(.5,5,.5),goal=new Vec3(16.5,5,.5);
        for(long seed:new long[]{1,14,67}) {
            var flight=new FlightController();flight.clearance((a,b)->true);
            flight.start(TravelPath.shapeFlight(start,List.of(goal),(a,b)->true,(a,b)->true,seed),0,false);
            Vec3 p=start,v=new Vec3(0,0,0);float yaw=-90,lastRate=0;double peak=0;var trace=new ArrayList<Vec3>();
            FlightController.Output out=null;
            for(int tick=0;tick<800;tick++) {
                out=flight.tick(new FlightController.Input(p,v,yaw,true,true,true,tick*50L),.2,4000);
                float rate=WalkController.wrap(out.yaw()-yaw);assertTrue(Math.abs(rate)<=6.001);assertTrue(Math.abs(rate-lastRate)<=1.201);yaw=out.yaw();lastRate=rate;
                if(out.state()!=FlightController.State.FLYING)break;
                double rad=Math.toRadians(yaw),forward=(out.forward()?1:0)-(out.back()?1:0),side=(out.left()?1:0)-(out.right()?1:0);
                v=new Vec3((v.x()+(-Math.sin(rad)*forward+Math.cos(rad)*side)*.04)*.91,0,(v.z()+(Math.cos(rad)*forward+Math.sin(rad)*side)*.04)*.91);
                p=new Vec3(p.x()+v.x(),5,p.z()+v.z());peak=Math.max(peak,Math.abs(p.z()-.5));trace.add(p);
            }
            assertEquals(FlightController.State.ARRIVED,out.state(),flight.status()+" p="+p+" v="+v);
            assertTrue(p.horizontalDistanceTo(goal)<.3);assertTrue(Math.hypot(v.x(),v.z())<.02);assertTrue(peak>.2);
            assertFalse(out.forward()||out.back()||out.left()||out.right()||out.up()||out.down());trajectories.add(trace);
        }
        assertEquals(3,trajectories.size());
    }
}
