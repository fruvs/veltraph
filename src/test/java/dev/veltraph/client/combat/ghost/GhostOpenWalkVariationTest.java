package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Authored key-driven trajectory model; native caller acceptance remains a separate mandatory gate. */
class GhostOpenWalkVariationTest {
    static final Point START=new Point(.5,70,.5),GOAL=new Point(16.5,70,.5);
    static class Geometry implements GhostOpenWalkVariation.Geometry {
        boolean current=true;int width;boolean blockNext;
        Geometry(int width){this.width=width;}
        public boolean current(){return current;}
        public boolean supportedSweep(Point from,Point to){if(!current||Math.abs(from.y()-to.y())>.02)return false;
            int n=Math.max(1,(int)Math.ceil(from.distance(to)*10));for(int i=0;i<=n;i++){
                double t=i/(double)n,x=from.x()+(to.x()-from.x())*t,z=from.z()+(to.z()-from.z())*t;
                if(width>0&&(z-.32<0||z+.32>width))return false;if(blockNext&&x>=7.9&&x<=8.1)return false;
            }return true;
        }
    }
    @Test void repeatedOpenSeedsProduceDifferentSupportedCurvesWithOriginalEndpoint(){
        var routes=new HashSet<List<Point>>();for(long seed:new long[]{1,67,99999}){
            var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,GOAL,seed,t);
            assertTrue(plan.admitted());assertSame(edge,plan.edgeIdentity());assertEquals(GOAL,plan.cachedCurve().getLast());
            Point previous=START;double peak=0;for(var p:plan.cachedCurve()){assertTrue(t.supportedSweep(previous,p));previous=p;peak=Math.max(peak,Math.abs(p.z()-.5));if(p.x()<=2.5||p.x()>=14.5)assertEquals(.5,p.z(),1e-9);}assertTrue(peak>.4&&peak<1);routes.add(plan.cachedCurve());
        }assertEquals(3,routes.size());
    }
    @Test void sourceRoughFourToSixBlockEdgesCanVaryBeforeTheOriginalCompletionMargin(){
        for(int length:new int[]{4,5,6}){
            var routes=new HashSet<List<Point>>();Point goal=new Point(START.x()+length,START.y(),START.z());
            for(long seed:new long[]{1,67}){var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,goal,seed,t);assertTrue(plan.admitted());assertEquals(goal,plan.cachedCurve().getLast());
                double peak=0;Point previous=START;for(var p:plan.cachedCurve()){assertTrue(t.supportedSweep(previous,p));previous=p;peak=Math.max(peak,Math.abs(p.z()-.5));if(p.x()<=START.x()+1||p.x()>=goal.x()-1)assertEquals(.5,p.z(),1e-9);}assertTrue(peak>.18&&peak<.33);routes.add(plan.cachedCurve());
            }assertEquals(2,routes.size(),"Source rough edge must have reachable distinct variation");
        }
    }
    @Test void oneAndTwoBlockCorridorsRetainTheExactOriginalCursorPoint(){
        for(int width:new int[]{1,2})for(long seed:new long[]{1,67}){
            var t=new Geometry(width);var from=new Point(.5,70,width/2d);var to=new Point(16.5,70,width/2d);Object edge=new Object();
            var plan=GhostOpenWalkVariation.prepare(edge,from,to,seed,t);assertFalse(plan.admitted());
            var point=new Point(8,70,width/2d);assertEquals(new GhostOpenWalkVariation.Result(GhostOpenWalkVariation.Outcome.ORIGINAL,point),plan.adapt(edge,from,point,true,false,t));
        }
    }
    @Test void shortEdgesAndElevationKeepOriginalSteeringAndJump(){
        for(Point to:List.of(new Point(3.5,70,.5),new Point(16.5,71,.5))){var t=new Geometry(0);Object edge=new Object();var p=GhostOpenWalkVariation.prepare(edge,START,to,1,t);assertFalse(p.admitted());assertEquals(to,p.adapt(edge,START,to,true,true,t).point());}
    }
    @Test void continuousActualProgressDoesNotCreateWaypointBearingJumps(){
        var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,GOAL,67,t);
        Point a=plan.adapt(edge,new Point(8.999,70,.5),GOAL,true,false,t).point(),b=plan.adapt(edge,new Point(9.001,70,.5),GOAL,true,false,t).point();
        assertTrue(a.distance(b)<.003);assertTrue(a.x()<GOAL.x());
    }
    @Test void rawCursorPointRemainsAnUpperProgressBound(){
        var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,GOAL,67,t);Point bound=new Point(8,70,.5);
        var p=plan.adapt(edge,new Point(7.7,70,.5),bound,true,false,t).point();assertEquals(8,p.x(),1e-12);
    }
    @Test void dynamicObstructionRevokesInsteadOfSnappingBackToTheCenterline(){
        var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,GOAL,67,t);t.blockNext=true;
        assertEquals(GhostOpenWalkVariation.Outcome.REVOKED,plan.adapt(edge,new Point(7.5,70,.5),GOAL,true,false,t).outcome());
    }
    @Test void missingObservationOrForeignEdgeRevokesWithoutAnySteeringPoint(){
        var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,GOAL,67,t);
        assertNull(plan.adapt(new Object(),START,GOAL,true,false,t).point());t.current=false;assertNull(plan.adapt(edge,START,GOAL,true,false,t).point());
    }
    @Test void sourceJumpAirborneAndOffSegmentPointRemainOriginal(){
        var t=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,GOAL,67,t);
        assertEquals(GOAL,plan.adapt(edge,START,GOAL,true,true,t).point());assertEquals(GOAL,plan.adapt(edge,START,GOAL,false,false,t).point());
        var off=new Point(8,70,4);assertEquals(off,plan.adapt(edge,START,off,true,false,t).point());
    }
    @Test void oneCursorSeedPersistsAcrossRepeatedObservationsAndRevocationPreventsReuse(){
        Object cursor=new Object(),edge=new Object();var run=new GhostOpenWalkVariationRun(cursor,67);var t=new Geometry(0);
        var a=run.adapt(cursor,edge,START,GOAL,new Point(7,70,.5),GOAL,true,false,t);
        for(int i=0;i<10;i++)assertEquals(a,run.adapt(cursor,edge,START,GOAL,new Point(7,70,.5),GOAL,true,false,t));assertEquals(1,run.cachedEdgeCount());
        assertEquals(GhostOpenWalkVariation.Outcome.REVOKED,run.adapt(new Object(),edge,START,GOAL,START,GOAL,true,false,t).outcome());run.revoke();assertEquals(0,run.cachedEdgeCount());assertEquals(GhostOpenWalkVariation.Outcome.REVOKED,run.adapt(cursor,edge,START,GOAL,START,GOAL,true,false,t).outcome());
    }
    @Test void actualQ3KeyDrivenOpenTripsDifferAndReachTheSameGoalWithinBoundedHeading(){
        for(int length:new int[]{4,6,16}){
        Point goal=new Point(START.x()+length,START.y(),START.z());var traces=new HashSet<List<Point>>();for(long seed:new long[]{1,67}){
            var geometry=new Geometry(0);Object edge=new Object();var plan=GhostOpenWalkVariation.prepare(edge,START,goal,seed,geometry);var steering=new GhostSourceWalkSteering();
            Point p=START;float yaw=-90,lastRate=0;double vx=0,vz=0;var trace=new ArrayList<Point>();
            for(int tick=0;tick<500&&p.distance(goal)>.10;tick++){
                var selected=plan.adapt(edge,p,goal,true,false,geometry);assertEquals(GhostOpenWalkVariation.Outcome.VARIED,selected.outcome());
                var keys=steering.advance(new GhostSourceWalkSteering.Frame(tick,p,yaw,0,vx,vz,true,selected.point(),false,0));
                float rate=wrap(keys.yaw()-yaw);assertTrue(Math.abs(rate)<=6.001);assertTrue(Math.abs(rate-lastRate)<=1.201);lastRate=rate;
                int f=(keys.forward()?1:0)-(keys.back()?1:0),s=(keys.left()?1:0)-(keys.right()?1:0);double n=Math.hypot(f,s),angle=Math.toRadians(yaw);
                vx=n==0?0:(-Math.sin(angle)*f+Math.cos(angle)*s)/n*.13;vz=n==0?0:(Math.cos(angle)*f+Math.sin(angle)*s)/n*.13;
                var next=new Point(p.x()+vx,p.y(),p.z()+vz);assertTrue(geometry.supportedSweep(p,next));p=next;yaw=keys.yaw();trace.add(p);
            }
            assertTrue(p.distance(goal)<=.10,"Original destination not reached: "+p);assertTrue(trace.stream().anyMatch(q->Math.abs(q.z()-.5)>(length<7?.08:.2)));traces.add(trace);
        }assertEquals(2,traces.size());
        }
    }
    @Test void bothSideSupportIsNecessaryEvenIfTheRawSourceEdgeIsClear(){
        var t=new Geometry(0){public boolean supportedSweep(Point a,Point b){return super.supportedSweep(a,b)&&a.z()>=.1&&b.z()>=.1;}};Object edge=new Object();
        assertTrue(t.supportedSweep(START,GOAL));assertFalse(GhostOpenWalkVariation.prepare(edge,START,GOAL,1,t).admitted());
    }
    private static float wrap(float v){return (v%360+540)%360-180;}
}
