package dev.veltraph.client.fishing;

/** Armor-stand obstruction -> return to saved cast position. Source return tick could
 * not be decompiled; the 15-second bound is an explicit rebuild default. */
public final class FishingReturnController {
    public enum State { IDLE, WALKING, AIMING, COMPLETE, FAILED }
    public interface Actions {
        boolean startWalking();
        /** -1 blocked, 0 walking, 1 arrived. */
        int walk();
        boolean restoreAim();
        void release();
    }
    private State state=State.IDLE;
    private long began;
    private String failure="";
    public State state() { return state; }
    public String failure() { return failure; }
    public boolean active() { return state==State.WALKING||state==State.AIMING; }
    public boolean tick(boolean obstructed,long now,Actions actions) {
        if(state==State.COMPLETE) { state=State.IDLE; return false; }
        if(state==State.FAILED) return false;
        if(state==State.IDLE) {
            if(!obstructed) return false;
            began=now;
            startWalk(actions);
            return true;
        }
        if(now-began>15_000) { fail("Fishing return timed out",actions);return true; }
        switch(state) {
            case WALKING -> {
                int progress=actions.walk();
                if(progress<0) fail("Fishing return path blocked",actions);
                else if(progress>0) state=State.AIMING;
            }
            case AIMING -> { if(actions.restoreAim()) { actions.release();state=State.COMPLETE; } }
            default -> {}
        }
        return true;
    }
    private void startWalk(Actions actions) {
        if(actions.startWalking()) state=State.WALKING;
        else fail("Cannot return to fishing position",actions);
    }
    private void fail(String reason,Actions actions) { failure=reason;state=State.FAILED;actions.release(); }
    public void stop(Actions actions) { if(active())actions.release();state=State.IDLE;failure="";began=0; }
}
