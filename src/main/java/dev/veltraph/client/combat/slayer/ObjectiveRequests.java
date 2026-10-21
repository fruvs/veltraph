package dev.veltraph.client.combat.slayer;

import java.util.UUID;

/** Requests are bounded; counters are progress, and only observed body removal completes a task. */
public final class ObjectiveRequests {
    public enum State { NONE, REQUEST, WAIT, PAUSED, STALLED, EXPIRED, ACKNOWLEDGED }
    public record Decision(State state,int requests,String detail) {}
    private UUID identity;private int lastProgress,requests,unconfirmed;private long nextRequest,started,progressAt;
    public void reset() {identity=null;lastProgress=-1;requests=0;unconfirmed=0;nextRequest=started=progressAt=0;}
    public Decision tick(UUID id,int progress,boolean present,boolean actionable,boolean paused,long now) {
        return tick(id,progress,present,actionable,paused,false,now);
    }
    public Decision tick(UUID id,int progress,boolean present,boolean actionable,boolean paused,boolean removed,long now) {
        if(id==null) {
            reset();
            return new Decision(State.NONE,0,"");
        }
        if(removed) {
            if(id.equals(identity)) {reset();return new Decision(State.ACKNOWLEDGED,0,"Server removed objective body");}
            return new Decision(State.NONE,0,"");
        }
        if(!id.equals(identity)) {reset();identity=id;lastProgress=progress;started=progressAt=now;}
        if(present&&progress>=0&&progress!=lastProgress) {unconfirmed=0;lastProgress=progress;progressAt=now;}
        if(now-started>=90_000||unconfirmed>=12&&now-progressAt>=20_000)
            return new Decision(State.EXPIRED,requests,"Objective acknowledgment timed out; disengage and stop");
        if(!present)return new Decision(State.PAUSED,requests,"Objective tracking incomplete; hold attacks");
        if(paused||!actionable)return new Decision(State.PAUSED,requests,"Objective pending; preserve lethal-mechanic response");
        if(progress==0)return new Decision(State.WAIT,requests,"Counter exhausted; awaiting server body removal");
        if(unconfirmed>=12)return new Decision(State.STALLED,requests,"No server objective progress after bounded requests");
        if(now<nextRequest)return new Decision(State.WAIT,requests,"Awaiting server objective progress/removal");
        requests++;unconfirmed++;nextRequest=now+200;
        return new Decision(State.REQUEST,requests,"Objective request; server destruction unconfirmed");
    }
}
