package dev.veltraph.client.combat.grind;

import dev.veltraph.client.navigation.WalkController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatCameraTest {
    @Test void combatPickupMenuAndRestartKeepOneContinuousNativeFrame(){
        var camera=new CombatCamera();float yaw=0,pitch=0,prior=0;
        for(int tick=0;tick<70;tick++){
            float requested=tick<15?180:tick<30?-90:tick<45?yaw:140;
            var next=camera.apply(tick,yaw,pitch,requested,0,1,1);
            float step=WalkController.wrap(next.yaw()-yaw);
            assertTrue(Math.abs(step)<=6.0001);
            assertTrue(Math.abs(step-prior)<=1.2001,"Stage handoff tick "+tick);
            assertEquals(next,camera.apply(tick,next.yaw(),next.pitch(),-180,85,1,1),"Child/outer cannot apply two frames");
            prior=step;yaw=next.yaw();pitch=next.pitch();
        }
    }
    @Test void accumulatedNativeYawRoundingCannotExceedTheActualAccelerationBound(){
        for(float initial:new float[]{2048,-2048,4096,-4096,8192,-8192}){
            var camera=new CombatCamera();float yaw=initial,pitch=0;double prior=0;
            for(int tick=0;tick<4000;tick++){
                float requested=yaw+(tick%91<45?170:-170);
                var next=camera.apply(tick,yaw,pitch,requested,0,1,1);
                double step=WalkController.wrap(next.yaw()-yaw);
                assertTrue(Math.abs(step)<=6.0001,"Native yaw rate at "+initial+" tick "+tick);
                assertTrue(Math.abs(step-prior)<=1.2001,"Native yaw acceleration at "+initial+" tick "+tick+": "+step+" after "+prior);
                prior=step;yaw=next.yaw();pitch=next.pitch();
            }
        }
    }
}
