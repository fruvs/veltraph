package dev.veltraph.client.foraging.maintenance;

/** Same-world, exact-parent ownership. A helper stop alone never grants a resume. */
public final class ForagingMaintenanceLease {
    public enum State { IDLE, PREPARED, CLAIMING, CLEANUP, RETURNING, RESUMED, CANCELLED }
    public static final class Token { private Token() {} }
    private Token token;
    private Object parent, helper;
    private State state=State.IDLE;
    private long started,last,timeout;
    public Token prepare(Object parent,Object helper,long now,long timeout){
        if(active()||parent==null||helper==null||parent==helper||timeout<=0)return null;
        this.parent=parent;this.helper=helper;started=last=now;this.timeout=timeout;
        token=new Token();state=State.PREPARED;return token;
    }
    public boolean commit(Token value){return move(value,State.PREPARED,State.CLAIMING);}
    public boolean cleanup(Token value){return move(value,State.CLAIMING,State.CLEANUP);}
    public boolean returning(Token value){return move(value,State.CLEANUP,State.RETURNING);}
    public boolean resume(Token value){return move(value,State.RETURNING,State.RESUMED);}
    private boolean move(Token value,State from,State to){if(value==null||value!=token||state!=from)return false;state=to;return true;}
    public boolean validate(Token value,Object parent,Object helper,long now,boolean valid){
        if(!owns(value))return false;
        if(this.parent!=parent||this.helper!=helper||!valid||now<last||now-started>=timeout){cancel(value);return false;}
        last=now;return true;
    }
    public void rollback(Token value){if(value!=null&&value==token&&state==State.PREPARED)clear();}
    public void cancel(Token value){if(owns(value))state=State.CANCELLED;}
    public boolean owns(Token value){return value!=null&&value==token&&active();}
    public boolean active(){return state==State.PREPARED||state==State.CLAIMING||state==State.CLEANUP||state==State.RETURNING;}
    public State state(){return state;}
    private void clear(){state=State.IDLE;token=null;parent=helper=null;}
}
