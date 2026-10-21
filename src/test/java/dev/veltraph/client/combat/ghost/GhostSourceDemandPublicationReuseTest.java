package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Publication identity/race/failure contracts; no native throughput or allocation benchmark claim. */
final class GhostSourceDemandPublicationReuseTest {
    @Test void repeatedPendingPrefetchKeepsOneExactFutureAndOneQueueEntry(){
        var published=new AtomicInteger();var capture=new GhostSourceWalkDemandCapture<Integer,Integer>(published::incrementAndGet);
        capture.prefetch(List.of(7));var first=pending(capture,7);
        for(int i=0;i<100;i++)capture.prefetch(List.of(7,7));
        assertSame(first,pending(capture,7));assertEquals(1,published.get());assertEquals(2,capture.pendingReads());
        assertEquals(1,capture.pump(1,()->0,()->true,k->k));assertEquals(7,capture.requireAvailable(7));
        assertEquals(0,capture.pendingReads());assertEquals(1,capture.cachedDiagnostic().nativeReads());
    }
    @Test void repeatedPendingDemandPromotesAheadOfEarlierSpeculationOnlyOnce(){
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(List.of(1,2,3));
        var exact=pending(capture,3);assertSame(exact,pending(capture,3));assertEquals(4,capture.pendingReads());
        var clock=new AtomicLong();var order=new ArrayList<Integer>();
        assertEquals(1,capture.pump(1,clock::get,()->true,k->{order.add(k);clock.set(1);return k;}));
        assertEquals(List.of(3),order);assertEquals(3,capture.requireAvailable(3));
        capture.pump(1,()->0,()->true,k->{order.add(k);return k;});assertEquals(List.of(3,1,2),order);
    }
    @Test void completedSuccessPrefetchCreatesNoPublicationOrNewRead(){
        var published=new AtomicInteger();var capture=new GhostSourceWalkDemandCapture<Integer,Integer>(published::incrementAndGet);
        capture.prefetch(List.of(4));capture.pump(1,()->0,()->true,k->k);
        for(int i=0;i<100;i++)capture.prefetch(List.of(4));
        assertEquals(1,published.get());assertEquals(0,capture.pendingReads());assertEquals(4,capture.requireAvailable(4));
        assertEquals(0,capture.pump(1,()->0,()->true,k->{fail("No duplicate native read");return k;}));
    }
    @Test void completedUnusedFailureIsReusedThenFailsOnlyWhenConsumed(){
        var published=new AtomicInteger();var capture=new GhostSourceWalkDemandCapture<Integer,Integer>(published::incrementAndGet);
        var original=new IllegalStateException("actual unavailable");capture.prefetch(List.of(5));
        capture.pump(1,()->0,()->true,k->{throw original;});capture.prefetch(List.of(5));
        assertTrue(capture.valid());assertEquals(1,published.get());assertEquals(0,capture.pendingReads());
        assertSame(original,assertThrows(CompletionException.class,()->capture.requireAvailable(5)).getCause());
        assertEquals(GhostSourceWalkDemandCapture.State.FAILED,capture.state());assertFalse(capture.valid());
    }
    @Test void loserCanPromotePublishedFutureBeforeWinnerQueuesSpeculation() throws Exception {
        var published=new AtomicInteger();var winnerEntered=new CountDownLatch(1);var releaseWinner=new CountDownLatch(1);
        var winnerFailure=new AtomicReference<Throwable>();
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>(()->{published.incrementAndGet();winnerEntered.countDown();await(releaseWinner);});
        var winner=new Thread(()->{try{capture.prefetch(List.of(9));}catch(Throwable e){winnerFailure.set(e);}});
        winner.start();try{
            assertTrue(winnerEntered.await(2,TimeUnit.SECONDS));var exact=pending(capture,9);
            assertEquals(1,capture.pendingReads());assertSame(exact,pending(capture,9));
            assertEquals(1,capture.pump(1,()->0,()->true,k->k));assertEquals(9,exact.get(2,TimeUnit.SECONDS));
            assertEquals(9,capture.requireAvailable(9));
        }finally{releaseWinner.countDown();winner.join(2000);}
        assertFalse(winner.isAlive());assertNull(winnerFailure.get());assertEquals(1,published.get());
        assertEquals(0,capture.pump(1,()->0,()->true,k->{fail("Completed winning future must skip its late speculative entry");return k;}));
        assertEquals(0,capture.pendingReads());
    }
    @Test void reentrantWinnerPublicationPromotesSameFutureWithoutRecursiveCallback(){
        var published=new AtomicInteger();var ref=new AtomicReference<GhostSourceWalkDemandCapture<Integer,Integer>>();
        var exact=new AtomicReference<CompletableFuture<?>>();
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>(()->{published.incrementAndGet();exact.set(pending(ref.get(),11));});ref.set(capture);
        capture.prefetch(List.of(11));assertEquals(1,published.get());assertEquals(2,capture.pendingReads());
        assertSame(exact.get(),pending(capture,11));assertEquals(1,capture.pump(1,()->0,()->true,k->k));
        assertEquals(11,capture.requireAvailable(11));assertEquals(0,capture.pendingReads());
    }
    @Test void throwingWinnerCallbackIsNotRetriedByDuplicateAndRevocationResolvesItsExactFuture(){
        var published=new AtomicInteger();var original=new IllegalStateException("publication hook failed");
        var capture=new GhostSourceWalkDemandCapture<Integer,Integer>(()->{published.incrementAndGet();throw original;});
        assertSame(original,assertThrows(IllegalStateException.class,()->capture.prefetch(List.of(13))));
        assertDoesNotThrow(()->capture.prefetch(List.of(13)));var exact=pending(capture,13);
        assertEquals(1,published.get());capture.revoke();assertTrue(exact.isCompletedExceptionally());
        assertEquals(0,capture.pendingReads());assertEquals(0,capture.pump(1,()->0,()->true,k->{fail("Revoked capture has no read permission");return k;}));
    }
    @Test void fastPromotionCannotBypassFreshScopeBeforeOrAfterNativeValue(){
        for(boolean revokeInsideRead:new boolean[]{false,true}){
            var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(List.of(17));var exact=pending(capture,17);
            var current=new AtomicBoolean(revokeInsideRead);var reads=new AtomicInteger();
            assertEquals(0,capture.pump(1,()->0,current::get,k->{reads.incrementAndGet();current.set(false);return k;}));
            assertEquals(revokeInsideRead?1:0,reads.get());assertTrue(exact.isCompletedExceptionally());
            assertFalse(capture.alreadyObserved(17));assertEquals(GhostSourceWalkDemandCapture.State.REVOKED,capture.state());
        }
    }
    private static CompletableFuture<?> pending(GhostSourceWalkDemandCapture<Integer,Integer> capture,int key){
        return assertThrows(GhostSourceWalkPendingCell.class,()->capture.requireAvailable(key)).availability();
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(2,TimeUnit.SECONDS))throw new AssertionError("Winner release timed out");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
