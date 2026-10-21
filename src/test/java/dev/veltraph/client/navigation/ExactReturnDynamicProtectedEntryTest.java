package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production pure-planner profiles; these model tests do not establish native authority. */
class ExactReturnDynamicProtectedEntryTest {
    private static final Vec3 START=new Vec3(.5,1,.5),GOAL=new Vec3(30.5,1,.5);
    private static GroundPathfinder.Terrain flat(int width,Set<BlockPos> blocked,Set<BlockPos> absent){
        return new GroundPathfinder.Terrain(){
            public boolean clear(BlockPos p){return p.y()>=1&&p.y()<3&&(width==0||p.z()>=0&&p.z()<width)&&!blocked.contains(p);}
            public boolean floor(BlockPos p){return p.y()==0&&!absent.contains(p);}
        };
    }
    private static DynamicGroundRoutePlanner.Geometry geometry(GroundPathfinder.Terrain terrain){
        return new DynamicGroundRoutePlanner.Geometry(){
            public boolean current(){return true;}
            public boolean standing(Vec3 p){return terrain.standingClear(p);}
            public boolean sweep(Vec3 a,Vec3 b){return terrain.walkClear(a,b);}
        };
    }
    private static List<Vec3> admitted(Vec3 start,Vec3 goal,GroundPathfinder.Terrain terrain){
        var path=GroundPathfinder.find(terrain,GroundPathfinder.node(start),GroundPathfinder.node(goal),2500,32);
        assertTrue(path.found(),path.reason());
        var result=new ArrayList<>(path.steps().stream().map(terrain::position).toList());
        if(!result.isEmpty()&&result.getLast().distanceTo(goal)<.001)result.removeLast();
        result.add(goal);return List.copyOf(result);
    }
    private static DynamicGroundRoutePlanner.Plan plan(List<Vec3> nodes,long ordinal,DynamicGroundRoutePlanner.Geometry geometry){
        return DynamicGroundRoutePlanner.planExactReturnWindows(START,nodes,77,ordinal,geometry);
    }
    private static void originalFinalThree(List<Vec3> nodes,DynamicGroundRoutePlanner.Plan plan){
        var tail=nodes.subList(nodes.size()-3,nodes.size());
        assertEquals(tail,plan.course().subList(plan.course().size()-3,plan.course().size()));
        for(int i=plan.course().size()-3;i<plan.course().size();i++)assertFalse(plan.broadEdges().get(i));
        assertSame(nodes.getLast(),plan.course().getLast(),"canonical final target identity retained");
    }
    @Test void openReturnsVaryBroadApproachesAndRetainOriginalStraightFinalRun(){
        var terrain=flat(0,Set.of(),Set.of());var nodes=admitted(START,GOAL,terrain);var courses=new ArrayList<List<Vec3>>();
        for(int run=0;run<4;run++){
            var p=plan(nodes,run,geometry(terrain));assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,p.outcome());originalFinalThree(nodes,p);
            Vec3 previous=START;for(var point:p.course()){assertTrue(terrain.walkClear(previous,point));previous=point;}
            courses.add(p.course());
        }
        for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)assertNotEquals(courses.get(i),courses.get(j));
    }
    @Test void laterJourneyDoesNotReplayItsEarlierBroadBand(){
        var terrain=flat(0,Set.of(),Set.of());var nodes=admitted(START,GOAL,terrain);
        assertNotEquals(plan(nodes,0,geometry(terrain)).course(),plan(nodes,4,geometry(terrain)).course());
    }
    @Test void oneAndTwoBlockCorridorsRemainOnOriginalGraph(){
        for(int width:new int[]{1,2}){
            var terrain=flat(width,Set.of(),Set.of());var nodes=admitted(START,GOAL,terrain);
            for(int run=0;run<4;run++){var p=plan(nodes,run,geometry(terrain));assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,p.outcome());assertEquals(nodes,p.course());}
        }
    }
    @Test void obstacleApproachCanChooseOtherProvedSidesWithoutAnglingFinalEntry(){
        var blocks=new HashSet<BlockPos>();for(int x=13;x<=17;x++)for(int z=-1;z<=1;z++)for(int y=1;y<=2;y++)blocks.add(new BlockPos(x,y,z));
        var terrain=flat(0,blocks,Set.of());var nodes=admitted(START,GOAL,terrain);boolean above=false,below=false;
        for(int run=0;run<4;run++){
            var p=plan(nodes,run,geometry(terrain));assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,p.outcome());originalFinalThree(nodes,p);
            var middle=p.course().stream().min(Comparator.comparingDouble(v->Math.abs(v.x()-15.5))).orElseThrow();above|=middle.z()>2;below|=middle.z()<-2;
            Vec3 previous=START;for(var point:p.course()){assertTrue(terrain.walkClear(previous,point));previous=point;}
        }
        assertTrue(above&&below);
    }
    @Test void unavailableSideSupportDoesNotAdoptUnprovedApproach(){
        var missing=new HashSet<BlockPos>();for(int x=-5;x<=36;x++)for(int z=-6;z<=6;z++)if(z!=0)missing.add(new BlockPos(x,0,z));
        var terrain=flat(0,Set.of(),missing);var nodes=admitted(START,GOAL,terrain);
        assertEquals(nodes,plan(nodes,0,geometry(terrain)).course());
        assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,plan(nodes,0,geometry(terrain)).outcome());
    }
    @Test void unavailableGeometryRetainsOnlyOriginalAdmittedGraph(){
        var terrain=flat(0,Set.of(),Set.of());var nodes=admitted(START,GOAL,terrain);
        var unknown=new DynamicGroundRoutePlanner.Geometry(){public boolean current(){return true;}public boolean standing(Vec3 p){return false;}public boolean sweep(Vec3 a,Vec3 b){return false;}};
        var p=plan(nodes,0,unknown);assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,p.outcome());assertEquals(nodes,p.course());
    }
    @Test void gateMutationDuringGeometryRevokesWithoutOriginalFallback(){
        var terrain=flat(0,Set.of(),Set.of());var nodes=admitted(START,GOAL,terrain);boolean[] gate={true};
        var mutating=new DynamicGroundRoutePlanner.Geometry(){public boolean current(){return gate[0];}public boolean standing(Vec3 p){gate[0]=false;return true;}public boolean sweep(Vec3 a,Vec3 b){return true;}};
        var p=plan(nodes,0,mutating);assertEquals(DynamicGroundRoutePlanner.Outcome.REVOKED,p.outcome());assertTrue(p.course().isEmpty());
    }
    @Test void originalShortTerminalRunAtCornerIsPreservedWhole(){
        var terrain=flat(0,Set.of(),Set.of());Vec3 boundary=new Vec3(25.5,1,.5),goal=new Vec3(25.5,1,1.5);
        var nodes=new ArrayList<>(admitted(START,boundary,terrain));nodes.add(goal);
        var p=plan(nodes,0,geometry(terrain));assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,p.outcome());
        assertEquals(boundary,p.course().get(p.course().size()-2));assertSame(goal,p.course().getLast());assertFalse(p.broadEdges().getLast());
    }
    @Test void verticalAndDegenerateTerminalEdgesRemainOriginal(){
        var terrain=flat(0,Set.of(),Set.of());var nodes=new ArrayList<>(admitted(START,GOAL,terrain));
        for(var last:List.of(GOAL,new Vec3(GOAL.x(),2,GOAL.z()))){var route=new ArrayList<>(nodes);route.add(last);var p=plan(route,0,geometry(terrain));assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,p.outcome());assertEquals(route,p.course());}
    }
    @Test void invalidAndExcessiveInputsCannotYieldCourse(){
        var terrain=flat(0,Set.of(),Set.of());
        for(var nodes:List.of(List.<Vec3>of(),List.of(new Vec3(Double.NaN,1,0)),Collections.nCopies(6001,GOAL))){
            var p=plan(nodes,0,geometry(terrain));assertEquals(DynamicGroundRoutePlanner.Outcome.REVOKED,p.outcome());assertTrue(p.course().isEmpty());
        }
    }
}
