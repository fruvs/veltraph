package dev.veltraph.client.foraging;

/**
 * One tick of input to the pure {@link TreeBreaker}. No Minecraft, no clock — the MC adapter resolves the
 * crosshair log + distance + movement and passes them in, so the break loop is unit-testable.
 *
 * @param enabled      whether the tree-chop macro is enabled
 * @param available    whether a world + player are present
 * @param lookingAtLog whether the crosshair is on a matching log block this tick
 * @param target       the targeted log's coordinate (null when {@code !lookingAtLog})
 * @param eyeDistance  distance from the player's eye to the targeted block (blocks); {@code NaN} when none
 * @param moving       whether the player is moving (for the "not in reach and not moving" failsafe)
 * @param nowMillis    wall clock in epoch millis (for the break timeout + blacklist TTL)
 */
public record TreeBreakInputs(
        boolean enabled,
        boolean available,
        boolean lookingAtLog,
        BlockCoord target,
        double eyeDistance,
        boolean moving,
        long nowMillis) {
}
