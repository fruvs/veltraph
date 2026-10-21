package dev.veltraph.client.combat.ghost;
/** Cached observations made by the actual bounded native child pump, never synthetic readiness. */
public final class GhostSourceLeavingPhaseObservation {
    public record Snapshot(long captureBeganTick,long readyTick) {}
    private long began=-1,ready=-1;
    public void capturing(long actualTick){if(actualTick<0||began>=0)throw new IllegalStateException("One actual capture transition required");began=actualTick;}
    public void ready(long actualTick){if(began<0||ready>=0||actualTick<began)throw new IllegalStateException("Actual monotonic ready transition required");ready=actualTick;}
    public Snapshot snapshot(){return new Snapshot(began,ready);}
    public static long boundedBudget(long actualRemaining){return Math.max(0,Math.min(4_000_000L,actualRemaining));}
}
