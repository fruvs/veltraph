package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Single callback transaction for the source SEARCH job. It has no control authority or automatic runtime binding. */
public final class GhostSourcePathJobTransaction {
    public static final int SOURCE_VISIT_LIMIT=6000;
    public record Scope(Object job,Object child,GhostSourceOwnedParentArea.Stamp parent) {
        public Scope {Objects.requireNonNull(job);Objects.requireNonNull(child);Objects.requireNonNull(parent);}
        public boolean same(Scope actual){return actual!=null&&job==actual.job&&child==actual.child&&parent.same(actual.parent);}
        GhostSourcePathFailureSuppression.Scope failureScope(){return new GhostSourcePathFailureSuppression.Scope(job,
                parent.parent(),child,parent.generation(),parent.player(),parent.world(),parent.connection());}
    }
    public record Target(Object nativeIdentity,UUID nativeUuid) {
        public Target {Objects.requireNonNull(nativeIdentity);Objects.requireNonNull(nativeUuid);}
        boolean same(Target t){return t!=null&&nativeIdentity==t.nativeIdentity&&nativeUuid.equals(t.nativeUuid);}
    }
    /** Implement on the actual registered Ghost parent/child. There are deliberately no success/UNKNOWN defaults. */
    public interface RuntimeParentAdapter {
        Scope captureActualCurrentJob();
        Target findActualCurrentTarget(UUID uuid);
        /** Atomically validate expected scope, set the actual target field, and install the source cursor child.
         * Return the actual resulting owned state, not the requested arguments. Null means no mutation. */
        CommittedSelection commitTargetAndCursor(Scope expected,Target target,WalkPath actualSourceWalkPath);
        /** Dispatch the mapped source retry-count/fallback branch after suppression. This is not a generic restart.
         * The actual adapter must capture the source fallback pair and validate the same scope before mutation. */
        boolean dispatchFailedPathContinuation(Scope expected,Target failedTarget);
    }
    /** WALK result only. Costs are recovered worker observations; this type does not invent edge costs. */
    public record WalkPath(Object nativeSourcePathIdentity,List<GhostSourcePathResultScoring.Edge> edges) {
        public WalkPath {Objects.requireNonNull(nativeSourcePathIdentity);edges=List.copyOf(edges);
            if(edges.isEmpty())throw new IllegalArgumentException("Source WALK edges required");}
    }
    public record CommittedSelection(Object parent,Object sourceSearchChild,Object cursorChild,long generation,
                                     long settingsRevision,Object player,Object world,Object connection,
                                     Target selectedTarget,Object selectedWalkPath) {
        boolean matches(Scope s,Target t,Object path){var p=s.parent();return parent==p.parent()&&sourceSearchChild==s.child()
                &&cursorChild!=null&&generation==p.generation()&&settingsRevision==p.settingsRevision()
                &&player==p.player()&&world==p.world()&&connection==p.connection()&&t.same(selectedTarget)&&selectedWalkPath==path;}
    }
    public enum Outcome { COMMITTED, FAILED_TARGET_SUPPRESSED, EMPTY_FAILURE, SUPPRESSED_CONTINUATION_REJECTED, EMPTY_CONTINUATION_REJECTED, STALE, REPLAY, REJECTED_COMMIT }
    public final class Result {
        private final IssuedJob issued;private final Target target;private final WalkPath sourceWalkPath;
        private Result(IssuedJob issued,Target target,WalkPath path){this.issued=issued;this.target=target;sourceWalkPath=path;}
    }
    public final class IssuedJob {
        private final Scope scope;private final List<Target> cohort;private final GhostSourcePathFailureSuppression.IssuedJob failure;
        private boolean completed;private boolean resultIssued;
        private IssuedJob(Scope scope,List<Target> cohort){this.scope=scope;this.cohort=List.copyOf(cohort);failure=suppression.issue(scope.failureScope());}
        public Scope scope(){return scope;}
        public int sourceVisitLimit(){return SOURCE_VISIT_LIMIT;}
        /** Only the mapped source worker may issue its chosen target/path result; this does not publish selection. */
        synchronized Result resultFromMappedWorker(Target target,WalkPath sourceWalkPath){
            if(completed||resultIssued)throw new IllegalStateException("Issued source result already consumed");
            if(target!=null&&cohort.stream().noneMatch(target::same))throw new IllegalArgumentException("Target outside issued native cohort");
            if(target==null&&sourceWalkPath!=null)throw new IllegalArgumentException("Path cannot exist without target");
            resultIssued=true;return new Result(this,target,sourceWalkPath);
        }
        public synchronized Outcome consume(Result result,long now){
            if(completed)return Outcome.REPLAY;completed=true;
            if(result==null||result.issued!=this||!scope.same(adapter.captureActualCurrentJob())){failure.revoke();return Outcome.STALE;}
            if(result.target!=null&&!result.target.same(adapter.findActualCurrentTarget(result.target.nativeUuid()))){failure.revoke();return Outcome.STALE;}
            // Recheck after the actual target lookup, before either native mutation or suppression write.
            var actual=adapter.captureActualCurrentJob();
            if(!scope.same(actual)){failure.revoke();return Outcome.STALE;}
            if(result.sourceWalkPath==null){
                boolean written=failure.completeFailure(actual.failureScope(),result.target==null?null:result.target.nativeUuid(),false,now);
                boolean restarted=adapter.dispatchFailedPathContinuation(scope,result.target);
                if(!restarted)return written?Outcome.SUPPRESSED_CONTINUATION_REJECTED:Outcome.EMPTY_CONTINUATION_REJECTED;
                return written?Outcome.FAILED_TARGET_SUPPRESSED:Outcome.EMPTY_FAILURE;
            }
            failure.revoke();
            var committed=adapter.commitTargetAndCursor(scope,result.target,result.sourceWalkPath);
            return committed!=null&&committed.matches(scope,result.target,result.sourceWalkPath.nativeSourcePathIdentity())?Outcome.COMMITTED:Outcome.REJECTED_COMMIT;
        }
        public synchronized void revoke(){completed=true;failure.revoke();}
    }
    private final Object parent;private final RuntimeParentAdapter adapter;private final GhostSourcePathFailureSuppression suppression;
    public GhostSourcePathJobTransaction(Object actualParent,RuntimeParentAdapter adapter,GhostSourcePathFailureSuppression suppression){
        parent=Objects.requireNonNull(actualParent);this.adapter=Objects.requireNonNull(adapter);this.suppression=Objects.requireNonNull(suppression);}
    public IssuedJob issueCurrent(List<Target> actualNativeCohort){
        var scope=adapter.captureActualCurrentJob();
        if(scope==null||scope.parent().parent()!=parent)throw new IllegalStateException("Actual current source SEARCH job required");
        var issued=new IssuedJob(scope,actualNativeCohort);
        if(!scope.same(adapter.captureActualCurrentJob())){issued.revoke();throw new IllegalStateException("SEARCH owner changed while issuing");}
        return issued;
    }
}
