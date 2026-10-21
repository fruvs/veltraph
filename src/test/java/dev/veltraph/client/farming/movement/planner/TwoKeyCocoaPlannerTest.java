package dev.veltraph.client.farming.movement.planner;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TwoKeyCocoaPlannerTest {
    static TwoKeyCocoaPlanner.Facts facts(double x,double z,double speed,boolean crop,long now){return new TwoKeyCocoaPlanner.Facts(true,true,true,true,new Vec3(x,68.375,z),speed,crop,now);}
    @Test void twoAxisStallAfterRealProgressReversesOnceWithoutHoldingOppositeKeys(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.LEFT,new Vec3(0,68.375,0),0);
        for(int i=1;i<=5;i++)assertTrue(p.tick(facts(i*.2,0,.1,true,i*50)).left());
        for(int i=6;i<12;i++){var out=p.tick(facts(1,0,0,true,i*50));assertTrue(out.left());assertFalse(out.right());}
        var end=p.tick(facts(1,0,0,true,600));assertEquals(1,p.completedEnds());assertFalse(end.left());assertFalse(end.right());assertFalse(end.attack());
        assertTrue(p.tick(facts(1,0,.1,true,650)).right());assertEquals(1,p.completedEnds());
    }
    @Test void wallSlidingProgressDoesNotCountAsAnEnd(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.RIGHT,new Vec3(3.7,68.375,0),0);
        for(int i=1;i<30;i++){var out=p.tick(facts(3.7,i*.03,.03,true,i*50));assertTrue(out.right());}
        assertEquals(0,p.completedEnds());
    }
    @Test void noTraversalOrUnsupportedUnloadedGroundStopsRatherThanInventingRowTransfer(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.LEFT,new Vec3(0,68.375,0),0);
        p.tick(facts(0,0,0,true,50));var out=p.tick(facts(0,0,0,true,400));assertEquals(TwoKeyCocoaPlanner.State.BLOCKED,out.state());
        p.start(LaneSide.LEFT,new Vec3(0,68.375,0),0);
        out=p.tick(new TwoKeyCocoaPlanner.Facts(false,true,true,true,new Vec3(0,68.375,0),.1,true,50));assertFalse(out.left());assertFalse(out.attack());
    }
    @Test void logTargetAlignmentLossAndOffGroundReleaseAttackAndMovement(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.RIGHT,new Vec3(0,68.375,0),0);
        assertFalse(p.tick(facts(.2,0,.1,false,50)).attack());
        var out=p.tick(new TwoKeyCocoaPlanner.Facts(true,true,false,true,new Vec3(.2,68.4,0),.1,true,100));assertFalse(out.right());assertFalse(out.attack());
        out=p.tick(new TwoKeyCocoaPlanner.Facts(true,true,true,false,new Vec3(.2,68.375,0),.1,true,150));assertFalse(out.right());assertFalse(out.attack());
    }
    @Test void manualStopDoesNotContinueLastStrafe(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.RIGHT,new Vec3(0,68.375,0),0);p.tick(facts(.2,0,.1,true,50));p.stop();
        var out=p.tick(facts(.3,0,.1,true,100));assertFalse(out.right());assertFalse(out.attack());assertEquals(TwoKeyCocoaPlanner.State.STOPPED,out.state());
    }
    @Test void nativeHarvestPauseIsNotAnEndAndResumesTheSameKey(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.RIGHT,new Vec3(0,68.375,0),0);
        p.tick(facts(1,0,.1,false,50));
        for(int i=2;i<=20;i++){
            var out=p.tick(new TwoKeyCocoaPlanner.Facts(true,true,true,true,new Vec3(1,68.375,0),0,true,true,i*50));
            assertTrue(out.attack());assertFalse(out.left());assertFalse(out.right());
        }
        assertEquals(0,p.completedEnds());assertEquals(LaneSide.RIGHT,p.side());
        var settling=p.tick(facts(1,0,0,false,1050));assertFalse(settling.right());assertFalse(settling.attack());
        assertTrue(p.tick(facts(1,0,0,false,1100)).right());
    }
    @Test void harvestPauseStillRejectsUnknownFloorAndTimesOutUnclearedTarget(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.LEFT,new Vec3(0,68.375,0),0);
        var out=p.tick(new TwoKeyCocoaPlanner.Facts(false,true,true,true,new Vec3(0,68.375,0),0,true,true,50));
        assertFalse(out.attack());assertEquals(TwoKeyCocoaPlanner.State.BLOCKED,out.state());
        p.start(LaneSide.LEFT,new Vec3(0,68.375,0),0);
        p.tick(new TwoKeyCocoaPlanner.Facts(true,true,true,true,new Vec3(0,68.375,0),0,true,true,50));
        out=p.tick(new TwoKeyCocoaPlanner.Facts(true,true,true,true,new Vec3(0,68.375,0),0,true,true,3100));
        assertEquals(TwoKeyCocoaPlanner.State.BLOCKED,out.state());assertFalse(out.attack());assertEquals(0,p.completedEnds());
    }
    @Test void freshNativeWitnessHoldsMotionUntilCachedRayRefreshWithoutAuthorizingAttack(){
        var p=new TwoKeyCocoaPlanner();p.start(LaneSide.RIGHT,new Vec3(0,68.375,0),0);
        p.tick(facts(1,0,.1,false,50));
        var hold=p.tick(new TwoKeyCocoaPlanner.Facts(true,true,true,true,new Vec3(1,68.375,0),0,false,true,100));
        assertFalse(hold.left());assertFalse(hold.right());assertFalse(hold.attack());assertEquals(0,p.completedEnds());
        var actual=p.tick(new TwoKeyCocoaPlanner.Facts(true,true,true,true,new Vec3(1,68.375,0),0,true,true,150));
        assertTrue(actual.attack());assertFalse(actual.right());
        assertFalse(p.tick(facts(1,0,0,false,200)).right());
        assertTrue(p.tick(facts(1,0,0,false,250)).right());
    }
}
