package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

/**
 * Namespaced block ids for the recovered farming crops, plus small scan helpers used to narrow the
 * crop-specific row probes (cocoa lane alignment, sugarcane lane-clear scan, mushroom staircase).
 * Pure (only {@link WorldService} + value types).
 */
public final class CropBlocks {

    private CropBlocks() {
    }

    public static final String SUGAR_CANE = "minecraft:sugar_cane";
    public static final String COCOA = "minecraft:cocoa";
    public static final String RED_MUSHROOM = "minecraft:red_mushroom";
    public static final String BROWN_MUSHROOM = "minecraft:brown_mushroom";
    public static final String NETHER_WART = "minecraft:nether_wart";

    public static boolean isMushroom(String blockId) {
        return RED_MUSHROOM.equals(blockId) || BROWN_MUSHROOM.equals(blockId);
    }

    /**
     * Scan up to {@code maxDistance} blocks from {@code from} in {@code dir} and return true if any
     * block (at body level or one above) matches {@code blockId}.
     */
    public static boolean anyAhead(WorldService world, BlockPos from, Direction dir,
                                   int maxDistance, String blockId) {
        for (int i = 1; i <= maxDistance; i++) {
            BlockPos p = from.offset(dir, i);
            if (world.isBlock(p, blockId) || world.isBlock(p.up(), blockId)) {
                return true;
            }
        }
        return false;
    }
}
