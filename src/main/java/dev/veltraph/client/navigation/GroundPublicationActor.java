package dev.veltraph.client.navigation;
/** Explicit construction-time dry-ground publication actor. Implementations must guard each input
 * operation and cleanup by their own actual publication receipts; raw epoch adoption is forbidden. */
public interface GroundPublicationActor {
    boolean current();
    /** Publish through the actor's actual owned camera path and return its positive native ticket. */
    long publishYaw(float yaw);
    /** Guarded same-leg cleanup only. Never close or release a foreign/newer publication actor. */
    void cleanupOwned();
}
