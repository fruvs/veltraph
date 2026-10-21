package dev.veltraph.client.combat.ghost;

/** Captured issued-job camera owner and revocation epoch. No generic renewal or silent rebasing. */
public final class GhostSourceJobCameraCustody {
    private final Object owner;private final long epoch;
    public GhostSourceJobCameraCustody(Object actualOwner,long actualEpoch){
        owner=java.util.Objects.requireNonNull(actualOwner);
        if(actualEpoch<0)throw new IllegalArgumentException("Actual camera epoch required");epoch=actualEpoch;
    }
    public boolean current(Object actualOwner,long actualEpoch){return owner==actualOwner&&epoch==actualEpoch;}
}
