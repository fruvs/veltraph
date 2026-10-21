package dev.veltraph.client.inventory.bottle;

/** One pre-dispatch attribution window; the independent native travel permit retains its original deadline. */
public final class BottleTeleportIntent {
    private BottleTeleportIntent() {}
    /** Matches the unchanged UnexpectedBehaviorMonitor.intent maximum, in milliseconds. */
    private static final long MAX_INTENT_MILLIS=5000;
    public record Lifetime(long millis) {
        public Lifetime {if(millis<1||millis>MAX_INTENT_MILLIS)throw new IllegalArgumentException("Bounded Bottle attribution lifetime required");}
    }
    public static Lifetime forActionTimeoutTicks(int ticks){
        if(ticks<1||ticks>2400)throw new IllegalArgumentException("Original bounded Bottle action timeout required");
        return new Lifetime(Math.min(MAX_INTENT_MILLIS,ticks*50L));
    }
}
