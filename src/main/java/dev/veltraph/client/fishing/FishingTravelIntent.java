package dev.veltraph.client.fishing;

import dev.veltraph.client.navigation.SharedMovementPolicy;

/** Between-job travel varies its ground journey; every saved cast/standing cursor remains exact. */
public enum FishingTravelIntent {
    TRASH_CLIFF(5000,50,.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL),
    VANESSA_APPROACH(2500,32,.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL),
    TRASH_RETURN(5000,50,.35,SharedMovementPolicy.Purpose.EXACT_RETURN),
    RAIN_RETURN(2500,32,.35,SharedMovementPolicy.Purpose.EXACT_RETURN),
    OBSTRUCTION_RETURN(2500,32,.35,SharedMovementPolicy.Purpose.EXACT_RETURN),
    GRAPPLE_RETURN(2500,32,.35,SharedMovementPolicy.Purpose.EXACT_RETURN),
    COMBAT_RETURN(2500,32,.18,SharedMovementPolicy.Purpose.EXACT_RETURN),
    OFFENSE_DRY_RETURN(2500,32,.18,SharedMovementPolicy.Purpose.EXACT_RETURN);
    private final int nodes,radius;private final double tolerance;private final SharedMovementPolicy.Purpose purpose;
    FishingTravelIntent(int nodes,int radius,double tolerance,SharedMovementPolicy.Purpose purpose){this.nodes=nodes;this.radius=radius;this.tolerance=tolerance;this.purpose=purpose;}
    public int nodes(){return nodes;}public int radius(){return radius;}public double tolerance(){return tolerance;}
    public SharedMovementPolicy.Purpose purpose(){return purpose;}
}
