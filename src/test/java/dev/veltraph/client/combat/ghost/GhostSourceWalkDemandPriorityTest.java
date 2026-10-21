package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

final class GhostSourceWalkDemandPriorityTest {
    @Test void requiredCellBypassesEarlierSpeculationAndIsReadOnlyOnce() throws Exception {
        var capture=new GhostSourceWalkDemandCapture<String,String>();capture.prefetch(List.of("unused1","wanted","unused2"));
        var required=CompletableFuture.supplyAsync(()->capture.require("wanted"));awaitPending(capture,4);
        var order=new ArrayList<String>();var clock=new AtomicLong();
        assertEquals(1,capture.pump(10,clock::get,()->true,k->{order.add(k);clock.set(10);return k;}));
        assertEquals(List.of("wanted"),order);assertEquals("wanted",required.get(2,TimeUnit.SECONDS));
        capture.pump(1,()->0,()->true,k->{order.add(k);return k;});
        assertEquals(1,order.stream().filter("wanted"::equals).count());assertEquals(3,order.size());
    }
    @Test void usefulSpeculationUsesAvailableTimeButCannotExceedFiniteReadCeiling() throws Exception {
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(java.util.stream.IntStream.range(0,200).boxed().toList());
        var order=new ArrayList<Integer>();var clock=new AtomicLong();
        assertEquals(16,capture.pump(4_000_000,clock::get,()->true,k->{order.add(k);clock.addAndGet(250_000);return k;}));
        assertEquals(4_000_000,clock.get());assertEquals(java.util.stream.IntStream.range(0,16).boxed().toList(),order);
        var required=CompletableFuture.supplyAsync(()->capture.require(199));awaitPending(capture,185);
        clock.set(0);capture.pump(10,clock::get,()->true,k->{order.add(k);clock.set(10);return k;});
        assertEquals(199,order.getLast().intValue());assertEquals(199,required.get(2,TimeUnit.SECONDS).intValue());
        assertEquals(java.util.Map.of(199,199),capture.usedSnapshot());
        var zeroClock=new GhostSourceWalkDemandCapture<Integer,Integer>();zeroClock.prefetch(java.util.stream.IntStream.range(0,1000).boxed().toList());
        assertEquals(128,zeroClock.pump(1,()->0,()->true,k->k));assertTrue(zeroClock.valid());
    }
    @Test void failedUnusedFutureRemainsLocalUntilActuallyRequired() {
        var capture=new GhostSourceWalkDemandCapture<String,String>();capture.prefetch(List.of("unloaded","good"));
        capture.pump(1,()->0,()->true,k->{if(k.equals("unloaded"))throw new IllegalStateException("unloaded");return k;});
        assertTrue(capture.valid());assertEquals("good",capture.require("good"));
        assertThrows(CompletionException.class,()->capture.require("unloaded"));
        assertEquals(GhostSourceWalkDemandCapture.State.FAILED,capture.state());assertFalse(capture.valid());
        assertThrows(IllegalStateException.class,capture::snapshot);
    }
    @Test void cancellationUnblocksQueuedRequiredAndSpeculativeValues() throws Exception {
        var capture=new GhostSourceWalkDemandCapture<String,String>();capture.prefetch(List.of("speculative"));
        var required=CompletableFuture.supplyAsync(()->capture.require("required"));awaitPending(capture,2);capture.revoke();
        assertThrows(ExecutionException.class,()->required.get(2,TimeUnit.SECONDS));assertFalse(capture.valid());assertEquals(0,capture.pendingReads());
        assertEquals(0,capture.pump(1,()->0,()->true,k->{fail("No revoked native reads");return k;}));
    }
    private static void awaitPending(GhostSourceWalkDemandCapture<?,?> capture,int expected){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(capture.pendingReads()<expected&&System.nanoTime()<deadline)Thread.onSpinWait();assertEquals(expected,capture.pendingReads());}
}
