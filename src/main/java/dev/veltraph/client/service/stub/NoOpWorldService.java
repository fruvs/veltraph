package dev.veltraph.client.service.stub;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;

/**
 * Safe placeholder {@link WorldService} used until the Minecraft-backed adapter is implemented.
 * Reports nothing loaded/standable so no movement decisions are made against it.
 */
public final class NoOpWorldService implements WorldService {

    @Override
    public boolean isLoaded(BlockPos pos) {
        return false;
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        return false;
    }

    @Override
    public boolean isStandable(BlockPos pos) {
        return false;
    }

    @Override
    public String blockId(BlockPos pos) {
        return "";
    }
}
