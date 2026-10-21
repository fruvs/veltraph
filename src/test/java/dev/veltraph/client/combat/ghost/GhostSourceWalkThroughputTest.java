package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import static org.junit.jupiter.api.Assertions.*;

/** Native-like tick schedule model. No Minecraft, graph solve or native throughput parity is claimed. */
final class GhostSourceWalkThroughputTest {
    private static final int GROUPS=67,REQUIRED_PER_WORKER=2389;
    private record Result(int ticks,int reads,int requiredValues,long maximumTickReadNanos) {}
    @Test void twoParkedWorkersComplete2389ValuesEachWithinBoundedTicks(){var adaptive=simulate(128);
        assertEquals(REQUIRED_PER_WORKER*2,adaptive.requiredValues());assertTrue(adaptive.ticks()<=100,"actual model ticks="+adaptive.ticks());
        assertTrue(adaptive.reads()>=REQUIRED_PER_WORKER*2);assertTrue(adaptive.reads()<=45*GROUPS*2);
        assertTrue(adaptive.maximumTickReadNanos()<=4_000_000);
    }
    @Test void fourReadLimitReproducesFrameLatencyEvenWithTheSameUsefulWindow(){var limited=simulate(4);var adaptive=simulate(128);
        assertEquals(limited.requiredValues(),adaptive.requiredValues());assertTrue(limited.ticks()>300,"limited model ticks="+limited.ticks());
        assertTrue(adaptive.ticks()<=100,"adaptive model ticks="+adaptive.ticks());assertTrue(limited.ticks()>adaptive.ticks()*3);
    }
    @Test void revokedScopeUnblocksBothParkedWorkersBeforeAnyWorldRead() throws Exception {
        var captures=List.of(new GhostSourceWalkDemandCapture<Cell,Integer>(),new GhostSourceWalkDemandCapture<Cell,Integer>());
        var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Integer>>();var current=new AtomicBoolean(true);
        try{for(int worker=0;worker<2;worker++){int w=worker;futures.add(pool.submit(()->captures.get(w).require(new Cell(w,76,0))));}
            for(int i=0;i<2;i++)awaitDemandOrFinish(captures.get(i),futures.get(i));current.set(false);
            for(var capture:captures)assertEquals(0,capture.pump(4_000_000,()->0,current::get,k->{fail("Revoked world read");return 0;}));
            for(var future:futures)assertThrows(ExecutionException.class,()->future.get(2,TimeUnit.SECONDS));
        }finally{captures.forEach(GhostSourceWalkDemandCapture::revoke);pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
    }
    private static Result simulate(int speculativeLimit){var mainThread=Thread.currentThread();
        var captures=List.of(new GhostSourceWalkDemandCapture<Cell,Integer>(()->{},speculativeLimit),new GhostSourceWalkDemandCapture<Cell,Integer>(()->{},speculativeLimit));
        var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Integer>>();var reads=new java.util.concurrent.atomic.AtomicInteger();var clock=new AtomicLong();var readingNativeTick=new AtomicBoolean();long maximum=0;int ticks=0;
        try{for(int worker=0;worker<2;worker++){int w=worker;futures.add(pool.submit(()->{
                int used=0;for(int group=0;group<GROUPS;group++){var window=GhostSourceWalkPrefetch.around(new Cell(group*5,76,w*20));captures.get(w).prefetch(window);
                    int count=group<44?36:35;for(int i=0;i<count;i++){var cell=window.get(i);var value=captures.get(w).require(cell);while(readingNativeTick.get())Thread.onSpinWait();assertEquals(cell.x()*31+cell.y()*17+cell.z(),value.intValue());used++;}}
                return used;}));}
            while(futures.stream().anyMatch(f->!f.isDone())){
                assertTrue(ticks++<1000,"model tick budget");
                // Conservative deterministic schedule: worker processes results between frames, then parks on its next exact demand.
                // Same-pump wakeups are deliberately unavailable, isolating the request/frame latency rather than host scheduler luck.
                for(int i=0;i<2;i++)awaitDemandOrFinish(captures.get(i),futures.get(i));clock.set(0);readingNativeTick.set(true);
                for(var capture:captures){long remaining=4_000_000-clock.get();if(remaining<=0)break;
                    capture.pump(Math.min(2_000_000,remaining),clock::get,()->true,cell->{assertSame(mainThread,Thread.currentThread());reads.incrementAndGet();clock.addAndGet(25_000);return cell.x()*31+cell.y()*17+cell.z();});}
                readingNativeTick.set(false);maximum=Math.max(maximum,clock.get());
            }
            int required=0;for(int i=0;i<2;i++){required+=futures.get(i).get(2,TimeUnit.SECONDS);assertEquals(REQUIRED_PER_WORKER,captures.get(i).usedSnapshot().size());}
            return new Result(ticks,reads.get(),required,maximum);
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
        catch(ExecutionException|TimeoutException error){throw new AssertionError(error);}
        finally{readingNativeTick.set(false);captures.forEach(GhostSourceWalkDemandCapture::revoke);pool.shutdownNow();try{assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}
    }
    private static void awaitDemandOrFinish(GhostSourceWalkDemandCapture<?,?> capture,Future<?> worker){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!worker.isDone()&&!capture.hasPendingRequiredReads()&&System.nanoTime()<deadline)Thread.onSpinWait();assertTrue(worker.isDone()||capture.hasPendingRequiredReads(),"worker must finish or publish a demand between model ticks");}
}
