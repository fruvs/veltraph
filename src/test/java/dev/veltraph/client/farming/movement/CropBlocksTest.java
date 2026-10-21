package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the sugarcane lane-clear scan helper used to detect row end. */
class CropBlocksTest {

    /** World where a configurable set of positions hold sugar cane. */
    private static final class CaneWorld implements WorldService {
        final Set<BlockPos> cane = new HashSet<>();

        @Override
        public boolean isLoaded(BlockPos pos) {
            return true;
        }

        @Override
        public boolean isPassable(BlockPos pos) {
            return true;
        }

        @Override
        public boolean isStandable(BlockPos pos) {
            return true;
        }

        @Override
        public String blockId(BlockPos pos) {
            return cane.contains(pos) ? CropBlocks.SUGAR_CANE : "minecraft:air";
        }
    }

    @Test
    void detectsCaneWithinScanLength() {
        CaneWorld world = new CaneWorld();
        BlockPos from = new BlockPos(0, 64, 0);
        world.cane.add(from.offset(Direction.EAST, 2)); // 2 blocks ahead

        assertTrue(CropBlocks.anyAhead(world, from, Direction.EAST, 5, CropBlocks.SUGAR_CANE));
        assertFalse(CropBlocks.anyAhead(world, from, Direction.WEST, 5, CropBlocks.SUGAR_CANE));
    }

    @Test
    void caneBeyondScanLengthIsRowEnd() {
        CaneWorld world = new CaneWorld();
        BlockPos from = new BlockPos(0, 64, 0);
        world.cane.add(from.offset(Direction.EAST, 7)); // beyond the 5-block scan

        assertFalse(CropBlocks.anyAhead(world, from, Direction.EAST, 5, CropBlocks.SUGAR_CANE));
    }

    @Test
    void detectsCaneOneBlockAbove() {
        CaneWorld world = new CaneWorld();
        BlockPos from = new BlockPos(0, 64, 0);
        world.cane.add(from.offset(Direction.EAST, 1).up()); // tall cane at head height

        assertTrue(CropBlocks.anyAhead(world, from, Direction.EAST, 5, CropBlocks.SUGAR_CANE));
    }
}
