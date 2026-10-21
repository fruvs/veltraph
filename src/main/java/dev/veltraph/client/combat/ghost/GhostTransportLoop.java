package dev.veltraph.client.combat.ghost;

import java.util.Optional;

/** Source clock-driven retries. The absolute local parent deadline is never renewed per attempt. */
public final class GhostTransportLoop {
    public enum State { ACTIVE, COMPLETE, EXHAUSTED, CANCELLED }
    public record Attempt(Object activation,int ordinal,long issuedAt,long deadline) {
        public Attempt {if(activation==null||ordinal<1||deadline<issuedAt)throw new IllegalArgumentException("Bound transport attempt required");}
    }
    private final Object activation=new Object();private final long interval,deadline;private long lastIssued;
    private int attempts;private Attempt current;private boolean dispatched;private State state=State.ACTIVE;
    public GhostTransportLoop(long startedAt,long interval,long duration){
        if(startedAt<0||interval<1||duration<500||duration>60000)throw new IllegalArgumentException("Explicit Ghost transport interval/deadline required");
        this.interval=interval;deadline=Math.addExact(startedAt,duration);
    }
    /** Timestamp is consumed even when the native command helper subsequently returns false. */
    public Optional<Attempt> next(long now){
        if(state!=State.ACTIVE)return Optional.empty();
        expire(now);if(state!=State.ACTIVE)return Optional.empty();
        if(now-lastIssued<=interval)return Optional.empty();
        lastIssued=now;current=new Attempt(activation,++attempts,now,deadline);dispatched=false;return Optional.of(current);
    }
    public void dispatched(Attempt attempt,boolean succeeded){if(state==State.ACTIVE&&attempt==current)dispatched=succeeded;}
    public boolean complete(Attempt attempt,long now){if(state!=State.ACTIVE||attempt!=current||!dispatched||now<attempt.issuedAt()||now>deadline)return false;state=State.COMPLETE;return true;}
    public void expire(long now){if(state==State.ACTIVE&&now>deadline){state=State.EXHAUSTED;current=null;dispatched=false;}}
    public Optional<Attempt> current(){return state==State.ACTIVE?Optional.ofNullable(current):Optional.empty();}
    public boolean dispatched(){return dispatched;}public int attempts(){return attempts;}public long deadline(){return deadline;}public State state(){return state;}
    public void cancel(){state=State.CANCELLED;current=null;dispatched=false;}
}
