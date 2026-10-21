package dev.veltraph.client.combat.slayer;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombatCadenceTest {
    @Test void actualLandingTraceCannotBorrowFuturePlayerMotionAtStaleMobReceipt(){
        var eye=new Vec3(-8.740955236694045,71.62000000476837,1.8294552112311244);
        var ray=new Vec3(.5654350519180298,-.14701497554779053,.8115847706794739);
        // Actual zombie pick volume: vanilla Entity.getPickRadius bytecode returns zero;
        // the private server oracle's .15 allowance is not a production authorization volume.
        var low=new Vec3(-7.206494152545929,70.582763671875,3.799609363079071);
        var high=new Vec3(-6.606494128704071,72.53276371955872,4.399609386920929);
        var target=new Vec3(.07234700089611579,-.12736001845041756,.12150064379966352);
        var player=new Vec3(-.0054251010031549345,-.0784000015258789,.11282563918859793);
        assertTrue(CombatHitEnvelope.intersects(eye,ray,low,high,3));
        assertFalse(CombatHitEnvelope.observedCadence(eye,ray,low,high,target,player,3,3));
    }
    @Test void stationaryBodyAndOffCenterRayRemainValidWithinActualReach(){
        assertTrue(CombatHitEnvelope.observedCadence(new Vec3(0,1.6,0),new Vec3(.02,0,1),new Vec3(-.3,0,2),new Vec3(.3,2,2.6),new Vec3(0,0,0),new Vec3(0,0,0),3,3));
        assertFalse(CombatHitEnvelope.observedCadence(new Vec3(0,1.6,0),new Vec3(0,0,1),new Vec3(-.3,0,3.1),new Vec3(.3,2,3.5),new Vec3(0,0,0),new Vec3(0,0,0),3,3));
    }
}
