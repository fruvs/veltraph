package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceCoordinatePreflightDemandTest {
    @Test void twoEnteredWorkersPublishBothDistantPreflightDependenciesBeforeEitherJoin() throws Exception {
        var a=new GhostSourceWalkDemandCapture<Integer,String>();
        var b=new GhostSourceWalkDemandCapture<Integer,String>();
        var entered=new CountDownLatch(2);
        var consume=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->{a.primeRequired(List.of(1,1000));entered.countDown();
                if(!consume.await(2,TimeUnit.SECONDS))throw new IllegalStateException("fixture release timeout");
                return List.of(a.require(1),a.require(1000));});
            var second=executor.submit(()->{b.primeRequired(List.of(2,2000));entered.countDown();
                if(!consume.await(2,TimeUnit.SECONDS))throw new IllegalStateException("fixture release timeout");
                return List.of(b.require(2),b.require(2000));});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals(2,a.cachedDiagnostic().pendingRequired());assertEquals(2,b.cachedDiagnostic().pendingRequired());
            assertEquals(Map.of(),a.usedSnapshot());assertEquals(Map.of(),b.usedSnapshot());
            var guardCalls=new AtomicInteger();
            assertEquals(2,a.pump(4_000_000L,()->0,()->{guardCalls.incrementAndGet();return true;},k->"native"+k));
            assertEquals(2,b.pump(4_000_000L,()->0,()->{guardCalls.incrementAndGet();return true;},k->"native"+k));
            assertEquals(10,guardCalls.get()); // Entry and pre/post fences for each of four values.
            consume.countDown();
            assertEquals(List.of("native1","native1000"),first.get(2,TimeUnit.SECONDS));
            assertEquals(List.of("native2","native2000"),second.get(2,TimeUnit.SECONDS));
            assertEquals(Map.of(1,"native1",1000,"native1000"),a.usedSnapshot());
            assertEquals(Map.of(2,"native2",2000,"native2000"),b.usedSnapshot());
        } finally {consume.countDown();a.revoke();b.revoke();executor.shutdownNow();}
    }
    @Test void queuedCandidateCapturesFixedDependenciesBeforeSolverThreadStarts() throws Exception {
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        capture.primeRequired(List.of(7,7000)); // Actual coordinate issuance, before task submission.
        var busy=new CountDownLatch(2);var release=new CountDownLatch(1);var started=new AtomicBoolean();
        var executor=Executors.newFixedThreadPool(2);
        try {
            for(int i=0;i<2;i++)executor.submit(()->{busy.countDown();
                if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("fixture release timeout");return null;});
            assertTrue(busy.await(2,TimeUnit.SECONDS));
            var queued=executor.submit(()->{started.set(true);return List.of(capture.require(7),capture.require(7000));});
            assertFalse(started.get());assertEquals(Map.of(),capture.usedSnapshot());
            assertEquals(2,capture.pump(4_000_000L,()->0,()->true,k->"actual"+k));
            assertFalse(started.get());assertEquals(2,capture.cachedDiagnostic().nativeReads());
            assertEquals(Map.of(),capture.usedSnapshot());
            release.countDown();assertEquals(List.of("actual7","actual7000"),queued.get(2,TimeUnit.SECONDS));
            assertTrue(started.get());assertEquals(Map.of(7,"actual7",7000,"actual7000"),capture.usedSnapshot());
        } finally {release.countDown();capture.revoke();executor.shutdownNow();}
    }
    @Test void perReadBudgetAndPriorityRemainExactAcrossClientPumps(){
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        capture.prefetch(List.of(999));capture.primeRequired(List.of(1,1000));
        var clock=new AtomicLong();var order=new java.util.ArrayList<Integer>();
        java.util.function.Function<Integer,String> read=k->{order.add(k);clock.addAndGet(4_000_000L);return "cell"+k;};
        assertEquals(1,capture.pump(4_000_000L,clock::get,()->true,read));
        assertEquals(List.of(1),order);
        assertEquals(1,capture.pump(4_000_000L,clock::get,()->true,read));
        assertEquals(List.of(1,1000),order);
        assertEquals("cell1",capture.require(1));assertEquals("cell1000",capture.require(1000));
        assertEquals(1,capture.cachedDiagnostic().pendingSpeculative());
    }
    @Test void unusedBatchFailureIsNotGuessedOrMarkedUsedAndConsumptionFailsCapture(){
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        capture.primeRequired(List.of(1,1000));
        assertEquals(1,capture.pump(4_000_000L,()->0,()->true,k->{if(k==1000)throw new IllegalStateException("unloaded native cell");return "actual";}));
        assertEquals(GhostSourceWalkDemandCapture.State.CAPTURING,capture.state());assertTrue(capture.valid());
        assertEquals("actual",capture.require(1));assertEquals(Map.of(1,"actual"),capture.usedSnapshot());
        assertThrows(CompletionException.class,()->capture.require(1000));
        assertEquals(GhostSourceWalkDemandCapture.State.FAILED,capture.state());assertFalse(capture.valid());
    }
    @Test void nativeScopeLossAfterFirstValueRevokesBatchAndUnblocksQueuedWorker() throws Exception {
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        capture.primeRequired(List.of(1,1000));var current=new AtomicBoolean(true);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var result=executor.submit(()->capture.require(1000));
            assertEquals(0,capture.pump(4_000_000L,()->0,current::get,k->{current.set(false);return "must-not-publish";}));
            assertEquals(GhostSourceWalkDemandCapture.State.REVOKED,capture.state());assertFalse(capture.valid());
            assertEquals(0,capture.cachedDiagnostic().observedKeys());
            assertThrows(ExecutionException.class,()->result.get(2,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->capture.primeRequired(List.of(2)));
        } finally {capture.revoke();executor.shutdownNow();}
    }
}
