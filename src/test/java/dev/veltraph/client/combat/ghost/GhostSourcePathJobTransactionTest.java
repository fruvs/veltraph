package dev.veltraph.client.combat.ghost;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.ghost.GhostSourcePathJobTransaction.*;
class GhostSourcePathJobTransactionTest {
    /** Model CAS adapter only. These tests do not bind native Root or publish its source SEARCH child. */
    static class Model implements RuntimeParentAdapter {
        Scope scope;Target actual;CommittedSelection selection;boolean reject;boolean rejectRestart;boolean mutateOnLookup;int commits,restarts;
        Model(){scope=new Scope(new Object(),new Object(),new GhostSourceOwnedParentArea.Stamp(new Object(),1,1,new Object(),new Object(),new Object()));actual=new Target(new Object(),UUID.randomUUID());}
        public Scope captureActualCurrentJob(){return scope;}
        public Target findActualCurrentTarget(UUID uuid){if(mutateOnLookup)changeSettings();return actual!=null&&actual.nativeUuid().equals(uuid)?actual:null;}
        void changeSettings(){var p=scope.parent();scope=new Scope(scope.job(),scope.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),p.generation(),p.settingsRevision()+1,p.player(),p.world(),p.connection()));}
        public CommittedSelection commitTargetAndCursor(Scope expected,Target target,WalkPath path){
            if(reject||!expected.same(scope))return null;commits++;var p=scope.parent();
            return selection=new CommittedSelection(p.parent(),scope.child(),new Object(),p.generation(),p.settingsRevision(),p.player(),p.world(),p.connection(),target,path.nativeSourcePathIdentity());}
        public boolean dispatchFailedPathContinuation(Scope expected,Target failedTarget){if(rejectRestart||!expected.same(scope))return false;restarts++;return true;}
    }
    WalkPath walk(){return new WalkPath(new Object(),List.of(new GhostSourcePathResultScoring.Edge(76,1)));}
    GhostSourcePathJobTransaction transaction(Model model,GhostSourcePathFailureSuppression suppression){return new GhostSourcePathJobTransaction(model.scope.parent().parent(),model,suppression);}
    @Test void selectionFieldsStayEmptyUntilValidSourceCallbackAndSingleReplayCannotCommit(){
        var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));
        var path=walk();var result=job.resultFromMappedWorker(m.actual,path);assertNull(m.selection);assertEquals(6000,job.sourceVisitLimit());
        assertEquals(Outcome.COMMITTED,job.consume(result,0));assertSame(m.actual,m.selection.selectedTarget());assertSame(path.nativeSourcePathIdentity(),m.selection.selectedWalkPath());
        assertEquals(Outcome.REPLAY,job.consume(result,1));assertEquals(1,m.commits);assertFalse(ledger.suppressed(m.actual.nativeUuid(),1));
    }
    @Test void failedNonnullTargetUsesGenuinelyIssuedSuppressionScopeAndRestartsOnce(){
        var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));
        assertEquals(Outcome.FAILED_TARGET_SUPPRESSED,job.consume(job.resultFromMappedWorker(m.actual,null),0));
        assertTrue(ledger.suppressed(m.actual.nativeUuid(),59999));assertFalse(ledger.suppressed(m.actual.nativeUuid(),60000));assertNull(m.selection);assertEquals(1,m.restarts);
    }
    @Test void emptyPairRestartsWithoutWritingAnyTarget(){
        var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));
        assertEquals(Outcome.EMPTY_FAILURE,job.consume(job.resultFromMappedWorker(null,null),0));assertFalse(ledger.suppressed(m.actual.nativeUuid(),0));assertEquals(1,m.restarts);
    }
    @Test void settingsChangeRejectsSuccessAndFailureWithoutMutationOrSuppression(){
        for(boolean success:List.of(false,true)){var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));
            var result=job.resultFromMappedWorker(m.actual,success?walk():null);m.changeSettings();
            assertEquals(Outcome.STALE,job.consume(result,0));assertNull(m.selection);assertEquals(0,m.restarts);assertFalse(ledger.suppressed(m.actual.nativeUuid(),0));}
    }
    @Test void differentJobChildGenerationPlayerWorldOrConnectionRevokesOriginalCallback(){
        for(int field=0;field<6;field++){var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var s=m.scope;var p=s.parent();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));
            var result=job.resultFromMappedWorker(m.actual,walk());
            m.scope=new Scope(field==0?new Object():s.job(),field==1?new Object():s.child(),new GhostSourceOwnedParentArea.Stamp(p.parent(),field==2?2:p.generation(),p.settingsRevision(),field==3?new Object():p.player(),field==4?new Object():p.world(),field==5?new Object():p.connection()));
            assertEquals(Outcome.STALE,job.consume(result,0));assertNull(m.selection);}
    }
    @Test void sameUuidReplacementEntityCannotReceiveOldEntityCallback(){
        var m=new Model();var job=transaction(m,new GhostSourcePathFailureSuppression()).issueCurrent(List.of(m.actual));var result=job.resultFromMappedWorker(m.actual,walk());
        m.actual=new Target(new Object(),m.actual.nativeUuid());assertEquals(Outcome.STALE,job.consume(result,0));assertNull(m.selection);
    }
    @Test void ownerChangeDuringActualTargetLookupIsRechecked(){
        var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));
        var result=job.resultFromMappedWorker(m.actual,null);m.mutateOnLookup=true;
        assertEquals(Outcome.STALE,job.consume(result,0));assertFalse(ledger.suppressed(m.actual.nativeUuid(),0));assertEquals(0,m.restarts);
    }
    @Test void resultsCannotCrossIssuedJobsOrLeaveOriginalCohort(){
        var m=new Model();var tx=transaction(m,new GhostSourcePathFailureSuppression());var first=tx.issueCurrent(List.of(m.actual));var second=tx.issueCurrent(List.of(m.actual));
        assertThrows(IllegalArgumentException.class,()->first.resultFromMappedWorker(new Target(new Object(),UUID.randomUUID()),walk()));
        assertEquals(Outcome.STALE,second.consume(first.resultFromMappedWorker(m.actual,walk()),0));assertNull(m.selection);
    }
    @Test void commitRefusalIsNotReportedAsSuccessfulPublication(){
        var m=new Model();var job=transaction(m,new GhostSourcePathFailureSuppression()).issueCurrent(List.of(m.actual));m.reject=true;
        assertEquals(Outcome.REJECTED_COMMIT,job.consume(job.resultFromMappedWorker(m.actual,walk()),0));assertNull(m.selection);
    }
    @Test void failureRestartRefusalIsExplicitWhileRealSuppressionWriteRemains(){
        var m=new Model();var ledger=new GhostSourcePathFailureSuppression();var job=transaction(m,ledger).issueCurrent(List.of(m.actual));m.rejectRestart=true;
        assertEquals(Outcome.SUPPRESSED_CONTINUATION_REJECTED,job.consume(job.resultFromMappedWorker(m.actual,null),0));assertTrue(ledger.suppressed(m.actual.nativeUuid(),0));assertEquals(0,m.restarts);
    }
    @Test void revokedOrDuplicateWorkerResultCannotPublish(){
        var m=new Model();var job=transaction(m,new GhostSourcePathFailureSuppression()).issueCurrent(List.of(m.actual));var result=job.resultFromMappedWorker(m.actual,walk());
        assertThrows(IllegalStateException.class,()->job.resultFromMappedWorker(m.actual,walk()));job.revoke();assertEquals(Outcome.REPLAY,job.consume(result,0));assertNull(m.selection);
    }
}
