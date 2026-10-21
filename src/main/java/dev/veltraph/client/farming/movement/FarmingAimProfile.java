package dev.veltraph.client.farming.movement;

/**
 * The macro-owned aim target for a farming row: the heading (yaw) the row engine maintains and the
 * pitch it holds while breaking. This is captured from the configured/initial row setup and then
 * <b>owned</b> by the macro — it is not re-derived from the player's accidental camera each tick (a
 * fix for the "row follows the camera" bug). Turning produces a new profile (a 180° heading flip for an
 * S-shape lane reversal); everything else maintains the same profile.
 *
 * <p>Mirrors the intent of the original {@code MovementDirection} controller, which captured a row
 * heading and aimed to it via the {@code RotationController}, rather than reading the live camera.
 */
public record FarmingAimProfile(String name, float yaw, float pitch) {

    public FarmingAimProfile {
        yaw = Angles.wrapDegrees(yaw);
        pitch = Math.max(-90f, Math.min(90f, pitch));
    }

    /** A profile with the row heading flipped 180° (S-shape boustrophedon row reversal). */
    public FarmingAimProfile flipped() {
        return new FarmingAimProfile(name, Angles.wrapDegrees(yaw + 180f), pitch);
    }

    public FarmingAimProfile withPitch(float newPitch) {
        return new FarmingAimProfile(name, yaw, newPitch);
    }
}
