package dev.veltraph.client.testutil;

import dev.veltraph.client.service.PlayerService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Controllable {@link PlayerService} for tests. */
public final class FakePlayerService implements PlayerService {

    public boolean available = true;
    public Vec3 position = new Vec3(0.5, 64.0, 0.5);
    public BlockPos blockPos = new BlockPos(0, 64, 0);
    public float yaw;
    public float pitch;
    public boolean onGround = true;
    public double horizontalSpeed;
    public Direction facing = Direction.NORTH;

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    public BlockPos blockPos() {
        return blockPos;
    }

    @Override
    public float yaw() {
        return yaw;
    }

    @Override
    public float pitch() {
        return pitch;
    }

    @Override
    public boolean onGround() {
        return onGround;
    }

    @Override
    public double horizontalSpeed() {
        return horizontalSpeed;
    }

    @Override
    public Direction facing() {
        return facing;
    }
}
