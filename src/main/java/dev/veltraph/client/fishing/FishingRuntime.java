package dev.veltraph.client.fishing;

import java.util.Random;



















public final class FishingRuntime {

    private static final long TICK_MS = 50L; // one MC tick

    private final Random random;
    private final FishingTuning tuning;

    private FishingState state = FishingState.IDLE;
    private long castTimeMs;
    private long reelTimeMs;
    private long humanizeMs;
    private int catchCount;
    private boolean awaitingCastAcknowledgment;
    private Object scheduledTicket;
    private long scheduledAt;
    private boolean tooSoon; // last bite was rejected as "too soon" (for diagnostics)

    public FishingRuntime(Random random, FishingTuning tuning) {
        this.random = random;
        this.tuning = tuning;
    }

    public FishingState state() {
        return state;
    }

    /** A sent native cast must be acknowledged before another rod helper may acquire. */
    public boolean awaitingCastAcknowledgment(){return awaitingCastAcknowledgment;}

    public int catchCount() {
        return catchCount;
    }

    public boolean lastBiteTooSoon() {
        return tooSoon;
    }

    /** Same minimum-catch gate used by the reel; obstruction return runs before accounting. */
    public boolean catchReady(long nowMillis) {
        return state==FishingState.WAITING_BITE && nowMillis-castTimeMs>=tuning.minCatchMs();
    }

    /** Advance the loop one tick and return the rod action to perform (NONE/CAST/REEL). */
    public FishingAction tick(FishingInputs in) {
        if(scheduledTicket!=null)return FishingAction.NONE;
        if (!in.enabled()) {
            reset();
            return FishingAction.NONE;
        }
        if (state == FishingState.IDLE) {
            state = FishingState.NEED_CAST;
        }
        // Wait (without changing state) until a world+player+rod are ready.
        if (!in.available() || !in.hasRod()) {
            return FishingAction.NONE;
        }

        if(in.bobberPresent())awaitingCastAcknowledgment=false;
        switch (state) {
            case NEED_CAST -> {
                castTimeMs = in.nowMillis();
                tooSoon = false;
                state = FishingState.WAITING_BITE;
                // Adopt an existing cast; right-clicking here would reel it accidentally.
                awaitingCastAcknowledgment=!in.bobberPresent();
                return in.bobberPresent() ? FishingAction.NONE : FishingAction.CAST;
            }
            case WAITING_BITE -> {
                long elapsed = in.nowMillis() - castTimeMs;
                if (!in.bobberPresent() && elapsed > tuning.castTimeoutMs()) {
                    // Bobber never appeared / despawned — recast.
                    state = FishingState.NEED_CAST;
                    return FishingAction.NONE;
                }
                if (in.bobberPresent() && in.biting()) {
                    if (elapsed < tuning.minCatchMs()) {
                        tooSoon = true; // "Caught fish too soon" — keep waiting, don't reel.
                        return FishingAction.NONE;
                    }
                    tooSoon = false;
                    reelTimeMs = in.nowMillis();
                    humanizeMs = tuning.humanize() ? random.nextInt(3) * TICK_MS : 0L;
                    catchCount++;
                    state = FishingState.POST_CATCH_DELAY;
                    return FishingAction.REEL;
                }
                return FishingAction.NONE;
            }
            case POST_CATCH_DELAY -> {
                // A right-click is a request, not proof that the server removed the hook.
                // Re-adopting the still-present hook could reel it repeatedly (or recast it
                // after a delayed removal). Wait for removal before permitting another cast.
                if (in.bobberPresent()) {
                    if (in.nowMillis() - reelTimeMs > tuning.castTimeoutMs()) {
                        state = FishingState.FAILED;
                    }
                    return FishingAction.NONE;
                }
                if (in.nowMillis() - reelTimeMs >= tuning.catchDelayMs() + humanizeMs) {
                    state = FishingState.NEED_CAST;
                }
                return FishingAction.NONE;
            }
            default -> {
                return FishingAction.NONE;
            }
        }
    }

    /** A rod-owning subflow completed: adopt its current hook as a fresh cast without
     * attributing the old cast/reel timers to that hook. Session statistics survive. */
    public void resumeAfterSubflow() {
        if(scheduledTicket!=null)return;
        state=FishingState.NEED_CAST;
        castTimeMs=reelTimeMs=humanizeMs=0;
        tooSoon=false;
    }

    /** Only work/cast boundaries; a real hook or native owner is denied by the module/port. */
    public boolean scheduledBoundary(){return scheduledTicket==null&&!awaitingCastAcknowledgment&&(state==FishingState.IDLE||state==FishingState.NEED_CAST||state==FishingState.POST_CATCH_DELAY);}
    public boolean suspendScheduled(Object ticket,long now){if(ticket==null||!scheduledBoundary())return false;scheduledTicket=ticket;scheduledAt=now;return true;}
    public boolean scheduledOwnedBy(Object ticket){return ticket!=null&&scheduledTicket==ticket;}
    public boolean resumeScheduled(Object ticket,long now){
        if(!scheduledOwnedBy(ticket)||now<scheduledAt)return false;
        try{if(state==FishingState.POST_CATCH_DELAY)reelTimeMs=Math.addExact(reelTimeMs,Math.subtractExact(now,scheduledAt));}
        catch(ArithmeticException error){return false;}
        scheduledTicket=null;scheduledAt=0;return true;
    }

    /** Reset transient state (on disable / stop). */
    public void reset() {
        scheduledTicket=null;scheduledAt=0;
        state = FishingState.IDLE;
        castTimeMs = 0;
        reelTimeMs = 0;
        humanizeMs = 0;
        catchCount = 0;
        awaitingCastAcknowledgment=false;
        tooSoon = false;
    }
}
