package dev.veltraph.client.galatea;

import java.util.Objects;

/** Pure one-event custody. Native consumption/submission never establish a server effect. */
public final class OwnedNativeUseTicket {
    public record Frame(Object owner,long generation,Object player,Object world,Object connection,
                        int slot,Object loadout,String config,Object rootAuthority,Object cameraAuthority,
                        long cameraEpoch,long callerDeadlineMillis,long nowMillis,boolean healthy) {}
    public enum Phase { ISSUED, QUEUED, NATIVE_CONSUMED, OUTGOING_SUBMITTED, REVOKED, QUEUE_RELEASED }
    public enum Consumption { FOREIGN, ALLOW_OWNED, DROP_OWNED }
    private final Frame issued;
    private final long deadline;
    private long lastMillis;
    private Phase phase=Phase.ISSUED;
    private boolean headPending, invocationReady, inInvocation, revoked;
    private int nativeConsumptions, outgoingSubmissions, authorizedSubmissions;

    public OwnedNativeUseTicket(Frame issued,long originalDeadlineMillis) {
        this.issued=Objects.requireNonNull(issued);deadline=originalDeadlineMillis;lastMillis=issued.nowMillis();
        if(!eligible(issued,deadline))throw new IllegalArgumentException("Finite original caller deadline and exact healthy ownership required");
    }
    public static boolean eligible(Frame f,long originalDeadlineMillis){return structural(f)
            &&originalDeadlineMillis<Long.MAX_VALUE&&originalDeadlineMillis>f.nowMillis()
            &&f.callerDeadlineMillis()==originalDeadlineMillis;}
    private static boolean structural(Frame f){return f!=null&&f.owner()!=null&&f.generation()>=0
            &&f.player()!=null&&f.world()!=null&&f.connection()!=null&&f.slot()>=0&&f.slot()<9
            &&f.loadout()!=null&&f.config()!=null&&f.rootAuthority()==f.owner()&&f.cameraAuthority()==f.owner()
            &&f.cameraEpoch()>=0&&f.nowMillis()>=0&&f.healthy();}
    public boolean matches(Frame current){
        if(revoked||!structural(current)||current.nowMillis()<lastMillis||current.nowMillis()>deadline)return false;
        lastMillis=current.nowMillis();
        return current.owner()==issued.owner()&&current.generation()==issued.generation()
                &&current.player()==issued.player()&&current.world()==issued.world()&&current.connection()==issued.connection()
                &&current.slot()==issued.slot()&&Objects.equals(current.loadout(),issued.loadout())
                &&Objects.equals(current.config(),issued.config())&&current.rootAuthority()==issued.rootAuthority()
                &&current.cameraAuthority()==issued.cameraAuthority()&&current.cameraEpoch()==issued.cameraEpoch()
                &&current.callerDeadlineMillis()==deadline;
    }
    /** Actual zero-before/one-after KeyMapping.click witness, not an input intent. */
    public boolean queued(int before,int after){
        if(phase!=Phase.ISSUED||before!=0||after!=1){revoke();return false;}
        headPending=true;phase=Phase.QUEUED;return true;
    }
    /** Vanilla already decremented one event. Tail events are never cleared or adopted. */
    public Consumption consumed(Frame current,int before,int after,boolean vanillaResult,boolean normalUsePath){
        if(!headPending)return Consumption.FOREIGN;
        // An empty/false poll proves no decrement. Only the real release callback forgets queue custody.
        if(!vanillaResult||before<=0||after!=before-1)return Consumption.FOREIGN;
        headPending=false;nativeConsumptions++;
        if(!normalUsePath||!matches(current)){revoke();return Consumption.DROP_OWNED;}
        phase=Phase.NATIVE_CONSUMED;invocationReady=true;return Consumption.ALLOW_OWNED;
    }
    /** Only the immediate normal-use callback may open the one native invocation. */
    public boolean beginUse(Frame current){
        if(!invocationReady||inInvocation||!matches(current)){revoke();return false;}
        invocationReady=false;inInvocation=true;return true;
    }
    public boolean beforeOutgoing(Frame current){
        if(!inInvocation||!matches(current)){revoke();return false;}authorizedSubmissions++;return true;
    }
    /** Called after the actual native send method returns; submission is not server acknowledgement. */
    public void outgoingSubmitted(){
        if(authorizedSubmissions<=0)throw new IllegalStateException("No prior owned submission permission");
        authorizedSubmissions--;
        outgoingSubmissions++;if(!revoked)phase=Phase.OUTGOING_SUBMITTED;
    }
    public void endUse(){invocationReady=false;inInvocation=false;}
    public void revoke(){revoked=true;invocationReady=false;phase=Phase.REVOKED;}
    /** Vanilla discarded the queue. Future manual events no longer belong to this ticket. */
    public void queueReleased(){headPending=false;invocationReady=false;revoked=true;phase=Phase.QUEUE_RELEASED;}
    public Object owner(){return issued.owner();} public long generation(){return issued.generation();}
    public Object connection(){return issued.connection();}
    public long originalDeadlineMillis(){return deadline;} public boolean headPending(){return headPending;}
    public Phase phase(){return phase;} public int nativeConsumptions(){return nativeConsumptions;}
    public int outgoingSubmissions(){return outgoingSubmissions;}
}
