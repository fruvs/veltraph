package dev.veltraph.client.fishing.offense;

import dev.veltraph.client.config.ConfigKey;

/** Actual source FISHING_USE_HYPERION; salted tool/cadence/receipt values require a declared binding. */
public final class FishingOffenseConfig {
    private FishingOffenseConfig() {}
    public static final ConfigKey<Boolean> FISHING_USE_HYPERION=ConfigKey.ofBool("FISHING_USE_HYPERION",false);
    public static final ConfigKey<String> FISHING_OFFENSIVE_PROFILE=new ConfigKey<>("FISHING_OFFENSIVE_PROFILE","{}");
}
