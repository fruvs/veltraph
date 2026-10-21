package dev.veltraph.client.hydra.response;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleSupplier;































public final class HydraResponseHandler {

    /** The original hardcoded fix-angle in-band half-width: the source {@code [-38, 38]} pitch band (L573). */
    private static final double PARITY_PITCH_BAND = 38.0;

    private record Scheduled(long fireAt, HydraResponseAction action) {
    }

    private final DoubleSupplier rng; // yields rolls in [0,1)
    private final Deque<Scheduled> queue = new ArrayDeque<>();
    private HydraResponseState state = HydraResponseState.LISTENING;
    private long lastResponseStartMs = Long.MIN_VALUE;
    private long disabledUntilMs = Long.MIN_VALUE;
    private boolean sourceTemporaryDisable,sourceDisableEntryPending;
    private int sourceDisableInvocations;
    /** Cached phase measurement only; it never advances a state or authorizes an action. */
    public int sourceDisableInvocations(){return sourceDisableInvocations;}
    public boolean sourceTemporaryDisable(){return sourceTemporaryDisable;}
    private long afterRespondingUntilMs = Long.MIN_VALUE;
    private long afterRespondingMs = 1L; // settle duration captured from settings on the latest check

    /** @param rng a source of {@code [0,1)} rolls; inject a deterministic supplier in tests. */
    public HydraResponseHandler(DoubleSupplier rng) {
        this.rng = rng == null ? () -> 0.0 : rng;
    }

    /** Current response state (read API for debug/HUD/status). */
    public HydraResponseState state() {
        return state;
    }

    /**
     * Move the handler from {@code INACTIVE} into {@code LISTENING} (called each tick while the watcher is
     * enabled). No-op once armed.
     */
    public void arm() {
        if (state == HydraResponseState.INACTIVE) {
            state = HydraResponseState.LISTENING;
        }
    }

    /**
     * Consider starting a response to a detected staff check / routing event. Schedules nothing and returns
     * false unless the handler is {@code LISTENING}, the per-encounter cooldown has elapsed, and the
     * (effective) policy permits a reaction.
     *
     * @param trigger      what kind of response the event warrants (+ rare flag)
     * @param reaction     the movement situation the event maps to (CAGE/BARRIER/FLOOR_CHANGE/BACKUP), or null
     * @param currentYaw   the local player's current look yaw (degrees) for fix-angle
     * @param currentPitch the local player's current look pitch (degrees) for fix-angle
     * @param now          wall clock in epoch millis
     * @param s            the response config snapshot
     * @return true if at least one action was scheduled
     */
    /** Convenience overload: a check with no movement reaction (chat/fix-angle/re-route only). */
    public boolean onCheck(HydraResponseTrigger trigger, double currentYaw, double currentPitch, long now,
                           HydraResponseSettings s) {
        return onCheck(trigger, null, currentYaw, currentPitch, now, s);
    }

    public boolean onCheck(HydraResponseTrigger trigger, HydraReaction reaction,
                           double currentYaw, double currentPitch, long now, HydraResponseSettings s) {
        boolean hasReaction = reaction != null;
        if ((trigger == null || !trigger.any()) && !hasReaction) {
            return false;
        }
        if (s == null) {
            return false;
        }
        if (trigger == null) {
            trigger = HydraResponseTrigger.fromEvent(null);
        }
        if (state != HydraResponseState.LISTENING) {
            return false; // busy / disabled — the original ignores checks unless LISTENING
        }
        if (s.cooldownActive() && lastResponseStartMs != Long.MIN_VALUE
                && now - lastResponseStartMs < Math.max(0L, s.cooldownMs())) {
            return false;
        }
        afterRespondingMs = Math.max(1L, s.afterRespondingMs());

        boolean scheduled = false;

        // Re-route (avoid-evacuation / afterdisconnect) is governed by its own toggles + rerouteAllowed(),
        // independent of the staff-check policy gate.
        if (trigger.evacuation() && s.rerouteAllowed() && s.avoidEvacuation()
                && notBlank(s.avoidEvacuationCommand())) {
            queue.addLast(new Scheduled(now, HydraResponseAction.command(strip(s.avoidEvacuationCommand()))));
            scheduled = true;
        }
        if (trigger.disconnect() && s.rerouteAllowed() && s.afterDisconnect()
                && notBlank(s.afterDisconnectCommand())) {
            queue.addLast(new Scheduled(now, HydraResponseAction.command(strip(s.afterDisconnectCommand()))));
            scheduled = true;
        }

        // Local movement reaction (CAGE/BARRIER/FLOOR_CHANGE/BACKUP) — its own master toggle + opt-in gate,
        // independent of the chat-check policy. Expanded to concrete steps by the pure planner.
        if (hasReaction && s.reactionsAllowed()) {
            List<HydraMovementStep> steps = HydraReactionPlanner.plan(reaction, s.reactionPlannerSettings());
            if (!steps.isEmpty()) {
                queue.addLast(new Scheduled(now, HydraResponseAction.move(reaction, steps)));
                scheduled = true;
            }
        }

        // The reply/REACT path runs for the proximity staff check (every enabled mode) and, in parity mode
        // only, for the confinement / floor-change checks (TELEPORT_INTO_BLOCK / FLOOR_CHANGE) — the local
        // analogues of the original non-proximity checks. Outside parity those stay passive-alert.
        boolean responseCheck = trigger.staffCheck() || (s.parityMode() && trigger.parityResponseCheck());
        if (responseCheck) {
            HydraOriginalPolicy policy = s.effectivePolicy();
            // Rare-check override forces REACT — gated by the check-class forceReactRare predicate
            // (original llllIll…()), NOT severity — and only when the response layer is opted in (so the
            // Veltraph default — UI policy OFF, parity off — stays fully silent even for rare checks).
            if (s.active() && s.alwaysReactRare() && trigger.forceReactRare()) {
                policy = HydraOriginalPolicy.REACT;
            }
            switch (policy) {
                case REACT -> scheduled |= scheduleReact(currentYaw, currentPitch, now, s, trigger.thresholdRare());
                case STOP_ALL -> {
                    queue.addLast(new Scheduled(now, HydraResponseAction.stopMacro()));
                    enterDisabled(now, s);
                    lastResponseStartMs = now;
                    return true;
                }
                case IGNORE -> {
                    if (!scheduled) {
                        enterDisabled(now, s);
                        return false;
                    }
                }
            }
        }

        if (scheduled) {
            state = HydraResponseState.ABOUT_TO_RESPOND;
            lastResponseStartMs = now;
        }
        return scheduled;
    }

    private void enterDisabled(long now, HydraResponseSettings s) {
        state = HydraResponseState.RESPONDING_TEMPORARILY_DISABLED;
        disabledUntilMs = now + Math.max(0L, s.afterRespondingMs());
        sourceTemporaryDisable=s.parityMode();sourceDisableEntryPending=sourceTemporaryDisable;sourceDisableInvocations=0;
    }

    /**
     * Schedule the REACT response. Parity mode uses the candidate pool + kind-filtered selection +
     * threshold-based escalation; non-parity uses the simpler chance-based chain. Returns whether anything
     * was scheduled.
     */
    private boolean scheduleReact(double yaw, double pitch, long now, HydraResponseSettings s,
                                  boolean thresholdRare) {
        return s.parityMode()
                ? scheduleReactParity(yaw, pitch, now, s, thresholdRare)
                : scheduleReactChance(yaw, pitch, now, s);
    }

    /**
     * Parity REACT flow (closer to the original handler):
     * <ol>
     *   <li>select the primary stage-1 reply candidate via {@link ResponseCandidates#selectReply} — it may be
     *       any reply kind ({@code GENERIC} / {@code SUBTLER_GENERIC} / {@code REALIGNMENT}), not only
     *       {@code GENERIC};</li>
     *   <li>if out of band AND the primary kind is not {@code REALIGNMENT} AND fix-angle is allowed, take the
     *       fix-angle path — select a direction-matching ({@code LOOK_UP}/{@code LOOK_DOWN}) candidate, emit
     *       its chat value (if any) + the randomized {@code FIX_ANGLE};</li>
     *   <li>otherwise reply: send the primary, and if {@link ResponseCandidates#computeExtreme} (threshold:
     *       {@code +5} avoid-evacuation, {@code -1} rare-coin) escalates, send the stage-2 / stage-3
     *       candidates.</li>
     * </ol>
     */
    private boolean scheduleReactParity(double yaw, double pitch, long now, HydraResponseSettings s,
                                        boolean thresholdRare) {
        List<HydraResponseCandidate> pool = ResponseCandidates.pool(s.templates(), s.realignmentMessages(),
                s.lookMessages(), s.barrierMessages(), s.floorChangeMessages());
        // Escalation compares the *reply* pool size (GENERIC/SUBTLER/REALIGNMENT) — never the seeded
        // LOOK_UP/LOOK_DOWN duplicates — against the sent counter (original L555 reply-pool size()).
        int replyPoolSize = ResponseCandidates.replyPoolSize(pool);
        // The "current/primary" response may be any reply kind (GENERIC / SUBTLER_GENERIC / REALIGNMENT).
        HydraResponseCandidate primary = ResponseCandidates.selectReply(pool, 1, roll());
        if (primary == null) {
            return false; // nothing to say
        }

        // Fix-angle path: out of band AND the selected response is not REALIGNMENT (a REALIGNMENT reply
        // already corrects the aim, so the separate fix-angle is skipped — reachable here at runtime).
        boolean takeFixPath = ResponseCandidates.shouldFixAngle(primary.kind(), pitch, s.fixAngleAllowed(),
                bandMin(s), bandMax(s)); // parity → hardcoded [-38,38]; non-parity → configured band
        if (takeFixPath) {
            HydraResponseAction fix = computeFixAngle(yaw, pitch, s); // 2 rolls (yaw, pitch)
            HydraResponseCandidate look = ResponseCandidates.selectForDirection(pool, pitch > 0, roll());
            queue.addLast(new Scheduled(now, fix));
            if (look != null && !look.isBlankValue()) {
                queue.addLast(new Scheduled(now, HydraResponseAction.chat(look.value())));
            }
            return true;
        }

        // Reply path with a real incrementing sent counter (mirrors the original's per-message
        // re-evaluation: reset to 0, then `extreme = pool.size() > sent + (avoidEvac?5:0) - (rareCoin?1:0)`
        // checked again after each message). The rare coin is flipped once for the encounter.
        long t = now;
        queue.addLast(new Scheduled(t, HydraResponseAction.chat(primary.value())));
        int sent = 1;
        // the rare-check 50% threshold decrement (once): gated by the check-class thresholdRare predicate
        // (original IIllll…()), NOT severity.
        boolean rareCoin = thresholdRare && roll() < 0.5;
        for (int stage = 2; stage <= 3; stage++) {
            if (!ResponseCandidates.computeExtreme(replyPoolSize, sent, s.avoidEvacuation(), rareCoin)) {
                break;
            }
            HydraResponseCandidate next = ResponseCandidates.selectReply(pool, stage, roll());
            if (next == null) {
                break;
            }
            t += delay(s);
            queue.addLast(new Scheduled(t, HydraResponseAction.chat(next.value())));
            sent++;
        }
        return true;
    }

    /**
     * Non-parity REACT flow: an optional fix-angle action (when the pitch is out of band) followed by the
     * 1–3 message chat reply chain (chance-based second/third + the {@code extreme} toggle), humanized
     * {@code 50..2000 ms} delays.
     */
    private boolean scheduleReactChance(double yaw, double pitch, long now, HydraResponseSettings s) {
        boolean scheduled = false;
        boolean fixScheduled = false;
        if (s.fixAngleAllowed()) {
            HydraResponseAction fix = computeFixAngle(yaw, pitch, s);
            if (fix != null) {
                queue.addLast(new Scheduled(now, fix));
                fixScheduled = true;
                scheduled = true;
            }
        }
        ResponseTemplates templates = s.templates();
        String first = ResponseTemplates.pick(templates.first(), roll());
        if (first != null) {
            long t = fixScheduled ? now + delay(s) : now;
            queue.addLast(new Scheduled(t, HydraResponseAction.chat(first)));
            scheduled = true;
            boolean forceMore = s.extreme();
            if (forceMore || roll() < clamp01(s.secondChance())) {
                String second = ResponseTemplates.pick(templates.second(), roll());
                if (second != null) {
                    t += delay(s);
                    queue.addLast(new Scheduled(t, HydraResponseAction.chat(second)));
                    if (forceMore || roll() < clamp01(s.thirdChance())) {
                        String third = ResponseTemplates.pick(templates.third(), roll());
                        if (third != null) {
                            t += delay(s);
                            queue.addLast(new Scheduled(t, HydraResponseAction.chat(third)));
                        }
                    }
                }
            }
        }
        return scheduled;
    }

    /**
     * Compute the fix-angle action, or null when no fix is needed. In-band pitch ({@code [min,max]}) is left
     * alone in both modes (original L573 {@code if (!(p < -38) && !(p > 38)) skip}).
     *
     * <p><b>Parity mode</b> reproduces the original target math exactly (verified against the readable
     * fragments of {@code IllllII…java}): the in-band gate is the <b>hardcoded original {@code [-38,38]}
     * band</b> (L573) — it ignores the GUI {@code FIX_ANGLE_MIN_PITCH}/{@code MAX_PITCH} so custom bounds
     * cannot alter the source math — then yaw {@code = curYaw + rand(-20,20)} (L691) and pitch
     * {@code = (pitch < 0 ? -18 : 18) + rand(-10,10)} (L693/L700) — a fixed ±18° base by look direction,
     * not a re-entry into the configured band. (The yaw/pitch jitter ranges are fixed in the original, so
     * parity ignores the configurable {@code fixAngleYawJitter} / {@code fixAngleReentryFraction}.)
     *
     * <p><b>Non-parity (Veltraph-shaped)</b> keeps the configurable behaviour: the target <i>lands inside the
     * configured band</i> (so the GUI min/max keys shape the result, not just gate it) — re-entering from the
     * violated edge by {@code fixAngleReentryFraction} of the band width toward neutral, with a randomized
     * yaw offset {@code ±fixAngleYawJitter}.
     *
     * <p>Both consume two rolls (yaw then pitch) in the original order. The smooth {@link HydraLook} execution
     * is unchanged in either mode — the target math here does not reintroduce snapping. The REALIGNMENT
     * exemption and the {@link HydraResponseKind#LOOK_UP}/{@link HydraResponseKind#LOOK_DOWN} candidate
     * filtering are applied by {@link #scheduleReactParity}; this method only computes the angle.
     */
    private HydraResponseAction computeFixAngle(double yaw, double pitch, HydraResponseSettings s) {
        if (Double.isNaN(pitch)) {
            return null;
        }
        double min = bandMin(s);
        double max = bandMax(s);
        if (pitch >= min && pitch <= max) {
            return null; // in band — no fix (parity: hardcoded [-38,38]; non-parity: configured band)
        }
        if (s.parityMode()) {
            // Exact original math: yaw + rand(-20,20); (sign*18) + rand(-10,10), sign by look direction.
            double yawTarget = yaw + range(-20.0, 20.0);       // roll 1 (yaw)
            double pitchBase = pitch < 0 ? -18.0 : 18.0;
            double pitchTarget = pitchBase + range(-10.0, 10.0); // roll 2 (pitch)
            return HydraResponseAction.fixAngle(yawTarget, pitchTarget);
        }
        // Non-parity: configurable re-entry into the band.
        double span = Math.max(0.0, max - min);
        double frac = clamp01(s.fixAngleReentryFraction());
        double jit = Math.abs(s.fixAngleYawJitter());
        double yawTarget = yaw + range(-jit, jit); // roll 1 (yaw)
        double pitchTarget = pitch > max
                ? max - range(0.0, span * frac)
                : min + range(0.0, span * frac); // roll 2 (pitch)
        return HydraResponseAction.fixAngle(yawTarget, pitchTarget);
    }

    /**
     * The lower edge of the fix-angle in-band "safe" zone for this tick: the <b>hardcoded original
     * {@code -38}</b> in parity mode (GUI bounds ignored — keeps the source math exact), otherwise the
     * configured {@code FIX_ANGLE_MIN_PITCH}/{@code MAX_PITCH} (min of the two).
     */
    static double bandMin(HydraResponseSettings s) {
        return s.parityMode() ? -PARITY_PITCH_BAND : Math.min(s.minPitch(), s.maxPitch());
    }

    /** The upper edge of the fix-angle in-band zone: hardcoded {@code +38} in parity, else configured (max). */
    static double bandMax(HydraResponseSettings s) {
        return s.parityMode() ? PARITY_PITCH_BAND : Math.max(s.minPitch(), s.maxPitch());
    }

    /**
     * Drain and return the actions whose scheduled time has arrived (fireAt &le; now), in time order, and
     * advance the state machine. Call once per client tick; returns an empty list when nothing is due.
     */
    public List<HydraResponseAction> tick(long now) {
        // timed state transitions first
        if(state==HydraResponseState.RESPONDING_TEMPORARILY_DISABLED){
            if(sourceTemporaryDisable){
                // The invocation entering the disabled phase leaves the recovered counter at zero.
                if(sourceDisableEntryPending)sourceDisableEntryPending=false;
                else {
                    var decision=dev.veltraph.client.hydra.source.SourceHydraLocalDispatch.tick(
                            dev.veltraph.client.hydra.source.SourceHydraLocalDispatch.State.RESPONDING_TEMPORARILY_DISABLED,
                            sourceDisableInvocations,0,dev.veltraph.client.hydra.source.SourceHydraLocalDispatch.Policy.IGNORE,false,false,false);
                    sourceDisableInvocations=decision.counter();
                    if(decision.action()==dev.veltraph.client.hydra.source.SourceHydraLocalDispatch.Action.CLEAR_LOCAL_STATE&&queue.isEmpty()){
                        state=HydraResponseState.LISTENING;sourceTemporaryDisable=false;
                    }
                }
            }else if(now>=disabledUntilMs&&queue.isEmpty())state=HydraResponseState.LISTENING;
        }
        if (state == HydraResponseState.AFTER_RESPONDING && now >= afterRespondingUntilMs) {
            state = HydraResponseState.LISTENING;
        }

        List<HydraResponseAction> due = new ArrayList<>();
        boolean drainedResponse = false;
        while (!queue.isEmpty() && queue.peekFirst().fireAt() <= now) {
            HydraResponseAction a = queue.pollFirst().action();
            due.add(a);
            switch (a.kind()) {
                case FIX_ANGLE -> {
                    state = HydraResponseState.FIX_PITCH;
                    drainedResponse = true;
                }
                case CHAT, COMMAND, MOVE -> {
                    state = HydraResponseState.RESPONDING;
                    drainedResponse = true;
                }
                case STOP_MACRO -> { /* stays RESPONDING_TEMPORARILY_DISABLED */ }
            }
        }
        // Chain finished → settle in AFTER_RESPONDING, then re-arm to LISTENING.
        if (drainedResponse && queue.isEmpty()
                && (state == HydraResponseState.FIX_PITCH || state == HydraResponseState.RESPONDING
                    || state == HydraResponseState.ABOUT_TO_RESPOND)) {
            state = HydraResponseState.AFTER_RESPONDING;
            afterRespondingUntilMs = now + afterRespondingMs; // settle before re-arming to LISTENING
        }
        return due;
    }

    /** Whether a response chain is currently scheduled (read API). */
    public boolean isResponding() {
        return !queue.isEmpty();
    }

    /** Number of actions still pending (read API). */
    public int pendingCount() {
        return queue.size();
    }

    /** Clear all pending actions + timers and mark the response layer INACTIVE (used on disable / world stop). */
    public void reset() {
        queue.clear();
        lastResponseStartMs = Long.MIN_VALUE;
        disabledUntilMs = Long.MIN_VALUE;
        sourceTemporaryDisable=false;sourceDisableEntryPending=false;sourceDisableInvocations=0;
        afterRespondingUntilMs = Long.MIN_VALUE;
        state = HydraResponseState.INACTIVE;
    }

    private long delay(HydraResponseSettings s) {
        long min = Math.max(0L, s.minDelayMs());
        long max = Math.max(min, s.maxDelayMs());
        if (max == min) {
            return min;
        }
        return min + (long) (roll() * (max - min));
    }

    /** A uniform sample in {@code [lo,hi)} from one roll. */
    private double range(double lo, double hi) {
        return lo + roll() * (hi - lo);
    }

    private double roll() {
        double r = rng.getAsDouble();
        if (Double.isNaN(r) || r < 0) {
            return 0.0;
        }
        return r >= 1 ? 0.9999999 : r;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) {
            return 0;
        }
        return Math.min(1.0, v);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String strip(String command) {
        String c = command.trim();
        while (c.startsWith("/")) {
            c = c.substring(1).trim();
        }
        return c;
    }
}
