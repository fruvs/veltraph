package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure key/physics integration only; real native fluid/jump/custody is required separately. */
class FixedViewKeySteeringTest {
    private void integrate(Vec3 start,Vec3 goal,double drag,double acceleration,int budget,boolean xCorridor) {
        var position=start;double vx=0,vz=0;float yaw=-69.443955f;double r=Math.toRadians(yaw);
        int tick;
        for(tick=0;tick<budget;tick++) {
            var k=FixedViewKeySteering.toward(position,goal,yaw);
            boolean pulse=position.horizontalDistanceTo(goal)<.85&&tick%2==0;
            double forward=pulse?0:(k.forward()?1:0)-(k.back()?1:0);
            double right=pulse?0:(k.right()?1:0)-(k.left()?1:0);
            double norm=Math.max(1,Math.hypot(forward,right));
            vx=vx*drag+(-Math.sin(r)*forward-Math.cos(r)*right)/norm*acceleration;
            vz=vz*drag+(Math.cos(r)*forward-Math.sin(r)*right)/norm*acceleration;
            position=new Vec3(position.x()+vx,70,position.z()+vz);
            double drift=xCorridor?Math.abs(position.z()-start.z()):Math.abs(position.x()-start.x());
            assertTrue(drift<.1,"Oblique keys must not drift into enclosing farmland");
            assertTrue(Math.hypot(vx,vz)<.5,"Original native horizontal-step bound remains required");
            if(position.horizontalDistanceTo(goal)<.1)break;
        }
        assertTrue(tick<budget,"Normal key feedback must reach the original waypoint");
    }
    @Test void outwardWaterCourseFitsOriginalBoundAndPreservesColumn(){integrate(new Vec3(55.5,70,-44.5),new Vec3(55.5,70,46.5),.8,.02,1100,false);}
    @Test void inwardWaterCourseFitsOriginalBoundAndPreservesColumn(){integrate(new Vec3(55.5,70,46.5),new Vec3(55.5,70,-44.5),.8,.02,1100,false);}
    @Test void dryCorridorReachesNextOriginalLaneWithoutWorldAxisDrift(){integrate(new Vec3(50.5,70,-46.5),new Vec3(57.5,70,-46.5),.546,.1,160,true);}
    @Test void parkedOrUnknownDirectionCannotRequestMovement(){
        var p=new Vec3(55.5,70,-44.5);var empty=new FixedViewKeySteering.Keys(false,false,false,false);
        assertEquals(empty,FixedViewKeySteering.toward(p,p,-69.44f));
        assertEquals(empty,FixedViewKeySteering.toward(p,p,Float.NaN));
        assertEquals(empty,FixedViewKeySteering.toward(p,new Vec3(Double.NaN,70,0),0));
    }
    @Test void allCardinalViewsChooseForwardAndCorrectCrossDrift(){
        for(float yaw:new float[]{-90,0,90,180}) {
            double angle=Math.toRadians(yaw),x=-Math.sin(angle),z=Math.cos(angle);
            var start=new Vec3(.5,70,.5);var goal=new Vec3(.5+x*7,70,.5+z*7);
            var keys=FixedViewKeySteering.toward(start,goal,yaw+20.556045f);
            assertTrue(keys.forward());assertFalse(keys.back());
            assertFalse(keys.left()||keys.right(),"No cross drift exists before native motion");
        }
    }
}
