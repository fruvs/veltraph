package dev.veltraph.client.rewarp;
import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Admission;
import dev.veltraph.client.combat.ghost.InstalledGhostWarpLifecycle.Decision;
/** Arrival validation remains the Bottle parent's; Root additionally retains its existing settled-footing gate. */
public final class BottleRootAdmission {
    private BottleRootAdmission(){}
    public static Admission withParentFooting(Admission admission,boolean grounded,double horizontalMotion){
        return admission==Admission.READY&&GhostRootAdmission.withParentFooting(Decision.COMMIT,grounded,horizontalMotion)!=Decision.COMMIT?Admission.WAIT:admission;
    }
}
