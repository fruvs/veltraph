package dev.veltraph.client.hydra.response;

/**
 * One step of a local movement reaction: which vanilla movement inputs to hold, and for how many client
 * ticks. A reaction ({@link HydraReaction}) expands into a short ordered list of these via
 * {@link HydraReactionPlanner}; the MC seam holds the requested inputs for {@link #durationTicks} ticks,
 * then advances to the next step (releasing everything at the end). Pure value type — no Minecraft.
 *
 * @param forward  hold the forward (walk) input
 * @param back     hold the back input
 * @param left     hold the strafe-left input
 * @param right    hold the strafe-right input
 * @param jump     hold the jump input
 * @param sneak    hold the sneak input
 * @param durationTicks how many client ticks to hold this input set (clamped to &ge; 1 on use)
 */
public record HydraMovementStep(boolean forward, boolean back, boolean left, boolean right,
                                boolean jump, boolean sneak, int durationTicks) {

    public static HydraMovementStep of(boolean forward, boolean back, boolean left, boolean right,
                                       boolean jump, boolean sneak, int durationTicks) {
        return new HydraMovementStep(forward, back, left, right, jump, sneak, durationTicks);
    }

    /** A back-pedal step (optionally hopping). */
    public static HydraMovementStep back(int ticks, boolean jump) {
        return new HydraMovementStep(false, true, false, false, jump, false, ticks);
    }

    /** A forward step. */
    public static HydraMovementStep forward(int ticks, boolean jump) {
        return new HydraMovementStep(true, false, false, false, jump, false, ticks);
    }

    /** A strafe step ({@code right=false} → left). */
    public static HydraMovementStep strafe(boolean right, int ticks, boolean jump) {
        return new HydraMovementStep(false, false, !right, right, jump, false, ticks);
    }

    /** A pure jump (no horizontal input). */
    public static HydraMovementStep jump(int ticks) {
        return new HydraMovementStep(false, false, false, false, true, false, ticks);
    }

    /** True if any input is held this step (a no-input step is a pause/idle). */
    public boolean anyHeld() {
        return forward || back || left || right || jump || sneak;
    }
}
