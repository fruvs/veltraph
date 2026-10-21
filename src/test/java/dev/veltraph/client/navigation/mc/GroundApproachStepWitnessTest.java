package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.world.BlockPos;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundApproachStepWitnessTest {
    @Test void failedOriginalOperandDoesNotEvaluateLaterWorldQuery(){var w=new GroundApproachStepWitness();var calls=new AtomicInteger();boolean accepted=w.observe("PADDED_NEXT_CELL",false)&&w.observe("PADDED_NEXT_SUPPORT",calls.incrementAndGet()>0);assertFalse(accepted);assertEquals(0,calls.get());assertEquals("PADDED_NEXT_CELL",w.snapshot(null,null,null,null).operand());}
    @Test void immutableWitnessRetainsExactAlreadyComputedPaddedEndpoint(){var w=new GroundApproachStepWitness();var a=new Vec3(.45,70,7.32);var b=new Vec3(.6,70,7.3);w.endpoints(a,b,-.12,.12);assertFalse(w.observe("COAST_BODY_UNION",false));var s=w.snapshot(a,a,b,new BlockPos(0,70,7));w.endpoints(b,a,0,0);assertEquals(a,s.paddedNext());assertEquals(b,s.paddedCoast());assertEquals(-.12,s.dx());assertEquals("COAST_BODY_UNION",s.operand());}
}
