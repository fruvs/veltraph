package dev.veltraph.client.combat.ghost;
/** Arbitration only; actual native scope validation remains with the private children and canonical parent. */
public final class GhostSourceRecoveryConcurrency {
    public enum Writer { FALLBACK, LEAVING, NONE }
    public record Turn(boolean prepareLeaving,boolean observeLeavingDeadline,boolean observeLeavingEvent,Writer writer) {}
    private GhostSourceRecoveryConcurrency() {}
    public static Turn turn(boolean actualFallbackCursorActive,boolean actualLeavingInstalled){
        return new Turn(actualLeavingInstalled,actualLeavingInstalled,actualLeavingInstalled,
                actualFallbackCursorActive?Writer.FALLBACK:actualLeavingInstalled?Writer.LEAVING:Writer.NONE);
    }
}
