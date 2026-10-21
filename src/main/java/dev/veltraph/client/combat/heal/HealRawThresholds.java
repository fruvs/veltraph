package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.GhostConfig;

/** Frozen native source threshold settings, with explicit raw HP units. */
public record HealRawThresholds(int normal, int emergency) {
    public static HealRawThresholds fromConfig(ConfigService config) {
        return new HealRawThresholds(config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE),
                config.get(GhostConfig.GHOST_EMERGENCY_HEALTH_TO_ACTIVATE));
    }
    public boolean matches(ConfigService config) { return equals(fromConfig(config)); }
    public String status() { return "activate<="+normal+" HP (emergency<="+emergency+" HP)"; }
}
