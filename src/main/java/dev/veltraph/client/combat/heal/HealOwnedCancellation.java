package dev.veltraph.client.combat.heal;

/** McRotationService.cancelRotation synchronously invokes exactly one trajectory.cancel epoch increment. */
public final class HealOwnedCancellation {
    private HealOwnedCancellation() {}
    public static boolean mayRenew(HealCameraLease before,HealCameraLease after,
            boolean validBefore,boolean installedBefore,boolean installedAfter) {
        if(!validBefore||installedBefore!=installedAfter||before==null||after==null)return false;
        if(before.player()!=after.player()||before.world()!=after.world()||before.connection()!=after.connection()
                ||before.authority()!=after.authority())return false;
        return installedBefore?after.epoch()==before.epoch()+1:after.epoch()==before.epoch();
    }
}
