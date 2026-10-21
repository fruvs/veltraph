package dev.veltraph.client.hydra;

/**
 * A Minecraft-free mirror of the player's gamemode, so the pure {@link HydraDetector} can reason about a
 * forced-adventure transition without importing {@code net.minecraft.world.GameMode}. The MC adapter
 * maps the live {@code MinecraftClient.interactionManager.getCurrentGameMode()} (yarn
 * {@code net.minecraft.world.GameMode}: {@code SURVIVAL/CREATIVE/ADVENTURE/SPECTATOR}) onto one of these.
 *
 * <p>{@link #UNKNOWN} is used when the gamemode can't be read (no interaction manager / signature drift),
 * so an unreadable tick never fabricates a transition.
 */
public enum GameModeKind {
    SURVIVAL,
    CREATIVE,
    ADVENTURE,
    SPECTATOR,
    UNKNOWN
}
