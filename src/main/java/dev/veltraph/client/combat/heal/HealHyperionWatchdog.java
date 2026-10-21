package dev.veltraph.client.combat.heal;

/** Exact conditional post-use watchdog; STARTING's Long.MAX_VALUE is an unarmed sentinel. */
public final class HealHyperionWatchdog {
    public enum Decision { WAIT, CONTINUE, TIMED_OUT }
    private long usedAt=Long.MAX_VALUE;
    public void reset(){usedAt=Long.MAX_VALUE;}
    public void emitted(long now){usedAt=now;}
    public Decision read(long now,boolean hyperionLookDown,boolean currentOwnedBuffer,boolean nativePositionReceipt){
        if(!currentOwnedBuffer||usedAt==Long.MAX_VALUE)return Decision.WAIT;
        if(!hyperionLookDown)return Decision.CONTINUE;
        if(nativePositionReceipt)return Decision.CONTINUE;
        return now-usedAt>500?Decision.TIMED_OUT:Decision.WAIT;
    }
}
