package dev.veltraph.client.gui;

import dev.veltraph.client.config.ConfigKey;

/** Readable stable 4.0.30 default; persistence registration belongs to Bootstrap integration. */
public final class HarpConfig {
    public static final ConfigKey<Boolean> AUTOMATIC_HARP_TICK_RATE_DETECTION =
            ConfigKey.ofBool("AUTOMATIC_HARP_TICK_RATE_DETECTION", true);
    private HarpConfig() {}
}
