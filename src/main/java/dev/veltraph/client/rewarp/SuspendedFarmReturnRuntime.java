package dev.veltraph.client.rewarp;

/** Pure request ordering. It owns no Minecraft objects and grants no native permission. */
public final class SuspendedFarmReturnRuntime {
    public enum Mode { COMMAND_ONLY, DISCONNECT_RECONNECT }
    public enum State { IDLE, PAUSING, PAUSED, DISCONNECTING, DISCONNECTED, CONNECTING, COMMANDING, WAITING_LANDING, RETURNING, SETTLING, COMMITTING, COMPLETE, FAILED, CANCELLED }
    public enum Scene { ORIGINAL, OWN_DISCONNECTED, OWN_CONNECTING, OWN_REJOINED, INVALID }
    public enum Landing { NONE, MATCHED, FOREIGN }
    public enum Action { NONE, DISCONNECT, REJOIN, SEND_COMMAND, START_RETURN, COMMIT, STOP }
    public record Observation(boolean originalSourceCurrent,Scene scene,Landing landing,boolean arrived,
                              boolean stationaryAligned,long nowMillis,long nativeTick) {}
    private State state=State.IDLE;
    private Mode mode;
    private long started,lastMillis,deadline,restDelay,disconnectedAt,settleSince=-1,lastSettleTick=-1;
    private int settleTicks,settled,commands,commits;
    private boolean returnAccepted,rejoinAccepted;
    private String reason="Idle";
    public boolean begin(Mode mode,long now,long maximumMillis,long restDelayMillis,int settleTicks) {
        if(active()||mode==null||now<0||maximumMillis<=0||maximumMillis>120000||restDelayMillis<0
                ||restDelayMillis>=maximumMillis||settleTicks<2||settleTicks>200||now>Long.MAX_VALUE-maximumMillis)return false;
        this.mode=mode;started=lastMillis=now;deadline=now+maximumMillis;restDelay=restDelayMillis;
        this.settleTicks=settleTicks;settled=commands=commits=0;returnAccepted=rejoinAccepted=false;settleSince=lastSettleTick=-1;
        state=State.PAUSING;reason="Capturing and suspending the original farm";return true;
    }
    public boolean paused(boolean actualOriginalPause) {
        if(state!=State.PAUSING||!actualOriginalPause){fail("Original farm pause refused");return false;}
        state=State.PAUSED;return true;
    }
    public boolean disconnected(boolean actualTransportDeparture,long now) {
        if(state!=State.DISCONNECTING||!actualTransportDeparture||!time(now)){fail("Owned disconnect refused");return false;}
        disconnectedAt=now;state=State.DISCONNECTED;return true;
    }
    public boolean rejoinIssued(boolean actualConnector) {
        if(state!=State.CONNECTING||rejoinAccepted||!actualConnector){fail("Owned rejoin refused");return false;}rejoinAccepted=true;return true;
    }
    public boolean commandSubmitted(boolean actualNormallyReturnedSubmission) {
        if(state!=State.COMMANDING||commands!=0||!actualNormallyReturnedSubmission){fail("Original command submission refused");return false;}
        commands++;state=State.WAITING_LANDING;reason="Waiting for the original command landing";return true;
    }
    public boolean returnStarted(boolean actualOriginalReturn) {
        if(state!=State.RETURNING||returnAccepted||!actualOriginalReturn){fail("Physical return refused");return false;}returnAccepted=true;return true;
    }
    public boolean committed(boolean actualOnceResume) {
        if(state!=State.COMMITTING||commits!=0||!actualOnceResume){fail("Original farm resume refused");return false;}
        commits++;state=State.COMPLETE;reason="Original farm resumed once";return true;
    }
    public Action tick(Observation o) {
        if(!active())return Action.NONE;
        if(o==null||!o.originalSourceCurrent()||o.scene()==null||o.scene()==Scene.INVALID||o.landing()==null||!time(o.nowMillis()))return stop("Original return source/context or deadline changed");
        if(o.landing()==Landing.FOREIGN)return stop("Unmatched native landing");
        boolean reconnect=mode==Mode.DISCONNECT_RECONNECT;
        boolean original=o.scene()==Scene.ORIGINAL,joined=o.scene()==Scene.OWN_REJOINED;
        if(!reconnect&&!original)return stop("Unexpected world/connection transition");
        switch(state) {
            case PAUSING -> {return Action.NONE;}
            case PAUSED -> {
                if(!original)return stop("Departure preceded original pause");
                if(reconnect){state=State.DISCONNECTING;return Action.DISCONNECT;}
                state=State.COMMANDING;return Action.SEND_COMMAND;
            }
            case DISCONNECTING -> {
                if(!original&&o.scene()!=Scene.OWN_DISCONNECTED)return stop("Unexpected disconnect scene");
            }
            case DISCONNECTED -> {
                if(o.scene()!=Scene.OWN_DISCONNECTED)return stop("Manual screen or foreign connection during rest");
                if(o.nowMillis()-disconnectedAt>=restDelay){state=State.CONNECTING;return Action.REJOIN;}
            }
            case CONNECTING -> {
                if(!rejoinAccepted)return stop("Owned rejoin effect did not return synchronously");
                if(joined){state=State.COMMANDING;return Action.SEND_COMMAND;}
                if(o.scene()!=Scene.OWN_CONNECTING&&o.scene()!=Scene.OWN_DISCONNECTED)return stop("Foreign connector or world");
            }
            case COMMANDING -> {return stop("Command effect did not return synchronously");}
            case WAITING_LANDING -> {
                if(reconnect&&!joined)return stop("Issued rejoin scene changed");
                if(o.landing()==Landing.MATCHED){state=State.RETURNING;return Action.START_RETURN;}
            }
            case RETURNING -> {
                if(!returnAccepted)return stop("Physical return effect did not return synchronously");
                if(reconnect&&!joined)return stop("Physical return left issued connection");
                if(o.arrived()&&o.stationaryAligned()){
                    state=State.SETTLING;settled=1;settleSince=o.nowMillis();lastSettleTick=o.nativeTick();
                }
            }
            case SETTLING -> {
                if(reconnect&&!joined)return stop("Arrival scene changed");
                if(!o.arrived()||!o.stationaryAligned()){state=State.RETURNING;settled=0;settleSince=lastSettleTick=-1;break;}
                if(o.nativeTick()<lastSettleTick)return stop("Native arrival tick rewound");
                if(o.nativeTick()==lastSettleTick)break;
                if(o.nativeTick()!=lastSettleTick+1)return stop("Native arrival observations skipped ticks");
                lastSettleTick=o.nativeTick();settled++;
                if(settled>=settleTicks&&o.nowMillis()-settleSince>=100){state=State.COMMITTING;return Action.COMMIT;}
            }
            case COMMITTING -> {return stop("Resume effect did not return synchronously");}
            default -> {}
        }
        return Action.NONE;
    }
    private boolean time(long now){if(now<started||now<lastMillis||now>=deadline)return false;lastMillis=now;return true;}
    private Action stop(String reason){fail(reason);return Action.STOP;}
    public void fail(String reason){state=State.FAILED;this.reason=reason;}
    public void cancel(String reason){if(active()){state=State.CANCELLED;this.reason=reason;}}
    public boolean active(){return switch(state){case IDLE,COMPLETE,FAILED,CANCELLED->false;default->true;};}
    public State state(){return state;}public Mode mode(){return mode;}public long deadline(){return deadline;}
    public int commands(){return commands;}public int commits(){return commits;}public String reason(){return reason;}
}
