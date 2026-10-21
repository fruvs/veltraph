package dev.veltraph.client.garden;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class GardenTerminalViewTest {
    static final class Camera implements RotationService {
        float yaw=150,pitch=60;int aimCalls,tracks,cancels;Runnable callback;
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public void aim(float y,float p){aimCalls++;yaw=y;pitch=p;}
        public void track(float y,float p){tracks++;yaw+=(y-yaw)*.22f;pitch+=(p-pitch)*.22f;}
        public void rotateTo(float y,float p,Runnable done){callback=done;}
        public void cancelRotation(){cancels++;}public void lookAt(Vec3 v){}
        void arrived(float y,float p){yaw=y;pitch=p;callback.run();}
    }
    @Test void completionWaitsForActualCallbackThenBoundedRemainder(){
        var c=new Camera();var v=new GardenTerminalView(c,()->true,()->true,35,20);
        assertTrue(v.start());assertEquals(150,c.yaw);assertEquals(60,c.pitch);assertEquals(0,c.aimCalls);
        for(int i=0;i<5;i++)assertEquals(GardenTerminalView.State.RESTORING,v.tick());
        c.arrived(35.07f,20.05f);assertEquals(GardenTerminalView.State.RESTORING,v.state());
        for(int i=0;i<40&&v.state()==GardenTerminalView.State.RESTORING;i++){
            float prior=c.yaw;v.tick();assertTrue(Math.abs(c.yaw-prior)<.08);
        }
        assertEquals(GardenTerminalView.State.COMPLETE,v.state());assertTrue(Math.abs(c.yaw-35)<.01);assertTrue(c.tracks>0);assertEquals(0,c.aimCalls);
    }
    @Test void cancelledGenerationNeverTracksOrWritesAfterLateCallback(){
        var c=new Camera();var v=new GardenTerminalView(c,()->true,()->true,35,20);v.start();Runnable late=c.callback;
        v.cancel();c.yaw=-70;c.pitch=-30;late.run();v.tick();assertEquals(-70,c.yaw);assertEquals(-30,c.pitch);assertEquals(0,c.tracks);assertEquals(1,c.cancels);
    }
    @Test void scopeOrEquipmentSlotPolicyDriftRevokesWithoutPoseWrites(){
        var valid=new AtomicBoolean(true);var c=new Camera();var v=new GardenTerminalView(c,valid::get,()->true,35,20);v.start();valid.set(false);v.tick();c.callback.run();
        assertEquals(GardenTerminalView.State.FAILED,v.state());assertEquals(150,c.yaw);assertEquals(0,c.tracks);assertEquals(1,c.cancels);
    }
    @Test void newerCameraOwnerIsNotCancelledByTerminalCleanup(){
        var own=new AtomicBoolean(true);var c=new Camera();var v=new GardenTerminalView(c,()->true,own::get,35,20);v.start();own.set(false);v.tick();v.cancel();c.callback.run();
        assertEquals(0,c.cancels);assertEquals(0,c.tracks);assertEquals(150,c.yaw);
    }
    @Test void incorrectPrematureCompletionCannotSnapToEndpoint(){
        var c=new Camera();var v=new GardenTerminalView(c,()->true,()->true,35,20);v.start();c.callback.run();
        assertEquals(GardenTerminalView.State.FAILED,v.state());assertEquals(0,c.tracks);assertEquals(0,c.aimCalls);
    }
    @Test void closedLeaseCannotStartOrRevive(){
        var c=new Camera();var v=new GardenTerminalView(c,()->false,()->true,35,20);assertFalse(v.start());v.cancel();assertFalse(v.start());assertNull(c.callback);
    }
}
