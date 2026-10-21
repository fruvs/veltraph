package dev.veltraph.client.combat.heal;

/** Source helper has one static type map; every native caller shares this history. */
public final class SharedHealUseLedger {
    private SharedHealUseLedger() {}
    private static final HealUseLedger LEDGER = new HealUseLedger();
    public static HealItemSelector selector() { return new HealItemSelector(LEDGER); }
    public static boolean cooling(HealItemType type, long now) { return LEDGER.cooling(type, now); }
    /** Local ordinary-use emission only; call after native ownership checks and immediately before use. */
    public static void recordEmittedUse(HealItemType type, long now) { LEDGER.recordEmittedUse(type, now); }
}
