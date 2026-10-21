package dev.veltraph.client.combat.heal;

/** Exact pre-dispatch counter: decrement and return before the switch, including one -> zero. */
public final class HealSourcePhaseDelay {
    private int remaining;
    public void reset() { remaining = 0; }
    /** Receives the already sampled source integer, never a millisecond conversion. */
    public void install(int sampledTicks) {
        if (sampledTicks < 0 || sampledTicks > 2) throw new IllegalArgumentException("Recovered phase delay must be zero to two ticks");
        remaining = sampledTicks;
    }
    public int remaining() { return remaining; }
    public boolean enterNextInvocation() {
        if (remaining > 0) { remaining--; return false; }
        return true;
    }
}
