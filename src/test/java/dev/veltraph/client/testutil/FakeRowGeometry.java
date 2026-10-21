package dev.veltraph.client.testutil;

import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.RowGeometry;
import dev.veltraph.client.farming.movement.TurnDirection;

/** Fully controllable {@link RowGeometry} for testing the shared row engine. */
public final class FakeRowGeometry implements RowGeometry {

    public boolean atRowEnd;
    public boolean forwardLaneValid;
    public boolean leftMovable;
    public boolean rightMovable;

    /** When {@link #overrideLaneYaw} is set, {@link #laneYaw} returns this fixed heading. */
    public boolean overrideLaneYaw;
    public float laneYawValue;

    @Override
    public boolean atRowEnd() {
        return atRowEnd;
    }

    @Override
    public boolean forwardLaneValid() {
        return forwardLaneValid;
    }

    @Override
    public boolean sideMovable(TurnDirection side) {
        return switch (side) {
            case LEFT -> leftMovable;
            case RIGHT -> rightMovable;
            default -> false;
        };
    }

    @Override
    public float laneYaw(float currentYaw) {
        return overrideLaneYaw ? laneYawValue : Angles.snapToCardinal(currentYaw);
    }
}
