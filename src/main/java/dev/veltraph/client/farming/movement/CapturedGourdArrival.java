package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Ordinary walking arrival: brake, reobserve and settle; never modifies pose or velocity. */
public final class CapturedGourdArrival {
    public enum Action { RELEASE, FORWARD, BACK, ARRIVED }
    private Vec3 previous;
    private int settled;
    private int pulseRest;
    private int pulseDirection;

    public Action observe(Vec3 position, Vec3 goal, Direction direction, double speed) {
        if (position == null || goal == null || direction == null || !direction.isHorizontal()
                || !Double.isFinite(speed) || speed < 0) {
            reset();
            return Action.RELEASE;
        }
        double signed = (goal.x() - position.x()) * direction.dx()
                + (goal.z() - position.z()) * direction.dz();
        double displacement = previous == null ? Double.POSITIVE_INFINITY
                : position.horizontalDistanceTo(previous);
        double along = previous == null ? 0
                : (position.x() - previous.x()) * direction.dx()
                + (position.z() - previous.z()) * direction.dz();
        previous = position;
        if (Math.abs(signed) <= .10) {
            settled = speed <= .015 && displacement <= .015 ? settled + 1 : 0;
            return settled >= 2 ? Action.ARRIVED : Action.RELEASE;
        }
        settled = 0;
        // Release early while recent motion would coast through the remaining distance.
        if (speed > .015 && Math.signum(along) == Math.signum(signed)
                && Math.abs(signed) <= .10 + speed * 2.5) {
            return Action.RELEASE;
        }
        int correctionDirection = signed > 0 ? 1 : -1;
        if (pulseDirection != correctionDirection) {
            pulseRest = 0; // A stale forward pulse cannot delay a physically needed back correction.
            pulseDirection = correctionDirection;
        }
        if (Math.abs(signed) <= .6) {
            if (pulseRest > 0) {
                pulseRest--;
                return Action.RELEASE;
            }
            pulseRest = 2;
        }
        return signed > 0 ? Action.FORWARD : Action.BACK;
    }

    public void reset() {
        previous = null;
        settled = 0;
        pulseRest = 0;
        pulseDirection = 0;
    }

    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "previous", previous, "settled", settled, "pulseRest", pulseRest, "pulseDirection", pulseDirection);
    }
}
