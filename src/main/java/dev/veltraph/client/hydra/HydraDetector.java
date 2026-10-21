package dev.veltraph.client.hydra;

import dev.veltraph.client.game.GameArea;









































public final class HydraDetector {

    private HydraState state = HydraState.IDLE;
    private long lastNearbyMillis = Long.MIN_VALUE; // last tick a non-whitelisted player was in range
    private long lastProximityReactionMillis = Long.MIN_VALUE;
    private long lastAnomalyReactionMillis = Long.MIN_VALUE;
    private String nearestPlayerName; // closest non-whitelisted player within range, else null
    private HydraEvent lastEvent;

    // Forced-adventure tracking. We must distinguish a SURVIVAL→ADVENTURE transition (staff freeze) from
    // a session that was ALREADY in Adventure before the macro started. `prevGameMode` is the last
    // observed mode; `forcedAdventureLatched` prevents re-firing every tick until the mode is restored.
    private GameModeKind prevGameMode = GameModeKind.UNKNOWN;
    private boolean forcedAdventureLatched;

    /** Current watcher state (HUD read). */
    public HydraState state() {
        return state;
    }

    /** Closest non-whitelisted player currently within distance, or null (HUD read). */
    public String nearestPlayerName() {
        return nearestPlayerName;
    }

    /** The most recent event that fired, or null if none yet (HUD read). */
    public HydraEvent lastEvent() {
        return lastEvent;
    }

    /** Reset all transient state (used when the watcher is disabled / world stop). */
    public void reset() {
        state = HydraState.IDLE;
        lastNearbyMillis = Long.MIN_VALUE;
        lastProximityReactionMillis = Long.MIN_VALUE;
        lastAnomalyReactionMillis = Long.MIN_VALUE;
        nearestPlayerName = null;
        prevGameMode = GameModeKind.UNKNOWN;
        forcedAdventureLatched = false;
        // lastEvent kept for HUD history; cleared only on explicit reset of the feature
        lastEvent = null;
    }

    /**
     * Advance the detector one tick. Returns the post-tick state, at most one fresh event to react to,
     * and the nearest non-whitelisted player (HUD).
     */
    public HydraResult tick(HydraTick t) {
        HydraSettings s = t.settings();
        if (!s.enabled()) {
            // Watcher off: collapse to IDLE, never fire. (HUD shows disabled separately.)
            state = HydraState.IDLE;
            nearestPlayerName = null;
            // Keep gamemode tracking primed so re-enabling mid-session doesn't fabricate a transition.
            trackGameMode(t.gameMode());
            return HydraResult.of(HydraState.IDLE, null, null);
        }

        long now = t.nowMillis();
        long cooldown = Math.max(0L, s.cooldownMs());

        // Update forced-adventure tracking every tick (so the SURVIVAL→ADVENTURE edge is detected and
        // the latch clears on restore) BEFORE the anomaly scan reads it.
        GameModeKind prior = prevGameMode;
        boolean forcedAdventureEdge = trackGameMode(t.gameMode());

        // --- Anomaly checks (macro-safety; only while scripting). At most one anomaly per tick;
        //     ordered by severity. Spaced by cooldown so they don't spam. ---
        HydraEvent anomaly = detectAnomaly(t, s, now, cooldown, prior, forcedAdventureEdge);
        if (anomaly != null) {
            lastAnomalyReactionMillis = now;
            lastEvent = anomaly;
            // Anomalies do not change the proximity state machine; still recompute nearest for the HUD.
            nearestPlayerName = nearestInRange(t, s);
            return HydraResult.of(state, anomaly, nearestPlayerName);
        }

        // --- Proximity state machine ---
        NearbyPlayer nearest = closestNonWhitelisted(t, s);
        boolean inRange = nearest != null;
        if (inRange) {
            lastNearbyMillis = now;
            nearestPlayerName = nearest.name();
        } else {
            nearestPlayerName = null;
        }

        HydraEvent fired = null;
        if (inRange) {
            boolean fresh = state == HydraState.IDLE;
            state = HydraState.ALERTED;
            boolean cooldownElapsed = lastProximityReactionMillis == Long.MIN_VALUE
                    || now - lastProximityReactionMillis >= cooldown;
            if (fresh || cooldownElapsed) {
                HydraSeverity severity = severityFor(nearest.distanceBlocks(), s);
                fired = HydraEvent.staffNearby(nearest.name(), nearest.distanceBlocks(), severity,
                        s.autoStopMacro());
                lastProximityReactionMillis = now;
                lastEvent = fired;
            }
        } else if (state == HydraState.ALERTED) {
            // ALERTED -> IDLE only after cooldownMs with no nearby player.
            if (lastNearbyMillis == Long.MIN_VALUE || now - lastNearbyMillis >= cooldown) {
                state = HydraState.IDLE;
            }
        }
        return HydraResult.of(state, fired, nearestPlayerName);
    }

    /**
     * Classify a proximity distance into a §10 severity band: within {@code dangerousDistanceBlocks} →
     * {@link HydraSeverity#DANGEROUS}; otherwise (still within the alert distance) → {@code POSSIBLE}.
     */
    private static HydraSeverity severityFor(double distance, HydraSettings s) {
        return distance <= s.dangerousDistanceBlocks() ? HydraSeverity.DANGEROUS : HydraSeverity.POSSIBLE;
    }

    /**
     * Update the forced-adventure tracking with this tick's gamemode and return whether a fresh
     * SURVIVAL→ADVENTURE edge occurred this tick (not yet latched). UNKNOWN ticks (unreadable) never
     * fabricate a transition: prior mode is preserved. The latch clears when the mode leaves ADVENTURE.
     */
    private boolean trackGameMode(GameModeKind mode) {
        if (mode == null || mode == GameModeKind.UNKNOWN) {
            return false; // unreadable — keep prevGameMode + latch unchanged
        }
        GameModeKind prior = prevGameMode;
        prevGameMode = mode;
        if (mode != GameModeKind.ADVENTURE) {
            forcedAdventureLatched = false; // restored / never in adventure → re-arm
            return false;
        }
        // mode == ADVENTURE
        if (prior == GameModeKind.SURVIVAL && !forcedAdventureLatched) {
            // Fresh SURVIVAL→ADVENTURE edge. The "already adventure before start" case never reaches
            // here because `prior` would be ADVENTURE (or UNKNOWN), not SURVIVAL.
            return true;
        }
        return false;
    }

    /** The single most-severe anomaly this tick (or null). Only while scripting. */
    private HydraEvent detectAnomaly(HydraTick t, HydraSettings s, long now, long cooldown,
                                     GameModeKind priorGameMode, boolean forcedAdventureEdge) {
        if (!t.scripting()) {
            return null;
        }
        boolean spaced = lastAnomalyReactionMillis == Long.MIN_VALUE
                || now - lastAnomalyReactionMillis >= cooldown;
        if (!spaced) {
            return null;
        }
        boolean stop = s.autoStopMacro();

        // 1. Routing / disconnect text (a received kick/limbo line) — most actionable.
        if (s.detectRoutingText() && t.routingTextMatch() != null && !t.routingTextMatch().isBlank()) {
            forcedAdventureLatched = forcedAdventureEdge || forcedAdventureLatched; // don't lose the edge
            return HydraEvent.routingText(t.routingTextMatch(), stop);
        }
        // 2. Forced Adventure (SURVIVAL→ADVENTURE while a macro runs).
        if (s.detectForcedAdventure() && forcedAdventureEdge) {
            forcedAdventureLatched = true; // fire once until the mode is restored
            return HydraEvent.forcedAdventure(stop);
        }
        // 3. Limbo / disconnect routing.
        if (!t.worldPresent()) {
            return HydraEvent.limboRouting(stop, "no world/player (disconnect)");
        }
        if (t.area() == GameArea.LIMBO) {
            return HydraEvent.limboRouting(stop, "Limbo");
        }
        // 4. Unexpected world/dimension change.
        if (t.dimensionChanged()) {
            return HydraEvent.worldAnomaly(stop);
        }
        // 5. Repeated floor/body block changes while stationary (original FLOOR_CHANGE analogue).
        if (s.detectFloorChange() && t.floorChanged()) {
            return HydraEvent.floorChange(stop);
        }
        // 5b. A barrier newly placed in the forward path while held in place (original BARRIER analogue).
        if (s.detectBarrier() && t.barrierDetected()) {
            return HydraEvent.barrier(stop);
        }
        // 6/7/8. Movement anomalies.
        double move = t.tickMoveDistance();
        boolean bigJump = !Double.isNaN(move) && move > s.teleportDistanceBlocks();
        // Teleport INTO a block: a big jump whose destination is enclosed (a "freeze in a box" trap).
        if (s.detectTeleportIntoBlock() && bigJump && t.teleportEnclosed()) {
            return HydraEvent.teleportIntoBlock(move, stop);
        }
        // Invalid movement / lagback: a backward rubber-band beyond the (smaller) lagback threshold.
        double lag = t.lagbackDistance();
        if (s.detectLagback() && !Double.isNaN(lag) && lag > s.lagbackDistanceBlocks()) {
            return HydraEvent.invalidMovement(lag, stop);
        }
        // Suspicious single-tick movement / teleport into open air.
        if (bigJump) {
            return HydraEvent.teleportAnomaly(move, stop);
        }
        // Carry forward an unfired adventure edge so the latch state stays consistent if a higher-
        // severity event consumed the tick above (defensive; reached only when nothing fired).
        if (forcedAdventureEdge) {
            forcedAdventureLatched = true;
        }
        return null;
    }

    /** The closest non-whitelisted player within the alert distance this tick, or null. */
    private static NearbyPlayer closestNonWhitelisted(HydraTick t, HydraSettings s) {
        NearbyPlayer best = null;
        if (!Double.isFinite(s.distanceBlocks()) || s.distanceBlocks() < 0) return null;
        for (NearbyPlayer p : t.nearbyPlayers()) {
            if (p == null || !Double.isFinite(p.distanceBlocks()) || p.distanceBlocks() < 0) continue;
            if (p.distanceBlocks() > s.distanceBlocks()) {
                continue;
            }
            if (t.whitelist().contains(p.name())) {
                continue;
            }
            if (best == null || p.distanceBlocks() < best.distanceBlocks()) {
                best = p;
            }
        }
        return best;
    }

    /** Name of the closest in-range non-whitelisted player, or null (HUD helper). */
    private static String nearestInRange(HydraTick t, HydraSettings s) {
        NearbyPlayer n = closestNonWhitelisted(t, s);
        return n == null ? null : n.name();
    }
}
