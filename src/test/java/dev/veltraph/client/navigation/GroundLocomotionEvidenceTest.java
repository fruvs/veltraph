package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLocomotionEvidenceTest {
    @Test void observedBooleanIsReturnedUnchangedAndOmittedLaterOperandStaysUnevaluated(){
        var e=new GroundLocomotionEvidence();var reads=new AtomicInteger();
        boolean result=e.observe(GroundLocomotionEvidence.Stage.MOTION,false)&&e.observe(GroundLocomotionEvidence.Stage.ENDPOINTS,reads.incrementAndGet()>0);
        assertFalse(result);assertEquals(0,reads.get());var snapshot=e.snapshot("done",GroundLocomotionPolicy.Phase.AIRBORNE,null,null,null);
        assertTrue(snapshot.describe().contains("MOTION=FALSE"));assertTrue(snapshot.describe().contains("ENDPOINTS=NOT_EVALUATED"));
    }
    @Test void snapshotsAreImmutableCachedOperandsAndFormattingPerformsNoLiveReads(){
        var e=new GroundLocomotionEvidence();var v=new Vec3(0,-.15233519,.158);
        assertSame(v,e.velocity(v));assertEquals(.1,e.speed(.1));e.observe(GroundLocomotionEvidence.Stage.MOTION,true);
        var snapshot=e.snapshot("done",GroundLocomotionPolicy.Phase.AIRBORNE,new Vec3(.5,71.17675927506424,4.399269740648488),new Vec3(.5,70,3.2),new Vec3(.5,71,4.5));
        e.velocity(new Vec3(2,2,2));e.speed(.2);e.observe(GroundLocomotionEvidence.Stage.MOTION,false);
        assertEquals(v,snapshot.velocity());assertEquals(.1,snapshot.speed());assertTrue(snapshot.describe().contains("MOTION=TRUE"));assertEquals(snapshot.describe(),snapshot.describe());
    }
    @Test void successfulAndFailedFreshNativeOperandReadAreRecordedOnce(){
        var e=new GroundLocomotionEvidence();var reads=new AtomicInteger();
        assertTrue(e.observe(GroundLocomotionEvidence.Stage.TAKEOFF_SUPPORT,reads.incrementAndGet()==1));
        assertFalse(e.observe(GroundLocomotionEvidence.Stage.STEP_SWEEP,reads.incrementAndGet()==9));assertEquals(2,reads.get());
        var s=e.snapshot("done",GroundLocomotionPolicy.Phase.AIRBORNE,null,null,null);assertTrue(s.describe().contains("TAKEOFF_SUPPORT=TRUE"));assertTrue(s.describe().contains("STEP_SWEEP=FALSE"));
    }
    @Test void diagnosticProofComponentCannotReplaceOriginalDecisionFlags(){
        var e=new GroundLocomotionEvidence();e.observe(GroundLocomotionEvidence.Stage.MOTION,false);
        var s=e.snapshot("done",GroundLocomotionPolicy.Phase.AIRBORNE,null,null,null);var target=new Vec3(.5,71,4.5);
        var proof=new GroundLocomotionPolicy.Proof(target,true,true,false,false,null,s);
        assertTrue(proof.bodyClear());assertTrue(proof.landingClear());assertFalse(proof.jumpClear());assertSame(s,proof.evidence());
        assertNull(new GroundLocomotionPolicy.Proof(target,true,true,false,false).evidence());
    }
}
