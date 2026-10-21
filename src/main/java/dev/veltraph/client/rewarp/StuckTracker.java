package dev.veltraph.client.rewarp;

/**
 * Pure no-position-change timer used to feed the {@link AutoRewarpRuntime} stuck trigger. The bootstrap
 * samples the player's position each tick and reports it here; the tracker records when the player last
 * moved more than a small epsilon and exposes "stuck since" so the runtime can apply its configured
 * stuck window.
 *
 * <p>This is the uniform, engine-agnostic "stuck" notion the spec allows ("a no-position-change timer
 * you pass in"): it works for every farming module regardless of which engine/planner drives it, and it
 * is fully deterministic (no clock read — now-millis is injected). It complements, rather than peeks
 * into, the engines' own {@code RECOVERING} state.
 *
 * <p>Pure (no Minecraft dependency).
 */
public final class StuckTracker {

    /** Squared block distance below which the player is considered "not progressing" between samples. */
    private static final double MOVE_EPSILON_SQ = 0.05 * 0.05;

    private boolean active;
    private boolean haveLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private long stuckSinceMs;

    /**
     * Sample the player's position for this tick.
     *
     * @param tracking true while a farm module is actively running (and not suspended); when false the
     *                 tracker resets so a paused/idle period is never counted as "stuck"
     * @param x,y,z    current player position (any units; only relative motion matters)
     * @param nowMs    injected epoch-millis
     */
    public void sample(boolean tracking, double x, double y, double z, long nowMs) {
        if (!tracking) {
            reset();
            return;
        }
        if (!haveLast) {
            mark(x, y, z, nowMs);
            return;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        if (dx * dx + dy * dy + dz * dz > MOVE_EPSILON_SQ) {
            // Moved enough: not stuck; restart the streak from here.
            mark(x, y, z, nowMs);
            return;
        }
        // No meaningful movement since the last sample: keep the existing stuckSinceMs running.
        if (!active) {
            active = true;
            stuckSinceMs = nowMs;
        }
    }

    private void mark(double x, double y, double z, long nowMs) {
        haveLast = true;
        active = false;
        lastX = x;
        lastY = y;
        lastZ = z;
        stuckSinceMs = nowMs;
    }

    /** Reset all tracking (e.g. when farming stops or the runtime begins a rewarp). */
    public void reset() {
        active = false;
        haveLast = false;
        stuckSinceMs = 0L;
    }

    /** True when the player has not moved meaningfully since {@link #stuckSinceMs()}. */
    public boolean isStuck() {
        return active;
    }

    /** Epoch-millis when the current no-movement streak began (meaningful only when {@link #isStuck()}). */
    public long stuckSinceMs() {
        return stuckSinceMs;
    }
}
