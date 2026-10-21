package dev.veltraph.client.foraging;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/**
 * Timestamped tree blacklist — the clean rebuild of the recovered TreeBreaker
 * {@code blacklistMap.put(pos, now)} (`reports/_spec_parts/TreeBreaker.md`). A tree that can't be broken
 * (timeout / not in reach / unbreakable) is blacklisted so it is skipped until the TTL expires (the
 * original shared flow resets at 60 s). Pure — no Minecraft, no clock (now is injected).
 */
public final class TreeBlacklist {

    private final Map<BlockCoord, Long> entries = new HashMap<>();

    /** Blacklist {@code coord} as of {@code now}. */
    public void blacklist(BlockCoord coord, long now) {
        if (coord != null) {
            entries.put(coord, now);
        }
    }

    /** Keep every sampled log as an alias: removing the base must not evade a tree blacklist. */
    public void blacklistTree(Collection<BlockCoord> logs,long now) {
        for(BlockCoord log:logs) blacklist(log,now);
    }

    /** Whether {@code coord} is currently blacklisted (within {@code ttlMs} of when it was added). */
    public boolean isActive(BlockCoord coord, long now, long ttlMs) {
        Long at = entries.get(coord);
        if (at == null) {
            return false;
        }
        if (now - at >= ttlMs) {
            entries.remove(coord); // expired — prune lazily
            return false;
        }
        return true;
    }

    /** Drop all expired entries (housekeeping). */
    public void prune(long now, long ttlMs) {
        entries.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
