package dev.veltraph.client.service.mc;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

/**
 * Conversions between Veltraph's pure value types and the Minecraft yarn types. Kept in one place so
 * the adapter classes stay small and the mapping is auditable.
 */
final class McConversions {

    private McConversions() {
    }

    static net.minecraft.core.BlockPos toMc(BlockPos pos) {
        return new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z());
    }

    static BlockPos fromMc(net.minecraft.core.BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    static Direction fromMc(net.minecraft.core.Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }
}
