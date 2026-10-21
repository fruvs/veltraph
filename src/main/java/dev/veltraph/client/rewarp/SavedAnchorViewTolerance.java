package dev.veltraph.client.rewarp;

import dev.veltraph.client.rotation.SourceRotationStepper;

/** Caller may tighten an observed saved-view receipt; ordinary recovery retains its 1.5-degree bound. */
public final class SavedAnchorViewTolerance {
    public static final double DEFAULT = 1.5;
    private SavedAnchorViewTolerance() {}
    public static boolean valid(double tolerance) {
        return Double.isFinite(tolerance) && tolerance > 0 && tolerance <= DEFAULT;
    }
    public static boolean aligned(float yaw,float pitch,float targetYaw,float targetPitch,double tolerance) {
        return valid(tolerance) && Float.isFinite(yaw) && Float.isFinite(pitch)
                && Float.isFinite(targetYaw) && Float.isFinite(targetPitch)
                && Math.abs(SourceRotationStepper.wrap(yaw-targetYaw)) < tolerance
                && Math.abs(pitch-targetPitch) < tolerance;
    }
}
