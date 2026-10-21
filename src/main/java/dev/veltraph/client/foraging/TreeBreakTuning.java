package dev.veltraph.client.foraging;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.ForagingConfig;

/**
 * Immutable tuning snapshot for {@link TreeBreaker}, from {@link ForagingConfig}. Recovered constants
 * (`reports/_spec_parts/TreeBreaker.md`): reach gate {@code 4.5}; break timeout was runtime-salted →
 * default {@code 8000} ms; blacklist TTL {@code 60000} ms (shared-flow reset).
 *
 * @param reach           max eye→trunk distance before the "not in reach" abort (default 4.5)
 * @param breakTimeoutMs  abort + blacklist a tree that hasn't fallen within this long (default 8000)
 * @param blacklistTtlMs  how long a blacklisted tree stays skipped (default 60000)
 */
public record TreeBreakTuning(double reach, long breakTimeoutMs, long blacklistTtlMs) {

    public static TreeBreakTuning fromConfig(ConfigService config) {
        return new TreeBreakTuning(
                config.get(ForagingConfig.FORAGING_TREE_REACH),
                config.get(ForagingConfig.FORAGING_TREE_BREAK_TIMEOUT_MS),
                config.get(ForagingConfig.FORAGING_BLACKLIST_TTL_MS));
    }
}
