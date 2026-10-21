package dev.veltraph.client.foraging;












public final class TreeBreaker {

    private final TreeBlacklist blacklist = new TreeBlacklist();

    private BlockCoord currentTarget;
    private long breakStartMs;
    private int aborted;       // reach/timeout aborts
    private long breakTicks;   // ticks spent holding the break (diagnostics)
    private String lastReason; // last abort reason (diagnostics)

    public TreeBlacklist blacklist() {
        return blacklist;
    }

    public BlockCoord currentTarget() {
        return currentTarget;
    }

    public int abortedCount() {
        return aborted;
    }

    public long breakTicks() {
        return breakTicks;
    }

    public String lastReason() {
        return lastReason;
    }

    /** Advance one tick and return whether to hold (BREAK) or release the attack key. */
    public TreeBreakAction tick(TreeBreakInputs in, TreeBreakTuning tuning) {
        if (!in.enabled() || !in.available() || !in.lookingAtLog() || in.target() == null) {
            currentTarget = null;
            return TreeBreakAction.RELEASE;
        }
        BlockCoord target = in.target();

        // Skip a blacklisted tree (until its TTL expires).
        if (blacklist.isActive(target, in.nowMillis(), tuning.blacklistTtlMs())) {
            currentTarget = null;
            return TreeBreakAction.RELEASE;
        }

        // New target → restart the break timer.
        if (currentTarget == null || !currentTarget.equals(target)) {
            currentTarget = target;
            breakStartMs = in.nowMillis();
        }

        // Reach failsafe: out of reach AND not moving → abandon + blacklist.
        if (!Double.isNaN(in.eyeDistance()) && in.eyeDistance() > tuning.reach() && !in.moving()) {
            return abort(target, in.nowMillis(), "not in reach of tree");
        }
        // Timeout failsafe: tree hasn't fallen in time → abandon + blacklist.
        if (in.nowMillis() - breakStartMs > tuning.breakTimeoutMs()) {
            return abort(target, in.nowMillis(), "took too long to break tree");
        }

        breakTicks++;
        return TreeBreakAction.BREAK;
    }

    private TreeBreakAction abort(BlockCoord target, long now, String reason) {
        blacklist.blacklist(target, now);
        currentTarget = null;
        aborted++;
        lastReason = reason;
        return TreeBreakAction.RELEASE;
    }

    public void onFeedback(String message,long now) {
        if(currentTarget!=null && message.startsWith("You cannot damage a tree")) abort(currentTarget,now,"unbreakable tree");
    }

    /** Reset transient state (on disable / world stop); keeps nothing across runs. */
    public void reset() {
        currentTarget = null;
        breakStartMs = 0;
        aborted = 0;
        breakTicks = 0;
        lastReason = null;
        blacklist.clear();
    }
}
