package dev.veltraph.client.combat.heal;



















public final class HyperionAutoHeal {

    private HealState state = HealState.IDLE;
    private long startTimeMs;
    private long castTimeMs;
    private long cooldownUntilMs = Long.MIN_VALUE;
    private boolean lookedDown;
    private boolean lastBlocked;
    private int healCount;
    private boolean castPending, refusedCast;
    private final boolean requireCastOutcome;

    /** Standalone legacy tests assume the returned CAST action is emitted. */
    public HyperionAutoHeal() { this(false); }
    /** Productive orchestrator reports the actual native port result. */
    public HyperionAutoHeal(boolean requireCastOutcome) { this.requireCastOutcome = requireCastOutcome; }

    public HealState state() {
        return state;
    }

    public int healCount() {
        return healCount;
    }

    public boolean lastAttemptBlocked() {
        return lastBlocked;
    }

    /** Native readiness is already checked; preserve all later outcome/camera/window states. */
    public HealAction beginSourceAttempt(long now) {
        if (state != HealState.IDLE) return HealAction.NONE;
        startTimeMs=now;lookedDown=true;lastBlocked=false;refusedCast=castPending=false;
        state=HealState.LOOK_DOWN;return HealAction.LOOK_DOWN;
    }
    /** Reuse the existing owned camera/slot lease for the one readable repeat. */
    public boolean continueSourceAttempt() {
        if(state!=HealState.WAIT || castPending || refusedCast)return false;
        state=HealState.LOOK_DOWN;return true;
    }
    /** Source SWITCHING_SLOT_BACK after its conditional buffer; no universal millisecond wait. */
    public HealAction finishSourceBuffer(){
        if(state!=HealState.WAIT||castPending||refusedCast)return HealAction.NONE;
        state=HealState.RESTORE;return HealAction.RESTORE;
    }

    /** Resolves only an emitted CAST action; neither refusal increments the count nor arms cooldown. */
    public void resolveCast(HealCastOutcome outcome) {
        if (!castPending || state != HealState.WAIT) return;
        castPending = false;
        if (outcome == HealCastOutcome.EMITTED) {
            if (requireCastOutcome) healCount++;
        } else {
            if (!requireCastOutcome) healCount--;
            refusedCast = true;
        }
    }

    /** Predict acquisition without advancing the FSM or capturing any gameplay state. */
    public boolean wouldStart(HealInputs in, HealTuning tuning) {
        if (state != HealState.IDLE || !in.enabled() || !in.available() || !in.hasHyperion()
                || !Double.isFinite(in.healthPct()) || in.healthPct() < 0 || in.healthPct() > 100) {
            return false;
        }
        boolean emergency = in.healthPct() <= tuning.emergencyPct();
        return in.healthPct() <= tuning.activatePct()
                && (emergency || cooldownUntilMs == Long.MIN_VALUE || in.nowMillis() >= cooldownUntilMs);
    }

    /** Advance one tick and return the action to perform. */
    public HealAction tick(HealInputs in, HealTuning tuning) {
        // Disabled / unavailable: abort any in-progress heal by restoring the camera once.
        if (!in.enabled() || !in.available()) {
            if (state == HealState.RESTORE && !in.restorationComplete()) return HealAction.NONE;
            state = HealState.IDLE;
            if (lookedDown) {
                lookedDown = false;
                if (!in.restorationComplete()) state = HealState.RESTORE;
                return HealAction.RESTORE;
            }
            return HealAction.NONE;
        }

        // Hard 15 s safety window on any active attempt.
        if (state != HealState.IDLE && state != HealState.RESTORE
                && in.nowMillis() - startTimeMs > tuning.windowMs()) {
            state = HealState.RESTORE;
            return HealAction.RESTORE;
        }

        switch (state) {
            case IDLE -> {
                if (wouldStart(in, tuning)) {
                    startTimeMs = in.nowMillis();
                    lookedDown = true;
                    lastBlocked = false;
                    refusedCast = castPending = false;
                    state = HealState.LOOK_DOWN;
                    return HealAction.LOOK_DOWN;
                }
                return HealAction.NONE;
            }
            case LOOK_DOWN -> {
                // A published target is insufficient: wait for the applied camera callback.
                if (!in.cameraReady()) return HealAction.NONE;
                state = HealState.CHECK_CLEAR;
                return HealAction.NONE;
            }
            case CHECK_CLEAR -> {
                if (!in.cameraReady()) { state = HealState.LOOK_DOWN; return HealAction.NONE; }
                if (!in.clearAbove()) {
                    lastBlocked = true; // "There are blocks in the way!" — abort without casting.
                    state = HealState.RESTORE;
                    return HealAction.RESTORE;
                }
                castTimeMs = in.nowMillis();
                if (!requireCastOutcome) healCount++;
                castPending = true;
                state = HealState.WAIT;
                return HealAction.CAST;
            }
            case WAIT -> {
                if (requireCastOutcome && castPending) return HealAction.NONE;
                if (refusedCast) {
                    state = HealState.RESTORE;
                    return HealAction.RESTORE;
                }
                if (in.nowMillis() - castTimeMs >= tuning.postCastWaitMs()) {
                    state = HealState.RESTORE;
                    return HealAction.RESTORE;
                }
                return HealAction.NONE;
            }
            case RESTORE -> {
                // The RESTORE camera action was already emitted on the transition into this state; here we
                // just finalize: clear the look flag, arm the cooldown, and return to IDLE.
                if (!in.restorationComplete()) return HealAction.NONE;
                lookedDown = false;
                if (!refusedCast) cooldownUntilMs = in.nowMillis() + tuning.cooldownMs();
                castPending = false;
                state = HealState.IDLE;
                return HealAction.NONE;
            }
            default -> {
                return HealAction.NONE;
            }
        }
    }

    /** Reset transient state (on disable / world stop). */
    public void reset() {
        state = HealState.IDLE;
        lookedDown = false;
        lastBlocked = false;
        cooldownUntilMs = Long.MIN_VALUE;
        healCount = 0;
        castPending = refusedCast = false;
    }
}
