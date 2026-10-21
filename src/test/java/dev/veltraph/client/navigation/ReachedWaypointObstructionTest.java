package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact native D seed/course/body clearance replay; ordinary pure controller, no field injection. */
class ReachedWaypointObstructionTest {
    static final Vec3 START=new Vec3(.5,70,.5),GOAL=new Vec3(18.5,70,12.5);
    static final Vec3 ACTUAL=new Vec3(3.349380414650343,70,2.6131248882105247);
    static final Vec3 CENTER=new Vec3(3.3724272440940952,70,2.5156834581831817),NEXT=new Vec3(3.8191932947269733,70,2.8995884362879187);
    static final class Wall implements GroundPathfinder.Terrain {
        boolean placed;
        public boolean clear(BlockPos p){return p.y()>=70&&!(placed&&p.x()==4&&p.y()<=73&&p.z()>=-6&&p.z()<=19);}
        public boolean floor(BlockPos p){return p.y()==69;}
        public boolean standingClear(Vec3 p){return p.y()==70&&!(placed&&p.x()+.3>4&&p.x()-.3<5&&p.z()+.3>-6&&p.z()-.3<20);}
        public boolean walkClear(Vec3 a,Vec3 b){if(Math.abs(a.y()-b.y())>.05)return false;int samples=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));for(int i=0;i<=samples;i++)if(!standingClear(TravelPath.lerp(a,b,i/(double)samples)))return false;return true;}
        public boolean stepClear(Vec3 a,Vec3 b){return walkClear(a,b);}
    }
    static WalkController atPrecedingPoint(Wall terrain){
        var walker=new WalkController(2001L);walker.start(List.of(GroundPathfinder.node(GOAL)),GOAL,0,.14);
        var course=TravelPath.shapeOpen(START,List.of(GOAL),terrain::walkClear,2001L);
        assertEquals(CENTER,course.get(5));assertEquals(NEXT,course.get(6));
        var out=walker.tick(new WalkController.Input(START,-56.309933f,true,0),terrain,4000);
        for(int i=0;i<4;i++)out=walker.tick(new WalkController.Input(course.get(i),out.yaw(),true,50L*(i+1)),terrain,4000);
        assertEquals(WalkController.State.WALKING,out.state());return walker;
    }
    @Test void reachedSafeIntermediateRejectsNewlyBlockedContinuationWithoutSpinningOrWaitingForTimeout(){
        var t=new Wall();var walker=atPrecedingPoint(t);t.placed=true;
        assertEquals(.10012985884931117,ACTUAL.distanceTo(CENTER),1e-12);
        assertTrue(t.standingClear(ACTUAL));assertTrue(t.standingClear(CENTER));assertFalse(t.standingClear(NEXT));
        assertFalse(t.walkClear(ACTUAL,NEXT));assertFalse(t.stepClear(ACTUAL,NEXT));
        var out=walker.tick(new WalkController.Input(ACTUAL,416.3806f,true,250),t,4000);
        assertEquals(WalkController.State.BLOCKED,out.state(),walker.diagnostics());
        assertFalse(out.forward()||out.jump());assertEquals(416.3806f,out.yaw(),0,"No new camera plan after physical obstruction");
        assertTrue(walker.diagnostics().contains("reached waypoint continuation obstructed"));
        assertEquals(WalkController.State.BLOCKED,walker.tick(new WalkController.Input(ACTUAL,416.3806f,true,300),t,4000).state());
    }
    @Test void unchangedSupportedContinuationStillAdvancesAndWalks(){
        var t=new Wall();var walker=atPrecedingPoint(t);assertTrue(t.walkClear(ACTUAL,NEXT));
        assertEquals(WalkController.State.WALKING,walker.tick(new WalkController.Input(ACTUAL,-56.3f,true,250),t,4000).state());
    }
}
