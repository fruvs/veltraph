package dev.veltraph.client.game;

/**
 * Immutable snapshot of the local game context, derived only from client-visible data (no network
 * calls). Used by the activation guard and HUD visibility service.
 *
 * @param onHypixel  the client is connected to Hypixel (by server brand / address)
 * @param inSkyBlock the player is in Hypixel SkyBlock (not BedWars/lobby/non-SkyBlock)
 * @param area       the resolved {@link GameArea} ({@link GameArea#UNKNOWN} if undetermined)
 */
public record GameContext(boolean onHypixel, boolean inSkyBlock, GameArea area) {

    public static final GameContext NONE = new GameContext(false, false, GameArea.UNKNOWN);

    public boolean isArea(GameArea target) {
        return area == target;
    }
}
