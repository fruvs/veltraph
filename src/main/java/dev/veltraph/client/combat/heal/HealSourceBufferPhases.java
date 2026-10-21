package dev.veltraph.client.combat.heal;

/** Post-use source phases: one handler phase per invocation with pre-switch decrement. */
public final class HealSourceBufferPhases {
    public enum Step { WAIT, SWITCH_DECISION, END }
    private enum Phase { BUFFER, SWITCH, END }
    private final HealSourcePhaseDelay delay=new HealSourcePhaseDelay();
    private Phase phase=Phase.BUFFER;
    private boolean active;
    public void emitted(){active=true;phase=Phase.BUFFER;delay.reset();}
    public void reset(){active=false;phase=Phase.BUFFER;delay.reset();}
    public boolean inBuffer(){return active&&phase==Phase.BUFFER;}
    /** Actual eligible source pose packet immediately changes phase, without sampling buffer delay. */
    public void posePacket(){if(active&&phase==Phase.BUFFER){phase=Phase.SWITCH;delay.reset();}}
    public Step tick(boolean bufferReady,int sampledBufferTicks){
        if(!active||!delay.enterNextInvocation())return Step.WAIT;
        if(phase==Phase.BUFFER){
            if(!bufferReady)return Step.WAIT;
            phase=Phase.SWITCH;delay.install(sampledBufferTicks);return Step.WAIT;
        }
        if(phase==Phase.SWITCH)return Step.SWITCH_DECISION;
        active=false;return Step.END;
    }
    public void decisionMade(int sampledEndTicks){phase=Phase.END;delay.install(sampledEndTicks);}
}
