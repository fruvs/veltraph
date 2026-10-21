package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.GhostConfig;

/**
 * Immutable tuning snapshot for {@link HyperionAutoHeal}, derived from {@link GhostConfig}. The original
 * thresholds/delays were runtime-DES-salted; these are the spec's documented defaults
 * (`reports/_spec_parts/GhostAutoHeal.md`).
 *
 * @param activatePct    heal when health ≤ this % of max (default 50)
 * @param emergencyPct   force-heal (ignore cooldown) when health ≤ this % (default 25)
 * @param postCastWaitMs hold after casting before restoring the camera (default 1200)
 * @param cooldownMs     minimum gap between heals (default 3000)
 * @param windowMs       hard upper bound on a single heal attempt (recovered 15000)
 */
public record HealTuning(int activatePct, int emergencyPct, long postCastWaitMs, long cooldownMs, long windowMs) {

    public static HealTuning fromConfig(ConfigService config) {
        return new HealTuning(
                config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT),
                config.get(GhostConfig.GHOST_EMERGENCY_HEALTH_PCT),
                config.get(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS),
                config.get(GhostConfig.GHOST_HEAL_COOLDOWN_MS),
                15000L);
    }
}
