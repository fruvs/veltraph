package dev.veltraph.client.fishing.worm;

/** Inert-bytecode recovered one-use child. WAIT is a source stage, never a kill acknowledgment. */
public final class WormDreadlordController {
    public enum State { IDLE, LOOK_BEHIND, USE_WEAPON, WAIT_FOR_DEADLORD, LOOK_BACK, COMPLETE, CANCELLED, FAILED }
    public enum Action { NONE, SELECT_TOOL, AIM_BEHIND, USE_ITEM, AIM_BACK, RETURN_PARENT, RELEASE }
    public record Frame(long tick,boolean valid,boolean toolHeld,boolean aimCompleted,boolean returnAimCompleted){}
    private State state=State.IDLE;
    private long aimAt,useAt,deadline,lastTick;
    private boolean selected,aimIssued,backIssued;
    private int delayAfterAim;
    public void start(long tick,int initialDelay,int delayAfterAim,long deadlineTick) {
        if(state!=State.IDLE&&state!=State.COMPLETE)throw new IllegalStateException("Dreadlord child already used");
        if(initialDelay<2||initialDelay>20||delayAfterAim<2||delayAfterAim>3||deadlineTick<=tick+initialDelay+delayAfterAim)
            throw new IllegalArgumentException("Invalid Dreadlord lifecycle timing");
        state=State.LOOK_BEHIND;aimAt=tick+initialDelay;this.delayAfterAim=delayAfterAim;
        deadline=deadlineTick;lastTick=tick;selected=aimIssued=backIssued=false;
    }
    public Action tick(Frame f) {
        if(!active())return Action.NONE;
        if(!f.valid||f.tick<lastTick||f.tick>deadline){state=State.FAILED;return Action.RELEASE;}
        lastTick=f.tick;
        switch(state) {
            case LOOK_BEHIND -> {
                if(!selected){selected=true;return Action.SELECT_TOOL;}
                if(!f.toolHeld){state=State.FAILED;return Action.RELEASE;}
                if(f.tick<aimAt)return Action.NONE;
                if(!aimIssued){aimIssued=true;return Action.AIM_BEHIND;}
                if(f.aimCompleted){useAt=f.tick+delayAfterAim;state=State.USE_WEAPON;}
            }
            case USE_WEAPON -> {
                if(!f.toolHeld){state=State.FAILED;return Action.RELEASE;}
                if(f.tick>=useAt){state=State.WAIT_FOR_DEADLORD;return Action.USE_ITEM;}
            }
            case WAIT_FOR_DEADLORD -> {state=State.LOOK_BACK;backIssued=true;return Action.AIM_BACK;}
            case LOOK_BACK -> {
                if(!backIssued){backIssued=true;return Action.AIM_BACK;}
                if(f.returnAimCompleted){state=State.COMPLETE;return Action.RETURN_PARENT;}
            }
            default -> {}
        }
        return Action.NONE;
    }
    public Action cancel(){if(!active())return Action.NONE;state=State.CANCELLED;return Action.RELEASE;}
    public State state(){return state;}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.CANCELLED&&state!=State.FAILED;}
}
