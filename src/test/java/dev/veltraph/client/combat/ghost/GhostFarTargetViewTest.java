package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostFarTargetViewTest {
    private static Point bearing(double yaw,double pitch,double distance){
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new Point(-Math.sin(y)*Math.cos(p)*distance,-Math.sin(p)*distance,Math.cos(y)*Math.cos(p)*distance);
    }
    private static GhostFarTargetView.Frame frame(long tick,float yaw,float pitch,Point target){return new GhostFarTargetView.Frame(tick,new Point(0,0,0),target,yaw,pitch);}
    private static float wrap(float v){float r=v%360;if(r>=180)r-=360;if(r< -180)r+=360;return r;}
    @Test void oppositeCapturedBearingTurnsNaturallyBeforeActualAcquisition(){
        var camera=new GhostFarTargetView();float yaw=77.08163f,pitch=15.42490f,priorRate=0;
        Point target=bearing(-111,2,20);boolean ready=false;
        for(int tick=0;tick<120;tick++){
            var actual=frame(tick,yaw,pitch,target);var plan=camera.advance(actual);assertTrue(plan.observed());
            assertEquals(plan.actualAngle()<=30,plan.acquisitionReady());
            float rate=wrap(plan.yaw()-yaw);assertTrue(Math.abs(rate)<=6.001);assertTrue(Math.abs(rate-priorRate)<=1.201);
            if(plan.acquisitionReady()){ready=true;assertTrue(tick>20);break;}
            yaw=plan.yaw();pitch=plan.pitch();priorRate=rate;
        }
        assertTrue(ready);
    }
    @Test void verticalErrorCountsEvenWithYawAlreadyAligned(){
        assertFalse(GhostFarTargetView.acquisitionReady(frame(0,0,-60,bearing(0,20,12))));
        assertEquals(80,GhostFarTargetView.angle(frame(0,0,-60,bearing(0,20,12))),.0001);
    }
    @Test void comfortableViewStaysOffCenterInsteadOfTrackingOneExactSpot(){
        var camera=new GhostFarTargetView();float yaw=12,pitch=-4;
        for(int tick=0;tick<100;tick++){
            var plan=camera.advance(frame(tick,yaw,pitch,bearing(18+(tick%9),-3,20)));
            assertTrue(plan.acquisitionReady());assertEquals(yaw,plan.yaw());assertEquals(pitch,plan.pitch());
        }
    }
    @Test void physicalMovingTargetStaysInsideOriginalFortyFiveDegreeMarginAcrossWrap(){
        var camera=new GhostFarTargetView();float yaw=170,pitch=0;
        for(int tick=0;tick<150;tick++){
            Point target=bearing(170+tick*.8,6*Math.sin(tick*.06),18);
            var actual=frame(tick,yaw,pitch,target);assertTrue(GhostFarTargetView.angle(actual)<45);
            var plan=camera.advance(actual);assertTrue(Math.abs(wrap(plan.yaw()-yaw))<=6.001);
            yaw=plan.yaw();pitch=plan.pitch();
        }
    }
    @Test void pathKeysRemainActualYawBasedWhenCameraFollowsDifferentTarget(){
        var path=new GhostSourceWalkSteering();var view=new GhostFarTargetView();
        var movement=path.advance(new GhostSourceWalkSteering.Frame(0,new Point(0,0,0),0,0,0,0,true,bearing(90,0,8),false,0));
        var camera=view.advance(frame(0,0,0,bearing(0,0,20)));
        assertTrue(movement.right());assertFalse(movement.forward());assertEquals(0,camera.yaw());assertTrue(movement.yaw()>0);
    }
    @Test void duplicateInvalidAndResetDoNotRepeatOrCarryViewMotion(){
        var view=new GhostFarTargetView();var f=frame(8,0,0,bearing(100,0,20));var first=view.advance(f);
        assertFalse(view.advance(f).observed());assertFalse(view.advance(frame(7,0,0,bearing(100,0,20))).observed());
        assertFalse(view.advance(frame(9,Float.NaN,0,bearing(100,0,20))).observed());
        assertFalse(view.advance(frame(9,0,0,new Point(0,0,0))).observed());
        view.reset();assertEquals(first,view.advance(f));
    }
}
