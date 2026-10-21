package dev.veltraph.client.fishing.setup;

/** One native request with observed outcome and separately acknowledged slot/position/aim return. */
public final class GrappleRuntime {
    public enum State { IDLE, WAIT_REEL, PREPARE, WALK_BACK, AIM, WAIT_ACK, RESTORE, RETURN, AIM_CAST, COMPLETE, FAILED }
    public enum Action { NONE, REEL, PREPARE, WALK_BACK, USE, RESTORE, RETURN }
    public record Input(boolean start,boolean bobber,boolean prepared,boolean returnBeforeUse,
            boolean standing,boolean aligned,boolean acknowledged,boolean restored,boolean returned,
            boolean castAligned,long now) {}
    private State state=State.IDLE;private String failure="";private long began,request;
    private final long requestTimeout,stageTimeout;
    public GrappleRuntime(long requestTimeout,long stageTimeout){this.requestTimeout=requestTimeout;this.stageTimeout=stageTimeout;}
    public State state(){return state;}public String failure(){return failure;}public boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED;}
    public Action tick(Input in){
        if(state==State.FAILED)return Action.NONE;
        if(state==State.COMPLETE){state=State.IDLE;return Action.NONE;}
        if(state==State.IDLE){if(!in.start())return Action.NONE;began=request=in.now();state=in.bobber()?State.WAIT_REEL:State.PREPARE;return in.bobber()?Action.REEL:Action.NONE;}
        if(in.now()-began>stageTimeout){fail("Grapple route timed out");return Action.NONE;}
        switch(state){
            case WAIT_REEL -> {if(!in.bobber())state=State.PREPARE;else if(in.now()-request>requestTimeout)fail("Grapple initial reel not acknowledged");}
            case PREPARE -> {if(in.prepared()){state=in.returnBeforeUse()?State.WALK_BACK:State.AIM;return in.returnBeforeUse()?Action.WALK_BACK:Action.NONE;}return Action.PREPARE;}
            case WALK_BACK -> {if(in.standing())state=State.AIM;}
            case AIM -> {if(in.aligned()){state=State.WAIT_ACK;request=in.now();return Action.USE;}}
            case WAIT_ACK -> {if(in.acknowledged())state=State.RESTORE;else if(in.now()-request>requestTimeout)fail("Grapple tether/pull/consumption not acknowledged");}
            case RESTORE -> {if(in.restored()){state=State.RETURN;return Action.RETURN;}return Action.RESTORE;}
            case RETURN -> {if(in.returned())state=State.AIM_CAST;}
            case AIM_CAST -> {if(in.castAligned())state=State.COMPLETE;}
            default -> {}
        }return Action.NONE;
    }
    public void fail(String reason){failure=reason;state=State.FAILED;}
    public void reset(){state=State.IDLE;failure="";began=request=0;}
    /** Source hysteresis5 activate /2 retain, bounded counter. Farther than14 bypasses hazard sampling. */
    public static final class Eligibility {
        private int samples;private boolean active;
        public void clear(){samples=0;active=false;}
        public boolean test(double distance,boolean hazard,boolean running){if(distance>14)return true;samples=hazard?Math.min(5,samples+1):Math.max(0,samples-1);if(!active&&samples>=5)active=true;else if(active&&samples<2)active=false;return active||running&&samples>0;}
    }
}
