package dev.veltraph.client.farming.movement;

/**
 * Shared farming movement state machine states. Drives the {@link MovementDirectionEngine} and is
 * surfaced on the HUD/debug panel.
 *
 * <p>Happy-path flow: {@link #PREPARE_TOOL} → {@link #AIMING} → {@link #FARMING} ⇄ ({@link #TURNING}
 * → {@link #REAIM_AFTER_TURN} → {@link #GOING_FORWARD} → {@link #FARMING}). Movement only ever
 * happens once the tool is selected, the player is on the ground, the aim is within tolerance, and the
 * lane ahead is valid.
 */
public enum MovementState {
    /** Not running. */
    IDLE,
    /** Ensuring the crop tool is selected (and the player is grounded) before aiming. */
    PREPARE_TOOL,
    /** Aiming to the lane heading before any forward movement is allowed. */
    AIMING,
    /** Actively walking the row and breaking crops. */
    FARMING,
    /** Re-acquiring forward motion after a turn until the lane re-validates. */
    GOING_FORWARD,
    /** Executing a row-end turn / lane switch. */
    TURNING,
    /** Re-aiming to the new lane heading immediately after a turn, before resuming forward. */
    REAIM_AFTER_TURN,
    /** Stuck handling: inputs released while the engine waits for a valid lane. */
    RECOVERING,
    /** Stopped by the user / safety guard; all inputs released. */
    STOPPED
}
