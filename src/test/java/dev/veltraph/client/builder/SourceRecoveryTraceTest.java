package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceRecoveryTraceTest {
    @Test void observedPacketGatesAndCallbackReceiptKeepPhysicalFactsSeparateFromSelectionCenter() {
        var goal=new Vec3(-.5,70,-.5);var start=new Vec3(.5,70,.5);
        var active=SourceRecoveryTrace.started(null,7,new BlockPos(0,70,0),start,new BlockPos(-1,70,-1),goal);
        var observed=active.observed(new Vec3(-.45,70,-.60),"ARRIVED real navigator receipt",new BuilderResourcePacketClock.Sample(1000,950,3),1040);
        assertEquals(start,active.actual());assertEquals(40,observed.packetAgeMillis());assertEquals(90,observed.recoveredGapAgeMillis());
        assertFalse(observed.lagBlocked());assertTrue(observed.clickBlocked());assertEquals(0,observed.successes());
        var receipt=observed.finished(true);assertEquals(1,receipt.successes());assertEquals("ARRIVED",receipt.outcome());
        assertEquals(observed.actual(),receipt.actual());assertEquals(goal,receipt.goal());
        var next=SourceRecoveryTrace.started(receipt,8,new BlockPos(-1,70,-1),receipt.actual(),new BlockPos(-2,70,-2),new Vec3(-1.5,70,-1.5));
        assertEquals(1,next.successes());assertEquals(1,next.cancelled().cancellations());assertEquals(0,next.failures());
    }
    @Test void navigationFailureHasNoArrivalCountOrChangedActualPose() {
        var active=SourceRecoveryTrace.started(null,2,new BlockPos(0,70,0),new Vec3(.5,70,.5),new BlockPos(-1,70,-1),new Vec3(-.5,70,-.5));
        var failed=active.finished(false);assertEquals(0,failed.successes());assertEquals(1,failed.failures());assertEquals(active.actual(),failed.actual());
    }
}
