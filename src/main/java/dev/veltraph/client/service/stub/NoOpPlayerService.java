package dev.veltraph.client.service.stub;

import dev.veltraph.client.service.PlayerService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/**
 * Safe placeholder {@link PlayerService} used until the Minecraft-backed adapter is implemented.
 * Reports the player as unavailable, so the bootstrap will not tick automation modules.
 */
public final class NoOpPlayerService implements PlayerService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Vec3 position() {
        return new Vec3(0, 0, 0);
    }

    @Override
    public BlockPos blockPos() {
        return new BlockPos(0, 0, 0);
    }

    @Override
    public float yaw() {
        return 0f;
    }

    @Override
    public float pitch() {
        return 0f;
    }

    @Override
    public boolean onGround() {
        return true;
    }

    @Override
    public double horizontalSpeed() {
        return 0.0;
    }

    @Override
    public Direction facing() {
        return Direction.NORTH;
    }
}
