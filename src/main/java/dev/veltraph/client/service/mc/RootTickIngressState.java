package dev.veltraph.client.service.mc;

/** Callback-free state kernel. Its model admissions alone confer no native authority. */
final class RootTickIngressState {
    private final Thread thread = Thread.currentThread();
    private final Object client;
    private Object event, loop, subscriber, claim;
    private boolean revoked, expired, eventConsumed, claimed;
    private ClosedNormalClaim normalClaim;
    /** Created only by the exact live claim's accepted normal close; never an entry capability. */
    private final class ClosedNormalClaim {
        private final Object exactClaim;
        private ClosedNormalClaim(Object exactClaim) { this.exactClaim = exactClaim; }
        private boolean current(Object exactClient,Object exact) {
            return !revoked && thread == Thread.currentThread() && client == exactClient && exactClaim == exact;
        }
    }

    RootTickIngressState(Object client) { this.client = client; }
    boolean live(Object exactClient) { return !revoked && !expired && thread == Thread.currentThread() && client == exactClient; }
    void revoke() { revoked = true; }
    boolean enterEvent(Object exactClient, Object exactEvent) {
        if (!live(exactClient) || exactEvent == null || eventConsumed || event != null) { revoke(); return false; }
        eventConsumed = true; event = exactEvent; return true;
    }
    boolean enterLoop(Object exactClient, Object exactEvent, Object exactLoop) {
        if (!live(exactClient) || event == null || event != exactEvent || exactLoop == null || loop != null) { revoke(); return false; }
        loop = exactLoop; return true;
    }
    boolean enterSubscriber(Object exactClient, Object exactLoop, Object exactSubscriber) {
        if (!live(exactClient) || loop == null || loop != exactLoop || exactSubscriber == null || subscriber != null) { revoke(); return false; }
        subscriber = exactSubscriber; return true;
    }
    boolean claim(Object exactClient, Object exactSubscriber, Object exactClaim) {
        if (!live(exactClient) || subscriber == null || subscriber != exactSubscriber || exactClaim == null || claimed || claim != null) { revoke(); return false; }
        claimed = true; claim = exactClaim; return true;
    }
    boolean current(Object exactClient, Object exactSubscriber, Object exactClaim) {
        return live(exactClient) && subscriber == exactSubscriber && claim == exactClaim && exactClaim != null;
    }
    boolean leaveClaim(Object exactClaim, boolean normal) {
        boolean accepted = live(client) && claim == exactClaim && exactClaim != null && normal;
        if (!accepted) revoke();
        else normalClaim = new ClosedNormalClaim(exactClaim);
        if (claim == exactClaim) claim = null;
        return accepted;
    }
    /** Historical accepted claim only; normal expiry does not revoke its exact source fact. */
    boolean normalClaimReturned(Object exactClient,Object exactClaim) {
        var returned = normalClaim;
        return returned != null && exactClaim != null && returned.current(exactClient,exactClaim);
    }
    /** Retire native entry once. Abnormal or incomplete retirement invalidates historical origin. */
    void closeTick(boolean normal) {
        if (!normal || !live(client) || event != null || loop != null || subscriber != null || claim != null) revoke();
        expired = true;
    }
    void leaveSubscriber(Object exactSubscriber, boolean normal) {
        if (subscriber != exactSubscriber || exactSubscriber == null || claim != null || !normal) revoke();
        if (subscriber == exactSubscriber) subscriber = null;
    }
    void leaveLoop(Object exactLoop, boolean normal) {
        if (loop != exactLoop || exactLoop == null || subscriber != null || !normal) revoke();
        if (loop == exactLoop) loop = null;
    }
    void leaveEvent(Object exactEvent, boolean normal) {
        if (event != exactEvent || exactEvent == null || loop != null || !normal) revoke();
        if (event == exactEvent) event = null;
    }
}
