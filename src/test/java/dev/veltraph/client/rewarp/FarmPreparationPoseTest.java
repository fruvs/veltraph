package dev.veltraph.client.rewarp;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarmPreparationPoseTest {
    final Object token=new Object(),player=new Object(),world=new Object(),connection=new Object(),posture=new Object();
    final Vec3 original=new Vec3(.5,70,.5);
    FarmPreparationPose.Observation o(Vec3 position,long serial,double motion,boolean ground,boolean support,boolean controls){return new FarmPreparationPose.Observation(player,world,connection,serial,position,-90,0,posture,ground,support,motion,controls);}
    FarmPreparationPose pose(){return new FarmPreparationPose(token,o(original,3,.12,true,true,true));}
    @Test void replayResidualPhysicsSettlesAtSeparatePoseWithoutChangingOriginalReturn(){var p=pose();assertTrue(p.valid(token,o(new Vec3(.57,70,.5),3,.07,true,true,true)));assertFalse(p.captureSettled(token,o(new Vec3(.57,70,.5),3,.07,true,true,true),true));var settled=new Vec3(.59,70,.5);assertTrue(p.captureSettled(token,o(settled,3,.018,true,true,true),true));assertEquals(original,p.originalReturn());assertEquals(settled,p.settled());assertTrue(p.valid(token,o(settled,3,0,true,true,true)));}
    @Test void actualNativeAckDrainPrecedesPoseCapture(){var p=pose();assertFalse(p.captureSettled(token,o(original,3,0,true,true,true),false));assertNull(p.settled());assertTrue(p.captureSettled(token,o(original,3,0,true,true,true),true));}
    @Test void freshServerCorrectionRejectsEvenIdenticalBodyAndView(){var p=pose();assertFalse(p.valid(token,o(original,4,0,true,true,true)));assertFalse(p.captureSettled(token,o(original,4,0,true,true,true),true));}
    @Test void guardOrOnEnableMovementCannotReplaceSettledPreparationPose(){var p=pose();assertTrue(p.captureSettled(token,o(original,3,0,true,true,true),true));assertFalse(p.valid(token,o(new Vec3(.502,70,.5),3,0,true,true,true)));assertEquals(original,p.settled());}
    @Test void tokenContextCameraAndControlsRemainExact(){var p=pose();assertFalse(p.valid(new Object(),o(original,3,0,true,true,true)));assertFalse(p.valid(token,o(original,3,0,true,true,false)));var changed=new FarmPreparationPose.Observation(player,new Object(),connection,3,original,-90,0,posture,true,true,0,true);assertFalse(p.valid(token,changed));var camera=new FarmPreparationPose.Observation(player,world,connection,3,original,-89,0,posture,true,true,0,true);assertFalse(p.valid(token,camera));}
    @Test void airborneUnsupportedOrNonfiniteMotionNeverAdmitsHelper(){var p=pose();assertFalse(p.captureSettled(token,o(original,3,0,false,true,true),true));assertFalse(p.captureSettled(token,o(original,3,0,true,false,true),true));assertFalse(p.captureSettled(token,o(original,3,Double.NaN,true,true,true),true));assertNull(p.settled());}
}
