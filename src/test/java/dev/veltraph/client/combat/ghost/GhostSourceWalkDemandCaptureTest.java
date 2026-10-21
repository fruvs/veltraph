package dev.veltraph.client.combat.ghost;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceWalkDemandCaptureTest {
    @Test void concurrentPumpCannotSeeEnqueuedUnpublishedFuture() throws Exception {
        var published=new CountDownLatch(1);var allowEnqueue=new CountDownLatch(1);
        var capture=new GhostSourceWalkDemandCapture<Integer,String>(()->{published.countDown();try{if(!allowEnqueue.await(2,TimeUnit.SECONDS))throw new IllegalStateException("test publication timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}});
        var result=CompletableFuture.supplyAsync(()->capture.require(7));assertTrue(published.await(2,TimeUnit.SECONDS));
        assertEquals(0,capture.pump(100,()->0,()->true,k->"observed"));assertEquals(GhostSourceWalkDemandCapture.State.CAPTURING,capture.state());
        allowEnqueue.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(capture.pendingReads()==0&&System.nanoTime()<deadline)Thread.onSpinWait();assertEquals(1,capture.pendingReads());
        assertEquals(1,capture.pump(100,()->0,()->true,k->"observed"));assertEquals("observed",result.get(2,TimeUnit.SECONDS));
    }
    @Test void boundedSlicesProgressAcrossTicksWithoutInitialFrameRenewal() throws Exception {
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();capture.prefetch(java.util.List.of(1,2,3));var tick=new AtomicLong();var scope=new AtomicBoolean(true);
        var future=CompletableFuture.supplyAsync(()->capture.require(3));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(capture.pendingReads()<4&&System.nanoTime()<deadline)Thread.onSpinWait();assertEquals(4,capture.pendingReads());
        assertEquals(1,capture.pump(2,tick::get,scope::get,k->{tick.addAndGet(2);return "cell"+k;}));assertEquals("cell3",future.get(2,TimeUnit.SECONDS));
        assertEquals(1,capture.pump(2,tick::get,scope::get,k->{tick.addAndGet(2);return "cell"+k;}));assertEquals(1,capture.pump(2,tick::get,scope::get,k->{tick.addAndGet(2);return "cell"+k;}));
        assertEquals(java.util.Map.of(1,"cell1",2,"cell2",3,"cell3"),capture.snapshot());
        capture.seal(scope::get);assertEquals(GhostSourceWalkDemandCapture.State.SEALED,capture.state());assertEquals("cell3",capture.require(3));
        assertThrows(IllegalStateException.class,()->capture.require(4));
    }
    @Test void epochOrSettingsLossRevokesPendingReadsBeforeAnyNativeRead(){
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(java.util.List.of(1));var reads=new AtomicInteger();
        assertEquals(0,capture.pump(10,()->0,()->false,k->{reads.incrementAndGet();return k;}));assertEquals(0,reads.get());assertFalse(capture.valid());
        assertThrows(IllegalStateException.class,()->capture.require(1));
    }
    @Test void boundaryLossInsideReadCannotCompleteAValueOrSeal(){
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(java.util.List.of(1));var current=new AtomicBoolean(true);
        assertEquals(0,capture.pump(10,()->0,current::get,k->{current.set(false);return k;}));assertEquals(GhostSourceWalkDemandCapture.State.REVOKED,capture.state());
        assertThrows(IllegalStateException.class,capture::snapshot);
    }
}
