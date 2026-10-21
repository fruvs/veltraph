package dev.veltraph.client.combat.ghost;

/** Source invalid-body order; this result grants no attack or movement permission. */
public final class GhostSourceBodyEligibility {
    private GhostSourceBodyEligibility() {}
    public static boolean eligible(boolean living,boolean graceReady,boolean alive,boolean nativeTicking,boolean deadOrHealthNonPositive){
        if(living&&!graceReady)return true; // Original returns not-invalid before other body checks during grace.
        if(!alive||!nativeTicking)return false;
        return !living||!deadOrHealthNonPositive;
    }
    /** Qualified modern defensive safety, separate from source's permissive fresh-body classification. */
    public static boolean defensiveBodySafe(boolean alive,boolean nativeTicking,boolean deadOrHealthNonPositive){
        return alive&&nativeTicking&&!deadOrHealthNonPositive;
    }
}
