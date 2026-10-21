package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
/** Passive proof of the one original Player.aiStep speed-field refresh callsite. */
public interface GroundOwnedSpeedRefreshCarrier {
    Refresh ownedSpeedRefresh();
    record Refresh(int tick,float before,float after,GroundPhysicsNativeRevisions.Token beforeRevision,
            GroundPhysicsNativeRevisions.Token afterRevision) {}
}
