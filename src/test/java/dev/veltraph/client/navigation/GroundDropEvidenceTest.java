package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundDropEvidenceTest {
    @Test void failedDropOperandCannotEvaluateLaterGraphOrSweepQueries(){var e=new GroundLocomotionEvidence();var calls=new AtomicInteger();boolean drop=e.observe(GroundLocomotionEvidence.Stage.DROP_MOTION,false)&&e.observe(GroundLocomotionEvidence.Stage.DROP_GRAPH_EDGE,calls.incrementAndGet()>0);assertFalse(drop);assertEquals(0,calls.get());var s=e.snapshot("drop_rejected",GroundLocomotionPolicy.Phase.DROPPING,null,null,null);assertTrue(s.describe().contains("DROP_MOTION=FALSE"));assertTrue(s.describe().contains("DROP_GRAPH_EDGE=NOT_EVALUATED"));}
    @Test void dropSnapshotRetainsOnlyAlreadyReadNativeOperands(){var e=new GroundLocomotionEvidence();var origin=new Vec3(.5,71,3.5);var velocity=new Vec3(0,-.155232,.1);e.dropOrigin(origin);assertSame(velocity,e.dropVelocity(velocity));assertEquals(.1,e.dropSpeed(.1));var s=e.snapshot("drop_rejected",GroundLocomotionPolicy.Phase.DROPPING,new Vec3(.5,70.92159999847412,4.557581306384697),origin,new Vec3(.5,70,4.5));e.dropVelocity(new Vec3(2,2,2));assertEquals(velocity,s.dropVelocity());assertEquals(origin,s.dropOrigin());assertEquals(.1,s.dropSpeed());assertEquals(s.describe(),s.describe());}
}
