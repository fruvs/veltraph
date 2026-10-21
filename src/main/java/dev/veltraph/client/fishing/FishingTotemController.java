package dev.veltraph.client.fishing;

/** Recovered support-item placement lifecycle with bounded server acknowledgment. */
public final class FishingTotemController {
    public enum State { IDLE, WAIT_REEL, PREPARE, AIM, BUFFER, WAIT_ACK, RESTORE, COMPLETE, FAILED }
    public enum Action { NONE, REEL, PREPARE, USE, RESTORE }
    public record Input(boolean start, boolean bobber, boolean prepared, boolean aligned,
                        boolean acknowledged, boolean restored, long now) {}
    private State state=State.IDLE;
    private long began, buffered, requested;
    private String failure="";
    private final long bufferMs,timeoutMs;
    public FishingTotemController(long bufferMs,long timeoutMs) { this.bufferMs=bufferMs;this.timeoutMs=timeoutMs; }
    public State state(){return state;}
    public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED;}
    public String failure(){return failure;}
    public Action tick(Input in) {
        if(state==State.COMPLETE){state=State.IDLE;return Action.NONE;}
        if(state==State.FAILED)return Action.NONE;
        if(state==State.IDLE){
            if(!in.start())return Action.NONE;
            began=in.now();state=in.bobber()?State.WAIT_REEL:State.PREPARE;
            return in.bobber()?Action.REEL:Action.NONE;
        }
        if(in.now()-began>15_000){fail("Totem placement timed out");return Action.NONE;}
        switch(state){
            case WAIT_REEL -> {
                if(!in.bobber())state=State.PREPARE;
                else if(in.now()-began>timeoutMs)fail("Server did not acknowledge the totem's initial reel");
            }
            case PREPARE -> {state=State.AIM;return Action.PREPARE;}
            case AIM -> {if(in.prepared()&&in.aligned()){buffered=in.now();state=State.BUFFER;}}
            case BUFFER -> {
                if(!in.aligned()){state=State.AIM;break;}
                if(in.now()-buffered>=bufferMs){requested=in.now();state=State.WAIT_ACK;return Action.USE;}
            }
            case WAIT_ACK -> {
                if(in.acknowledged())state=State.RESTORE;
                else if(in.now()-requested>timeoutMs)fail("Server did not acknowledge totem placement");
            }
            case RESTORE -> {if(in.restored())state=State.COMPLETE;else return Action.RESTORE;}
            default -> {}
        }
        return Action.NONE;
    }
    public void fail(String reason){failure=reason;state=State.FAILED;}
    public void reset(){state=State.IDLE;failure="";began=buffered=requested=0;}
}
