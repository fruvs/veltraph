package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {
    private GroundPathfinder.Terrain terrain(Set<BlockPos> blocks,Set<BlockPos> holes) {
        return new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1&&!blocks.contains(p); }
            public boolean floor(BlockPos p) { return (p.y()==0&&!holes.contains(p))||blocks.contains(p); }
        };
    }
    @Test void walksAroundTwoBlockWallAndNeverThroughIt() {
        var obstacles=Set.of(new BlockPos(1,1,0),new BlockPos(1,2,0));
        var t=terrain(obstacles,Set.of());
        var result=GroundPathfinder.find(t,new BlockPos(0,1,0),new BlockPos(3,1,0),200,8);
        assertTrue(result.found());
        assertTrue(result.steps().size()>3);
        assertTrue(result.steps().stream().noneMatch(obstacles::contains));
    }
    @Test void canStepUpWithHeadroomButNotIntoLowCeiling() {
        var from=new BlockPos(0,1,0);var to=new BlockPos(1,2,0);
        assertTrue(GroundPathfinder.canStep(terrain(Set.of(new BlockPos(1,1,0)),Set.of()),from,to));
        assertFalse(GroundPathfinder.canStep(terrain(Set.of(new BlockPos(1,1,0),from.up(2)),Set.of()),from,to));
    }
    @Test void rejectsVoidGoalAndStopsAtSearchBudget() {
        var result=GroundPathfinder.find(terrain(Set.of(),Set.of(new BlockPos(3,0,0))),new BlockPos(0,1,0),new BlockPos(3,1,0),100,8);
        assertFalse(result.found());
        var bounded=GroundPathfinder.find(terrain(Set.of(),Set.of()),new BlockPos(0,1,0),new BlockPos(20,1,0),3,24);
        assertFalse(bounded.found());assertEquals(3,bounded.visited());
    }
    @Test void followerReleasesOnObstacleAppearingAndOnTimeout() {
        var walker=new WalkController();var step=new BlockPos(1,1,0);
        walker.start(List.of(step),0);
        var in=new WalkController.Input(new Vec3(.5,1,.5),-90,true,100);
        assertTrue(walker.tick(in,terrain(Set.of(),Set.of()),500).forward());
        var blocked=walker.tick(in,terrain(Set.of(step),Set.of()),500);
        assertEquals(WalkController.State.BLOCKED,blocked.state()); assertFalse(blocked.forward());assertFalse(blocked.jump());
        walker.start(List.of(step),0);
        walker.tick(in,terrain(Set.of(),Set.of()),500);
        var timeout=walker.tick(new WalkController.Input(in.position(),-90,true,800),terrain(Set.of(),Set.of()),500);
        assertEquals(WalkController.State.BLOCKED,timeout.state());assertFalse(timeout.forward());
    }
    @Test void followerDoesNotWalkBeforeFacingPathAndStopsOnArrival() {
        var walker=new WalkController();var step=new BlockPos(1,1,0);walker.start(List.of(step),0);
        assertFalse(walker.tick(new WalkController.Input(new Vec3(.5,1,.5),90,true,0),terrain(Set.of(),Set.of()),1000).forward());
        var done=walker.tick(new WalkController.Input(new Vec3(1.5,1,.5),-90,true,100),terrain(Set.of(),Set.of()),1000);
        assertEquals(WalkController.State.ARRIVED,done.state());assertFalse(done.forward());
    }
    @Test void physicalEdgeVetoIsRespectedBySearchAndFollower() {
        var t=new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1; }
            public boolean floor(BlockPos p) { return p.y()==0; }
            public boolean stepClear(Vec3 a,Vec3 b) { return false; }
            public boolean walkClear(Vec3 a,Vec3 b) { return false; }
        };
        var start=new BlockPos(0,1,0);var goal=new BlockPos(1,1,0);
        assertFalse(GroundPathfinder.find(t,start,goal,50,3).found());
        var walker=new WalkController();walker.start(List.of(goal),0);
        assertEquals(WalkController.State.BLOCKED,walker.tick(new WalkController.Input(new Vec3(.5,1,.5),-90,true,0),t,1000).state());
    }
    @Test void destinationObstructionInsideArrivalToleranceDoesNotReportArrival() {
        var walker=new WalkController();var goal=new BlockPos(1,1,0);walker.start(List.of(goal),0);
        var out=walker.tick(new WalkController.Input(new Vec3(1.4,1,.5),-90,true,0),terrain(Set.of(goal),Set.of()),1000);
        assertEquals(WalkController.State.BLOCKED,out.state());assertFalse(out.forward()||out.jump());
    }
    @Test void exactEndpointPoseIsCheckedBeforeArrival() {
        var t=new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p) { return p.y()>=1; }
            public boolean floor(BlockPos p) { return p.y()==0; }
            public boolean standingClear(Vec3 p) { return p.x()<1.7; }
        };
        var walker=new WalkController();walker.start(List.of(new BlockPos(1,1,0)),new Vec3(1.8,1,.5),0);
        assertEquals(WalkController.State.BLOCKED,walker.tick(new WalkController.Input(new Vec3(1.6,1,.5),-90,true,0),t,1000).state());
    }
    @Test void nearbyWaypointDoesNotSkipToObstructedShortcutFromActualPose() {
        var stone=new BlockPos(1,70,1);
        var t=new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p){return p.y()>=70&&!p.equals(stone);}
            public boolean floor(BlockPos p){return p.y()==69||p.equals(stone);}
            public boolean standingClear(Vec3 p){return p.y()==70&&!(p.x()+.3>1&&p.x()-.3<2&&p.z()+.3>1&&p.z()-.3<2);}
            public boolean walkClear(Vec3 a,Vec3 b){
                for(int i=0;i<=100;i++)if(!standingClear(TravelPath.lerp(a,b,i/100d)))return false;
                return true;
            }
            public boolean stepClear(Vec3 a,Vec3 b){return walkClear(a,b);}
        };
        var goal=new Vec3(1.3579091401550278,70,.5);
        var walker=new WalkController(()->4098L);
        walker.start(java.util.stream.IntStream.iterate(6,x->x-1).limit(6).mapToObj(x->new BlockPos(x,70,0)).toList(),goal,0);
        walker.tick(new WalkController.Input(new Vec3(7.357909140155028,70,.5),0,true,0),t,4000);
        walker.tick(new WalkController.Input(new Vec3(5.857909140155028,70,.7509180300043539),90,true,25),t,4000);
        walker.tick(new WalkController.Input(new Vec3(4.357909140155028,70,1.0018360600087077),90,true,50),t,4000);
        // Native positive-offset trace: .15 from the safe penultimate waypoint, but the
        // direct edge to the exact anchor crosses the unchanged stone with the body's radius.
        var actual=new Vec3(2.9352937674031154,70,.8620565547661873);
        assertTrue(t.standingClear(actual));assertFalse(t.walkClear(actual,goal));
        var out=walker.tick(new WalkController.Input(actual,100.85027f,true,100),t,4000);
        assertEquals(WalkController.State.WALKING,out.state(),walker.diagnostics());
        assertTrue(walker.diagnostics().contains("index=2"),walker.diagnostics());
        // Reaching the course point makes the final segment physically clear; normal travel resumes.
        var center=new Vec3(2.8579091401550283,70,.75091803);
        assertTrue(t.walkClear(center,goal));
        assertEquals(WalkController.State.WALKING,walker.tick(new WalkController.Input(center,110,true,200),t,4000).state());
        assertTrue(walker.diagnostics().contains("index=3"),walker.diagnostics());
    }
}
