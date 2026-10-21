package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Deterministic bounded advisory search for adjacent Jump Boost VI rising edges.
 * Candidate controls require separate native issuance, every sweep, observed episode lineage,
 * exact profile/pose/input capture and actual support-clamped contact before landing admission.
 * Search failure means this finite controller found no candidate, never geometric impossibility.
 */
public final class CapturedAdjacentJumpController {
    private CapturedAdjacentJumpController() {}
    private static final int BEAM = 192, MAX_STEPS = 40;
    public static final double POSITION_WINDOW = .075D, CONTACT_MOTION_WINDOW = .05D;
    public enum Headroom { REQUIRED_CLEARANCE_EXCEEDS_CEILING, CLEARANCE_ONLY_POSSIBLE, UNKNOWN }
    public static final class Crossing {
        private final Vec3 before, freeAfter, interpolated; private final int nativeStep;
        private Crossing(Vec3 before, Vec3 after, Vec3 point, int step) {
            this.before = before; freeAfter = after; interpolated = point; nativeStep = step;
        }
        public Vec3 before() { return before; }
        public Vec3 freeAfter() { return freeAfter; }
        /** Diagnostic segment/plane intersection; native moves and axis clamps do not interpolate time. */
        public Vec3 interpolated() { return interpolated; }
        public int nativeStep() { return nativeStep; }
    }
    public static final class Plan {
        private final CapturedProfileTrajectory.Trace trace; private final Vec3 target;
        private final Crossing crossing; private final double apex; private final int candidatesExpanded;
        private Plan(CapturedProfileTrajectory.Trace trace, Vec3 target, Crossing crossing,
                double apex, int expanded) {
            this.trace = trace; this.target = target; this.crossing = crossing;
            this.apex = apex; candidatesExpanded = expanded;
        }
        public CapturedProfileTrajectory.Trace trace() { return trace; }
        public Vec3 target() { return target; }
        public Crossing crossing() { return crossing; }
        public double apex() { return apex; }
        public int candidatesExpanded() { return candidatesExpanded; }
        public List<CapturedProfileTrajectory.Controls> expectedControls() {
            return trace.samples().stream().map(CapturedProfileTrajectory.Sample::controls).toList();
        }
        public double requiredCeilingY(double actualBodyHeight) {
            return Double.isFinite(actualBodyHeight) && actualBodyHeight > 0
                    ? apex + actualBodyHeight : Double.NaN;
        }
        /** Pure necessary headroom comparison, not a body collision or permission API. */
        public Headroom compareCeiling(double actualCeilingY, double actualBodyHeight) {
            double required = requiredCeilingY(actualBodyHeight);
            if (!Double.isFinite(actualCeilingY) || !Double.isFinite(required)) return Headroom.UNKNOWN;
            return required > actualCeilingY ? Headroom.REQUIRED_CLEARANCE_EXCEEDS_CEILING
                    : Headroom.CLEARANCE_ONLY_POSSIBLE;
        }
    }
    private record Candidate(Vec3 position, Vec3 velocity, List<CapturedProfileTrajectory.Input> inputs,
            double score, int serial) {}
    /** The supplied yaw must be the actual aligned native yaw that will remain stable during the
     * candidate. This method never sets yaw, velocity, player position, flags, inputs or tick counts.
     * Admits a candidate only inside BOTH the segment-plane and full native-frame XZ windows,
     * with small pre-clamp horizontal movement at contact. Landing is still unresolved.
     */
    public static Optional<Plan> plan(GroundNativePhysicsProfile profile, Vec3 origin,
            Vec3 observedVelocity, Vec3 target, float friction, float actualAlignedYaw,
            int minimumRemainingEffectTicks) {
        if (profile == null || profile.sprinting() || profile.state().jumpAmplifier() != 5
                || !GroundLocomotionMotion.finite(origin) || !GroundLocomotionMotion.finite(target)
                || !GroundLocomotionMotion.finite(observedVelocity) || !Float.isFinite(actualAlignedYaw)
                || target.y() - origin.y() <= .6D || target.y() - origin.y() > 1.001D
                || origin.horizontalDistanceTo(target) < .35D || origin.horizontalDistanceTo(target) > 1.65D)
            return Optional.empty();
        double nodeDx = Math.abs(Math.floor(origin.x()) - Math.floor(target.x()));
        double nodeDz = Math.abs(Math.floor(origin.z()) - Math.floor(target.z()));
        if (nodeDx + nodeDz != 1D) return Optional.empty();
        var first = CapturedProfileTrajectory.frame(profile, origin, observedVelocity, friction,
                actualAlignedYaw, CapturedProfileTrajectory.Input.NEUTRAL,
                CapturedProfileTrajectory.Phase.GROUNDED_TAKEOFF);
        if (first.isEmpty()) return Optional.empty();
        var takeoff = first.orElseThrow();
        Vec3 p = takeoff.position(), v = takeoff.velocity(); double apex = Math.max(origin.y(), p.y());
        int contactStep = 0;
        for (int step = 2; step <= MAX_STEPS; step++) {
            var next = CapturedProfileTrajectory.frame(profile, p, v, friction, actualAlignedYaw,
                    CapturedProfileTrajectory.Input.NEUTRAL, CapturedProfileTrajectory.Phase.OBSERVED_AIRBORNE);
            if (next.isEmpty()) return Optional.empty();
            var sample = next.orElseThrow(); apex = Math.max(apex, sample.position().y());
            if (p.y() >= target.y() && sample.position().y() <= target.y()
                    && sample.intendedMove().y() < 0) { contactStep = step; break; }
            p = sample.position(); v = sample.velocity();
        }
        if (contactStep == 0 || (minimumRemainingEffectTicks != -1
                && minimumRemainingEffectTicks <= contactStep)) return Optional.empty();
        var beam = List.of(new Candidate(takeoff.position(), takeoff.velocity(),
                List.of(CapturedProfileTrajectory.Input.NEUTRAL), 0, 0));
        int expanded = 0, serial = 0;
        for (int step = 2; step <= contactStep; step++) {
            var candidates = new ArrayList<Candidate>();
            for (var previous : beam) {
                for (var input : List.of(CapturedProfileTrajectory.Input.NEUTRAL,
                        CapturedProfileTrajectory.Input.FORWARD, CapturedProfileTrajectory.Input.BACKWARD)) {
                    var next = CapturedProfileTrajectory.frame(profile, previous.position(), previous.velocity(),
                            friction, actualAlignedYaw, input, CapturedProfileTrajectory.Phase.OBSERVED_AIRBORNE);
                    if (next.isEmpty()) continue;
                    expanded++; var sample = next.orElseThrow();
                    int left = contactStep - step;
                    double drag = (double).91F;
                    double coast = left == 0 ? 0 : (1D - Math.pow(drag, left)) / (1D - drag);
                    double dx = sample.position().x() + sample.velocity().x() * coast - target.x();
                    double dz = sample.position().z() + sample.velocity().z() * coast - target.z();
                    double speed = Math.hypot(sample.velocity().x(), sample.velocity().z());
                    double score = dx * dx + dz * dz + 2D * speed * speed;
                    if (step == contactStep) {
                        // Native collision clamps Y independently; final full XZ move must itself fit.
                        if (sample.position().horizontalDistanceTo(target) > POSITION_WINDOW
                                || Math.hypot(sample.intendedMove().x(), sample.intendedMove().z())
                                    > CONTACT_MOTION_WINDOW) continue;
                    }
                    var inputs = new ArrayList<>(previous.inputs()); inputs.add(input);
                    candidates.add(new Candidate(sample.position(), sample.velocity(), List.copyOf(inputs), score, serial++));
                }
            }
            if (candidates.isEmpty()) return Optional.empty();
            candidates.sort(Comparator.comparingDouble(Candidate::score).thenComparingInt(Candidate::serial));
            beam = List.copyOf(candidates.subList(0, Math.min(BEAM, candidates.size())));
        }
        for (var candidate : beam) {
            var trace = CapturedProfileTrajectory.forecast(profile, origin, observedVelocity, friction,
                    actualAlignedYaw, CapturedProfileTrajectory.Phase.GROUNDED_TAKEOFF,
                    candidate.inputs(), minimumRemainingEffectTicks).orElseThrow();
            var samples = trace.samples(); var before = samples.get(samples.size() - 2).position();
            var after = samples.getLast().position();
            double fraction = (before.y() - target.y()) / (before.y() - after.y());
            Vec3 point = new Vec3(before.x() + (after.x() - before.x()) * fraction, target.y(),
                    before.z() + (after.z() - before.z()) * fraction);
            if (point.horizontalDistanceTo(target) <= POSITION_WINDOW)
                return Optional.of(new Plan(trace, target, new Crossing(before, after, point, contactStep), apex, expanded));
        }
        return Optional.empty();
    }
}
