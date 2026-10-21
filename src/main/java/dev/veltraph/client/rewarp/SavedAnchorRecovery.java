package dev.veltraph.client.rewarp;

import java.util.Objects;

/** Local phases recovered from ReturnToLocationController: travel, check original spot, rotate, settle. */
public final class SavedAnchorRecovery {
    public enum State { IDLE, WAITING_PLOT, WALKING, FLYING, ALIGNING, SETTLING, ARRIVED, FAILED, CANCELLED }
    public enum Travel { ACTIVE, ARRIVED, FAILED }
    public interface Actions {
        boolean startWalk();
        /** Explicit local loaded-ground fallback; existing callers remain default denied. */
        default boolean startLoadedWalk(){return false;}
        boolean startFlight();
        Travel travel();
        void align();
        void release();
    }
    public record Settings(long timeoutMs, long settleMs) {
        public Settings {
            if(timeoutMs<=0||settleMs<0||settleMs>=timeoutMs)
                throw new IllegalArgumentException("Invalid saved-anchor recovery timing");
        }
        // Timing salts are unresolved; explicit local defaults, not recovered constants.
        public static Settings defaults() { return new Settings(45_000,500); }
    }
    private final Actions actions;
    private final Settings settings;
    private State state=State.IDLE;
    private long started,lastTick,settledAt=-1;
    private boolean mayFly;
    public SavedAnchorRecovery(Actions actions, Settings settings) { this.actions=Objects.requireNonNull(actions);this.settings=Objects.requireNonNull(settings); }
    public boolean start(boolean valid,boolean mayFly,long now) {
        cancel();this.mayFly=mayFly;started=lastTick=now;settledAt=-1;
        if(!valid) { state=State.FAILED;return false; }
        try {
            if(actions.startWalk()||actions.startLoadedWalk()) state=State.WALKING;
            else if(mayFly && actions.startFlight()) state=State.FLYING;
            else finish(State.FAILED);
        } catch(RuntimeException failure) { failOriginal(failure);throw failure; }
        return active();
    }
    public void tick(boolean valid,boolean near,boolean aligned,boolean stationary,long now) {
        if(!active()) return;
        try {
            if(!valid) { finish(State.CANCELLED);return; }
            if(now<lastTick||now-started>=settings.timeoutMs()) { finish(State.FAILED);return; }
            lastTick=now;
            if(state==State.WALKING || state==State.FLYING) {
                Travel result=actions.travel();
                if(result==Travel.FAILED) {
                    actions.release();
                    if(state==State.WALKING && mayFly && actions.startFlight()) state=State.FLYING;
                    else finish(State.FAILED);
                } else if(result==Travel.ARRIVED) {
                    actions.release();
                    if(!near) finish(State.FAILED);else state=State.ALIGNING;
                }
                return;
            }
            if(!near) { finish(State.FAILED);return; }
            actions.align();
            if(!aligned || !stationary) { state=State.ALIGNING;settledAt=-1;return; }
            if(settledAt<0) settledAt=now;
            state=State.SETTLING;
            if(now-settledAt>=settings.settleMs()) finish(State.ARRIVED);
        } catch(RuntimeException failure) { failOriginal(failure);throw failure; }
    }
    /** Preserve the real failing operation when its mandatory original release also refuses. */
    private void failOriginal(Throwable original){
        state=State.FAILED;
        try{actions.release();}
        catch(RuntimeException|Error cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}
    }
    public boolean active() { return state==State.WALKING || state==State.FLYING || state==State.ALIGNING || state==State.SETTLING; }
    public State state() { return state; }
    public void cancel() { if(active()) finish(State.CANCELLED); }
    private void finish(State result) { state=result;actions.release(); }
}
