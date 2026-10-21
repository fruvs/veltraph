package dev.veltraph.client.hydra;

/**
 * A single other player visible to the local client this tick, with their distance (in blocks) to the
 * local player. Built by the MC adapter; consumed by the pure {@link HydraDetector}.
 *
 * @param name            the player's name (game-profile name)
 * @param distanceBlocks  straight-line distance to the local player in blocks
 */
public record NearbyPlayer(String name, double distanceBlocks) {
}
