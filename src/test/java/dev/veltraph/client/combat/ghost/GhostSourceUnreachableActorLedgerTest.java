package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceUnreachableActorLedgerTest {
    private static GhostSourcePathJobTransaction.Scope scope() {
        return new GhostSourcePathJobTransaction.Scope(new Object(),new Object(),
                new GhostSourceOwnedParentArea.Stamp(new Object(),1,2,new Object(),new Object(),new Object()));
    }
    private static GhostSourceUnreachableActorLedger.Failure write(Object actor) {
        return new GhostSourceUnreachableActorLedger.Failure(actor,GhostSourceUnreachableActorLedger.SourceFailurePredicate.WRITE_LEDGER);
    }
    private static void record(GhostSourceUnreachableActorLedger ledger,GhostSourcePathJobTransaction.Scope scope,Object actor,long now) {
        var token=ledger.issue(scope,List.of(actor));
        assertEquals(1,ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),now).written());
    }
    @Test void exactExpiryEqualityRetainsAndNextMillisecondRemovesOnlyAtWrapperCleanup() {
        var ledger=new GhostSourceUnreachableActorLedger();var actor=new Object();record(ledger,scope(),actor,100);
        assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(1,30100));assertTrue(ledger.containsIdentity(actor));
        assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,30101));assertFalse(ledger.containsIdentity(actor));
    }
    @Test void emptyCandidateWrapperReturnsBeforeCleanupAndClockRead() {
        var ledger=new GhostSourceUnreachableActorLedger();var actor=new Object();record(ledger,scope(),actor,100);
        assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(0,(java.util.function.LongSupplier)null));assertTrue(ledger.containsIdentity(actor));
        assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,100000));
    }
    @Test void membershipDoesNotExpireOrClearDuringAnotherWorldOrModuleScope() {
        var ledger=new GhostSourceUnreachableActorLedger();var actor=new Object();record(ledger,scope(),actor,0);
        var other=scope();ledger.issue(other,List.of(new Object())).revoke();
        for(int i=0;i<50;i++)assertTrue(ledger.containsIdentity(actor));
        assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,30001));
    }
    @Test void sameUuidAndEqualObjectsNeverMergeDifferentNativeEntities() {
        record Actor(UUID uuid) {}
        var uuid=UUID.randomUUID();var a=new Actor(uuid);var b=new Actor(uuid);assertEquals(a,b);assertNotSame(a,b);
        var ledger=new GhostSourceUnreachableActorLedger();record(ledger,scope(),a,0);
        assertTrue(ledger.containsIdentity(a));assertFalse(ledger.containsIdentity(b));
    }
    @Test void captureRevokedAndSuccessfulPathsCannotMasqueradeAsSourceFailure() {
        for(var outcome:List.of(GhostSourceUnreachableActorLedger.NativeOutcome.CAPTURE_REVOKED,
                GhostSourceUnreachableActorLedger.NativeOutcome.READY_WITH_PATH,GhostSourceUnreachableActorLedger.NativeOutcome.UNBOUND)) {
            var ledger=new GhostSourceUnreachableActorLedger();var actor=new Object();var scope=scope();var token=ledger.issue(scope,List.of(actor));
            assertEquals(0,ledger.recordReadyNoPath(token,scope,outcome,List.of(write(actor)),0).written());assertFalse(ledger.containsIdentity(actor));
            assertEquals(GhostSourceUnreachableActorLedger.Outcome.REPLAY,ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),0).outcome());
        }
    }
    @Test void staleJobGenerationSettingsChildOrPwcRejectWritesAndConsumeToken() {
        var s=scope();var p=s.parent();
        var alternatives=List.of(new GhostSourcePathJobTransaction.Scope(new Object(),s.child(),p),
                new GhostSourcePathJobTransaction.Scope(s.job(),new Object(),p),
                new GhostSourcePathJobTransaction.Scope(s.job(),s.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),2,p.settingsRevision(),p.player(),p.world(),p.connection())),
                new GhostSourcePathJobTransaction.Scope(s.job(),s.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),1,3,p.player(),p.world(),p.connection())),
                new GhostSourcePathJobTransaction.Scope(s.job(),s.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),1,2,new Object(),p.world(),p.connection())),
                new GhostSourcePathJobTransaction.Scope(s.job(),s.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),1,2,p.player(),new Object(),p.connection())),
                new GhostSourcePathJobTransaction.Scope(s.job(),s.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),1,2,p.player(),p.world(),new Object())));
        for(var actual:alternatives) {
            var ledger=new GhostSourceUnreachableActorLedger();var actor=new Object();var token=ledger.issue(s,List.of(actor));
            assertEquals(GhostSourceUnreachableActorLedger.Outcome.STALE,ledger.recordReadyNoPath(token,actual,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),0).outcome());
            assertFalse(ledger.containsIdentity(actor));assertEquals(GhostSourceUnreachableActorLedger.Outcome.REPLAY,ledger.recordReadyNoPath(token,s,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),0).outcome());
        }
    }
    @Test void sourcePredicateTrueAndUnknownDoNotWriteButKnownFalseDoes() {
        var ledger=new GhostSourceUnreachableActorLedger();var scope=scope();var a=new Object();var b=new Object();var c=new Object();var token=ledger.issue(scope,List.of(a,b,c));
        var results=List.of(new GhostSourceUnreachableActorLedger.Failure(a,GhostSourceUnreachableActorLedger.SourceFailurePredicate.SKIP_LEDGER),
                new GhostSourceUnreachableActorLedger.Failure(b,GhostSourceUnreachableActorLedger.SourceFailurePredicate.UNKNOWN),write(c));
        var result=ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,results,0);
        assertEquals(1,result.written());assertEquals(1,result.predicateSkipped());assertEquals(1,result.unknownSkipped());
        assertFalse(ledger.containsIdentity(a));assertFalse(ledger.containsIdentity(b));assertTrue(ledger.containsIdentity(c));
    }
    @Test void outsideCohortDuplicateAndForeignLedgerTokenAreRejectedWithoutPartialWrite() {
        var ledger=new GhostSourceUnreachableActorLedger();var scope=scope();var actor=new Object();
        var token=ledger.issue(scope,List.of(actor));var out=ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor),write(new Object())),0);
        assertEquals(GhostSourceUnreachableActorLedger.Outcome.REJECTED,out.outcome());assertFalse(ledger.containsIdentity(actor));
        var duplicate=ledger.issue(scope,List.of(actor));assertEquals(GhostSourceUnreachableActorLedger.Outcome.REJECTED,ledger.recordReadyNoPath(duplicate,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor),write(actor)),0).outcome());
        var foreign=new GhostSourceUnreachableActorLedger();var foreignToken=foreign.issue(scope,List.of(actor));
        assertEquals(GhostSourceUnreachableActorLedger.Outcome.REJECTED,ledger.recordReadyNoPath(foreignToken,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),0).outcome());
    }
    @Test void actualNewFailureRefreshesTimestampButReplayAndRevocationCannotRefresh() {
        var ledger=new GhostSourceUnreachableActorLedger();var scope=scope();var actor=new Object();var token=ledger.issue(scope,List.of(actor));
        ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),0);
        ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),20000);
        var revoked=ledger.issue(scope,List.of(actor));revoked.revoke();
        assertEquals(GhostSourceUnreachableActorLedger.Outcome.REPLAY,ledger.recordReadyNoPath(revoked,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,List.of(write(actor)),20000).outcome());
        record(ledger,scope,actor,20000);assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(1,30001));
        assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(1,50000));assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,50001));
    }
    @Test void wallClockRollbackRetainsEntryAndPerEntryCleanupSamplesClockIndividually() {
        var ledger=new GhostSourceUnreachableActorLedger();var scope=scope();var a=new Object();var b=new Object();record(ledger,scope,a,100);record(ledger,scope,b,100);
        assertEquals(0,ledger.cleanupAtNonemptyWrapperInvocation(1,-200));
        var calls=new AtomicInteger();assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,()->30100+calls.getAndIncrement()));assertEquals(2,calls.get());
        assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,30101));
    }
    @Test void eachKnownFailureSamplesActualClockAndSkippedPredicateDoesNotSample() {
        var ledger=new GhostSourceUnreachableActorLedger();var scope=scope();var a=new Object();var b=new Object();var skipped=new Object();
        var token=ledger.issue(scope,List.of(a,b,skipped));var calls=new AtomicInteger();
        var results=List.of(write(a),new GhostSourceUnreachableActorLedger.Failure(skipped,GhostSourceUnreachableActorLedger.SourceFailurePredicate.SKIP_LEDGER),write(b));
        assertEquals(2,ledger.recordReadyNoPath(token,scope,GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,results,()->100+calls.getAndIncrement()).written());
        assertEquals(2,calls.get());assertEquals(1,ledger.cleanupAtNonemptyWrapperInvocation(1,30101));
        assertFalse(ledger.containsIdentity(a));assertTrue(ledger.containsIdentity(b));
    }

}
