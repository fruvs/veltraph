package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostStandoffObservationPurityTest {
    final GhostConstrainedMeleeStandoff.Scope scope=new GhostConstrainedMeleeStandoff.Scope(new Object(),new Object(),new Object(),new Object(),new Object(),new Object(),0);
    GhostConstrainedMeleeStandoff.Frame frame(long tick,Vec3 target,Vec3 velocity){return new GhostConstrainedMeleeStandoff.Frame(scope,tick,new Vec3(0,76,0),target,velocity,2.25,true,true,true,false,false,false);}
    @Test void repeatedCachedReadsCannotAdvanceStationaryTicksOrMotionDecision(){
        var policy=new GhostConstrainedMeleeStandoff();var f=frame(1,new Vec3(0,76,2.25),new Vec3(0,0,0));var result=policy.observe(f);var observation=policy.cachedObservation();
        for(int i=0;i<100;i++){assertSame(result,policy.cachedEvidence());assertSame(observation,policy.cachedObservation());}
        assertSame(f,observation.frame());assertEquals(1,result.stationaryTargetTicks());assertEquals(.35,observation.activeBand().doubleValue(),0);assertNull(observation.withinBand());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,result.outcome());
    }
    @Test void actualDeclineRetainsItsRawVelocityAndPriorThreeDimensionalDisplacement(){
        var policy=new GhostConstrainedMeleeStandoff();policy.observe(frame(1,new Vec3(0,76,2.25),new Vec3(0,0,0)));
        var result=policy.observe(frame(2,new Vec3(0,76.03,2.25),new Vec3(0,0,0)));var snapshot=policy.cachedObservation();
        assertSame(result,snapshot.evidence());assertFalse(snapshot.velocityMoving());assertTrue(snapshot.displacementMoving());assertEquals(0,snapshot.speed3D(),1e-12);assertEquals(snapshot.priorTarget().distanceTo(snapshot.frame().target()),snapshot.displacement3D().doubleValue(),0);assertEquals(1L,snapshot.tickGap().longValue());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,result.outcome());
        var fast=policy.observe(frame(3,new Vec3(0,76.06,2.25),new Vec3(0,.03,0)));var shortCircuit=policy.cachedObservation();
        assertSame(fast,shortCircuit.evidence());assertTrue(shortCircuit.velocityMoving());assertNull(shortCircuit.displacementMoving());assertNull(shortCircuit.displacement3D());assertNull(shortCircuit.activeBand());assertNull(shortCircuit.withinBand());
    }
    @Test void staleRejectedFrameAndResetCannotExposeAnOldObservationAsFresh(){
        var policy=new GhostConstrainedMeleeStandoff();var f=frame(2,new Vec3(0,76,2.25),new Vec3(0,0,0));policy.observe(f);assertNotNull(policy.cachedObservation());
        assertEquals(GhostConstrainedMeleeStandoff.Outcome.REJECTED,policy.observe(f).outcome());assertNull(policy.cachedObservation());policy.reset();assertNull(policy.cachedObservation());
    }
}
