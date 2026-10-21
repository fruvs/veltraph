package dev.veltraph.client.hunting;

import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.rotation.RotationRequest;
import dev.veltraph.client.rotation.SourceRotationStepper;
import dev.veltraph.client.navigation.TravelHeading;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HuntingAimTest {
    /** Exact X retained request/kernel application contract, with actual angle mutation at tick only. */
    private static final class RequestPort implements RotationService {
        final RotationRequest request=new RotationRequest(new SourceRotationStepper(1));
        float yaw,pitch,targetYaw,targetPitch;int requests,tracks,applications;
        RequestPort(float yaw,float pitch){this.yaw=yaw;this.pitch=pitch;}
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public void aim(float yaw,float pitch){fail("Hunting caller must not apply a raw/planned camera");}
        public void lookAt(Vec3 target){fail("Hunting caller must submit recovered rotating target");}
        public void track(float yaw,float pitch){tracks++;fail("Local travel smoothing is not the recovered hunting caller");}
        public void rotateTo(float yaw,float pitch,Runnable completed){assertNull(completed);requests++;targetYaw=yaw;targetPitch=pitch;request.replace(yaw,pitch,null);}
        public void tickRotation(){var step=request.advance(yaw,pitch,SourceRotationStepper.Settings.standard(.5,1),true,false);assertFalse(step.rejected());yaw=step.yaw();pitch=step.pitch();applications++;request.applied(step);}
    }
    @Test void actualXCallerUsesQueuedRecoveredRotationAndPreservesSourceEndpoint(){
        var port=new RequestPort(-679.2208f,30.347393f);var eye=new Vec3(-.9242859089515065,72.55679978717544,-1.9549822706657816);var aim=new Vec3(-.5381164535938296,71.39989954906514,.378753664595024);
        HuntingAim.request(port,eye,aim);
        assertEquals(1,port.requests);assertEquals(0,port.tracks);assertEquals(-9.395746f,port.targetYaw,1e-5);assertEquals(26.062254f,port.targetPitch,1e-5);
        assertEquals(-679.2208f,port.yaw);assertEquals(30.347393f,port.pitch);assertEquals(0,port.applications,"A request is not an actually applied angle or a hit");
    }
    @Test void repeatedSourceTargetReplacementDoesNotStarveActualTickProgress(){
        var port=new RequestPort(-679.2208f,30.347393f);var eye=new Vec3(-.9242859089515065,72.55679978717544,-1.9549822706657816);var aim=new Vec3(-.5381164535938296,71.39989954906514,.378753664595024);
        for(int tick=0;tick<8;tick++){float beforeYaw=port.yaw,beforePitch=port.pitch;HuntingAim.request(port,eye,aim);assertEquals(beforeYaw,port.yaw);assertEquals(beforePitch,port.pitch);port.tickRotation();}
        assertEquals(8,port.requests);assertEquals(8,port.applications);assertEquals(0,port.tracks);
        assertTrue(Math.abs(SourceRotationStepper.wrap(port.targetYaw-port.yaw))<.16);assertTrue(Math.abs(port.targetPitch-port.pitch)<.16);
    }
    @Test void changingTargetsRetainSourceRequestProgressAndDoNotUseTravelLag(){
        var port=new RequestPort(0,0);var travelYaw=new TravelHeading();float localYaw=0;var eye=new Vec3(0,1.4,0);
        for(int tick=0;tick<40;tick++){
            float desired=30+2*tick;double angle=Math.toRadians(desired);var target=new Vec3(-Math.sin(angle)*2,1.4,Math.cos(angle)*2);
            HuntingAim.request(port,eye,target);port.tickRotation();localYaw=travelYaw.step(localYaw,desired,1);
        }
        assertEquals(40,port.requests);assertEquals(40,port.applications);assertEquals(0,port.tracks);
        double recoveredError=Math.abs(SourceRotationStepper.wrap(port.targetYaw-port.yaw)),localError=Math.abs(SourceRotationStepper.wrap(port.targetYaw-localYaw));
        assertTrue(recoveredError<3,"Source moving request must enter unchanged native3degree gate; error="+recoveredError);
        assertTrue(localError>6,"Travel .22 heading policy leaves a distinct moving-target lag; error="+localError);
    }
}
