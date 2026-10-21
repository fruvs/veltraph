package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceWalkAvailableCaptureTest {
    @Test void exactPendingFutureResolvesOnlyThroughFreshPerValueNativeFences(){
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        var pending=assertThrows(GhostSourceWalkPendingCell.class,()->capture.requireAvailable(11));
        assertFalse(pending.availability().isDone());
        var again=assertThrows(GhostSourceWalkPendingCell.class,()->capture.requireAvailable(11));
        assertSame(pending.availability(),again.availability());
        var fences=new AtomicInteger();var reads=new AtomicInteger();
        assertEquals(1,capture.pump(4_000_000L,()->0,()->{fences.incrementAndGet();return true;},key->{reads.incrementAndGet();return "actual";}));
        assertTrue(pending.availability().isDone());assertEquals(3,fences.get());assertEquals(1,reads.get());
        assertEquals("actual",capture.requireAvailable(11));assertEquals(Map.of(11,"actual"),capture.usedSnapshot());
        capture.seal(()->true);assertEquals("actual",capture.requireAvailable(11));
        assertThrows(IllegalStateException.class,()->capture.requireAvailable(12));
    }
    @Test void exactNativeUnavailableObservationFailsRatherThanResumingWithDefault(){
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        var pending=assertThrows(GhostSourceWalkPendingCell.class,()->capture.requireAvailable(11));
        assertEquals(0,capture.pump(4_000_000L,()->0,()->true,key->{throw new IllegalStateException("native cell unloaded");}));
        assertTrue(pending.availability().isCompletedExceptionally());
        assertEquals(GhostSourceWalkDemandCapture.State.FAILED,capture.state());assertFalse(capture.valid());
        assertThrows(IllegalStateException.class,()->capture.requireAvailable(11));
        assertEquals(0,capture.cachedDiagnostic().observedKeys());
    }
    @Test void scopeMutationDuringActualReadCannotPublishOrSealPendingValue(){
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        var pending=assertThrows(GhostSourceWalkPendingCell.class,()->capture.requireAvailable(11));
        var current=new AtomicBoolean(true);
        assertEquals(0,capture.pump(4_000_000L,()->0,current::get,key->{current.set(false);return "stale";}));
        assertTrue(pending.availability().isCompletedExceptionally());
        assertEquals(GhostSourceWalkDemandCapture.State.REVOKED,capture.state());
        assertEquals(0,capture.cachedDiagnostic().observedKeys());
        assertThrows(IllegalStateException.class,()->capture.requireAvailable(11));
        assertThrows(IllegalStateException.class,()->capture.seal(()->true));
    }
    @Test void callerPublishedCompleteFutureStillUsesOriginalCurrentAndUsedSemantics(){
        var capture=new GhostSourceWalkDemandCapture<Integer,String>();
        capture.primeRequired(List.of(11,12));
        assertEquals(2,capture.pump(4_000_000L,()->0,()->true,key->"cell"+key));
        assertEquals(Map.of(),capture.usedSnapshot());
        assertEquals("cell11",capture.requireAvailable(11));assertEquals(Map.of(11,"cell11"),capture.usedSnapshot());
        capture.revoke();assertThrows(IllegalStateException.class,()->capture.requireAvailable(12));
    }
}
