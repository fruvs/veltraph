package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceWalkUnusedPrefetchTest {
    @Test void unavailableUnusedSpeculationDoesNotFailHealthyCapture(){
        var c=new GhostSourceWalkDemandCapture<Integer,String>();c.prefetch(List.of(1,2));
        assertEquals(1,c.pump(100,()->0,()->true,key->{if(key==1)throw new IllegalStateException("unloaded speculative cell");return "known";}));
        assertTrue(c.valid());assertEquals(GhostSourceWalkDemandCapture.State.CAPTURING,c.state());assertEquals("known",c.require(2));
        assertThrows(java.util.concurrent.CompletionException.class,()->c.require(1)); // Required missing data never becomes air.
    }
    @Test void requiredUnavailableInputStillFailsWithoutGuessing() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var c=new GhostSourceWalkDemandCapture<Integer,String>(()->{entered.countDown();try{if(!release.await(2,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test latch timeout");}catch(InterruptedException error){throw new IllegalStateException(error);}});
        var required=java.util.concurrent.CompletableFuture.supplyAsync(()->c.require(1));assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
        // require() marked key1 used and published its future before this latch; queue publication is deliberately held.
        release.countDown();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while(c.pendingReads()==0&&System.nanoTime()<deadline)Thread.onSpinWait();assertEquals(1,c.pendingReads());
        c.prefetch(List.of(2));var queued=java.util.concurrent.CompletableFuture.supplyAsync(()->c.require(2));
        assertEquals(0,c.pump(100,()->0,()->true,key->{throw new IllegalStateException("required unloaded cell");}));
        assertEquals(GhostSourceWalkDemandCapture.State.FAILED,c.state());assertFalse(c.valid());
        assertThrows(java.util.concurrent.CompletionException.class,required::join);assertThrows(java.util.concurrent.CompletionException.class,queued::join);
        assertThrows(IllegalStateException.class,c::snapshot);
    }
}
