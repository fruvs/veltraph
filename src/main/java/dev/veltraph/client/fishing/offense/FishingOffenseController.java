package dev.veltraph.client.fishing.offense;

/** Bounded readable target -> rod reel -> downward aim -> paced ordinary use -> independent receipts -> return. */
public final class FishingOffenseController {
    public enum State { IDLE, REEL, SELECT, AIM, RECEIPT, RESTORE_SLOT, WAIT_FOOTING, RETURN, RESTORE_VIEW, COMPLETE, FAILED, CANCELLED }
    public enum Action { NONE, REEL, SELECT, AIM_DOWN, USE, RESTORE_SLOT, WAIT_FOOTING, RETURN, RESTORE_VIEW, RELEASE }
    /** One coherent native observation, including actual camera pose and monotonically observed receipts. */
    public record Frame(long now,boolean valid,boolean targetIdentity,boolean targetAlive,double horizontal,double vertical,
            boolean bobber,boolean toolAvailable,boolean toolHeld,boolean manaFresh,Integer mana,long manaRevision,
            long abilityRevision,float targetHealth,float pitch,boolean clear,boolean rodRestored,boolean positionReturned,boolean viewReturned,boolean returnReady) {
        public boolean inUseRange(){return Double.isFinite(horizontal)&&Double.isFinite(vertical)&&horizontal>=0&&vertical>=0&&horizontal<4&&vertical<6;}
        public boolean inTrackRange(){return horizontal>=0&&vertical>=0&&horizontal<6&&vertical<6;}
    }
    private State state=State.IDLE;
    private FishingOffenseProfile profile;
    private long deadline,nextUse,manaRevision,abilityRevision;
    private int manaBefore,uses;
    private float healthBefore;
    private boolean reelIssued;
    private String failure="";
    public boolean start(Frame frame,FishingOffenseProfile profile,long pacingMillis) {
        if(state!=State.IDLE&&state!=State.COMPLETE)return false;
        if(!frame.valid||!frame.targetIdentity||!frame.targetAlive||!frame.toolAvailable||!frame.inUseRange()
                ||!Float.isFinite(frame.pitch)||!Float.isFinite(frame.targetHealth)||frame.targetHealth<=0||!frame.manaFresh||frame.mana==null||frame.mana<Math.max(300,profile.manaCost()))return false;
        if(pacingMillis<profile.pacingMinMillis()||pacingMillis>profile.pacingMaxMillis())throw new IllegalArgumentException("Pacing outside declared endpoints");
        this.profile=profile;state=State.REEL;failure="";uses=0;reelIssued=false;deadline=frame.now+profile.timeoutMillis();nextUse=frame.now+pacingMillis;return true;
    }
    public Action tick(Frame f,long pacingMillis) {
        if(!active())return Action.NONE;
        if(!f.valid||!f.targetIdentity||!Float.isFinite(f.pitch))return fail("Offensive Fishing owner, native context, loadout or target changed");
        if(f.now>deadline)return fail("Offensive Fishing request/return deadline expired");
        if(state.ordinal()<=State.AIM.ordinal()&&(!f.targetAlive||!f.inTrackRange()||!f.toolAvailable))return fail("Offensive Fishing target or tool became unavailable");
        switch(state) {
            case REEL -> {state=State.SELECT;if(f.bobber){reelIssued=true;return Action.REEL;}}
            case SELECT -> {if(f.bobber){if(!reelIssued){reelIssued=true;return Action.REEL;}return Action.NONE;}state=State.AIM;return Action.SELECT;}
            case AIM -> {
                if(f.bobber)return fail("Native hook arrived after offensive tool selection");
                if(!f.toolHeld)return fail("Offensive Fishing tool selection changed");
                if(f.pitch<82)return Action.AIM_DOWN;
                if(f.now<nextUse)return Action.NONE;
                if(!f.inUseRange()||!f.clear||!f.manaFresh||f.mana==null||f.mana<Math.max(300,profile.manaCost()))return fail("Offensive Fishing range, clearance or fresh mana gate failed");
                manaBefore=f.mana;manaRevision=f.manaRevision;abilityRevision=f.abilityRevision;healthBefore=f.targetHealth;
                state=State.RECEIPT;deadline=f.now+profile.timeoutMillis();return Action.USE;
            }
            case RECEIPT -> {
                if(!f.toolHeld)return fail("Offensive Fishing tool ownership changed while awaiting receipt");
                if(f.manaFresh&&f.mana!=null&&f.manaRevision>manaRevision&&f.mana==manaBefore-profile.manaCost()
                        &&f.abilityRevision>abilityRevision&&Float.isFinite(f.targetHealth)&&f.targetHealth<healthBefore) {
                    uses++;
                    if(uses<profile.maxUses()&&f.targetAlive){
                        if(pacingMillis<profile.pacingMinMillis()||pacingMillis>profile.pacingMaxMillis())return fail("Offensive pacing changed");
                        nextUse=f.now+pacingMillis;deadline=f.now+profile.timeoutMillis();state=State.AIM;
                    } else {state=State.RESTORE_SLOT;deadline=f.now+profile.timeoutMillis();}
                }
            }
            case RESTORE_SLOT -> {if(!f.rodRestored)return Action.RESTORE_SLOT;state=State.WAIT_FOOTING;if(!f.returnReady)return Action.WAIT_FOOTING;state=State.RETURN;return Action.RETURN;}
            case WAIT_FOOTING -> {if(!f.returnReady)return Action.WAIT_FOOTING;state=State.RETURN;return Action.RETURN;}
            case RETURN -> {if(!f.positionReturned)return Action.RETURN;state=State.RESTORE_VIEW;return Action.RESTORE_VIEW;}
            case RESTORE_VIEW -> {
                // Position and view receipts must describe the same native frame: flow may move the body while the view converges.
                if(!f.positionReturned||!f.returnReady){state=State.WAIT_FOOTING;if(!f.returnReady)return Action.WAIT_FOOTING;state=State.RETURN;return Action.RETURN;}
                if(!f.viewReturned)return Action.RESTORE_VIEW;state=State.COMPLETE;return Action.RELEASE;
            }
            default -> {}
        }
        return Action.NONE;
    }
    private Action fail(String reason){failure=reason;state=State.FAILED;return Action.RELEASE;}
    public void failNative(String reason){failure=reason;state=State.FAILED;}
    /** Cancellation is terminal for this lease; cleanup may release, never start or reacquire. */
    public void cancel(){state=State.CANCELLED;}
    public void reset(){state=State.IDLE;profile=null;failure="";uses=0;}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED&&state!=State.CANCELLED;}
    public State state(){return state;}
    public String failure(){return failure;}
    public int uses(){return uses;}
    /** Original active request/return expiry; observing it never renews a lease. */
    public long deadlineMillis(){return deadline;}
}
