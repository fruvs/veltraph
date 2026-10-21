package dev.veltraph.client.hunting;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class HuntingApproachPrecisionTest {
    @Test void coarseWaterArrivalDoesNotAbandonUnreachedCertifiedPose(){
        var from=new Vec3(0,70,0);var goal=new Vec3(0,70,.38);var zero=new Vec3(0,0,0);
        var fine=HuntingApproachPrecision.steer(from,goal,zero,0,true);
        assertFalse(fine.arrived());assertTrue(fine.forward());assertFalse(fine.jump());assertFalse(fine.sneak());
    }
    @Test void exactPoseAndNativeMomentumSettleBeforeArrival(){
        var zero=new Vec3(0,0,0);var at=new Vec3(0,70,0);
        assertTrue(HuntingApproachPrecision.steer(at,at,zero,0,true).arrived());
        var moving=HuntingApproachPrecision.steer(at,at,new Vec3(0,0,.08),0,true);
        assertFalse(moving.arrived());assertTrue(moving.back());
    }
    @Test void candidateEyeUsesRecoveredHeightBeforeSameFifoLead(){
        double height=.4,fishY=70.8,lower=.22,upper=.81;
        double current=fishY+HuntingTactics.aimHeight(height,70.4-fishY,upper,lower);
        double candidate=fishY+HuntingTactics.aimHeight(height,71.62-fishY,upper,lower);
        assertNotEquals(current,candidate);var motion=new HuntingMotion();motion.sample(new Vec3(.02,0,.03));
        assertEquals(.1,motion.predict(new Vec3(0,candidate,0)).x(),1e-12);assertEquals(candidate,motion.predict(new Vec3(0,candidate,0)).y(),1e-12);
    }
}
