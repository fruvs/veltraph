package dev.veltraph.client.combat.heal;

/** Fixed source entry debounce, recovered independently of the opaque tick-dispatch key. */
public final class HealSourceEntryTiming {
    private HealSourceEntryTiming() {}
    public static boolean elapsed(long now,long lastEnd) { return now-lastEnd>200; }
}
