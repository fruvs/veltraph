package dev.veltraph.client.fishing.setup;

import dev.veltraph.client.config.ConfigKey;

/** Optional overrides for the stable native rod selector; {} uses recovered source bindings. */
public final class FishingSetupConfig {
    private FishingSetupConfig() {}
    public static final ConfigKey<Boolean> FISHING_AUTO_ROD = ConfigKey.ofBool("FISHING_AUTO_ROD", false);
    public static final ConfigKey<String> FISHING_ROD_PROFILE = new ConfigKey<>("FISHING_ROD_PROFILE", "{}");
}
