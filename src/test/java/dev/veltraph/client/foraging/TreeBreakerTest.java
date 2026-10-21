package dev.veltraph.client.foraging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tree-break loop tests (reach / timeout / blacklist / block-guard / new-target). */
class TreeBreakerTest {

    private static final TreeBreakTuning T = new TreeBreakTuning(4.5, 8000, 60_000);
    private static final BlockCoord TREE = new BlockCoord(10, 64, 10);

    private static TreeBreakInputs in(boolean onLog, BlockCoord t, double dist, boolean moving, long now) {
        return new TreeBreakInputs(true, true, onLog, t, dist, moving, now);
    }

    @Test
    void breaksWhenAimedAtLogInReach() {
        TreeBreaker b = new TreeBreaker();
        assertEquals(TreeBreakAction.BREAK, b.tick(in(true, TREE, 3.0, false, 0), T));
        assertEquals(TREE, b.currentTarget());
    }

    @Test
    void releasesWhenNotLookingAtLog() {
        TreeBreaker b = new TreeBreaker();
        assertEquals(TreeBreakAction.RELEASE, b.tick(in(false, null, Double.NaN, false, 0), T));
        assertNull(b.currentTarget());
    }

    @Test
    void disabledOrUnavailableReleases() {
        TreeBreaker b = new TreeBreaker();
        assertEquals(TreeBreakAction.RELEASE,
                b.tick(new TreeBreakInputs(false, true, true, TREE, 3.0, false, 0), T));
        assertEquals(TreeBreakAction.RELEASE,
                b.tick(new TreeBreakInputs(true, false, true, TREE, 3.0, false, 0), T));
    }

    @Test
    void outOfReachAndNotMovingAbandonsAndBlacklists() {
        TreeBreaker b = new TreeBreaker();
        assertEquals(TreeBreakAction.RELEASE, b.tick(in(true, TREE, 6.0, false, 0), T));
        assertEquals("not in reach of tree", b.lastReason());
        assertEquals(1, b.abortedCount());
        // Now blacklisted -> still released even when back in reach (until TTL).
        assertEquals(TreeBreakAction.RELEASE, b.tick(in(true, TREE, 3.0, false, 100), T));
    }

    @Test
    void outOfReachButMovingKeepsTrying() {
        TreeBreaker b = new TreeBreaker();
        // moving = the player is walking toward the tree; the "not in reach" abort requires !moving.
        assertEquals(TreeBreakAction.BREAK, b.tick(in(true, TREE, 6.0, true, 0), T));
        assertEquals(0, b.abortedCount());
    }

    @Test
    void breakTimeoutAbandonsAndBlacklists() {
        TreeBreaker b = new TreeBreaker();
        b.tick(in(true, TREE, 3.0, false, 0), T);          // start break @ 0
        assertEquals(TreeBreakAction.BREAK, b.tick(in(true, TREE, 3.0, false, 7000), T)); // < 8000
        assertEquals(TreeBreakAction.RELEASE, b.tick(in(true, TREE, 3.0, false, 8001), T)); // > 8000
        assertEquals("took too long to break tree", b.lastReason());
        assertTrue(b.blacklist().isActive(TREE, 8001, 60_000));
    }

    @Test
    void newTargetRestartsTheBreakTimer() {
        TreeBreaker b = new TreeBreaker();
        b.tick(in(true, TREE, 3.0, false, 0), T);          // tree A @ 0
        BlockCoord other = new BlockCoord(20, 64, 20);
        // Switch to tree B at t=7000; its timer restarts so it must NOT immediately time out.
        assertEquals(TreeBreakAction.BREAK, b.tick(in(true, other, 3.0, false, 7000), T));
        assertEquals(other, b.currentTarget());
        assertEquals(TreeBreakAction.BREAK, b.tick(in(true, other, 3.0, false, 14000), T)); // 7000 < 8000
    }

    @Test
    void blacklistedTreeIsSkipped() {
        TreeBreaker b = new TreeBreaker();
        b.blacklist().blacklist(TREE, 0);
        assertEquals(TreeBreakAction.RELEASE, b.tick(in(true, TREE, 3.0, false, 100), T));
        assertNull(b.currentTarget());
    }

    @Test
    void resetClearsState() {
        TreeBreaker b = new TreeBreaker();
        b.tick(in(true, TREE, 6.0, false, 0), T); // abort -> blacklist
        b.reset();
        assertEquals(0, b.abortedCount());
        assertEquals(0, b.blacklist().size());
        assertNull(b.lastReason());
        // After reset the same tree breaks again.
        assertEquals(TreeBreakAction.BREAK, b.tick(in(true, TREE, 3.0, false, 0), T));
    }
    @Test void cannotDamageFeedbackBlacklistsTheCurrentTree() {
        TreeBreaker b=new TreeBreaker();b.tick(in(true,TREE,3,false,0),T);
        b.onFeedback("You cannot damage a tree right now",100);
        assertNull(b.currentTarget());assertTrue(b.blacklist().isActive(TREE,100,60000));
        assertEquals("unbreakable tree",b.lastReason());
    }
}
