package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Saturation tests temporarily occupy the shared fixed pool, so this class runs in isolation. */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GhostSourceWalkResumableExecutorTest {
    private static final int TIMEOUT_SECONDS=3;

    @Test void nineWaitingTasksReleaseBothSolverSlotsBeforeMainThreadCompletionPump() throws Exception {
        var allWaiting=new CountDownLatch(9);
        var markerEntered=new CountDownLatch(2);
        var releaseMarkers=new CountDownLatch(1);
        var allCompleted=new CountDownLatch(9);
        var attempts=new AtomicIntegerArray(9);
        var active=new AtomicIntegerArray(9);
        var overlaps=new AtomicInteger();
        var finalResults=new AtomicInteger();
        var resolutions=new AtomicInteger();
        var availability=new ArrayList<CompletableFuture<Void>>();
        var tasks=new ArrayList<GhostSourceWalkSolverExecutor.Task<Integer>>();
        var markers=new ArrayList<GhostSourceWalkSolverExecutor.Task<Boolean>>();
        try {
            for(int id=0;id<9;id++) {
                int index=id;
                var cell=new CompletableFuture<Void>();
                availability.add(cell);
                var task=GhostSourceWalkSolverExecutor.submitResumable(()->{
                    assertTrue(Thread.currentThread().getName().startsWith("Veltraph-Ghost-Solver-"));
                    if(active.incrementAndGet(index)!=1)overlaps.incrementAndGet();
                    try {
                        if(attempts.incrementAndGet(index)==1){allWaiting.countDown();throw new GhostSourceWalkPendingCell(cell);}
                        assertTrue(cell.isDone());
                        return index;
                    } finally {active.decrementAndGet(index);}
                },resolutions::incrementAndGet);
                task.future().whenComplete((value,error)->{if(error==null)finalResults.incrementAndGet();allCompleted.countDown();});
                tasks.add(task);
            }
            await(allWaiting);
            for(int slot=0;slot<2;slot++)markers.add(GhostSourceWalkSolverExecutor.submit(()->{
                markerEntered.countDown();awaitInWorker(releaseMarkers);return true;
            },()->{}));
            await(markerEntered);
            // Both original solver slots are now occupied by unrelated work: all nine tasks yielded.
            for(var task:tasks){assertFalse(task.executing());assertFalse(task.future().isDone());}
            var pumpThread=Thread.currentThread();
            int nativeLikeReads=0;
            for(var cell:availability){assertSame(pumpThread,Thread.currentThread());nativeLikeReads++;cell.complete(null);}
            assertEquals(9,nativeLikeReads);
            releaseMarkers.countDown();
            for(int id=0;id<9;id++)assertEquals(id,tasks.get(id).future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
            await(allCompleted);
            assertEquals(9,finalResults.get());assertEquals(0,overlaps.get());assertEquals(0,resolutions.get());
            for(int id=0;id<9;id++)assertEquals(2,attempts.get(id));
            for(var marker:markers)assertTrue(marker.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
        } finally {releaseMarkers.countDown();cancelUnfinished(tasks);cancelUnfinished(markers);}
    }

    @Test void sameTaskCanYieldAtTwoSuccessiveCellsAndCompleteOnce() throws Exception {
        var first=new CompletableFuture<Void>();var second=new CompletableFuture<Void>();
        var firstWait=new CountDownLatch(1);var secondWait=new CountDownLatch(1);
        var attempts=new AtomicInteger();var finalResults=new AtomicInteger();
        var task=GhostSourceWalkSolverExecutor.submitResumable(()->{
            attempts.incrementAndGet();
            if(!first.isDone()){firstWait.countDown();throw new GhostSourceWalkPendingCell(first);}
            if(!second.isDone()){secondWait.countDown();throw new GhostSourceWalkPendingCell(second);}
            return "complete";
        },()->{});
        task.future().thenRun(finalResults::incrementAndGet);
        try {
            await(firstWait);drainSlots();assertFalse(task.executing());
            first.complete(null);await(secondWait);drainSlots();assertFalse(task.executing());
            second.complete(null);assertEquals("complete",task.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
            drainSlots();assertEquals(3,attempts.get());assertEquals(1,finalResults.get());
        } finally {if(!task.future().isDone())task.cancel();}
    }

    @Test void cancellationResolvesCaptureBeforePublicCancellationAndSuppressesSynchronousWakeup() throws Exception {
        var cell=new CompletableFuture<Void>();var waiting=new CountDownLatch(1);var attempts=new AtomicInteger();
        var taskRef=new AtomicReference<GhostSourceWalkSolverExecutor.Task<Integer>>();
        var resolutions=new AtomicInteger();var futureDoneDuringResolution=new AtomicBoolean();
        var task=GhostSourceWalkSolverExecutor.<Integer>submitResumable(()->{
            attempts.incrementAndGet();waiting.countDown();throw new GhostSourceWalkPendingCell(cell);
        },()->{
            resolutions.incrementAndGet();futureDoneDuringResolution.set(taskRef.get().future().isDone());
            cell.complete(null);
        });
        taskRef.set(task);
        await(waiting);drainSlots();task.cancel();drainSlots();
        assertFalse(futureDoneDuringResolution.get());assertEquals(1,resolutions.get());assertEquals(1,attempts.get());
        assertFalse(task.executing());assertThrows(CancellationException.class,task.future()::join);
    }

    @Test void lateAvailabilityAfterCancellationCannotResurrectTask() throws Exception {
        var cell=new CompletableFuture<Void>();var waiting=new CountDownLatch(1);var attempts=new AtomicInteger();
        var resolutions=new AtomicInteger();
        var task=GhostSourceWalkSolverExecutor.submitResumable(()->{
            if(attempts.incrementAndGet()==1){waiting.countDown();throw new GhostSourceWalkPendingCell(cell);}
            return 7;
        },resolutions::incrementAndGet);
        await(waiting);drainSlots();task.cancel();cell.complete(null);drainSlots();
        assertEquals(1,attempts.get());assertEquals(1,resolutions.get());assertFalse(task.executing());
        assertThrows(CancellationException.class,task.future()::join);
    }

    @Test void availabilityReadyBeforeCallbackRegistrationStillResumesWithoutOverlap() throws Exception {
        var cell=new CompletableFuture<Void>();var attempts=new AtomicInteger();var active=new AtomicInteger();
        var overlaps=new AtomicInteger();
        var task=GhostSourceWalkSolverExecutor.submitResumable(()->{
            if(active.incrementAndGet()!=1)overlaps.incrementAndGet();
            try {
                if(attempts.incrementAndGet()==1){cell.complete(null);throw new GhostSourceWalkPendingCell(cell);}
                return 42;
            } finally {active.decrementAndGet();}
        },()->{});
        try {assertEquals(42,task.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));assertEquals(2,attempts.get());assertEquals(0,overlaps.get());}
        finally {if(!task.future().isDone())task.cancel();}
    }

    @Test void exceptionalAvailabilityRetriesSupplierAndPreservesOriginalCaptureFailure() throws Exception {
        var cell=new CompletableFuture<Void>();var waiting=new CountDownLatch(1);var attempts=new AtomicInteger();
        var originalFailure=new IllegalStateException("capture revoked");
        var task=GhostSourceWalkSolverExecutor.submitResumable(()->{
            if(attempts.incrementAndGet()==1){waiting.countDown();throw new GhostSourceWalkPendingCell(cell);}
            // Models the resumed capture API decoding its original failure.
            throw originalFailure;
        },()->{});
        try {
            await(waiting);cell.completeExceptionally(originalFailure);
            var failure=assertThrows(ExecutionException.class,()->task.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
            assertSame(originalFailure,failure.getCause());assertEquals(2,attempts.get());
        } finally {if(!task.future().isDone())task.cancel();}
    }

    @Test void cancellationDuringBodyDoesNotInterruptOrPublishLateValue() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var exited=new CountDownLatch(1);
        var interrupted=new AtomicBoolean();var resolved=new AtomicBoolean();
        var taskRef=new AtomicReference<GhostSourceWalkSolverExecutor.Task<Integer>>();
        var task=GhostSourceWalkSolverExecutor.submitResumable(()->{
            entered.countDown();
            try {awaitInWorker(release);interrupted.set(Thread.currentThread().isInterrupted());return 8;}
            finally {exited.countDown();}
        },()->{assertFalse(taskRef.get().future().isDone());resolved.set(true);});
        taskRef.set(task);
        try {
            await(entered);assertTrue(task.executing());task.cancel();assertTrue(resolved.get());assertFalse(task.executing());
            release.countDown();await(exited);drainSlots();assertFalse(interrupted.get());
            assertThrows(CancellationException.class,task.future()::join);
        } finally {release.countDown();if(!task.future().isDone())task.cancel();}
    }

    @Test void queuedCancellationResolvesCaptureAndPreventsFirstBodyInvocation() throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        var blockers=new ArrayList<GhostSourceWalkSolverExecutor.Task<Boolean>>();
        var attempts=new AtomicInteger();var resolved=new AtomicInteger();
        var taskRef=new AtomicReference<GhostSourceWalkSolverExecutor.Task<Integer>>();
        try {
            occupyBothSlots(blockers,entered,release);
            var task=GhostSourceWalkSolverExecutor.submitResumable(attempts::incrementAndGet,()->{
                assertFalse(taskRef.get().future().isDone());resolved.incrementAndGet();
            });
            taskRef.set(task);assertFalse(task.executing());task.cancel();
            release.countDown();for(var blocker:blockers)blocker.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS);
            drainSlots();assertEquals(0,attempts.get());assertEquals(1,resolved.get());
            assertThrows(CancellationException.class,task.future()::join);
        } finally {release.countDown();cancelUnfinished(blockers);var task=taskRef.get();if(task!=null&&!task.future().isDone())task.cancel();}
    }

    @Test void boundedQueueRejectsInitialAndResumedSubmissionAndResolvesEachCapture() throws Exception {
        var cell=new CompletableFuture<Void>();var waiting=new CountDownLatch(1);var resumeAttempts=new AtomicInteger();
        var resumedResolutions=new AtomicInteger();var initialResolutions=new AtomicInteger();
        var resumedRef=new AtomicReference<GhostSourceWalkSolverExecutor.Task<Integer>>();
        var resumed=GhostSourceWalkSolverExecutor.<Integer>submitResumable(()->{
            resumeAttempts.incrementAndGet();waiting.countDown();throw new GhostSourceWalkPendingCell(cell);
        },()->{assertFalse(resumedRef.get().future().isDone());resumedResolutions.incrementAndGet();});
        resumedRef.set(resumed);
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        var blockers=new ArrayList<GhostSourceWalkSolverExecutor.Task<Boolean>>();
        var initialAttempts=new AtomicInteger();
        try {
            await(waiting);drainSlots();occupyBothSlots(blockers,entered,release);
            for(int queued=0;queued<32;queued++)blockers.add(GhostSourceWalkSolverExecutor.submit(()->{awaitInWorker(release);return true;},()->{}));
            var initial=GhostSourceWalkSolverExecutor.submitResumable(initialAttempts::incrementAndGet,initialResolutions::incrementAndGet);
            var initialFailure=assertThrows(ExecutionException.class,()->initial.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class,initialFailure.getCause());
            cell.complete(null);
            var resumedFailure=assertThrows(ExecutionException.class,()->resumed.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class,resumedFailure.getCause());
            assertEquals(0,initialAttempts.get());assertEquals(1,resumeAttempts.get());
            assertEquals(1,initialResolutions.get());assertEquals(1,resumedResolutions.get());
            cell.complete(null);assertFalse(initial.executing());assertFalse(resumed.executing());
            release.countDown();for(var blocker:blockers)assertTrue(blocker.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
            drainSlots();assertEquals(1,resumeAttempts.get());
        } finally {release.countDown();cancelUnfinished(blockers);if(!resumed.future().isDone())resumed.cancel();}
    }

    @Test void originalSubmitStillReturnsResultAndOriginalFailure() throws Exception {
        var original=new IllegalArgumentException("legacy supplier");
        var value=GhostSourceWalkSolverExecutor.submit(()->11,()->{});
        var failed=GhostSourceWalkSolverExecutor.submit(()->{throw original;},()->{});
        assertEquals(11,value.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
        var failure=assertThrows(ExecutionException.class,()->failed.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
        assertSame(original,failure.getCause());assertFalse(value.executing());assertFalse(failed.executing());
    }

    private static void occupyBothSlots(List<GhostSourceWalkSolverExecutor.Task<Boolean>> blockers,
            CountDownLatch entered,CountDownLatch release) throws InterruptedException {
        for(int slot=0;slot<2;slot++)blockers.add(GhostSourceWalkSolverExecutor.submit(()->{
            entered.countDown();awaitInWorker(release);return true;
        },()->{}));
        await(entered);
    }
    private static void drainSlots() throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        var markers=new ArrayList<GhostSourceWalkSolverExecutor.Task<Boolean>>();
        try {
            occupyBothSlots(markers,entered,release);
            release.countDown();
            for(var marker:markers)assertTrue(marker.future().get(TIMEOUT_SECONDS,TimeUnit.SECONDS));
        } finally {release.countDown();cancelUnfinished(markers);}
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(TIMEOUT_SECONDS,TimeUnit.SECONDS),"bounded executor test timed out");
    }
    private static void awaitInWorker(CountDownLatch latch) {
        try {if(!latch.await(TIMEOUT_SECONDS,TimeUnit.SECONDS))throw new IllegalStateException("bounded worker test timed out");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
    }
    private static void cancelUnfinished(List<? extends GhostSourceWalkSolverExecutor.Task<?>> tasks) {
        for(var task:tasks)if(!task.future().isDone())task.cancel();
    }
}
