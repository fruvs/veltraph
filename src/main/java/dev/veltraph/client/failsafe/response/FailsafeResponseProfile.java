package dev.veltraph.client.failsafe.response;

/**
 * How elaborate the local failsafe response routine is. All profiles are local-only (camera + key inputs);
 * none send chat, and only the {@code COMMAND} action — never produced by these built-in profiles — would
 * run a vanilla command.
 */
public enum FailsafeResponseProfile {
    /** A short look-around only (most conservative). */
    MINIMAL,
    /** Look around + a brief move/jump shuffle (the default — looks human without much disruption). */
    LOCAL_SAFE,
    /** Look + move + jump/sneak + clicks (the most active local routine). */
    FULL
}
