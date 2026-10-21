package dev.veltraph.client.hydra.mc;
import dev.veltraph.client.hydra.response.*;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.testutil.FakeRotationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class McHydraResponderOwnedResetTest {
    private static class Input implements InputService {
        boolean held;int writes,releases;
        public void setForwardHeld(boolean h){held=h;writes++;}public void setBackHeld(boolean h){writes++;}public void setLeftHeld(boolean h){writes++;}public void setRightHeld(boolean h){writes++;}public void setJumpHeld(boolean h){writes++;}public void setSneakHeld(boolean h){writes++;}public void releaseAllMovement(){held=false;releases++;}
    }
    @Test void idleResetLeavesForeignInputAndEmergencyPathUntouched(){var i=new Input();i.held=true;int[] emergency={0};var r=new McHydraResponder(null,null,new FakeRotationService(),i,()->emergency[0]++);for(int n=0;n<40;n++)r.reset();assertTrue(i.held);assertEquals(0,i.writes);assertEquals(0,i.releases);assertEquals(0,emergency[0]);}
    @Test void queuedMovementDoesNotOwnKeyboardBeforeFirstPublication(){var i=new Input();i.held=true;int[] emergency={0};var r=new McHydraResponder(null,null,new FakeRotationService(),i,()->emergency[0]++);r.move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(5,false)));r.reset();assertTrue(i.held);assertEquals(0,i.releases);assertEquals(0,emergency[0]);assertFalse(r.hasActiveLocalReaction());}
    @Test void lookOnlyResetDoesNotReleaseKeyboard(){var i=new Input();i.held=true;int[] emergency={0};var r=new McHydraResponder(null,null,new FakeRotationService(),i,()->emergency[0]++);r.fixAngle(30,0);r.tick();r.reset();assertTrue(i.held);assertEquals(0,i.releases);assertEquals(0,emergency[0]);}
    @Test void actualMovementResetReleasesAndKeepsOwnershipDuringCleanup(){var i=new Input();boolean[] ownership={false};McHydraResponder[] ref={null};var r=new McHydraResponder(null,null,new FakeRotationService(),i,()->ownership[0]=ref[0].hasActiveLocalReaction());ref[0]=r;r.move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(5,false)));r.tick();assertTrue(i.held);r.reset();assertFalse(i.held);assertTrue(ownership[0]);assertEquals(1,i.releases);assertFalse(r.hasActiveLocalReaction());r.reset();assertEquals(1,i.releases);}
    @Test void partialSetterFailureStillReleasesAttemptedMovement(){var i=new Input(){public void setBackHeld(boolean h){throw new IllegalStateException("failed after owned forward");}};int[] emergency={0};var r=new McHydraResponder(null,null,new FakeRotationService(),i,()->{i.held=false;emergency[0]++;});r.move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(5,false)));r.tick();assertFalse(i.held);assertFalse(r.hasActiveLocalReaction());assertEquals(1,emergency[0]);}
    @Test void oldSetterFailureDoesNotCancelReplacementMovement(){
        McHydraResponder[] ref={null};boolean[] replaced={false};
        var i=new Input(){public void setForwardHeld(boolean h){if(!replaced[0]){replaced[0]=true;ref[0].move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(8,false)));throw new IllegalStateException("old publication failed after replacement");}super.setForwardHeld(h);}};
        var r=new McHydraResponder(null,null,new FakeRotationService(),i);ref[0]=r;r.move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(3,false)));r.tick();assertTrue(r.hasActiveLocalReaction());r.tick();assertTrue(i.held);
    }
    @Test void oldTerminationCleanupDoesNotClearReplacementGeneration(){
        McHydraResponder[] ref={null};boolean[] replaced={false};
        var i=new Input(){public void releaseAllMovement(){super.releaseAllMovement();if(!replaced[0]){replaced[0]=true;ref[0].move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(8,false)));}}};
        var r=new McHydraResponder(null,null,new FakeRotationService(),i);ref[0]=r;r.move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(1,false)));r.tick();r.tick();assertTrue(r.hasActiveLocalReaction());r.tick();assertTrue(i.held);
    }

    @Test void oldLookFailureDoesNotCancelNewMovementChannel(){
        McHydraResponder[] ref={null};boolean[] changed={false};
        var rotation=new dev.veltraph.client.service.RotationService(){public float yaw(){if(ref[0]!=null&&!changed[0]){changed[0]=true;ref[0].move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(8,false)));throw new IllegalStateException("old look getter failed");}return 0;}public float pitch(){return 0;}public void aim(float y,float p){}public void lookAt(dev.veltraph.client.world.Vec3 v){}};
        var i=new Input();var r=new McHydraResponder(null,null,rotation,i);r.fixAngle(30,0);ref[0]=r;r.tick();assertTrue(r.hasActiveLocalReaction());assertTrue(i.held);
    }
    @Test void oldMovementFailureDoesNotCancelNewLookChannel(){
        McHydraResponder[] ref={null};boolean[] changed={false};
        var i=new Input(){public void setForwardHeld(boolean h){if(!changed[0]){changed[0]=true;ref[0].fixAngle(90,0);throw new IllegalStateException("old movement setter failed");}super.setForwardHeld(h);}};
        var rotation=new FakeRotationService();var r=new McHydraResponder(null,null,rotation,i);ref[0]=r;r.move(HydraReaction.BACKUP,List.of(HydraMovementStep.forward(3,false)));r.tick();assertTrue(r.hasActiveLocalReaction());r.tick();assertTrue(rotation.aimCalls>0);
    }

}
