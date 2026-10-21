package dev.veltraph.client.fishing;

/** Recoverable underwater melee -> surface/refill -> saved-position return cycle.
 * Source spin/Dreadlord and path-calculation stages remain unresolved. */
public final class FishingWormController {
    public enum State { IDLE, REELING, KILLING, SURFACING, REFILLING, RETURNING, AIMING, COMPLETE, FAILED }
    public interface Actions {
        boolean targetPresent();
        /** Parent source gate; emergency ascent does not automatically admit a weapon cycle. */
        default boolean killingAdmitted(){return true;}
        boolean hookPresent();
        boolean submerged();
        double bubbles();
        boolean surfaceReachable();
        boolean returnClear();
        boolean atAnchor();
        boolean restoreAim();
        void reel();
        void kill();
        void ascend();
        void refill();
        void returnToAnchor();
        void release();
    }
    private State state=State.IDLE;
    private long began,reelAt;
    private boolean pendingReel;
    private String failure="";
    public State state() { return state; }
    public boolean active() { return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED; }
    public String failure() { return failure; }
    public boolean tick(boolean enabled,boolean allowStart,long now,long acknowledgmentMs,Actions a) {
        if(!enabled) {
            if(active())fail("Underwater fishing disabled during its active flow",a);
            return false;
        }
        if(state==State.COMPLETE) {state=State.IDLE;return false;}
        if(state==State.FAILED)return false;
        double air=a.bubbles();
        if(state==State.IDLE) {
            if(!allowStart||!a.submerged()||((!a.targetPresent()||!a.killingAdmitted())&&air>1.5))return false;
            began=now;
            if(!a.surfaceReachable()) {fail("No clear overhead surface for underwater fishing",a);return true;}
            if(air<=1.5)state=State.SURFACING;
            else if(a.hookPresent()) {a.reel();pendingReel=true;reelAt=now;state=State.REELING;}
            else state=State.KILLING;
        }
        if(now-began>60_000) {fail("Underwater fishing cycle timed out",a);return true;}
        if(pendingReel) {
            if(!a.hookPresent())pendingReel=false;
            else if(now-reelAt>acknowledgmentMs) {fail("Server did not acknowledge underwater fishing reel",a);return true;}
        }
        // The source killer preempts its weapon actions at <=1.5 bubbles. This
        // also applies while waiting for a rod acknowledgment or diving back.
        if(a.submerged()&&air<=1.5&&state!=State.SURFACING&&state!=State.REFILLING) {
            a.release();state=State.SURFACING;
        }
        switch(state) {
            case REELING -> { if(!pendingReel)state=State.KILLING; }
            case KILLING -> {
                if(!a.targetPresent()) {a.release();state=State.SURFACING;}
                else a.kill();
            }
            case SURFACING -> {
                if(!a.surfaceReachable()) {fail("Underwater fishing surface became blocked",a);break;}
                if(!a.submerged()) {a.release();state=State.REFILLING;}
                else a.ascend();
            }
            case REFILLING -> {
                a.refill();
                // Surface1 waits for full air before restarting killing. We use
                // that same full refill gate before the bounded local return.
                if(air>=10&& !pendingReel)state=State.RETURNING;
            }
            case RETURNING -> {
                if(!a.returnClear()) {fail("Underwater fishing return corridor blocked",a);break;}
                if(a.atAnchor()) {a.release();state=State.AIMING;}
                else a.returnToAnchor();
            }
            case AIMING -> {
                if(a.restoreAim()) {
                    a.release();
                    if(a.targetPresent()&&a.killingAdmitted()) {
                        if(a.hookPresent()) {a.reel();pendingReel=true;reelAt=now;state=State.REELING;}
                        else state=State.KILLING;
                    } else state=State.COMPLETE;
                }
            }
            default -> {}
        }
        return true;
    }
    private void fail(String reason,Actions a) {failure=reason;state=State.FAILED;a.release();}
    public void stop(Actions a) {if(active())a.release();state=State.IDLE;began=reelAt=0;pendingReel=false;failure="";}
}
