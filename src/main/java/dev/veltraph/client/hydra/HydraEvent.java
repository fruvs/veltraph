package dev.veltraph.client.hydra;

/**
 * A typed anti-staff event emitted by the pure {@link HydraDetector} when something fires this tick.
 * The adapter turns an event into local-only reactions (chat / title / sound / auto-stop). Pure value
 * type — no Minecraft, no network.
 *
 * @param type          what fired
 * @param severity      how alarming it is (§10 STAFF_CHECK / POSSIBLE / DANGEROUS)
 * @param message       human-readable description for the local chat/title (never sent to the server)
 * @param playerName    the triggering player's name for {@link Type#STAFF_NEARBY}, else null
 * @param distanceBlocks distance for {@link Type#STAFF_NEARBY}, else {@code NaN}
 * @param stopRequested whether the macro should be stopped (respects {@code staffAlertAutoStopMacro})
 */
public record HydraEvent(
        Type type,
        HydraSeverity severity,
        String message,
        String playerName,
        double distanceBlocks,
        boolean stopRequested) {

    /** The categories of local anti-staff trigger (all local; none report anywhere). */
    public enum Type {
        /** A non-whitelisted player came within the alert distance. */
        STAFF_NEARBY,
        /** The local player's position jumped more than the teleport threshold in one tick. */
        TELEPORT_ANOMALY,
        /** The client world/dimension changed unexpectedly while scripting. */
        WORLD_ANOMALY,
        /** Routed to Limbo, or the player/world disappeared (disconnect routing). */
        LIMBO_ROUTING,
        /** Server rubber-band: local position corrected BACKWARD against intended movement (lagback). */
        INVALID_MOVEMENT,
        /** Large position jump whose destination is enclosed in solid blocks (teleport-into-block trap). */
        TELEPORT_INTO_BLOCK,
        /** Gamemode was forced SURVIVAL→ADVENTURE while a macro runs (a common staff freeze action). */
        FORCED_ADVENTURE,
        /** A RECEIVED chat/system line matched a routing/kick keyword (read-only; never replied to). */
        ROUTING_TEXT,
        /**
         * The floor/blocks under the player changed repeatedly (the local analogue of the original
         * {@code llIIIIIll…} FLOOR_CHANGE check — bedrock-floor swap probing). The live adapter emits this
         * from client-visible collision changes near/under the stationary player; no backend feed is used.
         */
        FLOOR_CHANGE,
        /**
         * A solid obstacle newly appeared in the player's forward path while held in place (the local,
         * client-visible analogue of the original {@code BARRIER} situation, whose detection in the original
         * came from the C2-fed pool, EXCLUDE_NETWORK). The live adapter emits this from a forward-collision
         * rising edge via {@link BarrierWatcher}; no backend feed is used.
         */
        BARRIER
    }

    static HydraEvent staffNearby(String name, double distance, HydraSeverity severity, boolean stop) {
        String d = String.format(java.util.Locale.ROOT, "%.1f", distance);
        String sev = severity == HydraSeverity.DANGEROUS ? "DANGEROUS — " : "";
        return new HydraEvent(Type.STAFF_NEARBY, severity,
                sev + "Player " + name + " within " + d + " blocks", name, distance, stop);
    }

    static HydraEvent teleportAnomaly(double distance, boolean stop) {
        String d = String.format(java.util.Locale.ROOT, "%.1f", distance);
        return new HydraEvent(Type.TELEPORT_ANOMALY, HydraSeverity.POSSIBLE,
                "Suspicious movement: position jumped " + d + " blocks in one tick", null, distance, stop);
    }

    static HydraEvent worldAnomaly(boolean stop) {
        return new HydraEvent(Type.WORLD_ANOMALY, HydraSeverity.POSSIBLE,
                "World/dimension changed unexpectedly while scripting", null, Double.NaN, stop);
    }

    static HydraEvent limboRouting(boolean stop, String detail) {
        return new HydraEvent(Type.LIMBO_ROUTING, HydraSeverity.DANGEROUS,
                "Routed to " + detail, null, Double.NaN, stop);
    }

    static HydraEvent invalidMovement(double distance, boolean stop) {
        String d = String.format(java.util.Locale.ROOT, "%.1f", distance);
        return new HydraEvent(Type.INVALID_MOVEMENT, HydraSeverity.POSSIBLE,
                "Invalid movement / lagback: pushed back " + d + " blocks against your movement",
                null, distance, stop);
    }

    static HydraEvent teleportIntoBlock(double distance, boolean stop) {
        String d = String.format(java.util.Locale.ROOT, "%.1f", distance);
        return new HydraEvent(Type.TELEPORT_INTO_BLOCK, HydraSeverity.DANGEROUS,
                "Teleported into a block: jumped " + d + " blocks into an enclosed space", null, distance, stop);
    }

    static HydraEvent forcedAdventure(boolean stop) {
        return new HydraEvent(Type.FORCED_ADVENTURE, HydraSeverity.DANGEROUS,
                "Gamemode forced to Adventure while a macro was running", null, Double.NaN, stop);
    }

    static HydraEvent routingText(String matchedKeyword, boolean stop) {
        return new HydraEvent(Type.ROUTING_TEXT, HydraSeverity.DANGEROUS,
                "Routing/kick message received: matched \"" + matchedKeyword + "\"", null, Double.NaN, stop);
    }

    /**
     * The local FLOOR_CHANGE analogue emitted by the live adapter's client-world watcher. The trigger mapping
     * routes it into the parity REACT path with both rare predicates set.
     */
    static HydraEvent floorChange(boolean stop) {
        return new HydraEvent(Type.FLOOR_CHANGE, HydraSeverity.DANGEROUS,
                "Floor/blocks under you changed repeatedly", null, Double.NaN, stop);
    }

    /**
     * The local BARRIER analogue emitted by the live adapter's forward-collision watcher: a block newly
     * appeared in front of you while held in place. Routes to the local BARRIER movement reaction.
     */
    static HydraEvent barrier(boolean stop) {
        return new HydraEvent(Type.BARRIER, HydraSeverity.DANGEROUS,
                "A barrier appeared in your path", null, Double.NaN, stop);
    }
}
