package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import java.util.Set;

/**
 * Capture-backed bed validation, including carved crop blocks without accepting decorative heads.
 * The captured gourd plot has 3,350 carved blocks on dirt adjacent to pumpkin stems. The decoded
 * PumpkinFarmController names CARVED_PUMPKIN/MELON; this loaded-world rule is a local interpretation.
 */
public final class GourdCropTarget {
    private static final Set<String> FRUIT = Set.of("minecraft:melon", "minecraft:pumpkin", "minecraft:carved_pumpkin");
    private static final Set<String> BED = Set.of("minecraft:dirt", "minecraft:farmland", "minecraft:grass_block");
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private GourdCropTarget() {}

    public static boolean harvestable(WorldService world, BlockPos target) {
        if (target == null || !world.isLoaded(target) || !FRUIT.contains(world.blockId(target))) return false;
        return cropBed(world,target,world.isBlock(target,"minecraft:melon")?"melon":"pumpkin");
    }

    /** Potential mature/empty planting bed, without authorizing attack on the empty cell. */
    public static boolean cropBed(WorldService world,BlockPos target,String crop) {
        if(target==null||!world.isLoaded(target)||!world.isLoaded(target.down())
                ||!BED.contains(world.blockId(target.down()))||(!"melon".equals(crop)&&!"pumpkin".equals(crop)))return false;
        for (int[] direction : NEIGHBORS) {
            BlockPos stem = target.add(direction[0], 0, direction[1]);
            if (!world.isLoaded(stem) || !world.isLoaded(stem.down())
                    || !world.isBlock(stem.down(), "minecraft:farmland")) continue;
            String id = world.blockId(stem);
            if (id.equals("minecraft:" + crop + "_stem") || id.equals("minecraft:attached_" + crop + "_stem")) return true;
        }
        return false;
    }
}
