package dev.veltraph.client.combat.heal;

/** Source parent-routed message prefix recovered from inert DES/classpool caller replay. */
public final class HealFloridFeedback {
    private HealFloridFeedback() {}
    public static boolean record(String message,long now,HealItemSelector selector) {
        if(message==null||!message.startsWith("No more charges"))return false;
        selector.recordEmittedUse(HealItemType.FLORID,now+15000);
        return true;
    }
    /** Uses the accepted helper-level singleton; timestamp denotes the source hold, not emitted use. */
    public static boolean recordShared(String message,long now) {
        return record(message,now,SharedHealUseLedger.selector());
    }
}
