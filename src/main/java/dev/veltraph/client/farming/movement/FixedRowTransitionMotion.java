package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import java.util.Optional;

/** Feedback only. Native momentum can refuse acceleration/arrival; it never proves a corridor or pose. */
public final class FixedRowTransitionMotion {
    private FixedRowTransitionMotion() {}
    // The original row engine's physical arrival radius, retained without widening.
    private static final double ARRIVAL_RADIUS = .18;
    private static final FixedViewKeySteering.Keys RELEASED =
            new FixedViewKeySteering.Keys(false, false, false, false);

    public static FixedViewKeySteering.Keys coastingKeys(Vec3 position, Vec3 target, float yaw,
                                                         Optional<Vec3> velocity) {
        if (!finite(position) || !finite(target) || velocity == null) return RELEASED;
        var desired = FixedViewKeySteering.toward(position, target, yaw);
        if (velocity.isEmpty()) return desired; // No guessed momentum for an unsupported adapter.
        if (!finite(velocity.get())) return RELEASED;
        var ahead = FixedViewKeySteering.toward(offset(position, velocity.get()), target, yaw);
        // The observed native delta is only a refusal lookahead, not a stopping-distance oracle.
        // Keep each currently needed direction while momentum still leaves it needed. Otherwise
        // coast that direction; never press an opposite key based only on a projected position.
        return new FixedViewKeySteering.Keys(desired.forward() && ahead.forward(),
                desired.back() && ahead.back(), desired.left() && ahead.left(),
                desired.right() && ahead.right());
    }

    public static boolean arrivalObserved(Vec3 previous, Vec3 position, Vec3 target,
                                          Optional<Vec3> velocity) {
        if (!finite(previous) || !finite(position) || !finite(target) || velocity == null
                || previous.horizontalDistanceTo(target) > ARRIVAL_RADIUS
                || position.horizontalDistanceTo(target) > ARRIVAL_RADIUS) return false;
        // Two actual grounded observations must survive a crossing. Momentum may additionally
        // refuse an imminent drift outside the same radius, but can never manufacture arrival.
        return velocity.isEmpty() || finite(velocity.get())
                && offset(position, velocity.get()).horizontalDistanceTo(target) <= ARRIVAL_RADIUS;
    }

    private static Vec3 offset(Vec3 position, Vec3 velocity) {
        return new Vec3(position.x() + velocity.x(), position.y(), position.z() + velocity.z());
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y())
                && Double.isFinite(value.z());
    }
}
