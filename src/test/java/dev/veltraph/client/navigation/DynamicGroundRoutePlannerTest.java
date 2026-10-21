package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicGroundRoutePlannerTest {
    private static final Vec3 START=new Vec3(.5,1,.5),GOAL=new Vec3(30.5,1,.5);
    private static GroundPathfinder.Terrain flat(int width,Set<BlockPos> blocked,Set<BlockPos> absent) {
        return new GroundPathfinder.Terrain() {
            public boolean clear(BlockPos p){return p.y()>=1&&p.y()<3&&(width==0||p.z()>=0&&p.z()<width)&&!blocked.contains(p);}
            public boolean floor(BlockPos p){return p.y()==0&&!absent.contains(p);}
        };
    }
    private static DynamicGroundRoutePlanner.Geometry geometry(GroundPathfinder.Terrain terrain){return new DynamicGroundRoutePlanner.Geometry(){
        public boolean current(){return true;}
        public boolean standing(Vec3 p){return terrain.standingClear(p);}
        public boolean sweep(Vec3 a,Vec3 b){return terrain.walkClear(a,b);}
    };}
    private static DynamicGroundRoutePlanner.Plan plan(Vec3 start,Vec3 goal,GroundPathfinder.Terrain terrain,long ordinal){
        var path=GroundPathfinder.find(terrain,GroundPathfinder.node(start),GroundPathfinder.node(goal),2500,32);assertTrue(path.found(),path.reason());
        var nodes=new ArrayList<>(path.steps().stream().map(terrain::position).toList());nodes.add(goal);
        return DynamicGroundRoutePlanner.plan(start,nodes,77,ordinal,geometry(terrain));
    }
    @Test void fourOrdinaryJourneysHaveBroadPairwiseDistinctMidpointsAndTheSameExactGoal(){
        var t=flat(0,Set.of(),Set.of());var midpoints=new ArrayList<Double>();
        for(int run=0;run<4;run++){
            var p=plan(START,GOAL,t,run);assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,p.outcome());
            assertEquals(GOAL,p.course().getLast());Vec3 previous=START;
            for(var point:p.course()){assertTrue(t.walkClear(previous,point));previous=point;}
            midpoints.add(p.course().stream().min(Comparator.comparingDouble(v->Math.abs(v.x()-15.5))).orElseThrow().z());
        }
        for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)assertTrue(Math.abs(midpoints.get(i)-midpoints.get(j))>1.4,"merely tiny bends: "+midpoints);
    }
    @Test void fifthJourneyIsNotAReplayOfItsEarlierBand(){
        var t=flat(0,Set.of(),Set.of());assertNotEquals(plan(START,GOAL,t,0).course(),plan(START,GOAL,t,4).course());
    }
    @Test void mixedHeightJourneyBroadensOnlyLevelWindowsAndRetainsOriginalStepEndpoints(){
        var low=new Vec3(20.5,1,.5);var step=new Vec3(21.5,2,.5);var goal=new Vec3(44.5,2,.5);
        var g=new DynamicGroundRoutePlanner.Geometry(){public boolean current(){return true;}public boolean standing(Vec3 p){return true;}public boolean sweep(Vec3 a,Vec3 b){return a.y()==b.y();}};
        var result=DynamicGroundRoutePlanner.planWindows(START,List.of(low,step,goal),77,0,g);
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,result.outcome());assertTrue(result.course().contains(low));assertTrue(result.course().contains(step));
        assertEquals(step,result.course().get(result.course().indexOf(low)+1));assertEquals(goal,result.course().getLast());
        assertTrue(result.course().stream().filter(p->p.y()==1).anyMatch(p->Math.abs(p.z()-.5)>1));
        assertTrue(result.course().stream().filter(p->p.y()==2).anyMatch(p->Math.abs(p.z()-.5)>1));
    }
    @Test void longJourneyUsesMultipleFiniteLevelWindowsWithoutChangingTheDestination(){
        var nodes=new ArrayList<Vec3>();for(int x=1;x<=90;x++)nodes.add(new Vec3(x+.5,1,.5));
        var t=flat(0,Set.of(),Set.of());var result=DynamicGroundRoutePlanner.planWindows(START,nodes,77,0,geometry(t));
        assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,result.outcome());assertEquals(nodes.getLast(),result.course().getLast());
        Vec3 previous=START;for(var p:result.course()){assertTrue(t.walkClear(previous,p));previous=p;}assertTrue(result.course().stream().anyMatch(p->Math.abs(p.z()-.5)>1));
    }
    @Test void narrowOneAndTwoBlockPassagesRetainTheirOriginalDeterministicCenterline(){
        for(int width:new int[]{1,2}){
            var t=flat(width,Set.of(),Set.of());var start=new Vec3(.5,1,width/2d);var goal=new Vec3(30.5,1,width/2d);
            for(int run=0;run<4;run++){var p=plan(start,goal,t,run);assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,p.outcome());assertEquals(plan(start,goal,t,0).course(),p.course());}
        }
    }
    @Test void missingSupportAndUnknownSideTerrainNeverPermitAnUnprovedDetour(){
        var missing=new HashSet<BlockPos>();for(int x=-5;x<=36;x++)for(int z=-6;z<=6;z++)if(z!=0)missing.add(new BlockPos(x,0,z));
        var t=flat(0,Set.of(),missing);for(int run=0;run<4;run++)assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,plan(START,GOAL,t,run).outcome());
    }
    @Test void centralObstacleAllowsDistinctProvedSidesWithoutImportingOrRerunningAStar(){
        var block=new HashSet<BlockPos>();for(int x=13;x<=17;x++)for(int z=-1;z<=1;z++)for(int y=1;y<=2;y++)block.add(new BlockPos(x,y,z));
        var t=flat(0,block,Set.of());boolean above=false,below=false;
        for(int run=0;run<4;run++){
            var p=plan(START,GOAL,t,run);assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,p.outcome());
            Vec3 middle=p.course().stream().min(Comparator.comparingDouble(v->Math.abs(v.x()-15.5))).orElseThrow();above|=middle.z()>2;below|=middle.z()<-2;
            Vec3 previous=START;for(var v:p.course()){assertTrue(t.walkClear(previous,v));previous=v;}
        }
        assertTrue(above&&below,"Only the original obstacle side was used");
    }
    @Test void contextRevocationDuringProofCannotPublishPartialRouteOrFallback(){
        var calls=new int[1];var t=flat(0,Set.of(),Set.of());var g=new DynamicGroundRoutePlanner.Geometry(){
            public boolean current(){return calls[0]<6;}
            public boolean standing(Vec3 p){calls[0]++;return t.standingClear(p);}
            public boolean sweep(Vec3 a,Vec3 b){calls[0]++;return t.walkClear(a,b);}
        };
        var p=DynamicGroundRoutePlanner.plan(START,List.of(GOAL),77,0,g);assertEquals(DynamicGroundRoutePlanner.Outcome.REVOKED,p.outcome());assertTrue(p.course().isEmpty());
    }
    @Test void proofBudgetExhaustionRetainsTheOriginalRouteRatherThanInventingPermission(){
        var original=List.of(GOAL);var g=new DynamicGroundRoutePlanner.Geometry(){public boolean current(){return true;}public boolean standing(Vec3 p){return false;}public boolean sweep(Vec3 a,Vec3 b){return false;}};
        var p=DynamicGroundRoutePlanner.plan(START,original,77,0,g);assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,p.outcome());assertEquals(original,p.course());
    }
    @Test void fractionalShortAndHeightChangingDestinationsArePreserved(){
        var g=geometry(flat(0,Set.of(),Set.of()));for(var goal:List.of(new Vec3(5.137,1,.62),new Vec3(30.5,2,.5),new Vec3(80.5,1,.5))){
            var p=DynamicGroundRoutePlanner.plan(START,List.of(goal),77,0,g);assertEquals(DynamicGroundRoutePlanner.Outcome.ORIGINAL,p.outcome());assertEquals(goal,p.course().getLast());
        }
    }
    @Test void finiteAdmissionAndCourseBoundsFailClosed(){
        var g=geometry(flat(0,Set.of(),Set.of()));for(var invalid:List.of(List.of(new Vec3(Double.NaN,1,0)),List.<Vec3>of(),Collections.nCopies(6001,GOAL)))
            assertEquals(DynamicGroundRoutePlanner.Outcome.REVOKED,DynamicGroundRoutePlanner.plan(START,invalid,77,0,g).outcome());
        var walker=new WalkController();assertThrows(IllegalArgumentException.class,()->walker.startCourse(List.of(),START,0,.14));
    }
    @Test void realFollowerInputsCompleteFourBroadTrajectoriesWithBoundedYawAndNoPerTickRandom(){
        var t=flat(0,Set.of(),Set.of());var midpoints=new ArrayList<Double>();
        for(int run=0;run<4;run++){
            var p=plan(START,GOAL,t,run);int[] draws={0};var walker=new WalkController(()->{draws[0]++;return 4;});walker.startCourse(p.course(),START,0,.14);
            Vec3 position=START;float yaw=-90,lastRate=0;WalkController.Output out=null;double middle=Double.NaN,nearest=Double.POSITIVE_INFINITY;
            for(int tick=0;tick<500;tick++){
                out=walker.tick(new WalkController.Input(position,yaw,true,tick*50L),t,4000);float rate=WalkController.wrap(out.yaw()-yaw);
                assertTrue(Math.abs(rate)<=6.001);assertTrue(Math.abs(rate-lastRate)<=1.201);lastRate=rate;yaw=out.yaw();
                if(Math.abs(position.x()-15.5)<nearest){nearest=Math.abs(position.x()-15.5);middle=position.z();}
                if(out.state()!=WalkController.State.WALKING)break;
                if(out.forward()){double a=Math.toRadians(yaw);var next=new Vec3(position.x()-Math.sin(a)*.17,1,position.z()+Math.cos(a)*.17);assertTrue(t.walkClear(position,next));position=next;}
            }
            assertEquals(WalkController.State.ARRIVED,out.state(),walker.diagnostics());assertTrue(position.distanceTo(GOAL)<.14);assertFalse(out.forward()||out.jump());assertEquals(0,draws[0]);midpoints.add(middle);
        }
        for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)assertTrue(Math.abs(midpoints.get(i)-midpoints.get(j))>1.4,"follower erased variety "+midpoints);
    }
    @Test void changedHazardStopsTheCachedCourseAndResetReleasesIt(){
        var blocked=new HashSet<BlockPos>();var t=flat(0,blocked,Set.of());var p=plan(START,GOAL,t,0);var walker=new WalkController();walker.startCourse(p.course(),START,0,.14);
        for(int x=0;x<3;x++)for(int z=-5;z<6;z++)for(int y=1;y<3;y++)blocked.add(new BlockPos(x,y,z));
        var out=walker.tick(new WalkController.Input(START,-90,true,50),t,4000);assertEquals(WalkController.State.BLOCKED,out.state());assertFalse(out.forward()||out.jump());
        walker.reset();assertEquals(WalkController.State.IDLE,walker.state());
    }
}
