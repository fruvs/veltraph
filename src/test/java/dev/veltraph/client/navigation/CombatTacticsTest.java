package dev.veltraph.client.navigation;

import dev.veltraph.client.combat.grind.*;
import dev.veltraph.client.combat.slayer.SlayerSignals;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatTacticsTest {
    @Test void aCloserOccludingMobEncouragesLateralExposureOfTheSelectedTarget() {
        var c=new CombatMovement();c.reset();
        var keys=c.choose(new Vec3(0,1,0),new Vec3(0,1,3),0,2.35,false,false,p->true,List.of(new Vec3(0,1,1)));
        assertTrue(keys.left()||keys.right());assertFalse(keys.forward());
    }
    @Test void behaviorHandoffsCannotAbruptlyReverseAngularVelocity() {
        var limiter=new AngularRateLimiter();float yaw=0,previousStep=0;
        for(int i=0;i<80;i++) {
            float next=limiter.apply(yaw,yaw+(i%20<10?6:-6),1),step=next-yaw;
            assertTrue(Math.abs(step-previousStep)<1.201);assertTrue(Math.abs(step)<=6.001);
            previousStep=step;yaw=next;
        }
    }
    @Test void aimLeavesAnOffCenterVisibleBossAlone() {
        var aim=new CombatAim();var result=aim.step(0,0,12,8,18,15,1);
        assertEquals(0,result.yaw());assertEquals(0,result.pitch());
    }
    @Test void closeAimUsesHitWindowInsteadOfLockingCenter() {
        var aim=new CombatAim();float yaw=0,pitch=0;
        for(int i=0;i<120;i++) {var a=aim.step(yaw,pitch,20,15,4,6,1);assertTrue(Math.abs(a.yaw()-yaw)<=6.001);yaw=a.yaw();pitch=a.pitch();}
        assertTrue(yaw>=15.9&&yaw<=17);assertTrue(pitch>=8.9&&pitch<=10);
    }
    @Test void followsAnOrbitWithoutFallingOutsideTheHitVolume() {
        var aim=new CombatAim();float yaw=0;
        for(int tick=0;tick<100;tick++) {
            float target=tick*4.5f;
            var a=aim.step(yaw,0,target,0,3,5,1);yaw=a.yaw();
            if(tick>25) assertTrue(Math.abs(WalkController.wrap(target-yaw))<8,"lost moving hit volume at "+tick);
        }
    }
    @Test void targetReversalDoesNotSnap() {
        var aim=new CombatAim();float yaw=0;
        for(int i=0;i<20;i++) {var a=aim.step(yaw,0,i<10?120:-120,0,0,0,1);assertTrue(Math.abs(WalkController.wrap(a.yaw()-yaw))<=6.001);yaw=a.yaw();}
    }
    @Test void allBlockedDirectionsReleaseAndRetreatMovesAway() {
        var move=new CombatMovement();move.reset();
        Vec3 p=new Vec3(0,1,2),boss=new Vec3(0,1,0);
        var keys=move.choose(p,boss,180,8,true,false,n->true,List.of());assertTrue(keys.back());
        keys=move.choose(p,boss,180,8,true,false,n->false,List.of());assertFalse(keys.forward()||keys.back()||keys.left()||keys.right());
    }
    @Test void preciseGoalCountersMomentumRatherThanChoosingArbitraryMovement() {
        var c=new CombatMovement();c.reset();var p=new Vec3(.5,1,.5);
        var keys=c.choose(p,p,0,0,true,false,n->true,List.of(),new Vec3(0,0,.18));
        assertTrue(keys.back());assertFalse(keys.forward()||keys.left()||keys.right());
    }
    @Test void labelsAreScopedToBossFamilyAndDifferentMechanicsHaveDifferentResponses() {
        assertEquals(SlayerSignals.Response.RETREAT,SlayerSignals.response(SlayerSignals.Family.REVENANT,"§cBoom!"));
        assertEquals(SlayerSignals.Response.NONE,SlayerSignals.response(SlayerSignals.Family.SPIDER,"Boom!"));
        assertEquals(SlayerSignals.Response.SAFE_FLOOR,SlayerSignals.response(SlayerSignals.Family.VAMPIRE,"MANIA 2s"));
        assertEquals(SlayerSignals.Response.DODGE,SlayerSignals.response(SlayerSignals.Family.VAMPIRE,"TWINCLAWS 0.5s"));
        assertEquals(SlayerSignals.Response.PILLAR,SlayerSignals.response(SlayerSignals.Family.BLAZE,"5s 8 hits"));
        assertEquals(SlayerSignals.Response.EGGS,SlayerSignals.response(SlayerSignals.Family.SPIDER,"SHOOT ME!"));
        assertEquals(SlayerSignals.Response.GLYPH,SlayerSignals.response(SlayerSignals.Family.ENDERMAN,"Yang Glyph"));
    }
}
