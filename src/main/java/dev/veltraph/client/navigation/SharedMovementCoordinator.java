package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Shared transaction lifecycle. Native ports publish ordinary keys/item uses, never player setters. */
public final class SharedMovementCoordinator {
    public enum State { IDLE, SELECTING, MOVING, WAITING_RECEIPT, ARRIVED, STOPPED, FAILED }
    public enum Progress { ACTIVE, ARRIVED, REJECTED, UNKNOWN }
    /** All reference fields use identity, including configuration store and selected full tool handle. */
    public record Lease(Object module,Object player,Object world,Object connection,Object inputOwner,
                        Object cameraOwner,Object configStore,long configRevision,long cameraEpoch) {
        public Lease { if(module==null||player==null||world==null||connection==null||inputOwner==null
            ||cameraOwner==null||configStore==null||configRevision<0||cameraEpoch<0)
            throw new IllegalArgumentException("Actual registered owner lease required"); }
        public boolean same(Lease other){return other!=null&&module==other.module&&player==other.player
            &&world==other.world&&connection==other.connection&&inputOwner==other.inputOwner
            &&cameraOwner==other.cameraOwner&&configStore==other.configStore
            &&configRevision==other.configRevision&&cameraEpoch==other.cameraEpoch;}
    }
    /** Issue identity is allocated before action publication; receipt domain and baseline are independent. */
    public record Issue(Object token,Object receiptDomain,long baseline,long issuedAt,boolean dispatched) {
        public Issue {if(token==null||receiptDomain==null||baseline<0||issuedAt<0)
            throw new IllegalArgumentException("Native issue identity required");}
    }
    public record AppliedReceipt(Object token,Object domain,long serial,long appliedAt,Vec3 actual,
                                 Object player,Object world,Object connection) {}
    public interface Port {
        long now();
        /** Must verify native client thread, registered active parent, PWC, both leases, menu/hazards and ABA revisions. */
        boolean owned(Lease lease);
        List<SharedMovementPolicy.Candidate> candidates(Vec3 goal,SharedMovementPolicy.Options options,int uses);
        /** Revalidate the exact proof handles, full slot/components and mana/readiness immediately before dispatch. */
        boolean permitted(Lease lease,SharedMovementPolicy.Candidate candidate);
        Issue begin(Lease lease,SharedMovementPolicy.Candidate candidate);
        /** Ordinary native updates only, with fresh scope fences before each key/camera/use publication. */
        Progress tick(Lease lease,SharedMovementPolicy.Candidate candidate,Issue issue);
        AppliedReceipt receipt(Lease lease,Issue issue);
        /** Actual one-shot issuance may follow aiming; no tool budget is consumed by a dormant plan. */
        default Issue dispatchedIssue(Lease lease,Issue armed){return armed.dispatched()?armed:null;}
        /** Actual current loaded body/support, stable native terminal pose, and unchanged world. */
        boolean at(Vec3 destination,double radius);
        /** Cancel only this exact transaction generation; release its nine keys and owned camera. */
        void stopOwned(Lease lease,Issue issue);
        /** Diagnostic notification after failure is fixed, before existing cleanup. Grants no permission. */
        default void observeFirstFailure(String reason) {}
    }
    private final Port port;
    private Lease lease;private SharedMovementPolicy.Options options;private Vec3 goal;
    private SharedMovementPolicy.Candidate candidate;private Issue issue;
    private State state=State.IDLE;private long began,lastNow,journey;private int remainingUses;
    private String previous,reason="Idle";
    public SharedMovementCoordinator(Port port){this.port=Objects.requireNonNull(port);}
    private long cleanupGeneration,lastEmptyScopedRetirement=-1;private boolean cleanupGenerationExhausted;
    private ScopedStopAttempt lastScopedStop;
    /** Never changes legacy lifecycle decisions, callbacks, clock operands, or selection sequence. */
    private void observeCleanupGeneration(){
        lastScopedStop=null;
        if(cleanupGeneration==Long.MAX_VALUE)cleanupGenerationExhausted=true;else cleanupGeneration++;
    }
    private final class StopSnapshot {
        final long generation=cleanupGeneration;
        final Lease originalLease=lease;final SharedMovementPolicy.Options originalOptions=options;final Vec3 originalGoal=goal;
        final SharedMovementPolicy.Candidate originalCandidate=candidate;final Issue originalIssue=issue;final State originalState=state;
        final long originalBegan=began,originalLastNow=lastNow,originalJourney=journey;
        final int originalRemaining=remainingUses;final String originalPrevious=previous,originalReason=reason;
        boolean sameTransaction(){return !cleanupGenerationExhausted&&cleanupGeneration==generation
            &&journey==originalJourney&&lease==originalLease&&issue==originalIssue;}
        boolean current(){return !cleanupGenerationExhausted&&cleanupGeneration==generation
            &&lease==originalLease&&options==originalOptions&&goal==originalGoal&&candidate==originalCandidate
            &&issue==originalIssue&&state==originalState&&began==originalBegan&&lastNow==originalLastNow
            &&journey==originalJourney&&remainingUses==originalRemaining&&previous==originalPrevious&&reason==originalReason;}
    }
    /** One bounded cleanup attempt, not native authority or a successful native-effect receipt. */
    private static final class ScopedStopAttempt {
        final StopSnapshot original;boolean inFlight,attempted;Throwable failure;
        ScopedStopAttempt(StopSnapshot original){this.original=original;}
    }
    public boolean start(Lease lease,Vec3 goal,SharedMovementPolicy.Options options) {
        observeCleanupGeneration(); // Passive scoped-stop ABA history only; no legacy callback/control change.
        try{return startInternal(lease,goal,options);}catch(RuntimeException|Error failure){retireException(failure);throw failure;}
    }
    private boolean startInternal(Lease lease,Vec3 goal,SharedMovementPolicy.Options options) {
        stop();if(lease==null||!SharedMovementPolicy.finite(goal)||options==null)return false;
        this.lease=lease;this.goal=goal;this.options=options;began=lastNow=port.now();remainingUses=options.toolUses();
        if(began<0||!port.owned(lease))return fail("No current registered parent authority");
        state=State.SELECTING;return select();
    }
    private boolean current(){long now=port.now();if(now<lastNow||now<began||now-began>options.deadlineMillis())return false;lastNow=now;return port.owned(lease);}
    private boolean select() {
        if(!current())return fail("Scope or original journey deadline expired");
        var candidates=port.candidates(goal,options,remainingUses);
        if(!current())return fail("Scope changed during candidate reads");
        var selected=SharedMovementPolicy.select(candidates,options,remainingUses,previous,journey++);
        if(selected.isEmpty())return fail("No qualified movement option");
        candidate=selected.get();
        if(!port.permitted(lease,candidate)||!current())return fail("Movement proof expired before publication");
        issue=port.begin(lease,candidate);
        if(issue==null)return fail("Native movement publication refused");
        if(!current()||issue.issuedAt()<began||issue.issuedAt()>lastNow)return fail("Movement issuance lost scope/time");
        if(SharedMovementPolicy.device(candidate.mode())) {
            if(issue.dispatched()){remainingUses-=candidate.toolUses();state=State.WAITING_RECEIPT;}
            else state=State.MOVING;
        } else state=State.MOVING;
        previous=candidate.id();reason="Selected "+candidate.mode()+": "+candidate.id();return true;
    }
    public State tick() {
        try{return tickInternal();}catch(RuntimeException|Error failure){retireException(failure);throw failure;}
    }
    private State tickInternal() {
        if(state!=State.MOVING&&state!=State.WAITING_RECEIPT)return state;
        if(!current()||!port.permitted(lease,candidate)||!current()){fail("Active movement authority/proof expired");return state;}
        Progress progress=port.tick(lease,candidate,issue);
        if(!current()){fail("Scope changed during movement publication");return state;}
        if(progress==null||progress==Progress.REJECTED){fail("Native movement rejected");return state;}
        if(SharedMovementPolicy.device(candidate.mode())&&state==State.MOVING){
            var actual=port.dispatchedIssue(lease,issue);
            if(!current()){fail("Scope changed during actual device issuance read");return state;}
            if(actual!=null){
                if(!actual.dispatched()||actual.issuedAt()<issue.issuedAt()||actual.issuedAt()>lastNow){fail("Invalid actual device issuance");return state;}
                issue=actual;remainingUses-=candidate.toolUses();state=State.WAITING_RECEIPT;
            }else {if(progress==Progress.ARRIVED)fail("Transport arrival without actual issue");return state;}
        }
        if(state==State.WAITING_RECEIPT) {
            var receipt=port.receipt(lease,issue);
            if(!current()){fail("Scope changed during native receipt read");return state;}
            if(receipt==null)return state; // Unknown dispatched outcome never retries another device.
            if(!ack(receipt)){fail("Unqualified or unexpected transport receipt");return state;}
            if(!port.at(candidate.destination(),SharedMovementPolicy.legTerminalRadius(candidate,options))||!current())return state;
            completeLeg();return state;
        }
        if(progress==Progress.ARRIVED&&port.at(candidate.destination(),SharedMovementPolicy.legTerminalRadius(candidate,options))&&current())completeLeg();
        return state;
    }
    private boolean ack(AppliedReceipt r){return r.token()==issue.token()&&r.domain()==issue.receiptDomain()
        &&r.serial()>issue.baseline()&&r.appliedAt()>=issue.issuedAt()&&r.appliedAt()<=lastNow
        &&r.player()==lease.player()&&r.world()==lease.world()&&r.connection()==lease.connection()
        &&SharedMovementPolicy.finite(r.actual())&&r.actual().distanceTo(candidate.destination())<=SharedMovementPolicy.legTerminalRadius(candidate,options);}
    private void completeLeg(){
        port.stopOwned(lease,issue);issue=null;
        if(!current()){fail("Scope changed while retiring a movement leg");return;}
        if(port.at(goal,options.terminalRadius())&&current()){state=State.ARRIVED;reason="Actual original destination reached";return;}
        state=State.SELECTING;select();
    }
    private boolean fail(String why){state=State.FAILED;reason=why;try{port.observeFirstFailure(why);}catch(Throwable unavailable){}if(lease!=null)port.stopOwned(lease,issue);issue=null;return false;}
    private void retireException(Throwable original){state=State.FAILED;reason="Native port callback failed";try{port.observeFirstFailure(reason+" exception="+original.getClass().getName()+":"+original.getMessage());}catch(Throwable unavailable){}
        try{if(lease!=null)port.stopOwned(lease,issue);}catch(RuntimeException|Error cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}finally{issue=null;}}
    public void stop(){try{if(lease!=null)port.stopOwned(lease,issue);}finally{lease=null;issue=null;candidate=null;goal=null;options=null;state=State.STOPPED;reason="Stopped";}}
    /** Retire only the original private transaction. The supplied veto can refuse, never grant native authority. */
    public boolean stopScoped(java.util.function.BooleanSupplier refusalOnlyVeto){
        Objects.requireNonNull(refusalOnlyVeto);
        if(lease==null||cleanupGenerationExhausted)return false;
        var before=new StopSnapshot();
        if(!refusalOnlyVeto.getAsBoolean()||!before.current())return false;
        var attempt=lastScopedStop;
        if(attempt==null||!attempt.original.sameTransaction()){
            attempt=new ScopedStopAttempt(before);lastScopedStop=attempt;
        }
        if(attempt.inFlight)return false;
        // Repeated late-veto cleanup can retire the same metadata, but never submit another native stop.
        if(!attempt.attempted){
            attempt.attempted=true;attempt.inFlight=true;
            try{port.stopOwned(before.originalLease,before.originalIssue);}
            catch(RuntimeException|Error failure){attempt.failure=failure;}
            finally{attempt.inFlight=false;}
        }
        boolean retired=false;Throwable failure=attempt.failure;
        try{
            // Final source callback precedes stored original-field/generation checks and metadata writes.
            if(refusalOnlyVeto.getAsBoolean()&&before.current()){
                lease=null;issue=null;candidate=null;goal=null;options=null;state=State.STOPPED;reason="Stopped";
                observeCleanupGeneration();lastEmptyScopedRetirement=cleanupGeneration;retired=true;
            }
        }catch(RuntimeException|Error vetoFailure){if(failure==null)failure=vetoFailure;else if(failure!=vetoFailure)failure.addSuppressed(vetoFailure);}
        if(failure instanceof RuntimeException exception)throw exception;
        if(failure instanceof Error exception)throw exception;
        return retired;
    }
    /** Exact empty-kernel metadata retirement; never submits a native Port stop or grants a receipt. */
    public boolean retireEmptyScoped(java.util.function.BooleanSupplier refusalOnlyVeto){
        Objects.requireNonNull(refusalOnlyVeto);
        if(cleanupGenerationExhausted||lease!=null||issue!=null||lastEmptyScopedRetirement==cleanupGeneration)return false;
        var before=new StopSnapshot();
        // Both source callbacks may replace/restart even an apparently empty transaction.
        if(!refusalOnlyVeto.getAsBoolean()||!before.current())return false;
        if(!refusalOnlyVeto.getAsBoolean()||!before.current())return false;
        lease=null;issue=null;candidate=null;goal=null;options=null;state=State.STOPPED;reason="Stopped";
        observeCleanupGeneration();lastEmptyScopedRetirement=cleanupGeneration;return true;
    }
    public State state(){return state;}
    public String status(){return state+" | "+reason;}
    public SharedMovementPolicy.Candidate selected(){return candidate;}
    public int remainingToolUses(){return remainingUses;}
    /** Read-only original clock operands. Their values do not admit or publish a native action. */
    public long originalBeganAt(){return options==null?-1:began;}
    public long originalDeadlineAt(){return options==null||began<0||began>Long.MAX_VALUE-options.deadlineMillis()?-1:began+options.deadlineMillis();}
    public long observedClockFloor(){return options==null?-1:lastNow;}
}
