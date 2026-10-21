package dev.veltraph.client.service;

import dev.veltraph.client.world.Vec3;

/**
 * Camera aiming. Implementations must aim deterministically; Veltraph deliberately omits the
 * humanized randomization present in the original mod (see project hard rules).
 */
public interface RotationService {

    float yaw();

    float pitch();

    /** Aim toward an absolute yaw/pitch (degrees, Minecraft convention). */
    void aim(float yaw, float pitch);

    /** Take one source-derived, mouse-quantized step toward the complete target. */
    default void track(float yaw,float pitch) { aim(yaw,pitch); }
    /** Refusal-only caller leaf checked by a known native producer after its public callbacks.
     * Returned data never authorizes movement, settlement, restart or an unknown implementation. */
    default Object trackWithFinalNativeVeto(float yaw,float pitch,java.util.function.BooleanSupplier finalNativeVeto){return null;}
    /** Replace an asynchronous target. A new request supersedes the previous callback. */
    default void rotateTo(float yaw,float pitch,Runnable completed) { throw new UnsupportedOperationException("Queued rotation unavailable"); }
    default void tickRotation() {}
    default void cancelRotation() {}
    /** Read-only recovered busy(true) predicate; adapters without source axis controllers are idle. */
    default boolean sourceBusy(){return false;}

    /** Aim toward a world position. */
    void lookAt(Vec3 target);
}
