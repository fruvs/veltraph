package dev.veltraph.client.combat.ghost;

/** Scoring after source native loaded/support/ray predicates; it cannot make unknown geometry valid. */
public final class GhostSourceStandingScore {
    private GhostSourceStandingScore() {}
    public static double score(double distanceToTargetStanding,double distanceToPlayer,
                               double parentFieldValue,boolean lavaAtOrBelow,double customPenalty) {
        if (!Double.isFinite(distanceToTargetStanding) || distanceToTargetStanding<0 || distanceToTargetStanding>2.5
                || !Double.isFinite(distanceToPlayer) || distanceToPlayer<0
                || !Double.isFinite(parentFieldValue) || !Double.isFinite(customPenalty))
            throw new IllegalArgumentException("Exact finite source score operands required");
        return Math.pow(distanceToTargetStanding-2,2)+distanceToPlayer+Math.pow(parentFieldValue,2)
                +(lavaAtOrBelow?400:0)+customPenalty;
    }
}
