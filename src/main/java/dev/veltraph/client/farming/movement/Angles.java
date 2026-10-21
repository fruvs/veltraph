package dev.veltraph.client.farming.movement;

/**
 * Pure angle helpers shared by the farming aim/row code. Minecraft yaw is degrees increasing clockwise;
 * these wrap into a signed range and compute shortest-path deltas so a correction never spins the long
 * way around. No Minecraft dependency, so they are unit-testable on a plain JVM.
 */
public final class Angles {

    private Angles() {
    }

    /** Normalize a degree value into {@code [-180, 180)}. */
    public static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** Signed shortest-path delta from {@code from} to {@code to} (degrees, in {@code [-180,180)}). */
    public static float signedDelta(float from, float to) {
        return wrapDegrees(to - from);
    }

    /** Nearest cardinal yaw (0 / ±90 / 180) to {@code yaw} — the deterministic default lane heading. */
    public static float snapToCardinal(float yaw) {
        float w = wrapDegrees(yaw);
        float snapped = Math.round(w / 90f) * 90f;
        return wrapDegrees(snapped);
    }
}
