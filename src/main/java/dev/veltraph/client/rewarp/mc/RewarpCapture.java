package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.rewarp.RewarpPoint;
import dev.veltraph.client.service.PlayerService;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Minecraft-backed helper that snapshots the live client into a {@link RewarpPoint}. It reads:
 *
 * <ul>
 *   <li><b>dimension</b> — the client world registry key value (e.g. {@code "minecraft:overworld"}),
 *       via {@code ClientWorld#getRegistryKey().getValue()} (verified against the yarn mappings:
 *       {@code method_27983} = {@code getRegistryKey}; the same call is used in {@code VeltraphBootstrap}
 *       to log world changes).</li>
 *   <li><b>area</b> — the resolved {@link GameArea} from the existing {@link GameContextService}.</li>
 *   <li><b>block pos / yaw / pitch</b> — from the existing {@link PlayerService} adapter.</li>
 *   <li><b>timestamp</b> — passed in by the caller (so the pure {@link RewarpPoint} stays deterministic
 *       and never touches {@code System.currentTimeMillis()}).</li>
 * </ul>
 *
 * <p>Returns empty when no world/player is present (the caller should report a chat error then).
 */
public final class RewarpCapture {

    private final Minecraft client;
    private final PlayerService player;
    private final GameContextService gameContext;

    public RewarpCapture(Minecraft client, PlayerService player, GameContextService gameContext) {
        this.client = client;
        this.player = player;
        this.gameContext = gameContext;
    }

    /**
     * Build a {@link RewarpPoint} from the current client state, stamped with {@code nowMillis}.
     *
     * @param nowMillis capture time in epoch millis (passed in for determinism)
     * @return the captured point, or empty if no world/player is present
     */
    public Optional<RewarpPoint> capture(long nowMillis) {
        if (player == null || !player.isAvailable()) {
            return Optional.empty();
        }
        String dimension = dimensionId();
        if (dimension == null) {
            return Optional.empty();
        }
        var exact=player.position();float yaw=player.yaw(),pitch=player.pitch();
        if(exact==null||!Double.isFinite(exact.x())||!Double.isFinite(exact.y())||!Double.isFinite(exact.z())
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch< -90||pitch>90)return Optional.empty();
        GameArea area = gameContext == null ? GameArea.UNKNOWN : gameContext.current().area();
        return Optional.of(new RewarpPoint(
                dimension,
                area,
                exact.toBlockPos(),
                yaw,
                pitch,
                nowMillis,
                exact));
    }

    /** The current world registry id (e.g. {@code "minecraft:overworld"}), or null if no world. */
    private String dimensionId() {
        try {
            ClientLevel world = client.level;
            if (world == null) {
                return null;
            }
            return world.dimension().identifier().toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
