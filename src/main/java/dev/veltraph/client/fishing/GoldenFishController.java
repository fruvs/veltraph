package dev.veltraph.client.fishing;

/** Recovered GoldenFishHandler flow. Timings are explicit rebuild defaults: source values are salted. */
public final class GoldenFishController {
    public static final long SEARCH_WINDOW_MS=15_000, ACTION_DELAY_MS=250, ACK_TIMEOUT_MS=4_000, SESSION_TIMEOUT_MS=60_000;
    public enum State { IDLE, SEARCH, TRACK, WAIT_CAST, WAIT_REEL, WAIT_FINAL, COMPLETE, FAILED }
    public record Input(boolean target, boolean visible, boolean bobber, boolean hooked,
                        boolean nearFish, boolean groundedOrFluid, boolean aligned, long now) {}
    private State state=State.IDLE;
    private boolean weak;
    private long started,actionAt;
    private int invisible,contact,missed;
    private String failure="";
    public State state(){return state;}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED;}
    public String failure(){return failure;}
    public void message(String text,long now) {
        if(text==null)return;
        String plain=text.replaceAll("§.","").strip();
        if(plain.startsWith("You spot a Golden Fish surface from beneath the lava!")&&!active()) {
            reset();state=State.SEARCH;started=now;actionAt=now;
        } else if(active()&&plain.startsWith("The Golden Fish is weak!"))weak=true;
    }
    public FishingAction tick(Input in) {
        if(!active())return FishingAction.NONE;
        // An outstanding rod request must be resolved even if the fish disappears.
        if(state==State.WAIT_REEL||state==State.WAIT_FINAL) {
            if(!in.bobber) {if(state==State.WAIT_FINAL)finish();else state=State.TRACK;}
            else if(in.now-actionAt>ACK_TIMEOUT_MS)fail("Golden Fish reel was not acknowledged");
            return FishingAction.NONE;
        }
        if(state==State.WAIT_CAST) {
            if(in.bobber)state=State.TRACK;
            else if(in.now-actionAt>ACK_TIMEOUT_MS)fail("Golden Fish cast was not acknowledged");
            return FishingAction.NONE;
        }
        if(in.now-started>SESSION_TIMEOUT_MS){finish();return FishingAction.NONE;}
        if(state==State.SEARCH) {
            if(in.now-started>SEARCH_WINDOW_MS){finish();return FishingAction.NONE;}
            if(!in.target||!in.visible)return FishingAction.NONE;
            state=State.TRACK;
            if(in.bobber&&!in.nearFish)return use(FishingAction.REEL,State.WAIT_REEL,in.now);
        }
        if(!in.target){finish();return FishingAction.NONE;}
        if(!in.visible && ++invisible>10){finish();return FishingAction.NONE;}
        contact=in.nearFish?contact+1:0;
        if(!in.visible||!in.aligned||in.now-actionAt<ACTION_DELAY_MS)return FishingAction.NONE;
        if(!in.bobber)return use(FishingAction.CAST,State.WAIT_CAST,in.now);
        if(in.hooked||contact>5) {
            if(weak)return use(FishingAction.REEL,State.WAIT_FINAL,in.now);
            return FishingAction.NONE;
        }
        if(in.groundedOrFluid && contact==0 && ++missed>5) {
            missed=0;return use(FishingAction.REEL,State.WAIT_REEL,in.now);
        }
        return FishingAction.NONE;
    }
    private FishingAction use(FishingAction action,State next,long now){state=next;actionAt=now;return action;}
    private void finish(){state=State.COMPLETE;}
    private void fail(String reason){state=State.FAILED;failure=reason;}
    public void reset(){state=State.IDLE;weak=false;started=actionAt=0;invisible=contact=missed=0;failure="";}
}
