package dev.veltraph.client.combat.ghost;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Separate source CombatPathing Entity -> wall-clock failure ledger. Native Entity
 * identity is retained; UUID suppression and the source identity-ignore map are distinct.
 * This pure model supplies no native job authority: the canonical caller must validate
 * actual RESULTS_READY, source no-path results, predicate and scope before its one callback.
 */
public final class GhostSourceUnreachableActorLedger {
    public static final long SOURCE_EXPIRY_MS=30000;
    private static final GhostSourceUnreachableActorLedger RUNTIME=new GhostSourceUnreachableActorLedger();
    public enum NativeOutcome { READY_NO_PATH, READY_WITH_PATH, CAPTURE_REVOKED, UNBOUND }
    /** Original worker: predicate true skips the failure write; false writes. */
    public enum SourceFailurePredicate { SKIP_LEDGER, WRITE_LEDGER, UNKNOWN }
    public record Failure(Object nativeActor, SourceFailurePredicate predicate) {
        public Failure { Objects.requireNonNull(nativeActor); Objects.requireNonNull(predicate); }
    }
    public enum Outcome { RECORDED, NO_WRITES, STALE, REPLAY, REJECTED }
    public record WriteResult(Outcome outcome,int written,int predicateSkipped,int unknownSkipped) {}
    public final class CallbackToken {
        private final GhostSourcePathJobTransaction.Scope scope;
        private final IdentityHashMap<Object,Boolean> cohort=new IdentityHashMap<>();
        private boolean consumed;
        private CallbackToken(GhostSourcePathJobTransaction.Scope actualIssued,Collection<?> actualNativeCohort) {
            scope=Objects.requireNonNull(actualIssued);
            for(Object actor:actualNativeCohort)cohort.put(Objects.requireNonNull(actor),Boolean.TRUE);
        }
        public GhostSourcePathJobTransaction.Scope scope() { return scope; }
        private GhostSourceUnreachableActorLedger getOuter() { return GhostSourceUnreachableActorLedger.this; }
        public void revoke() { synchronized(GhostSourceUnreachableActorLedger.this) { consumed=true; } }
    }
    private final IdentityHashMap<Object,Long> entries=new IdentityHashMap<>();
    public static GhostSourceUnreachableActorLedger runtime() { return RUNTIME; }
    /** Actual registered worker creation issues this token with its own actual native cohort. */
    public CallbackToken issue(GhostSourcePathJobTransaction.Scope actualIssued,Collection<?> actualNativeCohort) {
        return new CallbackToken(actualIssued,Objects.requireNonNull(actualNativeCohort));
    }
    /** READY_NO_PATH refers to the actual known no-path subset; successful sibling paths do not join this list. */
    public synchronized WriteResult recordReadyNoPath(CallbackToken token,
            GhostSourcePathJobTransaction.Scope actualCurrentScope, NativeOutcome nativeOutcome,
            List<Failure> actualNoPathResults,LongSupplier actualWallClock) {
        if(token==null||token.getOuter()!=this)return result(Outcome.REJECTED,0,0,0);
        if(token.consumed)return result(Outcome.REPLAY,0,0,0);
        token.consumed=true;
        if(!token.scope.same(actualCurrentScope))return result(Outcome.STALE,0,0,0);
        if(nativeOutcome!=NativeOutcome.READY_NO_PATH)return result(Outcome.NO_WRITES,0,0,0);
        if(actualNoPathResults==null||actualWallClock==null)return result(Outcome.REJECTED,0,0,0);
        var distinct=new IdentityHashMap<Object,Boolean>();
        // Validate the complete provided native subset before any write; caller-created outsiders cannot enter.
        for(Failure failure:actualNoPathResults) {
            if(failure==null||!token.cohort.containsKey(failure.nativeActor())
                    ||distinct.put(failure.nativeActor(),Boolean.TRUE)!=null)return result(Outcome.REJECTED,0,0,0);
        }
        int written=0,skipped=0,unknown=0;
        for(Failure failure:actualNoPathResults) {
            switch(failure.predicate()) {
                case WRITE_LEDGER -> { entries.put(failure.nativeActor(),actualWallClock.getAsLong()); written++; }
                case SKIP_LEDGER -> skipped++;
                case UNKNOWN -> unknown++;
            }
        }
        return result(written>0?Outcome.RECORDED:Outcome.NO_WRITES,written,skipped,unknown);
    }
    /** Uniform observed-clock overload for pure verification; native callback samples each actual failure write. */
    public WriteResult recordReadyNoPath(CallbackToken token,GhostSourcePathJobTransaction.Scope actualCurrentScope,
            NativeOutcome nativeOutcome,List<Failure> actualNoPathResults,long observedWallClockMs) {
        return recordReadyNoPath(token,actualCurrentScope,nativeOutcome,actualNoPathResults,()->observedWallClockMs);
    }
    /** Exact source reader is membership only. Stale entries remain until the mapped wrapper cleanup. */
    public synchronized boolean containsIdentity(Object actualNativeActor) {
        return entries.containsKey(Objects.requireNonNull(actualNativeActor));
    }
    /**
     * Source wrapper returns before cleanup for an empty candidate list. Otherwise its
     * predicate samples System.currentTimeMillis separately for each entry, with strict >.
     * Native binding passes System::currentTimeMillis, not a caller-authored expiry policy.
     */
    public synchronized int cleanupAtNonemptyWrapperInvocation(int actualCandidateCount,LongSupplier actualWallClock) {
        if(actualCandidateCount<0)throw new IllegalArgumentException("Actual nonnegative candidate count required");
        if(actualCandidateCount==0)return 0;
        Objects.requireNonNull(actualWallClock);
        int removed=0;var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(actualWallClock.getAsLong()-entry.getValue()>SOURCE_EXPIRY_MS){iterator.remove();removed++;}
        }
        return removed;
    }
    /** Uniform observed-clock overload for pure verification; native source hook uses per-entry wall clock. */
    public int cleanupAtNonemptyWrapperInvocation(int actualCandidateCount,long observedWallClockMs) {
        return cleanupAtNonemptyWrapperInvocation(actualCandidateCount,()->observedWallClockMs);
    }
    private static WriteResult result(Outcome outcome,int written,int skipped,int unknown) {
        return new WriteResult(outcome,written,skipped,unknown);
    }
}
