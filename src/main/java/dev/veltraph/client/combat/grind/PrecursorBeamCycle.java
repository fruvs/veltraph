package dev.veltraph.client.combat.grind;

/** One owned source false/true/false pulse, with native outcome and interrupted-input uncertainty. */
public final class PrecursorBeamCycle {
    public enum State { UNKNOWN,OFF,ON }
    private State state=State.UNKNOWN;
    private Boolean desired;
    private long issued;
    private int phase;
    private boolean acknowledged,failed;
    private String reason="Awaiting observed beam state";

    public void observe(State value){
        if(value==State.UNKNOWN)return;
        state=value;
        if(failed)return;
        if(desired!=null){
            acknowledged=desired==(state==State.ON);
            // No toggle was emitted yet, or all source pulse segments have completed.
            if(acknowledged&&(phase<=2||phase>=6))complete();
        }
    }
    public boolean request(boolean on,long time){
        if(failed||state==State.UNKNOWN||desired!=null)return false;
        if((state==State.ON)==on)return true;
        desired=on;phase=0;issued=time;acknowledged=false;
        reason="One owned beam toggle; awaiting native outcome and source pulse completion";
        return true;
    }
    /** Called once per client tick; an early receipt does not shorten either held source tick. */
    public boolean sneak(long time,long timeout){
        if(desired==null||failed)return false;
        if(!acknowledged&&time-issued>timeout){
            failed=true;phase=6;
            reason="Beam toggle not acknowledged; explicit OFF observation required";
            return false;
        }
        int p=phase++;
        boolean held=p>=2&&p<4;
        if(phase>=6&&acknowledged)complete();
        return held;
    }
    /** Quiesce released the key: never resume an interrupted held segment as a second rising edge. */
    public void interruptPulse(){
        if(desired==null)return;
        if(phase<=2){desired=null;phase=0;acknowledged=false;reason="Owned pulse cancelled before any toggle";return;}
        phase=6;
        if(acknowledged)complete();
    }
    private void complete(){desired=null;phase=0;acknowledged=false;reason="Received beam "+state;}
    public State state(){return state;}
    public boolean pending(){return desired!=null;}
    public boolean failed(){return failed;}
    public String status(){return reason;}
    public boolean safelyOff(){return state==State.OFF&&desired==null;}
    public void reset(){state=State.UNKNOWN;desired=null;phase=0;acknowledged=false;failed=false;reason="Awaiting observed beam state";}
    /** Exact OFF resolves uncertainty; ordinary OFF acknowledgment retains both held pulse ticks. */
    public void observedOff(){
        if(!failed&&Boolean.FALSE.equals(desired)){observe(State.OFF);return;}
        state=State.OFF;desired=null;phase=0;acknowledged=false;failed=false;reason="Received beam OFF";
    }
}
