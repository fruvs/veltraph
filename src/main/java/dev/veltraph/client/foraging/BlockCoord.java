package dev.veltraph.client.foraging;

/**
 * A plain integer block coordinate, so the pure foraging logic ({@link TreeBreaker} / {@link TreeBlacklist})
 * stays Minecraft-free and unit-testable. The MC adapter converts to/from {@code BlockPos}.
 */
public record BlockCoord(int x, int y, int z) {

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}
