package dev.veltraph.client.testutil;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;

/**
 * Simple controllable {@link WorldService}. {@code walkable} toggles whether every queried block is
 * loaded/passable/standable, which is enough to drive the row engine's row-end logic in tests.
 *
 * <p>Not {@code final} so per-crop module tests can subclass it to report specific crop block ids
 * (e.g. sugar cane at chosen positions) by overriding {@link #blockId(BlockPos)}.
 */
public class FakeWorldService implements WorldService {

    public boolean walkable = true;

    @Override
    public boolean isLoaded(BlockPos pos) {
        return true;
    }

    @Override
    public boolean isPassable(BlockPos pos) {
        return walkable;
    }

    @Override
    public boolean isStandable(BlockPos pos) {
        return walkable;
    }

    /** When {@code solidFloor} is set it controls the farm-aware floor check independently of {@code walkable}. */
    public Boolean solidFloor;

    @Override
    public boolean isSolidFloor(BlockPos pos) {
        return solidFloor != null ? solidFloor : walkable;
    }

    @Override
    public String blockId(BlockPos pos) {
        return "";
    }
}
