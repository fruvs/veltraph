package dev.veltraph.client.foraging;

/**
 * The break-key state the pure {@link TreeBreaker} wants this tick.
 */
public enum TreeBreakAction {
    /** Release the attack key (no valid tree under the crosshair, or aborted). */
    RELEASE,
    /** Hold the attack key to break the targeted log. */
    BREAK
}
