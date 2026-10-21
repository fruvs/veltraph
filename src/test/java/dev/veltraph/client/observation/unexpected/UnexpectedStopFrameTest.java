package dev.veltraph.client.observation.unexpected;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UnexpectedStopFrameTest {
    @Test void preSampleStopVetoesSameFrameGameplay() {
        var fence=new UnexpectedStopFrame();var callbacks=new AtomicInteger();fence.begin();fence.stop();
        if(!fence.stopped())callbacks.incrementAndGet();assertEquals(0,callbacks.get());
    }
    @Test void fenceDoesNotBlockNextFrameExplicitManualActivation() {
        var fence=new UnexpectedStopFrame();fence.begin();fence.stop();assertTrue(fence.stopped());
        fence.begin();assertFalse(fence.stopped());
    }
    @Test void postSampleStopCannotLeakIntoNextFrameThroughOldReceipt() {
        var fence=new UnexpectedStopFrame();fence.begin();assertFalse(fence.stopped());fence.stop();assertTrue(fence.stopped());
        fence.begin();assertFalse(fence.stopped());fence.stop();assertTrue(fence.stopped());
    }
}
