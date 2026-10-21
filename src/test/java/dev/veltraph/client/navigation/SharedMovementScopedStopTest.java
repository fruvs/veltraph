package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Pure callback/restart ownership cases; a Port invocation is never a native receipt in these tests. */
class SharedMovementScopedStopTest {
    private static final Vec3 GOAL=new Vec3(12,70,0),NEXT=new Vec3(24,70,0);
    private static SharedMovementPolicy.Options options(){return new SharedMovementPolicy.Options(Set.of(SharedMovementPolicy.Mode.WALK),SharedMovementPolicy.Purpose.GENERAL_TRAVEL,0,1000,.3,1.25);}
    private static SharedMovementCoordinator.Lease lease(){return new SharedMovementCoordinator.Lease(new Object(),new Object(),new Object(),new Object(),new Object(),new Object(),new Object(),4,9);}
    private record StopCall(SharedMovementCoordinator.Lease lease,SharedMovementCoordinator.Issue issue) {}
    private static final class Port implements SharedMovementCoordinator.Port {
        long now=100;int begins,ticks,clockReads;boolean reuseIssue;Runnable stopped=()->{};
        final SharedMovementPolicy.Candidate candidate=new SharedMovementPolicy.Candidate("ground",SharedMovementPolicy.Mode.WALK,GOAL,12,0,new Object(),new Object(),null);
        final List<StopCall> calls=new ArrayList<>();SharedMovementCoordinator.Issue issued;
        public long now(){clockReads++;return now;}
        public boolean owned(SharedMovementCoordinator.Lease original){return true;}
        public List<SharedMovementPolicy.Candidate> candidates(Vec3 goal,SharedMovementPolicy.Options options,int uses){return List.of(candidate);}
        public boolean permitted(SharedMovementCoordinator.Lease original,SharedMovementPolicy.Candidate value){return true;}
        public SharedMovementCoordinator.Issue begin(SharedMovementCoordinator.Lease original,SharedMovementPolicy.Candidate value){
            begins++;if(!reuseIssue||issued==null)issued=new SharedMovementCoordinator.Issue(new Object(),new Object(),30,now,false);return issued;
        }
        public SharedMovementCoordinator.Progress tick(SharedMovementCoordinator.Lease original,SharedMovementPolicy.Candidate value,SharedMovementCoordinator.Issue issue){ticks++;return SharedMovementCoordinator.Progress.ACTIVE;}
        public SharedMovementCoordinator.AppliedReceipt receipt(SharedMovementCoordinator.Lease original,SharedMovementCoordinator.Issue issue){return null;}
        public boolean at(Vec3 destination,double radius){return false;}
        public void stopOwned(SharedMovementCoordinator.Lease original,SharedMovementCoordinator.Issue issue){calls.add(new StopCall(original,issue));stopped.run();}
    }
    private static long generation(SharedMovementCoordinator coordinator){
        try{var field=SharedMovementCoordinator.class.getDeclaredField("cleanupGeneration");field.setAccessible(true);return field.getLong(coordinator);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    @Test void normalOriginalStopRetiresOnceWithoutClockReadOrSecondPortStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var lease=lease();assertTrue(s.start(lease,GOAL,options()));var issue=p.issued;int clocks=p.clockReads;
        assertTrue(s.stopScoped(()->true));assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());assertNull(s.selected());
        assertEquals(List.of(new StopCall(lease,issue)),p.calls);assertEquals(clocks,p.clockReads);
        assertFalse(s.stopScoped(()->true));assertEquals(1,p.calls.size());assertEquals(0,p.ticks);
    }
    @Test void vetoBeforeSubmissionDoesNotStopOrChangeOriginalTransaction(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var selected=s.selected();long original=generation(s);
        assertFalse(s.stopScoped(()->false));assertSame(selected,s.selected());assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertEquals(original,generation(s));assertTrue(p.calls.isEmpty());
    }
    @Test void reentrantNewLeaseDuringPortCleanupSurvivesOldScopedStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var first=lease();var next=lease();assertTrue(s.start(first,GOAL,options()));var original=p.issued;
        p.stopped=()->{p.stopped=()->{};assertTrue(s.start(next,NEXT,options()));};
        assertFalse(s.stopScoped(()->true));assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());assertNotSame(original,p.issued);
        // The nested start retains its original legacy stop call; scoped cleanup adds no later replacement stop.
        assertEquals(2,p.calls.size());assertTrue(p.calls.stream().allMatch(call->call.lease()==first&&call.issue()==original));assertEquals(2,p.begins);
    }
    @Test void sameObjectsAndIssueRestartStillAdvancesPrivateGenerationAndSurvives(){
        var p=new Port();p.reuseIssue=true;var s=new SharedMovementCoordinator(p);var originalLease=lease();var originalOptions=options();assertTrue(s.start(originalLease,GOAL,originalOptions));long before=generation(s);var originalIssue=p.issued;
        p.stopped=()->{p.stopped=()->{};assertTrue(s.start(originalLease,GOAL,originalOptions));};
        assertFalse(s.stopScoped(()->true));assertEquals(before+1,generation(s));assertSame(originalIssue,p.issued);assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertSame(p.candidate,s.selected());
    }
    @Test void sameValueDistinctLeaseRestartCannotBeAdopted(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var old=lease();assertTrue(s.start(old,GOAL,options()));
        var copied=new SharedMovementCoordinator.Lease(old.module(),old.player(),old.world(),old.connection(),old.inputOwner(),old.cameraOwner(),old.configStore(),old.configRevision(),old.cameraEpoch());assertTrue(old.same(copied));assertNotSame(old,copied);
        p.stopped=()->{p.stopped=()->{};assertTrue(s.start(copied,GOAL,options()));};
        assertFalse(s.stopScoped(()->true));assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());
    }
    @Test void restartInsideFirstVetoPreventsOldPortSubmission(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var first=lease();assertTrue(s.start(first,GOAL,options()));var issue=p.issued;
        assertFalse(s.stopScoped(()->{assertTrue(s.start(lease(),NEXT,options()));return true;}));
        assertEquals(1,p.calls.size());assertSame(issue,p.calls.getFirst().issue());assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertEquals(2,p.begins);
    }
    @Test void finalVetoRestartPreservesNewJourneyAfterOriginalNativeAttempt(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var first=lease();assertTrue(s.start(first,GOAL,options()));var calls=new AtomicInteger();
        assertFalse(s.stopScoped(()->{if(calls.incrementAndGet()==2)assertTrue(s.start(lease(),NEXT,options()));return true;}));
        assertEquals(2,p.begins);assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());assertTrue(p.calls.stream().allMatch(c->c.lease()==first));
    }
    @Test void lateVetoWithholdsRetirementButNeverRepeatsSameOriginalPortStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var checks=new AtomicInteger();
        assertFalse(s.stopScoped(()->checks.incrementAndGet()==1));assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertEquals(1,p.calls.size());
        assertTrue(s.stopScoped(()->true));assertEquals(1,p.calls.size());assertFalse(s.stopScoped(()->true));assertEquals(1,p.calls.size());
    }
    @Test void ordinaryClockMetadataChangeDoesNotReplayAlreadyAttemptedSameIssueStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var checks=new AtomicInteger();
        assertFalse(s.stopScoped(()->checks.incrementAndGet()==1));long began=s.originalBeganAt(),deadline=s.originalDeadlineAt();p.now++;
        assertEquals(SharedMovementCoordinator.State.MOVING,s.tick());assertEquals(began,s.originalBeganAt());assertEquals(deadline,s.originalDeadlineAt());
        assertTrue(s.stopScoped(()->true));assertEquals(1,p.calls.size());
    }
    @Test void thrownOriginalPortAfterRestartPreservesReplacementAndOriginalException(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var thrown=new IllegalStateException("original stop");
        p.stopped=()->{p.stopped=()->{};assertTrue(s.start(lease(),NEXT,options()));throw thrown;};
        assertSame(thrown,assertThrows(IllegalStateException.class,()->s.stopScoped(()->true)));assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());assertEquals(2,p.begins);
    }
    @Test void nativeAndFinalVetoExceptionsKeepOriginalFailureWithoutMetadataRetirement(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var original=new IllegalStateException("port");var late=new AssertionError("final veto");p.stopped=()->{throw original;};var checks=new AtomicInteger();
        assertSame(original,assertThrows(IllegalStateException.class,()->s.stopScoped(()->{if(checks.incrementAndGet()==2)throw late;return true;})));
        assertEquals(List.of(late),Arrays.asList(original.getSuppressed()));assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertEquals(1,p.calls.size());
    }
    @Test void failedOriginalAttemptIsNotRetriedWhenLaterMetadataRetirementIsAllowed(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var original=new IllegalStateException("partial stop");p.stopped=()->{throw original;};var checks=new AtomicInteger();
        assertSame(original,assertThrows(IllegalStateException.class,()->s.stopScoped(()->checks.incrementAndGet()==1)));assertEquals(1,p.calls.size());
        assertSame(original,assertThrows(IllegalStateException.class,()->s.stopScoped(()->true)));assertEquals(1,p.calls.size());assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());
    }
    @Test void nestedScopedStopCannotSubmitAnotherPortCallWhileOriginalIsInFlight(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));var nested=new AtomicBoolean(true);
        p.stopped=()->nested.set(s.stopScoped(()->true));assertTrue(s.stopScoped(()->true));assertFalse(nested.get());assertEquals(1,p.calls.size());
    }
    @Test void exhaustedPrivateHistoryDeniesScopedMetadataWithoutChangingLegacyStartStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);
        try{var field=SharedMovementCoordinator.class.getDeclaredField("cleanupGeneration");field.setAccessible(true);field.setLong(s,Long.MAX_VALUE);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
        assertTrue(s.start(lease(),GOAL,options()));assertFalse(s.stopScoped(()->true));assertTrue(p.calls.isEmpty());
        s.stop();assertEquals(1,p.calls.size());assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());
    }

    @Test void initialEmptyKernelRetiresOnlyMetadataOnceWithoutPortStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);long before=generation(s);
        assertTrue(s.retireEmptyScoped(()->true));assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());assertEquals(before+1,generation(s));assertTrue(p.calls.isEmpty());
        assertFalse(s.retireEmptyScoped(()->true));assertEquals(0,p.begins);assertEquals(0,p.clockReads);
    }
    @Test void failedAdmissionBeforeLeaseCanRetireExactAlreadyStoppedEmptyMetadataOnce(){
        var p=new Port();var s=new SharedMovementCoordinator(p);
        assertFalse(s.start(null,GOAL,options()));assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());long before=generation(s);
        assertTrue(s.retireEmptyScoped(()->true));assertEquals(before+1,generation(s));assertTrue(p.calls.isEmpty());
        assertFalse(s.retireEmptyScoped(()->true));assertEquals(0,p.begins);
    }
    @Test void nonemptyKernelCannotUseMetadataOnlyEmptyBranch(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));
        assertFalse(s.retireEmptyScoped(()->true));assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertTrue(p.calls.isEmpty());
    }
    @Test void emptyVetoDoesNotChangeStateGenerationOrSubmitNativeStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);long before=generation(s);
        assertFalse(s.retireEmptyScoped(()->false));assertEquals(SharedMovementCoordinator.State.IDLE,s.state());assertEquals(before,generation(s));assertTrue(p.calls.isEmpty());
    }
    @Test void emptyEarlyCallbackNewJourneyIsPreservedWithoutPortStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);
        assertFalse(s.retireEmptyScoped(()->{assertTrue(s.start(lease(),GOAL,options()));return true;}));
        assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());assertTrue(p.calls.isEmpty());assertEquals(1,p.begins);
    }
    @Test void emptyFinalCallbackNewJourneyIsPreservedWithoutPortStop(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var checks=new AtomicInteger();
        assertFalse(s.retireEmptyScoped(()->{if(checks.incrementAndGet()==2)assertTrue(s.start(lease(),GOAL,options()));return true;}));
        assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());assertTrue(p.calls.isEmpty());assertEquals(1,p.begins);
    }
    @Test void emptySameValueStoppedABAFromRealFailedStartCannotRetireOldSnapshot(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertFalse(s.start(null,GOAL,options()));long before=generation(s);var checks=new AtomicInteger();
        assertFalse(s.retireEmptyScoped(()->{if(checks.incrementAndGet()==2)assertFalse(s.start(null,GOAL,options()));return true;}));
        assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());assertEquals(before+1,generation(s));assertTrue(p.calls.isEmpty());
    }
    @Test void throwingFinalEmptyCallbackPreservesNewJourneyAndOriginalFailure(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var failure=new AssertionError("empty final source");var checks=new AtomicInteger();
        assertSame(failure,assertThrows(AssertionError.class,()->s.retireEmptyScoped(()->{if(checks.incrementAndGet()==2){assertTrue(s.start(lease(),GOAL,options()));throw failure;}return true;})));
        assertEquals(SharedMovementCoordinator.State.MOVING,s.state());assertNotNull(s.selected());assertTrue(p.calls.isEmpty());
    }
    @Test void lateEmptyVetoKeepsOriginalUntilOneLaterMetadataRetirement(){
        var p=new Port();var s=new SharedMovementCoordinator(p);var checks=new AtomicInteger();
        assertFalse(s.retireEmptyScoped(()->checks.incrementAndGet()==1));assertEquals(SharedMovementCoordinator.State.IDLE,s.state());
        assertTrue(s.retireEmptyScoped(()->true));assertFalse(s.retireEmptyScoped(()->true));assertTrue(p.calls.isEmpty());
    }
    @Test void completedAllocatedScopedStopCannotThenRetireItsEmptyMetadataAgain(){
        var p=new Port();var s=new SharedMovementCoordinator(p);assertTrue(s.start(lease(),GOAL,options()));assertTrue(s.stopScoped(()->true));
        assertFalse(s.retireEmptyScoped(()->true));assertEquals(1,p.calls.size());assertEquals(SharedMovementCoordinator.State.STOPPED,s.state());
    }
}
