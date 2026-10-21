package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.Input;

/** Pure keyboard intent in the yaw basis consumed by vanilla. This is neither collision
 * proof nor authority: the native owner must prove the selected pulse and its full coast.
 */
public final class CameraRelativeGroundSteering {
    private CameraRelativeGroundSteering() {}
    private static final Input[] DIRECTIONS = {Input.FORWARD, Input.FORWARD_LEFT,
            Input.LEFT, Input.BACKWARD_LEFT, Input.BACKWARD, Input.BACKWARD_RIGHT,
            Input.RIGHT, Input.FORWARD_RIGHT};
    private static final double SECTOR_HALF_WIDTH = 22.5D;
    private static final double HYSTERESIS = 5D;

    /** Vanilla direction capture is limited to an already admitted flat course. Ordinary
     * jump, drop and ramp executors retain their original phase-specific inputs and proofs.
     * This is only a scope restriction; each native pulse still requires fresh geometry.
     */
    public static boolean flatRoute(Vec3 original, java.util.List<Vec3> admitted) {
        return GroundLocomotionMotion.finite(original) && admitted != null && !admitted.isEmpty()
                && admitted.size() <= 6000 && admitted.stream().allMatch(point ->
                    GroundLocomotionMotion.finite(point) && Math.abs(point.y() - original.y()) < .05D);
    }

    /** A retained sector can delay switching by at most five degrees. Every tick still
     * requires new native geometry; a prior input is never permission to retain a key.
     */
    public static Input select(Vec3 actual, Vec3 target, float actualYaw, Input previous) {
        if (!GroundLocomotionMotion.finite(actual) || !GroundLocomotionMotion.finite(target)
                || !Float.isFinite(actualYaw) || Math.abs(target.y() - actual.y()) >= .05D)
            return Input.NEUTRAL;
        double length = actual.horizontalDistanceTo(target);
        if (!Double.isFinite(length) || length < 1E-6D) return Input.NEUTRAL;
        Input selected = Input.NEUTRAL;
        double smallest = Double.POSITIVE_INFINITY;
        for (Input candidate : DIRECTIONS) {
            double error = headingError(actual, target, actualYaw, candidate);
            if (error < smallest) { smallest = error; selected = candidate; }
        }
        double prior = headingError(actual, target, actualYaw, previous);
        return prior <= smallest + HYSTERESIS && prior <= SECTOR_HALF_WIDTH + HYSTERESIS
                ? previous : selected;
    }

    /** Uses the same modern float normalization, unit-square remap and native sine table
     * evaluation as the existing conditional physics model; requested camera yaw is absent.
     */
    public static Vec3 worldDirection(float actualYaw, Input input) {
        if (!Float.isFinite(actualYaw) || input == null || input == Input.NEUTRAL)
            return new Vec3(0D, 0D, 0D);
        Vec3 acceleration = CapturedProfileTrajectory.inputAcceleration(input, 1F, actualYaw);
        double length = Math.hypot(acceleration.x(), acceleration.z());
        return !(length > 0D) || !Double.isFinite(length) ? new Vec3(0D, 0D, 0D)
                : new Vec3(acceleration.x() / length, 0D, acceleration.z() / length);
    }

    public static double headingError(Vec3 actual, Vec3 target, float actualYaw, Input input) {
        if (!GroundLocomotionMotion.finite(actual) || !GroundLocomotionMotion.finite(target)
                || !Float.isFinite(actualYaw) || input == null || input == Input.NEUTRAL)
            return Double.POSITIVE_INFINITY;
        double length = actual.horizontalDistanceTo(target);
        if (!(length > 0D) || !Double.isFinite(length)) return Double.POSITIVE_INFINITY;
        Vec3 direction = worldDirection(actualYaw, input);
        if (direction.x() == 0D && direction.z() == 0D) return Double.POSITIVE_INFINITY;
        double dot = ((target.x() - actual.x()) * direction.x()
                + (target.z() - actual.z()) * direction.z()) / length;
        return Math.toDegrees(Math.acos(Math.max(-1D, Math.min(1D, dot))));
    }
}
