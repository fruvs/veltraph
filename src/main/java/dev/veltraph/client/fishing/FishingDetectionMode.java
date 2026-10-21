package dev.veltraph.client.fishing;

import java.util.Locale;

/**
 * How a bite is detected from the live {@code FishingBobberEntity}. The original predicate
 * (`test332`/`test333`) is an erased {@code Object[]} bridge; {@code FishingBiteWire.md} gives the
 * standard deterministic equivalents — any of these reproduces the behavior:
 * <ul>
 *   <li>{@link #BOBBER_DIP} — a sharp downward bobber Y-velocity (the fish yanks the bobber under). The
 *       most version-stable signal (uses {@code Entity#getVelocity().y}); the default.</li>
 *   <li>{@link #HOOKED_ENTITY} — the bobber reports a hooked entity (exact vanilla signal).</li>
 *   <li>{@link #ANY} — either of the above fires.</li>
 * </ul>
 */
public enum FishingDetectionMode {
    TRACKED_BITE,
    BOBBER_DIP,
    HOOKED_ENTITY,
    ANY;

    public static FishingDetectionMode parse(String raw) {
        if (raw == null) {
            return BOBBER_DIP;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        for (FishingDetectionMode m : values()) {
            if (m.name().equals(s)) {
                return m;
            }
        }
        return BOBBER_DIP;
    }
}
