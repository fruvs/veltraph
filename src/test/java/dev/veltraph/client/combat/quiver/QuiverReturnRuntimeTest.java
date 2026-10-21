package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuiverReturnRuntimeTest {
    private static final QuiverReturnRuntime.Pose POSE=new QuiverReturnRuntime.Pose(new Vec3(1.5,70,2.5),179,10);
    @Test void merchantPositionDoesNotPermitCallerResume(){var r=new QuiverReturnRuntime(POSE,10,20);r.observe(new QuiverReturnRuntime.Pose(new Vec3(6,70,2.5),179,10),11,true,true,false);assertEquals(QuiverReturnRuntime.State.WALK,r.state());}
    @Test void requiresPhysicalPositionThenCameraAcknowledgment(){var r=new QuiverReturnRuntime(POSE,10,20);r.observe(new QuiverReturnRuntime.Pose(POSE.position(),0,0),11,true,true,false);assertEquals(QuiverReturnRuntime.State.AIM,r.state());r.observe(new QuiverReturnRuntime.Pose(new Vec3(1.6,70.1,2.5),-179,12),12,true,true,false);assertEquals(QuiverReturnRuntime.State.COMPLETE,r.state());}
    @Test void wrongHeightCannotAcknowledgeReturn(){var r=new QuiverReturnRuntime(POSE,10,20);r.observe(new QuiverReturnRuntime.Pose(new Vec3(1.5,71,2.5),179,10),11,true,true,false);assertEquals(QuiverReturnRuntime.State.WALK,r.state());}
    @Test void lostPoseDuringCameraRestoreFails(){var r=new QuiverReturnRuntime(POSE,10,20);r.observe(new QuiverReturnRuntime.Pose(POSE.position(),0,0),11,true,true,false);r.observe(new QuiverReturnRuntime.Pose(new Vec3(5,70,2.5),179,10),12,true,true,false);assertEquals(QuiverReturnRuntime.State.FAILED,r.state());}
    @Test void blockedChangedInventoryOwnerOrTimeoutCannotResume(){for(int cause=0;cause<4;cause++){var r=new QuiverReturnRuntime(POSE,10,20);r.observe(POSE,cause==3?31:11,cause!=0,cause!=1,cause==2);assertEquals(QuiverReturnRuntime.State.FAILED,r.state());}}
    @Test void unknownPoseAndUnboundedWaitReject(){assertThrows(IllegalArgumentException.class,()->new QuiverReturnRuntime.Pose(new Vec3(Double.NaN,70,2),0,0));assertThrows(IllegalArgumentException.class,()->new QuiverReturnRuntime(POSE,0,2401));}
}
