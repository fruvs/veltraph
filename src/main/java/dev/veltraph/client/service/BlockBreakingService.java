package dev.veltraph.client.service;

/**
 * Crop/block breaking input. Farming breaks crops by holding the attack input while walking the row,
 * so the surface is a simple held-state toggle rather than a one-shot break call.
 */
public interface BlockBreakingService {

    /** Hold ({@code true}) or release ({@code false}) the attack/break input. */
    void setBreaking(boolean breaking);

    boolean isBreaking();

    /**
     * Guard every vanilla start/continue breaking attempt using its actual target coordinate.
     * Install for the enabled module's lifetime; releasing the attack key does not remove the guard.
     * Minecraft adapters bind this ownership to the current player/world/interaction context.
     * Pure service fakes may leave this as a no-op.
     */
    default void installTargetGuard(Object owner,
            java.util.function.Predicate<dev.veltraph.client.world.BlockPos> allowedTarget) {}

    /** Remove only the guard installed by this exact owner; stale owners cannot clear a new guard. */
    default void clearTargetGuard(Object owner) {}

    /** Loaded block under the actual crosshair, or empty when there is no block hit. */
    default String targetBlockId() { return ""; }

    /** Actual loaded crosshair block coordinate for crop-bed validation. */
    default java.util.Optional<dev.veltraph.client.world.BlockPos> targetBlockPos() { return java.util.Optional.empty(); }

    /**
     * Read-only outstanding vanilla block predictions for this exact guard owner and native context.
     * Empty Optional means unknown/stale; a present empty set is an observed acknowledgment witness.
     * Pure nonpredicting adapters have a known empty set. Never filter predicted-air coordinates.
     */
    default java.util.Optional<java.util.Set<dev.veltraph.client.world.BlockPos>> pendingBlockChanges(Object owner) {
        return java.util.Optional.of(java.util.Set.of());
    }

    /** Fresh read-only native pick for traversal observation; never replaces the actual action target. */
    default java.util.Optional<dev.veltraph.client.world.BlockPos> freshTargetBlockPos() { return java.util.Optional.empty(); }
    /** Opaque receipt of the exact cached crop policy/native-context binding. */
    interface TargetGuardBinding {}
    default java.util.Optional<TargetGuardBinding> targetGuardBinding(Object owner){return java.util.Optional.empty();}
    default boolean targetGuardBoundToCurrentContext(Object owner,TargetGuardBinding binding){return false;}
    /** Compare-and-set only the native references, retaining the exact source crop predicate. */
    default java.util.Optional<TargetGuardBinding> rebindTargetGuard(Object owner,TargetGuardBinding expected){return java.util.Optional.empty();}
}
