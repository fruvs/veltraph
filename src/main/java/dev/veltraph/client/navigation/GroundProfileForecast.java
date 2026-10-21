package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Bounded advisory zero-input forecast. This cannot issue keys or replace observed collision/landing.
 * An integration must prove genuine neutral inputs, live profile custody and every body sweep. */
public final class GroundProfileForecast {
    private GroundProfileForecast() {}
    public record Sample(int nativeStep, Vec3 position, Vec3 velocity) {}
    public static final class Brake {
        private final List<Sample> samples; private final Vec3 asymptoticCoast, origin, observedVelocity;
        private final float blockFriction; private final GroundNativePhysicsProfile.State profileState;
        private Brake(List<Sample> samples, Vec3 coast, float friction, Vec3 origin, Vec3 velocity,
                GroundNativePhysicsProfile.State profileState) {
            this.samples = List.copyOf(samples); asymptoticCoast = coast; blockFriction = friction;
            this.origin = origin; observedVelocity = velocity; this.profileState = profileState;
        }
        public List<Sample> samples() { return samples; }
        public Vec3 asymptoticCoast() { return asymptoticCoast; }
        public float blockFriction() { return blockFriction; }
        public Vec3 origin() { return origin; }
        public Vec3 observedVelocity() { return observedVelocity; }
        public GroundNativePhysicsProfile.State profileState() { return profileState; }
    }
    public enum StartPhase { GROUNDED_TAKEOFF, OBSERVED_AIRBORNE }
    public static final class Jump {
        private final List<Sample> samples; private final double apex; private final float impulse, takeoffFriction;
        private final StartPhase phase; private final Vec3 origin, observedVelocity;
        private final GroundNativePhysicsProfile.State profileState;
        private Jump(List<Sample> samples, double apex, float impulse, StartPhase phase, float friction,
                Vec3 origin, Vec3 velocity, GroundNativePhysicsProfile.State profileState) {
            this.samples = List.copyOf(samples); this.apex = apex; this.impulse = impulse; this.phase = phase;
            takeoffFriction = friction; this.origin = origin; observedVelocity = velocity; this.profileState = profileState;
        }
        public List<Sample> samples() { return samples; }
        public double apex() { return apex; }
        public float impulse() { return impulse; }
        public StartPhase phase() { return phase; }
        public float takeoffFriction() { return takeoffFriction; }
        public Vec3 origin() { return origin; }
        public Vec3 observedVelocity() { return observedVelocity; }
        public GroundNativePhysicsProfile.State profileState() { return profileState; }
    }
    /** 26.3 Player aiStep has a combined horizontal squared threshold, not two independent axes. */
    private static Vec3 normalisePlayerMomentum(Vec3 v) {
        boolean horizontalZero = v.x() * v.x() + v.z() * v.z() < 9E-6D;
        return new Vec3(horizontalZero ? 0 : v.x(), Math.abs(v.y()) < .003D ? 0 : v.y(),
                horizontalZero ? 0 : v.z());
    }
    /** Neutral ground friction sequence. No fixed .16/.22 velocity cap is widened.
     * This gives the distance that geometry must prove for THIS live observed velocity. */
    public static Optional<Brake> groundBrake(GroundNativePhysicsProfile profile,
            Vec3 position, Vec3 observedVelocity, float blockFriction, int steps) {
        if (profile == null || !GroundLocomotionMotion.finite(position)
                || !GroundLocomotionMotion.finite(observedVelocity) || steps < 1 || steps > 64
                || !Float.isFinite(profile.groundAcceleration(blockFriction))
                || observedVelocity.y() < -.081 || observedVelocity.y() > .01) return Optional.empty();
        double drag = (double)(blockFriction * .91F);
        if (!(drag > 0 && drag < 1)) return Optional.empty();
        var samples = new ArrayList<Sample>();
        Vec3 p = position, v = new Vec3(observedVelocity.x(), 0, observedVelocity.z());
        for (int i = 1; i <= steps; i++) {
            v = normalisePlayerMomentum(v);
            p = new Vec3(p.x() + v.x(), position.y(), p.z() + v.z());
            v = new Vec3(v.x() * drag, 0, v.z() * drag);
            samples.add(new Sample(i, p, v));
        }
        var coast = new Vec3(position.x() + observedVelocity.x() / (1 - drag), position.y(),
                position.z() + observedVelocity.z() / (1 - drag));
        if (!GroundLocomotionMotion.finite(coast)) return Optional.empty();
        return Optional.of(new Brake(samples, coast, blockFriction, position, observedVelocity, profile.state()));
    }
    /** Grounded jump preview. jumpFromGround leaves onGround set; travelInAir caches the
     * ground friction BEFORE move changes grounding. Its FIRST drag must therefore use the
     * proven native support friction, unlike subsequent airborne steps. No sprint impulse. */
    public static Optional<Jump> groundedNeutralTakeoff(GroundNativePhysicsProfile profile, Vec3 origin,
            Vec3 observedVelocity, float blockJumpFactor, float nativeBlockFriction,
            int steps, int remainingJumpEffectTicks) {
        if (profile == null || profile.sprinting() || !GroundLocomotionMotion.finite(origin)
                || !GroundLocomotionMotion.finite(observedVelocity)
                || !Float.isFinite(profile.groundAcceleration(nativeBlockFriction))
                || observedVelocity.y() < -.081 || observedVelocity.y() > .01) return Optional.empty();
        float impulse = profile.jumpImpulse(blockJumpFactor);
        if (!Float.isFinite(impulse)) return Optional.empty();
        Vec3 beforeJump = normalisePlayerMomentum(observedVelocity);
        var takeoff = new Vec3(beforeJump.x(), Math.max(beforeJump.y(), impulse), beforeJump.z());
        return flight(profile, origin, observedVelocity, takeoff, steps, remainingJumpEffectTicks,
                impulse, StartPhase.GROUNDED_TAKEOFF, nativeBlockFriction);
    }
    /** Resume a genuinely OBSERVED airborne velocity. Never synthesizes a second jump impulse.
     * Ownership, natural tick lineage, collision and landing remain external prerequisites. */
    public static Optional<Jump> observedAirborneNeutral(GroundNativePhysicsProfile profile, Vec3 position,
            Vec3 observedVelocity, int steps, int remainingJumpEffectTicks) {
        if (profile == null || profile.sprinting() || !GroundLocomotionMotion.finite(position)
                || !GroundLocomotionMotion.finite(observedVelocity)) return Optional.empty();
        return flight(profile, position, observedVelocity, normalisePlayerMomentum(observedVelocity),
                steps, remainingJumpEffectTicks, Float.NaN, StartPhase.OBSERVED_AIRBORNE, Float.NaN);
    }
    private static Optional<Jump> flight(GroundNativePhysicsProfile profile, Vec3 origin,
            Vec3 observedVelocity, Vec3 initialVelocity, int steps, int remainingJumpEffectTicks,
            float impulse, StartPhase phase, float takeoffFriction) {
        if (steps < 1 || steps > 64) return Optional.empty();
        if (profile.state().jumpAmplifier() >= 0
                ? remainingJumpEffectTicks != -1 && remainingJumpEffectTicks <= steps
                : remainingJumpEffectTicks != 0) return Optional.empty();
        var samples = new ArrayList<Sample>(); Vec3 p = origin, v = initialVelocity;
        double apex = origin.y();
        for (int i = 1; i <= steps; i++) {
            if (i > 1) v = normalisePlayerMomentum(v);
            p = new Vec3(p.x() + v.x(), p.y() + v.y(), p.z() + v.z());
            double horizontalDrag = phase == StartPhase.GROUNDED_TAKEOFF && i == 1
                    ? (double)(takeoffFriction * .91F) : (double).91F;
            v = new Vec3(v.x() * horizontalDrag, (v.y() - profile.state().gravity()) * (double).98F,
                    v.z() * horizontalDrag);
            if (!GroundLocomotionMotion.finite(p) || !GroundLocomotionMotion.finite(v)) return Optional.empty();
            apex = Math.max(apex, p.y()); samples.add(new Sample(i, p, v));
        }
        return Optional.of(new Jump(samples, apex, impulse, phase, takeoffFriction, origin, observedVelocity, profile.state()));
    }
}
