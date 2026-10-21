package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure, conditional 26.3 player math. No result grants native movement or collision admission.
 * Requires standing, controlled camera, no item use/crouch/fluid/climb/flight/interpolation,
 * stable exact profile, loaded world, normal gravity and friction, and issued-input custody.
 * SUPPORTED_GROUND projects a freshly proved flat support clamp; flight is wholly unclamped.
 */
public final class CapturedProfileTrajectory {
    private CapturedProfileTrajectory() {}
    public enum Phase { SUPPORTED_GROUND, GROUNDED_TAKEOFF, GROUNDED_EXIT, OBSERVED_AIRBORNE }
    public enum Input {
        NEUTRAL(0, 0), FORWARD(0, 1), BACKWARD(0, -1), LEFT(1, 0), RIGHT(-1, 0),
        FORWARD_LEFT(1, 1), FORWARD_RIGHT(-1, 1), BACKWARD_LEFT(1, -1), BACKWARD_RIGHT(-1, -1);
        private final int left, forward;
        Input(int left, int forward) { this.left = left; this.forward = forward; }
        public int left() { return left; }
        public int forward() { return forward; }
    }
    public enum JumpIntent { PRESS, RELEASE }
    public enum SprintIntent { PRESS, RELEASE }
    public static final class Controls {
        private final Input input; private final JumpIntent jump; private final SprintIntent sprint;
        private Controls(Input input, JumpIntent jump, SprintIntent sprint) {
            this.input = input; this.jump = jump; this.sprint = sprint;
        }
        public Input input() { return input; }
        public JumpIntent jump() { return jump; }
        public SprintIntent sprint() { return sprint; }
    }
    public enum Condition { FRESH_FLAT_SUPPORT_CLAMP, UNCLAMPED_FLIGHT }
    public static final class Sample {
        private final Vec3 position, velocity, intendedMove, acceleration;
        private final Phase phase; private final Input input; private final Condition condition;
        private final Controls controls;
        private Sample(Vec3 p, Vec3 v, Vec3 move, Vec3 a, Phase phase, Input input, Condition condition, JumpIntent jump, SprintIntent sprint) {
            position = p; velocity = v; intendedMove = move; acceleration = a;
            this.phase = phase; this.input = input; this.condition = condition;
            controls = new Controls(input, jump, sprint);
        }
        public Vec3 position() { return position; }
        public Vec3 velocity() { return velocity; }
        public Vec3 intendedMove() { return intendedMove; }
        public Vec3 acceleration() { return acceleration; }
        public Phase phase() { return phase; }
        public Input input() { return input; }
        public Condition condition() { return condition; }
        public Controls controls() { return controls; }
    }
    public static final class Trace {
        private final List<Sample> samples; private final Vec3 origin, observedVelocity;
        private final GroundNativePhysicsProfile.State state; private final float yaw, friction;
        private Trace(List<Sample> samples, Vec3 origin, Vec3 observedVelocity,
                GroundNativePhysicsProfile.State state, float yaw, float friction) {
            this.samples = List.copyOf(samples); this.origin = origin; this.observedVelocity = observedVelocity;
            this.state = state; this.yaw = yaw; this.friction = friction;
        }
        public List<Sample> samples() { return samples; }
        public Vec3 origin() { return origin; }
        public Vec3 observedVelocity() { return observedVelocity; }
        public GroundNativePhysicsProfile.State profileState() { return state; }
        public float yaw() { return yaw; }
        public float friction() { return friction; }
    }
    /** Exact KeyboardInput Vec2 normalization, LocalPlayer .98F scale, then unit-square remap.
     * All float operations retain their native evaluation order. Item and sneak factors excluded.
     */
    static float[] localInput(Input input) {
        float x = input.left(), z = input.forward();
        float length = (float)Math.sqrt(x * x + z * z);
        if (length < 1E-4F) return new float[]{0F, 0F};
        x = x / length * .98F; z = z / length * .98F;
        length = (float)Math.sqrt(x * x + z * z);
        float nx = x * (1F / length), nz = z * (1F / length);
        float ax = Math.abs(nx), az = Math.abs(nz);
        float ratio = az > ax ? ax / az : az / ax;
        float squareDistance = (float)Math.sqrt(1F + ratio * ratio);
        float scale = Math.min(length * squareDistance, 1F);
        return new float[]{nx * scale, nz * scale};
    }
    // Native Mth SIN is initialized as (float)Math.sin(index / 10430.378350470453D).
    // Reconstruct the consulted table entry without loading native classes or a public LUT.
    private static float nativeSin(double radians) {
        int index = (int)((long)(radians * 10430.378350470453D) & 65535L);
        return (float)Math.sin(index / 10430.378350470453D);
    }
    private static float nativeCos(double radians) {
        int index = (int)((long)(radians * 10430.378350470453D + 16384D) & 65535L);
        return (float)Math.sin(index / 10430.378350470453D);
    }
    static Vec3 inputAcceleration(Input input, float speed, float yaw) {
        float[] local = localInput(input);
        double x = local[0], z = local[1], squared = x * x + z * z;
        if (squared < 1E-7D) return new Vec3(0, 0, 0);
        if (squared > 1D) { double length = Math.sqrt(squared); x /= length; z /= length; }
        x *= (double)speed; z *= (double)speed;
        double radians = (double)(yaw * .017453292F);
        float sin = nativeSin(radians), cos = nativeCos(radians);
        return new Vec3(x * cos - z * sin, 0, z * cos + x * sin);
    }
    static Vec3 playerMomentum(Vec3 v) {
        boolean zero = v.x() * v.x() + v.z() * v.z() < 9E-6D;
        return new Vec3(zero ? 0 : v.x(), Math.abs(v.y()) < .003D ? 0 : v.y(), zero ? 0 : v.z());
    }
    /** A single conditional native frame. Jump impulse occurs only for GROUNDED_TAKEOFF.
     * Sprint takeoff is deliberately unresolved because native adds the separate .2 impulse.
     */
    public static Optional<Sample> frame(GroundNativePhysicsProfile profile, Vec3 position,
            Vec3 observedVelocity, float friction, float yaw, Input input, Phase phase) {
        return frame(profile, position, observedVelocity, friction, yaw, input, phase,
                phase == Phase.GROUNDED_TAKEOFF ? JumpIntent.PRESS : JumpIntent.RELEASE,
                profile != null && profile.sprinting() ? SprintIntent.PRESS : SprintIntent.RELEASE);
    }
    /** Explicit ground sprint-transition advice uses the BEFORE profile travel speed.
     * LocalPlayer updates its sprint flag before super.aiStep; Player refreshes getSpeed only
     * after super.aiStep has travelled. A requested mismatch is mathematical advice only: a
     * separate private native producer must prove the actual flag transition and input custody.
     * Such mismatch is unresolved for takeoff and observed airborne frames.
     */
    public static Optional<Sample> frame(GroundNativePhysicsProfile profile, Vec3 position,
            Vec3 observedVelocity, float friction, float yaw, Input input, Phase phase,
            JumpIntent jump, SprintIntent sprint) {
        if (profile == null || input == null || phase == null || jump == null || sprint == null
                || (phase == Phase.GROUNDED_TAKEOFF) != (jump == JumpIntent.PRESS)
                || (profile.sprinting() != (sprint == SprintIntent.PRESS)
                    && phase != Phase.SUPPORTED_GROUND) || !Float.isFinite(yaw)
                || !GroundLocomotionMotion.finite(position) || !GroundLocomotionMotion.finite(observedVelocity))
            return Optional.empty();
        boolean ground = phase != Phase.OBSERVED_AIRBORNE;
        if (ground && (!Float.isFinite(profile.groundAcceleration(friction))
                || observedVelocity.y() < -.081 || observedVelocity.y() > .01)) return Optional.empty();
        if (phase == Phase.GROUNDED_TAKEOFF && profile.sprinting()) return Optional.empty();
        Vec3 v = playerMomentum(observedVelocity);
        if (phase == Phase.SUPPORTED_GROUND && v.y() > 0D) return Optional.empty();
        if (phase == Phase.GROUNDED_TAKEOFF)
            v = new Vec3(v.x(), Math.max(v.y(), profile.jumpImpulse(1F)), v.z());
        float speed = ground ? profile.groundAcceleration(friction)
                : profile.sprinting() ? .025999999F : .02F;
        Vec3 acceleration = inputAcceleration(input, speed, yaw);
        Vec3 move = new Vec3(v.x() + acceleration.x(), v.y(), v.z() + acceleration.z());
        boolean supportClamp = phase == Phase.SUPPORTED_GROUND;
        Vec3 p = new Vec3(position.x() + move.x(), supportClamp ? position.y() : position.y() + move.y(),
                position.z() + move.z());
        double drag = ground ? (double)(friction * .91F) : (double).91F;
        Vec3 after = new Vec3(move.x() * drag,
                ((supportClamp ? 0D : move.y()) - profile.state().gravity()) * (double).98F, move.z() * drag);
        if (!GroundLocomotionMotion.finite(p) || !GroundLocomotionMotion.finite(after)) return Optional.empty();
        return Optional.of(new Sample(p, after, move, acceleration, phase, input,
                supportClamp ? Condition.FRESH_FLAT_SUPPORT_CLAMP : Condition.UNCLAMPED_FLIGHT, jump, sprint));
    }
    /** GROUNDED_EXIT is a conditional source-certified drop frame: native ground acceleration
     * and first drag are cached while its actual downward movement is not support-clamped.
     * A separate native geometry producer must prove support loss for that exact first move.
     */
    /** Effects must outlast the entire preview (native -1 means infinite). No expiry is inferred. */
    public static Optional<Trace> forecast(GroundNativePhysicsProfile profile, Vec3 origin,
            Vec3 observedVelocity, float friction, float yaw, Phase start, List<Input> inputs,
            int minimumRemainingEffectTicks) {
        if (profile == null || start == null || inputs == null || inputs.isEmpty() || inputs.size() > 64
                || (profile.state().effects().isEmpty() ? minimumRemainingEffectTicks != 0
                    : minimumRemainingEffectTicks != -1 && minimumRemainingEffectTicks <= inputs.size()))
            return Optional.empty();
        var samples = new ArrayList<Sample>(); Vec3 p = origin, v = observedVelocity;
        for (int i = 0; i < inputs.size(); i++) {
            Phase phase = start == Phase.SUPPORTED_GROUND ? start
                    : i == 0 ? start : Phase.OBSERVED_AIRBORNE;
            var next = frame(profile, p, v, friction, yaw, inputs.get(i), phase);
            if (next.isEmpty()) return Optional.empty();
            Sample sample = next.orElseThrow(); samples.add(sample); p = sample.position(); v = sample.velocity();
        }
        return Optional.of(new Trace(samples, origin, observedVelocity, profile.state(), yaw, friction));
    }
    /** Conservative infinite geometric coast bound ignores the finite native dead zone.
     * Caller must freshly sweep this whole distance on consistent native support friction.
     */
    public static Optional<Vec3> groundCoastBound(Vec3 origin, Vec3 observedVelocity, float friction) {
        double drag = (double)(friction * .91F);
        if (!GroundLocomotionMotion.finite(origin) || !GroundLocomotionMotion.finite(observedVelocity)
                || !Float.isFinite(friction) || friction <= 0 || friction > .600001F || !(drag > 0 && drag < 1))
            return Optional.empty();
        Vec3 bound = new Vec3(origin.x() + observedVelocity.x() / (1D - drag), origin.y(),
                origin.z() + observedVelocity.z() / (1D - drag));
        return GroundLocomotionMotion.finite(bound) ? Optional.of(bound) : Optional.empty();
    }
}
