package dev.veltraph.client.service;

import dev.veltraph.client.world.BlockPos;

/**
 * Block-level queries against the loaded world. Intentionally generic (no crop-specific methods) so
 * the core stays independent of the farming domain; callers match crop blocks by namespaced id.
 */
public interface WorldService {

    /** Whether the chunk/section covering {@code pos} is loaded and queryable. */
    boolean isLoaded(BlockPos pos);

    /** A player body can occupy this block (air, crop, replaceable, or otherwise non-colliding). */
    boolean isPassable(BlockPos pos);

    /** This block has a solid top surface a player can stand on. */
    boolean isStandable(BlockPos pos);

    /**
     * Farm-aware "is there a floor to stand on here" check. Unlike {@link #isStandable} (which requires
     * a full solid top face and therefore rejects <b>farmland</b>, soul sand variants, paths, slabs,
     * etc.), this accepts any block with collision — i.e. anything a player can stand on, including the
     * surfaces crops grow on. Default: loaded and not pass-through. This is the fix for the live
     * "no-move on a normal crop/farmland row" bug (generic {@code isStandable} stranded the engine).
     */
    default boolean isSolidFloor(BlockPos pos) {
        return isLoaded(pos) && !isPassable(pos);
    }

    /**
     * Body volume at actual fractional feet. Live adapters use the current actor pose and scale;
     * the supplied dimensions are conservative fallback probes for pure adapters, which reject occupied cells.
     */
    default boolean bodyClear(dev.veltraph.client.world.Vec3 feet, double halfWidth, double height) {
        if (!validVolume(feet,halfWidth,height)) return false;
        int minX=(int)Math.floor(feet.x()-halfWidth),maxX=(int)Math.floor(feet.x()+halfWidth-1e-7);
        int minY=(int)Math.floor(feet.y()+.001),maxY=(int)Math.floor(feet.y()+height-1e-7);
        int minZ=(int)Math.floor(feet.z()-halfWidth),maxZ=(int)Math.floor(feet.z()+halfWidth-1e-7);
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++) {
            var p=new BlockPos(x,y,z);if(!isLoaded(p)||!isPassable(p))return false;
        }
        return true;
    }

    /**
     * Block contact immediately beneath the actual footprint; unknown footprint cells fail closed.
     * Shape-backed adapters distinguish a real contact from a lower slab/step or a distant floor.
     */
    default boolean hasGroundSupport(dev.veltraph.client.world.Vec3 feet,double halfWidth) {
        if(!validVolume(feet,halfWidth,1.8)||Math.abs(feet.y()-Math.rint(feet.y()))>.025)return false;
        int minX=(int)Math.floor(feet.x()-halfWidth),maxX=(int)Math.floor(feet.x()+halfWidth-1e-7);
        int minZ=(int)Math.floor(feet.z()-halfWidth),maxZ=(int)Math.floor(feet.z()+halfWidth-1e-7);
        int y=(int)Math.floor(feet.y()-.01);boolean contact=false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            var p=new BlockPos(x,y,z);if(!isLoaded(p))return false;contact|=isStandable(p);
        }
        return contact;
    }
    private static boolean validVolume(dev.veltraph.client.world.Vec3 feet,double halfWidth,double height) {
        return feet!=null&&Double.isFinite(feet.x())&&Double.isFinite(feet.y())&&Double.isFinite(feet.z())
                &&Math.abs(feet.x())<=30_000_000&&Math.abs(feet.z())<=30_000_000&&Math.abs(feet.y())<=4096
                &&Double.isFinite(halfWidth)&&halfWidth>=.01&&halfWidth<=2&&Double.isFinite(height)&&height>=.01&&height<=4;
    }

    /** Dedicated continuous-envelope witness: unknown adapters provide no permission. */
    default boolean envelopeProbe(dev.veltraph.client.world.Vec3 center,double horizontalMargin){return false;}

    /** Namespaced id of the block at {@code pos} (e.g. {@code "minecraft:nether_wart"}); empty if unknown. */
    String blockId(BlockPos pos);

    /** Loaded native state observation; unknown properties or unsupported adapters return empty. */
    default java.util.Optional<String> blockProperty(BlockPos pos, String name) {
        return java.util.Optional.empty();
    }

    /** Geometry candidate only; an action still requires the actual native ray and interaction guards. */
    default java.util.Optional<dev.veltraph.client.world.Vec3> interactionPoint(BlockPos pos,
            dev.veltraph.client.world.Vec3 eye, double range) {
        return java.util.Optional.empty();
    }

    /** Water fluid, including waterlogged collision blocks when supplied by the world adapter. */
    default boolean isWater(BlockPos pos) { return isBlock(pos,"minecraft:water"); }

    /** Convenience: the block id at {@code pos} equals {@code blockId}. */
    default boolean isBlock(BlockPos pos, String blockId) {
        return blockId(pos).equals(blockId);
    }
}
