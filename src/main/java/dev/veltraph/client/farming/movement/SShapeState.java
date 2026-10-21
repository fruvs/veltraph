package dev.veltraph.client.farming.movement;

/**
 * States for the lateral S-shape row engine ({@link SShapeRowEngine}). The main crop-row traversal is
 * <b>side-to-side</b> across the row ({@link #FARMING_SIDEWAYS}); at a row end the engine steps
 * <b>forward</b> into the next row ({@link #ROW_TRANSITION_FORWARD}) and then sweeps back the other
 * lateral direction — the corrected S-shape axis (goal Target 2 / root cause #7). The "return" sweep is
 * the same {@code FARMING_SIDEWAYS} state with the lateral direction reversed.
 */
public enum SShapeState {
    /** Not running. */
    IDLE,
    /** Ensuring the crop tool is selected (and grounded) before aiming. */
    PREPARE_TOOL,
    /** Smoothly panning to the row heading + crop target before any movement. */
    AIMING,
    /** Strafing along the crop row (primary motion) while breaking the crop in front. */
    FARMING_SIDEWAYS,
    /** Stepping forward into the next row at a row end, before reversing the lateral direction. */
    ROW_TRANSITION_FORWARD,
    /** Loaded supplemental water strip reached through real keyed cap traversal; row view stays held. */
    SUPPLEMENTAL_COVERAGE,
    /** Explicit validated ledge descent owned by the shared navigator; farm aim pauses. */
    INTENTIONAL_DROP,
    /** Stuck: inputs released while waiting for a valid lane (60s reconsider). */
    RECOVERING,
    /** Stopped by the user / safety guard; all inputs released. */
    STOPPED
}
