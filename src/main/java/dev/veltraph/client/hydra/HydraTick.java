package dev.veltraph.client.hydra;

import dev.veltraph.client.game.GameArea;

import java.util.List;

/**
 * One tick of input to the pure {@link HydraDetector}. Everything the detector needs is passed in here
 * so the detector contains <b>no</b> Minecraft calls and <b>no</b> clock reads — making the full state
 * machine unit-testable on a plain JDK. The MC adapter ({@code hydra/mc/HydraAntiStaffAdapter}) builds
 * one of these per client tick.
 *
 * @param settings             config snapshot for this tick
 * @param whitelist            local friend whitelist (parsed from config)
 * @param nowMillis            monotonic-ish wall clock in epoch millis (for the cooldown window)
 * @param nearbyPlayers        other players visible this tick with their distances (empty if none/no world)
 * @param scripting            whether any Veltraph automation is currently active
 * @param worldPresent         whether a world + local player are present (false on menu/disconnect)
 * @param area                 the resolved {@link GameArea} (e.g. {@link GameArea#LIMBO})
 * @param tickMoveDistance     the local player's straight-line position delta since the previous tick, in
 *                             blocks; {@code NaN} when unknown (first tick / no prior position)
 * @param dimensionChanged     whether the client world/dimension changed unexpectedly since last tick
 * @param lagbackDistance      magnitude (blocks) of a server rubber-band that pushed the player BACKWARD
 *                             against their intended movement this tick; {@code NaN}/0 when none. Used by
 *                             the invalid-movement / lagback trigger.
 * @param teleportEnclosed     whether the destination of {@code tickMoveDistance} (a large jump) is
 *                             enclosed in solid blocks (feet/head/surroundings non-passable). Drives the
 *                             teleport-into-block trigger; meaningless unless a teleport-sized jump
 *                             occurred this tick.
 * @param gameMode             the local player's current gamemode this tick ({@link GameModeKind#UNKNOWN}
 *                             when unreadable). Drives the forced-adventure trigger.
 * @param routingTextMatch     the routing/kick keyword matched by a RECEIVED chat/system line this tick,
 *                             or null when none. The adapter supplies this from its read-only
 *                             {@link RoutingTextMatcher}; the detector never sends or replies.
 * @param floorChanged         whether the adapter observed repeated client-visible block/collision changes
 *                             near and under the player while the player stayed on the same block.
 * @param lookPitch            the local player's current look pitch (degrees) this tick, or {@code NaN}
 *                             when unreadable. Used only by the LOCAL_INCLUDE fix-angle response (the
 *                             detector itself ignores it); kept here so the response layer stays pure.
 * @param lookYaw              the local player's current look yaw (degrees) this tick, or {@code NaN} when
 *                             unreadable. Used only by the LOCAL_INCLUDE fix-angle response.
 * @param barrierDetected      whether the adapter's forward-collision watcher saw a solid block newly
 *                             appear in the player's forward path this tick (the BARRIER trigger signal).
 */
public record HydraTick(
        HydraSettings settings,
        Whitelist whitelist,
        long nowMillis,
        List<NearbyPlayer> nearbyPlayers,
        boolean scripting,
        boolean worldPresent,
        GameArea area,
        double tickMoveDistance,
        boolean dimensionChanged,
        double lagbackDistance,
        boolean teleportEnclosed,
        GameModeKind gameMode,
        String routingTextMatch,
        boolean floorChanged,
        double lookPitch,
        double lookYaw,
        boolean barrierDetected) {

    public HydraTick {
        nearbyPlayers = nearbyPlayers == null ? List.of() : List.copyOf(nearbyPlayers);
        whitelist = whitelist == null ? Whitelist.empty() : whitelist;
        gameMode = gameMode == null ? GameModeKind.UNKNOWN : gameMode;
    }

    /**
     * Convenience constructor matching the pre-BARRIER canonical shape (no barrier signal). Defaults
     * {@code barrierDetected} to false so every existing caller/test keeps its exact behaviour.
     */
    public HydraTick(HydraSettings settings,
                     Whitelist whitelist,
                     long nowMillis,
                     List<NearbyPlayer> nearbyPlayers,
                     boolean scripting,
                     boolean worldPresent,
                     GameArea area,
                     double tickMoveDistance,
                     boolean dimensionChanged,
                     double lagbackDistance,
                     boolean teleportEnclosed,
                     GameModeKind gameMode,
                     String routingTextMatch,
                     boolean floorChanged,
                     double lookPitch,
                     double lookYaw) {
        this(settings, whitelist, nowMillis, nearbyPlayers, scripting, worldPresent, area,
                tickMoveDistance, dimensionChanged, lagbackDistance, teleportEnclosed, gameMode,
                routingTextMatch, floorChanged, lookPitch, lookYaw, /*barrierDetected*/ false);
    }

    /**
     * Convenience constructor for callers that supply a look pitch but no yaw. Defaults {@code lookYaw} to
     * {@code NaN} (fix-angle keeps the current yaw).
     */
    public HydraTick(HydraSettings settings,
                     Whitelist whitelist,
                     long nowMillis,
                     List<NearbyPlayer> nearbyPlayers,
                     boolean scripting,
                     boolean worldPresent,
                     GameArea area,
                     double tickMoveDistance,
                     boolean dimensionChanged,
                     double lagbackDistance,
                     boolean teleportEnclosed,
                     GameModeKind gameMode,
                     String routingTextMatch,
                     boolean floorChanged,
                     double lookPitch) {
        this(settings, whitelist, nowMillis, nearbyPlayers, scripting, worldPresent, area,
                tickMoveDistance, dimensionChanged, lagbackDistance, teleportEnclosed, gameMode,
                routingTextMatch, floorChanged, lookPitch, /*lookYaw*/ Double.NaN);
    }

    /**
     * Convenience constructor for callers that supply look angles but no floor-change signal. Defaults
     * {@code floorChanged} to false.
     */
    public HydraTick(HydraSettings settings,
                     Whitelist whitelist,
                     long nowMillis,
                     List<NearbyPlayer> nearbyPlayers,
                     boolean scripting,
                     boolean worldPresent,
                     GameArea area,
                     double tickMoveDistance,
                     boolean dimensionChanged,
                     double lagbackDistance,
                     boolean teleportEnclosed,
                     GameModeKind gameMode,
                     String routingTextMatch,
                     double lookPitch,
                     double lookYaw) {
        this(settings, whitelist, nowMillis, nearbyPlayers, scripting, worldPresent, area,
                tickMoveDistance, dimensionChanged, lagbackDistance, teleportEnclosed, gameMode,
                routingTextMatch, /*floorChanged*/ false, lookPitch, lookYaw);
    }

    /**
     * Convenience constructor for callers that don't supply a look angle (the detector path). Defaults
     * {@code lookPitch}/{@code lookYaw} to {@code NaN}, which suppresses the fix-angle response.
     */
    public HydraTick(HydraSettings settings,
                     Whitelist whitelist,
                     long nowMillis,
                     List<NearbyPlayer> nearbyPlayers,
                     boolean scripting,
                     boolean worldPresent,
                     GameArea area,
                     double tickMoveDistance,
                     boolean dimensionChanged,
                     double lagbackDistance,
                     boolean teleportEnclosed,
                     GameModeKind gameMode,
                     String routingTextMatch) {
        this(settings, whitelist, nowMillis, nearbyPlayers, scripting, worldPresent, area,
                tickMoveDistance, dimensionChanged, lagbackDistance, teleportEnclosed, gameMode,
                routingTextMatch, /*floorChanged*/ false, /*lookPitch*/ Double.NaN, /*lookYaw*/ Double.NaN);
    }

    /**
     * Convenience constructor for detector tests/adapters that supply the floor-change signal but no look
     * angles. Defaults {@code lookPitch}/{@code lookYaw} to {@code NaN}.
     */
    public HydraTick(HydraSettings settings,
                     Whitelist whitelist,
                     long nowMillis,
                     List<NearbyPlayer> nearbyPlayers,
                     boolean scripting,
                     boolean worldPresent,
                     GameArea area,
                     double tickMoveDistance,
                     boolean dimensionChanged,
                     double lagbackDistance,
                     boolean teleportEnclosed,
                     GameModeKind gameMode,
                     String routingTextMatch,
                     boolean floorChanged) {
        this(settings, whitelist, nowMillis, nearbyPlayers, scripting, worldPresent, area,
                tickMoveDistance, dimensionChanged, lagbackDistance, teleportEnclosed, gameMode,
                routingTextMatch, floorChanged, /*lookPitch*/ Double.NaN, /*lookYaw*/ Double.NaN);
    }

    /**
     * Backwards-compatible convenience constructor for the original proximity/teleport/world/limbo
     * inputs. The new defensive-trigger inputs default to "no signal" (no lagback, not enclosed, unknown
     * gamemode, no routing match) so existing call sites and tests keep their exact behavior.
     */
    public HydraTick(HydraSettings settings,
                     Whitelist whitelist,
                     long nowMillis,
                     List<NearbyPlayer> nearbyPlayers,
                     boolean scripting,
                     boolean worldPresent,
                     GameArea area,
                     double tickMoveDistance,
                     boolean dimensionChanged) {
        this(settings, whitelist, nowMillis, nearbyPlayers, scripting, worldPresent, area,
                tickMoveDistance, dimensionChanged,
                /*lagbackDistance*/ Double.NaN, /*teleportEnclosed*/ false,
                /*gameMode*/ GameModeKind.UNKNOWN, /*routingTextMatch*/ null);
    }
}
