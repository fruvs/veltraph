package dev.veltraph.client.rewarp;

import java.util.Objects;

/** Pure token/state ownership. Native identity, loadout, menus and physical proof belong to its port. */
public final class FarmMaintenanceLease {
    public enum State { IDLE, PREPARED, HELPER_ACTIVE, RESTORING, RETURNING, READY, RESUMED, FAILED, CANCELLED }
    public static final class Token {private Token(){}}
    private Token token;private State state=State.IDLE;private long started,last,deadline;
    private Object farm,helper;private String reason="none";
    public Token prepare(Object farm,Object helper,long now,long timeout){
        if(active()||farm==null||helper==null||farm==helper||timeout<=0)return null;
        this.farm=farm;this.helper=helper;started=last=now;deadline=timeout;token=new Token();state=State.PREPARED;reason="reserved exact pair";return token;
    }
    public boolean commit(Token candidate){if(!at(candidate,State.PREPARED))return false;state=State.HELPER_ACTIVE;reason="helper owns maintenance";return true;}
    /** Failed activation has no farm-suspension side effect and leaves no token behind. */
    public void rollback(Token candidate){if(at(candidate,State.PREPARED)){clear();reason="activation rolled back";}}
    public boolean beginRestore(Token candidate){if(!at(candidate,State.HELPER_ACTIVE))return false;state=State.RESTORING;reason="restoring farming loadout";return true;}
    public boolean beginReturn(Token candidate){if(!at(candidate,State.RESTORING))return false;state=State.RETURNING;reason="returning to captured farm pose";return true;}
    public boolean ready(Token candidate){if(!at(candidate,State.RETURNING))return false;state=State.READY;reason="physical return verified; explicit resume required";return true;}
    public boolean resume(Token candidate){if(!at(candidate,State.READY))return false;state=State.RESUMED;reason="same engine resumed";return true;}
    /** Distinct same-pose terminal operation. No Boolean or public loadout record can waive return proof. */
    public boolean completeRetained(Token candidate,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation actual){
        if(!at(candidate,State.HELPER_ACTIVE)||actual==null||!actual.terminalLeaseCurrent(this,candidate))return false;
        long finalNow=System.currentTimeMillis();if(!deadlineCurrent(candidate,finalNow)){actual.retireObservedLeaseExpiry(this,candidate,finalNow);return false;}
        state=State.RESUMED;reason="native retained same-pose loadout; same engine resumed";return true;
    }
    /** Negative clock fence during actual retained native closure; no phase refresh or resumed authority. */
    public boolean completedRetainedDeadlineCurrent(Token candidate,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.RetainedLoadoutOperation actual,long now){
        if(candidate==null||candidate!=token||state!=State.RESUMED||actual==null||!actual.completedOriginalLeaseIdentity(this,candidate))return false;
        long elapsed=now-started;return now>=last&&now>=started&&elapsed>=0&&elapsed<deadline;
    }
    /** Read-only original timeout fence; no token/clock refresh, state change, callback, or new permission. */
    public boolean deadlineCurrent(Token candidate,long now){long elapsed=now-started;return candidate!=null&&candidate==token&&active()&&now>=last&&now>=started&&elapsed>=0&&elapsed<deadline;}
    /** Diagnostic only; never grants a current deadline or a loadout operation. */
    public long elapsedMillis(Token candidate,long now){long elapsed=now-started;return candidate==token&&candidate!=null&&now>=started&&elapsed>=0?elapsed:-1;}
    public boolean validate(Token candidate,Object farm,Object helper,long now,boolean identitiesValid){
        if(candidate==null||candidate!=token||!active())return false;
        if(this.farm!=farm||this.helper!=helper||!identitiesValid||now<last||now-started>=deadline){cancel(candidate,"Lease identity/context/deadline invalid");return false;}
        last=now;return true;
    }
    public void cancel(Token candidate,String reason){if(candidate!=null&&candidate==token&&active()){state=State.CANCELLED;this.reason=Objects.requireNonNullElse(reason,"cancelled");}}
    public void fail(Token candidate,String reason){if(candidate!=null&&candidate==token&&active()){state=State.FAILED;this.reason=Objects.requireNonNullElse(reason,"failed");}}
    public boolean owns(Token candidate){return candidate!=null&&candidate==token&&active();}
    private boolean at(Token candidate,State required){return candidate!=null&&candidate==token&&state==required;}
    public boolean active(){return state==State.PREPARED||state==State.HELPER_ACTIVE||state==State.RESTORING||state==State.RETURNING||state==State.READY;}
    public State state(){return state;}
    public Object farm(){return farm;}public Object helper(){return helper;}
    public void clear(){token=null;farm=helper=null;state=State.IDLE;started=last=deadline=0;}
    public String status(){return "maintenance: "+state+" | "+reason;}
}
