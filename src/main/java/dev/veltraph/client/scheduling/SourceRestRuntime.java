package dev.veltraph.client.scheduling;

/** Typed source dynamic-rest transport phases; arrival/restart validation is explicit port evidence. */
public final class SourceRestRuntime {
    public enum State { IDLE,WAITING_TO_DISCONNECT,DISCONNECTED,RECONNECTING,VERIFYING_ARRIVAL,COMPLETE,CANCELLED,FAILED }
    public enum Action { NONE,DISCONNECT,REJOIN,VERIFY_ARRIVAL,RESTART,CANCEL }
    public record Observation(boolean validTicket,boolean disconnected,boolean matchingArrival,boolean restartReady,long nowMillis){}
    private State state=State.IDLE;private long started,last,restEnds,rejoinStarted,timeout;private int delay,counter;private boolean restartIssued;
    private String reason="idle";
    public void start(long nowMillis,long durationMillis,int disconnectDelayTicks,long rejoinTimeoutMillis){
        if(active()||durationMillis<60_000||disconnectDelayTicks<20||disconnectDelayTicks>=120||rejoinTimeoutMillis<=0)
            throw new IllegalArgumentException("Invalid source rest schedule");
        if(nowMillis>Long.MAX_VALUE-durationMillis)throw new IllegalArgumentException("Rest deadline overflow");
        started=last=nowMillis;restEnds=nowMillis+durationMillis;delay=disconnectDelayTicks;timeout=rejoinTimeoutMillis;counter=0;
        restartIssued=false;state=State.WAITING_TO_DISCONNECT;reason="waiting to disconnect";
    }
    public Action tick(Observation observation){
        if(!active())return Action.NONE;
        if(!observation.validTicket()||observation.nowMillis()<last){cancel("Rest ticket/context/clock changed");return Action.CANCEL;}
        last=observation.nowMillis();
        switch(state){
            case WAITING_TO_DISCONNECT->{
                if(observation.disconnected()){cancel("Unexpected disconnect before issued rest action");return Action.CANCEL;}
                // Source increments, then compares the previous counter with <= chosen delay.
                if(counter++<=delay)return Action.NONE;
                state=State.DISCONNECTED;reason="resting disconnected";return Action.DISCONNECT;
            }
            case DISCONNECTED->{
                if(!observation.disconnected()){cancel("Foreign world arrived before owned rejoin");return Action.CANCEL;}
                if(observation.nowMillis()<=restEnds)return Action.NONE;
                state=State.RECONNECTING;rejoinStarted=observation.nowMillis();reason="rejoining saved native server";return Action.REJOIN;
            }
            case RECONNECTING,VERIFYING_ARRIVAL->{
                if(observation.nowMillis()-rejoinStarted>=timeout){state=State.FAILED;reason="Native rejoin/readiness timed out";return Action.CANCEL;}
                if(!observation.disconnected()&&!observation.matchingArrival()){cancel("Unmatched server/world arrival");return Action.CANCEL;}
                if(!observation.matchingArrival())return Action.NONE;
                if(state==State.RECONNECTING){state=State.VERIFYING_ARRIVAL;reason="validating saved module/profile after native arrival";return Action.VERIFY_ARRIVAL;}
                if(!observation.restartReady()||restartIssued)return Action.NONE;
                // Keep the exact arrival authority live until the synchronous handoff succeeds.
                restartIssued=true;reason="committing saved owner resume";return Action.RESTART;
            }
            default->{return Action.NONE;}
        }
    }
    /** Commit once after resume and input restoration; failure/cancellation never becomes COMPLETE. */
    public boolean commitRestart(Observation observation){
        if(!restartPending())return false;
        if(!observation.validTicket()||observation.disconnected()||!observation.matchingArrival()
                ||!observation.restartReady()||!withinDeadline(observation.nowMillis())){
            cancel("Saved owner resume commit lost arrival proof");return false;
        }
        last=observation.nowMillis();state=State.COMPLETE;reason="saved owner resumed";return true;
    }
    public boolean restartPending(){return state==State.VERIFYING_ARRIVAL&&restartIssued;}
    public void cancel(String reason){if(state!=State.FAILED)state=State.CANCELLED;this.reason=reason;}
    public boolean active(){return state==State.WAITING_TO_DISCONNECT||state==State.DISCONNECTED||state==State.RECONNECTING||state==State.VERIFYING_ARRIVAL;}
    /** Read-only owner predicates cannot extend the finite native join/readiness window. */
    public boolean withinDeadline(long now){return active()&&now>=last&&((state!=State.RECONNECTING&&state!=State.VERIFYING_ARRIVAL)||now-rejoinStarted<timeout);}
    public State state(){return state;}public long restEndsMillis(){return restEnds;}public long startedMillis(){return started;}
    public String status(){return state+": "+reason;}
}
