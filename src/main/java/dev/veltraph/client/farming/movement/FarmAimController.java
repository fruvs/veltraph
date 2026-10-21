package dev.veltraph.client.farming.movement;

import java.util.Random;































public final class FarmAimController {

    /** Do not chase sub-degree quantization/crop-target noise once the camera is effectively settled. */
    private static final float STEADY_DEADBAND_DEG = 0.75f;

    /** Tunable rotation feel (sourced from {@code FarmingConfig} at runtime). */
    public record Settings(float maxYawPerTick, float maxPitchPerTick, double acceleration,
                           float toleranceDeg, float pauseErrorDeg, boolean useMouseGcd,
                           double mouseSensitivity, boolean jitter, float jitterMaxDeg,
                           int jitterMinIntervalTicks, int jitterMaxIntervalTicks, int jitterHoldTicks) {
    }

    private final Settings s;
    private final Random random;
    private final float gcdStep;
    private final float steadyDeadbandDeg;
    private final boolean preciseSettling;

    private float targetYaw;
    private float targetPitch;
    private float targetSpeedMul = 1f;

    private float speedYaw;
    private float speedPitch;
    private float residYaw;
    private float residPitch;

    // Periodic micro view-jitter (degrees of offset added to the held target, then released).
    private float jitterYaw;
    private float jitterPitch;
    private int jitterCountdown;
    private int jitterTicksLeft;

    public FarmAimController(Settings settings, Random random) {
        this(settings, random, STEADY_DEADBAND_DEG);
    }

    /**
     * Opt-in precision for a measured geometry window. Never chase the unreachable gap between
     * adjacent mouse steps; the normal constructor keeps its existing .75-degree settled view.
     */
    public FarmAimController(Settings settings, Random random, float requestedSteadyDeadbandDeg) {
        if (!Float.isFinite(requestedSteadyDeadbandDeg) || requestedSteadyDeadbandDeg <= 0f) {
            throw new IllegalArgumentException("Steady aim deadband must be positive and finite");
        }
        this.s = settings;
        this.random = random == null ? new Random(0L) : random;
        this.gcdStep = settings.useMouseGcd()
                ? (float) (Math.pow(settings.mouseSensitivity() * 0.6 + 0.2, 3.0) * 1.2)
                : 0f;
        if (settings.useMouseGcd() && (!Float.isFinite(gcdStep) || gcdStep <= 0f)) {
            throw new IllegalArgumentException("Configured mouse step must be positive and finite");
        }
        // Pitch is represented as float over [-90,90]. Leave a few ULPs for accumulation at the
        // half-step boundary, so an equidistant target settles rather than alternating by one GCD.
        this.steadyDeadbandDeg = Math.max(requestedSteadyDeadbandDeg,
                gcdStep > 0f ? gcdStep * .5f + 4f * Math.ulp(90f) : 0f);
        this.preciseSettling = requestedSteadyDeadbandDeg < STEADY_DEADBAND_DEG;
        this.jitterCountdown = nextJitterInterval();
    }

    /** Set the macro-owned target heading/pitch and (re)prime the smoothing state for a fresh move. */
    public void setTarget(FarmingAimProfile profile) {
        setTarget(profile.yaw(), profile.pitch());
    }

    public void setTarget(float yaw, float pitch) {
        this.targetYaw = Angles.wrapDegrees(yaw);
        this.targetPitch = Math.max(-90f, Math.min(90f, pitch));
        this.speedYaw = 0f;
        this.speedPitch = 0f;
        this.residYaw = 0f;
        this.residPitch = 0f;
        this.targetSpeedMul = 1f;
    }

    /**
     * Update the target without resetting the smoothing ramp — for continuous per-tick tracking of a
     * slowly-moving crop target (so the aim keeps easing instead of restarting every tick). Use
     * {@link #setTarget} only for a fresh, large move.
     */
    public void retarget(float yaw, float pitch) {
        this.targetYaw = Angles.wrapDegrees(yaw);
        this.targetPitch = Math.max(-90f, Math.min(90f, pitch));
    }

    public float targetYaw() {
        return targetYaw;
    }

    public float targetPitch() {
        return targetPitch;
    }

    public boolean jittering() {
        return jitterTicksLeft > 0;
    }

    /** Yaw error vs the base (un-jittered) target, magnitude in degrees. */
    public float yawError(float curYaw) {
        return Math.abs(Angles.signedDelta(curYaw, targetYaw));
    }

    /** Combined aim error (max of yaw/pitch error) vs the base target. */
    public float aimError(float curYaw, float curPitch) {
        return Math.max(yawError(curYaw), Math.abs(targetPitch - curPitch));
    }

    /** Within the completion threshold — safe to move/continue. */
    public boolean withinTolerance(float curYaw, float curPitch) {
        return aimError(curYaw, curPitch) <= s.toleranceDeg();
    }

    /** Error large enough that movement should pause and re-aim before continuing. */
    public boolean isLargeError(float curYaw, float curPitch) {
        return aimError(curYaw, curPitch) > s.pauseErrorDeg();
    }

    /**
     * Advance the aim one tick toward the (jittered) target and return the new {@code {yaw, pitch}}.
     * Bounded, accelerated, GCD-quantized — never an instant snap.
     */
    public float[] step(float curYaw, float curPitch) {
        advanceJitter(curYaw, curPitch);

        float effYaw = Angles.wrapDegrees(targetYaw + jitterYaw);
        float effPitch = Math.max(-90f, Math.min(90f, targetPitch + jitterPitch));

        if (jitterTicksLeft <= 0 && aimError(curYaw, curPitch) <= steadyDeadbandDeg) {
            speedYaw = 0f;
            speedPitch = 0f;
            residYaw = 0f;
            residPitch = 0f;
            return new float[]{curYaw, curPitch};
        }

        float errYaw = Angles.signedDelta(curYaw, effYaw);
        float errPitch = effPitch - curPitch;

        float appliedYaw = axisStep(errYaw, true);
        float appliedPitch = axisStep(errPitch, false);

        float newYaw = Angles.wrapDegrees(curYaw + appliedYaw);
        float newPitch = Math.max(-90f, Math.min(90f, curPitch + appliedPitch));
        return new float[]{newYaw, newPitch};
    }

    private float axisStep(float err, boolean yaw) {
        // During an opt-in precise correction, one axis may already be at its nearest reachable
        // mouse angle while the other is still turning. Do not accumulate that settled axis into
        // alternating single-step pulses. Legacy callers retain their existing combined behavior.
        if (preciseSettling && jitterTicksLeft <= 0 && Math.abs(err) <= steadyDeadbandDeg) {
            if (yaw) { speedYaw = 0f; residYaw = 0f; }
            else { speedPitch = 0f; residPitch = 0f; }
            return 0f;
        }
        float maxStep = (yaw ? s.maxYawPerTick() : s.maxPitchPerTick()) * targetSpeedMul;
        float speed = yaw ? speedYaw : speedPitch;
        // Ease-in: ramp the speed toward the cap by the acceleration fraction each tick.
        float accel = (float) Math.max(0.0, Math.min(1.0, s.acceleration()));
        speed = Math.min(maxStep, speed + Math.max(0.05f, accel) * maxStep);
        // Small speed variation only during an actual scheduled jitter pulse. This prevents visible
        // nonstop "jitter" while preserving the rare drift/correct behavior.
        if (jitterTicksLeft > 0) {
            speed *= (float) (0.92 + random.nextDouble() * 0.16);
            speed = Math.min(speed, maxStep);
        }
        if (yaw) {
            speedYaw = speed;
        } else {
            speedPitch = speed;
        }
        // Ease-out: never overshoot the remaining error.
        float mag = Math.min(speed, Math.abs(err));
        float step = Math.signum(err) * mag;
        return quantize(step, yaw);
    }

    /** Quantize to the mouse GCD step, carrying the residual so sub-step motion is not lost. */
    private float quantize(float step, boolean yaw) {
        if (gcdStep <= 0f) {
            return step;
        }
        float resid = yaw ? residYaw : residPitch;
        float total = step + resid;
        float steps = Math.round(total / gcdStep);
        float applied = steps * gcdStep;
        float newResid = total - applied;
        if (yaw) {
            residYaw = newResid;
        } else {
            residPitch = newResid;
        }
        return applied;
    }

    private void advanceJitter(float curYaw, float curPitch) {
        if (!s.jitter()) {
            jitterYaw = 0f;
            jitterPitch = 0f;
            return;
        }
        if (jitterTicksLeft > 0) {
            jitterTicksLeft--;
            if (jitterTicksLeft == 0) {
                jitterYaw = 0f;
                jitterPitch = 0f;
                jitterCountdown = nextJitterInterval();
            }
            return;
        }
        // Only start a new micro-jitter once steady on the row (not mid big correction).
        if (aimError(curYaw, curPitch) > s.toleranceDeg()) {
            return;
        }
        if (--jitterCountdown <= 0) {
            jitterTicksLeft = Math.max(1, s.jitterHoldTicks());
            jitterYaw = (float) ((random.nextDouble() * 2.0 - 1.0) * s.jitterMaxDeg());
            jitterPitch = (float) ((random.nextDouble() * 2.0 - 1.0) * s.jitterMaxDeg() * 0.5);
            dev.veltraph.client.log.VeltraphLog.verbose("FarmAimController.jitter",
                    String.format("scheduled jitter dyaw=%.2f dpitch=%.2f hold=%d ticks",
                            jitterYaw, jitterPitch, jitterTicksLeft));
        }
    }

    private int nextJitterInterval() {
        int min = Math.max(1, s.jitterMinIntervalTicks());
        int max = Math.max(min + 1, s.jitterMaxIntervalTicks());
        return min + random.nextInt(max - min);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "s", s,
                "random", random,
                "gcdStep", gcdStep,
                "steadyDeadbandDeg", steadyDeadbandDeg,
                "preciseSettling", preciseSettling,
                "targetYaw", targetYaw,
                "targetPitch", targetPitch,
                "targetSpeedMul", targetSpeedMul,
                "speedYaw", speedYaw,
                "speedPitch", speedPitch,
                "residYaw", residYaw,
                "residPitch", residPitch,
                "jitterYaw", jitterYaw,
                "jitterPitch", jitterPitch,
                "jitterCountdown", jitterCountdown,
                "jitterTicksLeft", jitterTicksLeft);
    }
}
