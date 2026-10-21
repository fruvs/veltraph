package dev.veltraph.client.hunting;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
final class HuntingAimHoldTest {
    private static final UUID ID=UUID.fromString("3b36eb6c-7fd3-4f10-9330-712f399c8766");
    private static final Vec3 PLAYER=new Vec3(12.892527335798428,70,-.9465681438246744),TARGET=new Vec3(14.74,71.09,-.258);
    private static HuntingAimHold.Decision observe(HuntingAimHold h,int hits,String pose,double eye,Vec3 player,Vec3 target,double angle,long tick){return h.observe(ID,hits,pose,eye,player,target,angle,tick);}
    @Test void convergingNativeAnglesMayContinueOrdinaryHold(){var h=new HuntingAimHold();assertEquals(HuntingAimHold.Decision.HOLD,observe(h,0,"STANDING",1.62,PLAYER,TARGET,9.54,1));assertEquals(HuntingAimHold.Decision.HOLD,observe(h,0,"STANDING",1.62,PLAYER,TARGET,7.11,40));assertEquals(HuntingAimHold.Decision.HOLD,observe(h,0,"STANDING",1.62,PLAYER,TARGET,2.31,79));}
    @Test void actualTargetMotionBeyondExistingReplanThresholdInvalidatesHold(){var h=new HuntingAimHold();observe(h,0,"STANDING",1.62,PLAYER,TARGET,9.54,1);assertEquals(HuntingAimHold.Decision.REAPPROACH,observe(h,0,"STANDING",1.62,PLAYER,new Vec3(14.74,71.09,.5),7.11,10));}
    @Test void firstNativeHitInvalidatesPriorPoseContext(){var h=new HuntingAimHold();observe(h,0,"SWIMMING",.4,PLAYER,TARGET,4,1);assertEquals(HuntingAimHold.Decision.REAPPROACH,observe(h,1,"SWIMMING",.4,PLAYER,TARGET,3,2));}
    @Test void nativePoseAndEyeChangeRequiresAnotherCertifiedPose(){var h=new HuntingAimHold();observe(h,0,"SWIMMING",.4,PLAYER,TARGET,4,1);assertEquals(HuntingAimHold.Decision.REAPPROACH,observe(h,0,"STANDING",1.62,PLAYER,TARGET,3,2));}
    @Test void fullFortyNativeTickWindowWithoutAngularProgressReapproaches(){var h=new HuntingAimHold();observe(h,0,"STANDING",1.62,PLAYER,TARGET,4,1);assertEquals(HuntingAimHold.Decision.HOLD,observe(h,0,"STANDING",1.62,PLAYER,TARGET,4,40));assertEquals(HuntingAimHold.Decision.REAPPROACH,observe(h,0,"STANDING",1.62,PLAYER,TARGET,4,41));}
    @Test void rejectedPoseCannotImmediatelyCancelRepositionWithAnotherHold(){var h=new HuntingAimHold();h.reject(PLAYER);assertFalse(h.candidateAllowed(new Vec3(PLAYER.x()+.05,70,PLAYER.z())));assertEquals(HuntingAimHold.Decision.MOVE_BEFORE_HOLD,observe(h,1,"STANDING",1.62,PLAYER,TARGET,2,1));var moved=new Vec3(PLAYER.x()+.2,70,PLAYER.z());assertTrue(h.candidateAllowed(moved));assertEquals(HuntingAimHold.Decision.HOLD,observe(h,1,"STANDING",1.62,moved,TARGET,2,2));}
}
