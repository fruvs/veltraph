package dev.veltraph.client.rewarp;
import dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision;
/** Root must wait for the parent's unchanged native footing predicates after the receipt arrives. */
public final class GhostRootAdmission {
    private GhostRootAdmission(){}
    public static Decision withParentFooting(Decision decision,boolean grounded,double horizontalMotion){
        return decision==Decision.COMMIT&&!(grounded&&horizontalMotion<=.02)?Decision.WAIT:decision;
    }
}
