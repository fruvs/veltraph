package dev.veltraph.client.world;

/**
 * Immutable integer block position. Pure value type (no Minecraft dependency) so planners can be
 * tested off-thread / off-game.
 */
public record BlockPos(int x, int y, int z) {

    public BlockPos offset(Direction dir) {
        return offset(dir, 1);
    }

    public BlockPos offset(Direction dir, int n) {
        return new BlockPos(x + dir.dx() * n, y + dir.dy() * n, z + dir.dz() * n);
    }

    public BlockPos up() {
        return offset(Direction.UP, 1);
    }

    public BlockPos up(int n) {
        return offset(Direction.UP, n);
    }

    public BlockPos down() {
        return offset(Direction.DOWN, 1);
    }

    public BlockPos down(int n) {
        return offset(Direction.DOWN, n);
    }

    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
