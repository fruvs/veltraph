package dev.veltraph.client.failsafe.response;

/**
 * Focused dev-test routines that exercise one slice of the failsafe response harness in isolation (so a
 * developer can verify look / jump+sneak / clicks / movement separately, plus a full plan). Not used by
 * production triggers — production uses per-{@link FailsafeResponseType} plans.
 */
public enum FailsafeRoutine {
    /** Just the alternating look sweep. */
    LOOK_SWEEP,
    /** A jump then a brief sneak. */
    JUMP_SNEAK,
    /** An attack click then a use click. */
    CLICKS,
    /** A short forward / back / strafe movement shuffle. */
    MOVEMENT_SHUFFLE,
    /** The full LOCAL_SAFE-style plan (look + move + jump). */
    FULL_PLAN
}
