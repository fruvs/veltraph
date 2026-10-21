package dev.veltraph.client.world;

/**
 * Immutable double-precision position/vector. Pure value type used for player position and movement
 * deltas (e.g. the vertical-wart still-detection distance check).
 */
public record Vec3(double x, double y, double z) {

    public double distanceTo(Vec3 other) {
        return Math.sqrt(squaredDistanceTo(other));
    }

    /** Exact distance projection when the source coordinates need not escape as a vector.
     * Caller still evaluates every original coordinate getter before the target argument. */
    public static double distanceFromCoordinates(double originalX,double originalY,double originalZ,Vec3 other) {
        double dx=originalX-other.x;
        double dy=originalY-other.y;
        double dz=originalZ-other.z;
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    public double squaredDistanceTo(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Horizontal (XZ-plane) distance, ignoring the Y axis. */
    public double horizontalDistanceTo(Vec3 other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(floor(x), floor(y), floor(z));
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
