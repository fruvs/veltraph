package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.world.Direction;

/**
 * The recovered shared crop-lane/movement enum {@code {FORWARD, LEFT, RIGHT, BACK}} (see
 * {@code FarmingMovementPlannerHotspots.md} §5c). Distinct from the config {@link LaneSide}
 * ({@code LEFT/RIGHT}); use {@link #fromLaneSide(LaneSide)} to bridge.
 */
public enum TurnDirection {
    FORWARD,
    LEFT,
    RIGHT,
    BACK;

    public TurnDirection opposite() {
        return switch (this) {
            case FORWARD -> BACK;
            case BACK -> FORWARD;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    /** Resolve this lane-relative direction against the player's current facing. */
    public Direction resolve(Direction facing) {
        return switch (this) {
            case FORWARD -> facing;
            case BACK -> facing.opposite();
            case LEFT -> facing.rotateYCounterclockwise();
            case RIGHT -> facing.rotateYClockwise();
        };
    }

    public static TurnDirection fromLaneSide(LaneSide side) {
        return side == LaneSide.LEFT ? LEFT : RIGHT;
    }
}
