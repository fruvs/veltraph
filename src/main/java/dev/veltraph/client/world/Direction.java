package dev.veltraph.client.world;

/**
 * A pure, dependency-free direction enum mirroring the subset of Minecraft's {@code Direction}
 * behavior the farming planners need. Keeping this independent of {@code net.minecraft} lets the
 * movement engine and planners be unit-tested on a plain JVM.
 *
 * <p>The horizontal directions are ordered clockwise (NORTH -> EAST -> SOUTH -> WEST) so that
 * {@link #rotateYClockwise()} / {@link #rotateYCounterclockwise()} match vanilla semantics.
 */
public enum Direction {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public boolean isHorizontal() {
        return this != UP && this != DOWN;
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    /** Turn right (clockwise when viewed from above). Vertical directions are returned unchanged. */
    public Direction rotateYClockwise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this;
        };
    }

    /** Turn left (counter-clockwise when viewed from above). Vertical directions unchanged. */
    public Direction rotateYCounterclockwise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> this;
        };
    }

    /**
     * Approximate the horizontal direction nearest a given yaw (Minecraft convention: 0 = SOUTH,
     * 90 = WEST, 180 = NORTH, 270 = EAST). Used by planners that derive a facing from an aim yaw.
     */
    public static Direction fromYaw(double yaw) {
        int index = (int) Math.floor(yaw / 90.0 + 0.5) & 3;
        return switch (index) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }

    /** Base yaw (degrees, Minecraft convention) that faces this horizontal direction. */
    public double toYaw() {
        return switch (this) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
    }
}
