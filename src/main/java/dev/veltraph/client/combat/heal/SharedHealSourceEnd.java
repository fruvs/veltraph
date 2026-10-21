package dev.veltraph.client.combat.heal;

public final class SharedHealSourceEnd {
    private SharedHealSourceEnd() {}
    private static final HealSourceEndLedger LEDGER=new HealSourceEndLedger();
    static HealSourceEndLedger processLedger() { return LEDGER; }
    public static boolean mayAcquire(long now) { return LEDGER.mayAcquire(now); }
    public static void endBeforeRepeatOrRelease(long now) { LEDGER.endBeforeRepeatOrRelease(now); }
}
