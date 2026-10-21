package dev.veltraph.client.hydra.response;














public final class HydraLook {

    /** Tunable feel: per-tick caps, the ease-in acceleration fraction, and the completion tolerance. */
    public record Settings(float maxYawPerTick, float maxPitchPerTick, double acceleration, float toleranceDeg) {

        /** Sane defaults: ~14°/tick yaw, ~10°/tick pitch, gentle ramp, 0.6° completion tolerance. */
        public static Settings defaults() {
            return new Settings(14f, 10f, 0.34, 0.6f);
        }
    }

    private final Settings s;

    private float targetYaw;
    private float targetPitch;
    private boolean active;
    private float speedYaw;
    private float speedPitch;

    public HydraLook(Settings settings) {
        this.s = settings == null ? Settings.defaults() : settings;
    }

    /** Begin a smooth move to an absolute yaw/pitch target (degrees). Resets the easing ramp. */
    public void begin(float targetYaw, float targetPitch) {
        this.targetYaw = wrap(targetYaw);
        this.targetPitch = clampPitch(targetPitch);
        this.speedYaw = 0f;
        this.speedPitch = 0f;
        this.active = true;
    }

    public boolean active() {
        return active;
    }

    /** Abort any in-progress smooth move. */
    public void stop() {
        active = false;
        speedYaw = 0f;
        speedPitch = 0f;
    }

    public float targetYaw() {
        return targetYaw;
    }

    public float targetPitch() {
        return targetPitch;
    }

    /** Whether the camera is within the completion tolerance of the target. */
    public boolean done(float curYaw, float curPitch) {
        return error(curYaw, curPitch) <= s.toleranceDeg();
    }

    private float error(float curYaw, float curPitch) {
        return Math.max(Math.abs(signedDelta(curYaw, targetYaw)), Math.abs(targetPitch - clampPitch(curPitch)));
    }

    /**
     * Advance one tick toward the target and return the new {@code {yaw, pitch}}. When the target is
     * reached the move deactivates and the current angle is returned unchanged. Bounded + accelerated —
     * never a snap.
     */
    public float[] step(float curYaw, float curPitch) {
        if (!active) {
            return new float[]{curYaw, curPitch};
        }
        if (done(curYaw, curPitch)) {
            active = false;
            speedYaw = 0f;
            speedPitch = 0f;
            return new float[]{targetYaw, clampPitch(curPitch)};
        }
        float errYaw = signedDelta(curYaw, targetYaw);
        float errPitch = targetPitch - clampPitch(curPitch);

        float[] yawState = axisStep(errYaw, s.maxYawPerTick(), speedYaw);
        float[] pitchState = axisStep(errPitch, s.maxPitchPerTick(), speedPitch);
        speedYaw = yawState[1];
        speedPitch = pitchState[1];

        float newYaw = wrap(curYaw + yawState[0]);
        float newPitch = clampPitch(curPitch + pitchState[0]);
        if (done(newYaw, newPitch)) {
            active = false;
        }
        return new float[]{newYaw, newPitch};
    }

    /** @return {@code {appliedStep, newSpeed}} — ease-in ramp then ease-out capped to the remaining error. */
    private float[] axisStep(float err, float maxStep, float speed) {
        float accel = (float) Math.max(0.05, Math.min(1.0, s.acceleration()));
        float ramped = Math.min(maxStep, speed + accel * maxStep);
        float mag = Math.min(ramped, Math.abs(err)); // ease-out: never overshoot
        return new float[]{Math.signum(err) * mag, ramped};
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    /** Wrap a yaw into [-180, 180). */
    static float wrap(float deg) {
        float d = deg % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** Signed shortest angular delta from {@code from} to {@code to} in degrees, range [-180, 180). */
    static float signedDelta(float from, float to) {
        return wrap(to - from);
    }
}
