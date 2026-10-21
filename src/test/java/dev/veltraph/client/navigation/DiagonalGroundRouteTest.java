package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagonalGroundRouteTest {
    private static GroundPathfinder.Terrain flat(Set<BlockPos> blocked,Set<BlockPos> missing) {
        return new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p){return p.y()>=1&&p.y()<3&&!blocked.contains(p);}
            public boolean floor(BlockPos p){return p.y()==0&&!missing.contains(p);}
        };
    }
    @Test void openGridUsesPhysicalDiagonalsInsteadOfACardinalStaircase() {
        var t=flat(Set.of(),Set.of());var start=new BlockPos(0,1,0);var goal=new BlockPos(10,1,10);
        var result=GroundPathfinder.find(t,start,goal,2500,32);
        assertTrue(result.found(),result.reason());assertEquals(10,result.steps().size());
        var previous=start;
        for(var next:result.steps()){assertEquals(1,next.x()-previous.x());assertEquals(1,next.z()-previous.z());assertTrue(t.walkClear(t.position(previous),t.position(next)));previous=next;}
    }
    @Test void directDiagonalRequiresBothCornerCellsAndTheSweptPhysicalBody() {
        var from=new BlockPos(0,1,0);var to=new BlockPos(1,1,1);
        assertTrue(GroundPathfinder.canStep(flat(Set.of(),Set.of()),from,to));
        for(var corner:List.of(new BlockPos(1,1,0),new BlockPos(0,1,1))) {
            assertFalse(GroundPathfinder.canStep(flat(Set.of(corner),Set.of()),from,to));
            assertFalse(GroundPathfinder.canStep(flat(Set.of(),Set.of(corner.down())),from,to));
        }
        var veto=new GroundPathfinder.Terrain(){public boolean clear(BlockPos p){return p.y()>=1;}public boolean floor(BlockPos p){return p.y()==0;}public boolean walkClear(Vec3 a,Vec3 b){return false;}};
        assertFalse(GroundPathfinder.canStep(veto,from,to));
    }
    @Test void physicalStepPolicyCanIndependentlyVetoADiagonalEvenWhenSweepIsClear() {
        var t=new GroundPathfinder.Terrain(){public boolean clear(BlockPos p){return p.y()>=1;}public boolean floor(BlockPos p){return p.y()==0;}public boolean stepClear(Vec3 a,Vec3 b){return false;}};
        assertTrue(t.walkClear(new Vec3(.5,1,.5),new Vec3(1.5,1,1.5)));
        assertFalse(GroundPathfinder.canStep(t,new BlockPos(0,1,0),new BlockPos(1,1,1)));
    }
    @Test void diagonalHeightChangesRemainDiscreteCardinalSteps() {
        var t=flat(Set.of(),Set.of());assertFalse(GroundPathfinder.canStep(t,new BlockPos(0,1,0),new BlockPos(1,2,1)));
        assertFalse(GroundPathfinder.canStep(t,new BlockPos(0,2,0),new BlockPos(1,1,1)));
    }
    @Test void narrowTwoHighPassageDoesNotGainSidewaysEdgesOrRandomBends() {
        var t=new GroundPathfinder.Terrain(){public boolean clear(BlockPos p){return p.z()==0&&p.y()>=1&&p.y()<3;}public boolean floor(BlockPos p){return p.y()==0&&p.z()==0;}};
        var result=GroundPathfinder.find(t,new BlockPos(0,1,0),new BlockPos(14,1,0),2500,32);assertTrue(result.found());
        for(var step:result.steps())assertEquals(0,step.z());
        for(long seed:new long[]{1,9999})for(var p:TravelPath.shapeOpen(new Vec3(.5,1,.5),result.steps().stream().map(t::position).toList(),t::walkClear,seed))assertEquals(.5,p.z(),1e-9);
    }
    @Test void diagonalSearchStillHonorsTheOriginalTotalNodeBudget() {
        var result=GroundPathfinder.findLevelFirst(flat(Set.of(),Set.of()),new BlockPos(0,1,0),new BlockPos(20,1,20),3,32);
        assertFalse(result.found());assertTrue(result.visited()<=3);
    }
    @Test void shapedDiagonalTripsHaveDistinctSupportedTrajectoriesAndExactDestination() {
        var t=flat(Set.of(),Set.of());var start=new Vec3(.5,1,.5);var goal=new Vec3(14.5,1,10.5);
        var result=GroundPathfinder.find(t,GroundPathfinder.node(start),GroundPathfinder.node(goal),2500,32);assertTrue(result.found());
        var trajectories=new HashSet<List<Vec3>>();
        for(long seed:new long[]{1,14,67}){
            var walker=new WalkController(()->seed);walker.start(result.steps(),goal,0,.14);
            Vec3 p=start;float yaw=(float)Math.toDegrees(Math.atan2(-(goal.x()-p.x()),goal.z()-p.z())),rate=0;var trace=new ArrayList<Vec3>();WalkController.Output out=null;
            for(int tick=0;tick<400;tick++){
                out=walker.tick(new WalkController.Input(p,yaw,true,tick*50L),t,4000);
                float nextRate=WalkController.wrap(out.yaw()-yaw);assertTrue(Math.abs(nextRate-rate)<=1.201);rate=nextRate;yaw=out.yaw();
                if(out.state()!=WalkController.State.WALKING)break;
                if(out.forward()){double angle=Math.toRadians(yaw);var next=new Vec3(p.x()-Math.sin(angle)*.17,1,p.z()+Math.cos(angle)*.17);assertTrue(t.walkClear(p,next));p=next;}
                trace.add(p);
            }
            assertEquals(WalkController.State.ARRIVED,out.state(),walker.diagnostics());assertTrue(p.distanceTo(goal)<.14);trajectories.add(trace);
        }
        assertEquals(3,trajectories.size());
    }
}
