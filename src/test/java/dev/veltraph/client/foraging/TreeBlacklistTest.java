package dev.veltraph.client.foraging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure timestamped tree-blacklist tests. */
class TreeBlacklistTest {

    private static final long TTL = 60_000L;

    @Test
    void blacklistedTreeIsActiveWithinTtl() {
        TreeBlacklist bl = new TreeBlacklist();
        BlockCoord t = new BlockCoord(1, 2, 3);
        bl.blacklist(t, 1000);
        assertTrue(bl.isActive(t, 1000, TTL));
        assertTrue(bl.isActive(t, 1000 + 59_999, TTL));
    }

    @Test
    void blacklistExpiresAfterTtl() {
        TreeBlacklist bl = new TreeBlacklist();
        BlockCoord t = new BlockCoord(1, 2, 3);
        bl.blacklist(t, 1000);
        assertFalse(bl.isActive(t, 1000 + TTL, TTL));
        assertEquals(0, bl.size(), "expired entry pruned lazily on lookup");
    }

    @Test
    void unknownTreeIsNotActive() {
        TreeBlacklist bl = new TreeBlacklist();
        assertFalse(bl.isActive(new BlockCoord(9, 9, 9), 0, TTL));
    }

    @Test
    void pruneRemovesExpiredOnly() {
        TreeBlacklist bl = new TreeBlacklist();
        bl.blacklist(new BlockCoord(1, 1, 1), 0);
        bl.blacklist(new BlockCoord(2, 2, 2), 30_000);
        bl.prune(61_000, TTL); // first expired (61000-0), second not (61000-30000=31000)
        assertEquals(1, bl.size());
        assertTrue(bl.isActive(new BlockCoord(2, 2, 2), 61_000, TTL));
    }
    @Test void removingBaseDoesNotEvadeWholeTreeBlacklist() {
        var base=new BlockCoord(0,70,0);var remaining=new BlockCoord(0,71,0);
        var blacklist=new TreeBlacklist();blacklist.blacklistTree(java.util.List.of(base,remaining),100);
        assertTrue(blacklist.isActive(remaining,200,60000));
        assertFalse(blacklist.isActive(remaining,60100,60000));
    }
}
