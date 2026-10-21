package dev.veltraph.client.hydra.source;

/** Recovered local dispatch fragment only; no native actions, report export or runtime wiring. */
public final class SourceHydraLocalDispatch {
    public enum State { INACTIVE, LISTENING, ABOUT_TO_RESPOND, RESPONDING_TEMPORARILY_DISABLED, FIX_PITCH, RESPONDING, AFTER_RESPONDING }
    public enum Policy { REACT, STOP_ALL, IGNORE }
    public enum Action { NONE, SCAN_ORDERED_CHECKS, BEGIN_LOCAL_REACTION, STOP_ALL, CLEAR_LOCAL_STATE, ADVANCE_AFTER_RESPONSE }
    public record Decision(State state,int counter,Action action) {}
    public record Acquisition(State state,int counter,int threshold,Object check,Object kind,long occurredAt) {}
    public record CleanupSnapshot(State state,int counter,long occurredAt,Object check,Object kind,int threshold) {}
    @FunctionalInterface public interface CleanupPort { void afterReset(CleanupSnapshot prefix); }
    private SourceHydraLocalDispatch() {}
    /** Exact visible writes occur before opaque helper cleanup. Selected identities and threshold survive. */
    public static CleanupSnapshot cleanupPrefix(CleanupSnapshot before) {
        return new CleanupSnapshot(State.INACTIVE,0,0,before.check(),before.kind(),before.threshold());
    }
    /** Normal returned helper permits caller final state; an external exception propagates after prefix. */
    public static CleanupSnapshot cleanupThen(CleanupSnapshot before,State normalFinalState,CleanupPort external) {
        var prefix=cleanupPrefix(before);
        external.afterReset(prefix);
        return new CleanupSnapshot(normalFinalState,0,0,prefix.check(),prefix.kind(),prefix.threshold());
    }
    /** Pure normal-return decision. CLEAR/STOP require the cleanupPrefix before any external action. */
    public static Decision tick(State state,int counter,int threshold,Policy policy,boolean alwaysReactRare,boolean selectedCheckRare,boolean afterResponsePresent) {
        if(state==State.INACTIVE)return new Decision(state,counter,Action.NONE);
        int next=counter+1;
        return switch(state) {
            case LISTENING -> new Decision(state,next,Action.SCAN_ORDERED_CHECKS);
            case ABOUT_TO_RESPOND -> next<threshold?new Decision(state,next,Action.NONE)
                    :alwaysReactRare&&selectedCheckRare||policy==Policy.REACT?new Decision(state,0,Action.BEGIN_LOCAL_REACTION)
                    :new Decision(State.RESPONDING_TEMPORARILY_DISABLED,0,policy==Policy.STOP_ALL?Action.STOP_ALL:Action.CLEAR_LOCAL_STATE);
            case RESPONDING_TEMPORARILY_DISABLED -> next>=600?new Decision(State.LISTENING,0,Action.CLEAR_LOCAL_STATE):new Decision(state,next,Action.NONE);
            case AFTER_RESPONDING -> new Decision(state,next,afterResponsePresent?Action.ADVANCE_AFTER_RESPONSE:Action.NONE);
            default -> new Decision(state,next,Action.NONE);
        };
    }
    public static boolean busy(State state){return state==State.ABOUT_TO_RESPOND||state==State.RESPONDING||state==State.AFTER_RESPONDING||state==State.FIX_PITCH;}
    /** Caller must separately establish opaque source context/config gates and select delay. */
    public static Acquisition acquire(State current,int currentCounter,int currentThreshold,Object currentCheck,Object currentKind,long currentAt,Object check,Object kind,long now,int sampledThreshold) {
        if(busy(current))return new Acquisition(current,currentCounter,currentThreshold,currentCheck,currentKind,currentAt);
        return new Acquisition(State.ABOUT_TO_RESPOND,0,sampledThreshold,check,kind,now);
    }
    /** Bytecode Random.nextDouble(lower,upper)*20 D2I; exceptional sampling uses25. */
    public static int sampledThreshold(double seconds,boolean samplerFailed){return samplerFailed?25:(int)(seconds*20);}
}
