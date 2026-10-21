package dev.veltraph.client.combat.heal;

/** One process-shared source END timestamp; cancellation/reset does not manufacture an END event. */
public final class HealSourceEndLedger {
    private long endedAt;
    public boolean mayAcquire(long now) { return HealSourceEntryTiming.elapsed(now,endedAt); }
    public void endBeforeRepeatOrRelease(long now) { endedAt=now; }
    public long endedAt() { return endedAt; }
}
