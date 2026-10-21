package dev.veltraph.client.testutil;

import dev.veltraph.client.farming.movement.SShapeGeometry;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Fully controllable {@link SShapeGeometry} for testing the lateral S-shape engine. */
public final class FakeSShapeGeometry implements SShapeGeometry {

    public Direction heading = Direction.NORTH;
    public boolean nearMiddle = true;
    public boolean blockInFront = true;
    public boolean canStrafeRight = true;
    public boolean canStrafeLeft = true;
    public boolean canStepForward = true;
    public Vec3 transitionTarget;
    public Vec3 endCenter;
    public boolean harvestAvailable = true;
    public Vec3 dropTarget;
    public boolean dropValid=true;
    public Vec3 target = new Vec3(0.5, 64.0, -0.5);

    @Override
    public void setHeading(Direction heading) {
        this.heading = heading;
    }

    @Override
    public Direction heading() {
        return heading;
    }

    @Override
    public boolean nearMiddle() {
        return nearMiddle;
    }

    @Override
    public boolean blockInFront() {
        return blockInFront;
    }

    @Override
    public boolean canStrafe(TurnDirection side) {
        return switch (side) {
            case RIGHT -> canStrafeRight;
            case LEFT -> canStrafeLeft;
            default -> false;
        };
    }

    @Override
    public boolean canStepForward() {
        return canStepForward;
    }

    @Override
    public TurnDirection movableLateral() {
        if (canStrafeRight) {
            return TurnDirection.RIGHT;
        }
        if (canStrafeLeft) {
            return TurnDirection.LEFT;
        }
        return TurnDirection.FORWARD;
    }

    @Override public Vec3 rowTransitionTarget(TurnDirection sweep) { return transitionTarget; }
    @Override public Vec3 rowEndCenter() { return endCenter; }
    @Override public boolean harvestTargetAvailable() { return harvestAvailable; }
    @Override public Vec3 safeDropTarget(TurnDirection side){return dropTarget;}
    @Override public boolean dropLandingValid(Vec3 target){return dropValid;}

    @Override
    public Vec3 targetVec() {
        return target;
    }
}
