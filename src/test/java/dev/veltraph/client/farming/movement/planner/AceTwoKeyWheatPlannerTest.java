package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AceTwoKeyWheatPlannerTest {
    private AceTwoKeyWheatPlanner.Facts facts(Vec3 p,double speed,boolean ground,boolean safe,boolean travel,long now){
        return new AceTwoKeyWheatPlanner.Facts(p,speed,true,true,ground,ground,false,safe,travel,true,true,now);
    }
    @Test void firstAlignedStationaryFrameDrivesKeysBeforeAnyStallDetection(){
        var p=new AceTwoKeyWheatPlanner();p.start(LaneSide.RIGHT,new Vec3(0,4,0),0);
        var output=p.tick(facts(new Vec3(0,4,0),0,true,true,true,50));
        assertTrue(output.right());assertFalse(output.left());assertTrue(output.attack());
        assertEquals(0,p.completedEnds());
    }
    @Test void physicalEndRequiresActualTraversalAndConfirmedBlockedMotion(){
        var p=new AceTwoKeyWheatPlanner();p.start(LaneSide.RIGHT,new Vec3(0,4,0),0);
        p.tick(facts(new Vec3(0,4,0),0,true,true,true,50));
        p.tick(facts(new Vec3(0,4,3),.2,true,true,true,100));
        var first=p.tick(facts(new Vec3(0,4,3),0,true,true,false,150));
        assertFalse(first.right());assertEquals(0,p.completedEnds());
        var second=p.tick(facts(new Vec3(0,4,3),0,true,true,false,200));
        assertTrue(second.left());assertEquals(1,p.completedEnds());
    }
    @Test void boundedDropUsesOnlyOriginalStrafeInputAndIsCountedAtActualLanding(){
        var p=new AceTwoKeyWheatPlanner();p.start(LaneSide.RIGHT,new Vec3(0,4,0),0);
        p.tick(facts(new Vec3(0,4,0),0,true,true,true,50));
        var falling=p.tick(facts(new Vec3(0,2,1),.1,false,true,true,100));
        assertEquals(AceTwoKeyWheatPlanner.State.DROPPING,falling.state());
        assertFalse(falling.left());assertTrue(falling.right());assertTrue(falling.attack());
        assertEquals(0,p.observedDrops());
        p.tick(facts(new Vec3(0,1,1),0,true,true,true,150));assertEquals(1,p.observedDrops());
    }
    @Test void validatedFractionalGapDoesNotReleaseNativeInputBeforeReachingTheNextPlate(){
        var p=new AceTwoKeyWheatPlanner();p.start(LaneSide.RIGHT,new Vec3(0,1.375,0),0);
        p.tick(facts(new Vec3(0,1.375,0),0,true,true,true,50));
        var gap=new AceTwoKeyWheatPlanner.Facts(new Vec3(.35,1.375,.2),.2,true,true,
                false,true,false,true,true,true,false,100);
        assertTrue(p.tick(gap).right());assertEquals(0,p.completedEnds());
    }
    @Test void safeOriginalAirborneGuideKeepsAttackOnlyForAnActualWheatTarget(){
        var p=new AceTwoKeyWheatPlanner();p.start(LaneSide.RIGHT,new Vec3(0,4,0),0);
        p.tick(facts(new Vec3(0,4,0),0,true,true,true,50));
        var noCrop=new AceTwoKeyWheatPlanner.Facts(new Vec3(0,3,1),.1,true,true,
                false,false,false,true,true,true,false,100);
        assertFalse(p.tick(noCrop).attack());
        var unknown=new AceTwoKeyWheatPlanner.Facts(new Vec3(0,2,1),.1,false,true,
                false,false,false,true,true,true,true,150);
        var stop=p.tick(unknown);assertFalse(stop.attack());assertFalse(stop.right());
        assertEquals(AceTwoKeyWheatPlanner.State.BLOCKED,stop.state());
    }
    @Test void unknownLandingAndUnsupportedEntryFailClosed(){
        var p=new AceTwoKeyWheatPlanner();p.start(LaneSide.RIGHT,new Vec3(0,4,0),0);
        var output=p.tick(facts(new Vec3(0,2,0),0,false,false,true,50));
        assertEquals(AceTwoKeyWheatPlanner.State.BLOCKED,output.state());assertFalse(output.right());
        p.start(LaneSide.LEFT,new Vec3(0,4,0),100);
        assertEquals(AceTwoKeyWheatPlanner.State.BLOCKED,p.tick(facts(new Vec3(0,4,0),0,true,true,false,150)).state());
    }
}
