package dev.veltraph.client.failsafe.response;

import dev.veltraph.client.navigation.GroundPathfinder;
import dev.veltraph.client.navigation.TravelPath;
import dev.veltraph.client.world.Vec3;

/** Conservative clearance for short local reactions; never overrides a hazard by inventing a route. */
public final class FailsafeMovementGuard {
    private FailsafeMovementGuard() {}

    public static boolean allowsJump(GroundPathfinder.Terrain terrain, Vec3 position) {
        if (!finite(position)) return false;
        for (double x : new double[]{-.32,.32}) for (double z : new double[]{-.32,.32}) {
            var feet = new Vec3(position.x()+x,position.y()+.01,position.z()+z).toBlockPos();
            if (!GroundPathfinder.standable(terrain,feet) || !terrain.clear(feet.up(2))) return false;
        }
        return true;
    }

    public static boolean allows(GroundPathfinder.Terrain terrain, Vec3 position, Vec3 velocity,
                                 float yaw, boolean forward, boolean back, boolean left, boolean right) {
        if (!Float.isFinite(yaw) || !finite(position) || !finite(velocity)) return false;
        double along = (forward ? 1 : 0) - (back ? 1 : 0);
        double across = (left ? 1 : 0) - (right ? 1 : 0);
        double length = Math.hypot(along, across);
        if (length == 0) return true;
        double angle = Math.toRadians(yaw);
        double dx = (-Math.sin(angle) * along + Math.cos(angle) * across) / length;
        double dz = (Math.cos(angle) * along + Math.sin(angle) * across) / length;
        // Check the next several ticks, including current momentum; both sweep and support are required.
        Vec3 drift = new Vec3(position.x() + velocity.x() * 3, position.y(), position.z() + velocity.z() * 3);
        Vec3 end = new Vec3(drift.x() + dx * .65, position.y(), drift.z() + dz * .65);
        return TravelPath.walkClear(terrain, position, drift) && TravelPath.walkClear(terrain, drift, end);
    }

    private static boolean finite(Vec3 p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z());
    }
}
