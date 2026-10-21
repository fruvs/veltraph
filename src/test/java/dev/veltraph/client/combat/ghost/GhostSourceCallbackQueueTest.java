package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceCallbackQueueTest {
    @Test void actualCounterOneCallbackCannotRunAtIssuanceTickAndCanRunOnlyOnce(){
        var q=new GhostSourceCallbackQueue<Object>();var job=new Object();var result=new Object();q.enqueue(job,result,30);
        assertTrue(q.take(job,30).isEmpty());assertTrue(q.pending());assertSame(result,q.take(job,31).orElseThrow());
        assertFalse(q.pending());assertTrue(q.take(job,31).isEmpty());assertTrue(q.take(job,32).isEmpty());
    }
    @Test void foreignCurrentJobRevokesCallbackRatherThanTransferringIt(){
        var q=new GhostSourceCallbackQueue<Object>();var original=new Object();q.enqueue(original,new Object(),10);
        assertTrue(q.take(new Object(),11).isEmpty());assertFalse(q.pending());assertTrue(q.take(original,11).isEmpty());
    }
    @Test void explicitContextCancellationAllowsNewJobWithoutReplayingOldCompletion(){
        var q=new GhostSourceCallbackQueue<String>();var old=new Object();var next=new Object();q.enqueue(old,"old",1);q.cancel();
        q.enqueue(next,"new",2);assertTrue(q.take(next,2).isEmpty());assertEquals("new",q.take(next,3).orElseThrow());assertTrue(q.take(old,4).isEmpty());
    }
    @Test void pendingCallbackCannotBeSilentlyOverwrittenAndLateObservationStillConsumesOnce(){
        var q=new GhostSourceCallbackQueue<String>();var job=new Object();q.enqueue(job,"original",2);
        assertThrows(IllegalStateException.class,()->q.enqueue(new Object(),"replacement",3));
        assertEquals("original",q.take(job,9).orElseThrow());assertTrue(q.take(job,10).isEmpty());
    }
}
