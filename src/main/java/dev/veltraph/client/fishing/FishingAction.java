package dev.veltraph.client.fishing;

/**
 * The single side-effect the pure {@link FishingRuntime} asks the MC layer to perform this tick. Both
 * {@link #CAST} and {@link #REEL} are a single rod right-click (the same {@code interactItem} action that
 * casts when no bobber is out and reels when one is); they are distinguished only for logging/clarity.
 */
public enum FishingAction {
    /** Do nothing this tick. */
    NONE,
    /** Right-click the rod to cast (no bobber out). */
    CAST,
    /** Right-click the rod to reel in a bite. */
    REEL
}
