package dev.veltraph.client.hydra;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.HydraConfig;
import dev.veltraph.client.failsafe.response.FailsafeResponseController;
import dev.veltraph.client.failsafe.response.FailsafeResponseType;
import dev.veltraph.client.hydra.response.HydraOriginalPolicy;
import dev.veltraph.client.hydra.response.HydraReaction;
import dev.veltraph.client.hydra.response.HydraReactionTrigger;
import dev.veltraph.client.hydra.response.HydraResponseAction;
import dev.veltraph.client.hydra.response.HydraResponseHandler;
import dev.veltraph.client.hydra.response.HydraResponsePolicy;
import dev.veltraph.client.hydra.response.HydraResponseSettings;
import dev.veltraph.client.hydra.response.HydraResponseState;
import dev.veltraph.client.hydra.response.HydraResponseTrigger;
import dev.veltraph.client.log.VeltraphLog;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;































public final class HydraAntiStaff {

    public static final String CHAT_PREFIX = "[Veltraph Anti-Staff] ";

    private final ConfigService config;
    private final HydraDetector detector;
    private final Consumer<String> chat;       // local client-side chat only
    private final Runnable stopMacro;          // ModuleManager.stopAll()
    private final Runnable releaseInput;       // ScriptingInputLock.releaseAll()
    private final HydraAlertSink alertSink;    // local title + sound
    private final HydraResponder responder;    // LOCAL_INCLUDE staff-response executor (vanilla in-game)
    private final HydraResponseHandler responseHandler; // pure auto-reply/fix-angle/re-route orchestrator

    /** Whether a Veltraph macro was running on the last tick — the global runtime gate (see {@link #tick}). */
    private boolean macroActive;

    /** Stop/release after an enabled failsafe automated-response routine finishes. */
    private boolean stopAfterFailsafeResponse;

    /**
     * Optional local failsafe automated-response controller (clean-room LOOK/MOVE/JUMP/SNEAK/clicks routine),
     * wired by the bootstrap after construction. Null in unit tests that don't exercise it. Scheduled only
     * from the macro-gated {@link #runResponse} path; advanced/reset alongside the responder.
     */
    private FailsafeResponseController failsafeResponse;
    private HydraResponseSettings capturedResponsePermissions;
    private Runnable generalPolicyCompletion;
    private FailsafeResponseType generalResponseType;
    private java.util.function.BooleanSupplier externalControlOwned = () -> false;

    /** Root binds its actual higher-priority helper; observation continues, response execution yields. */
    public void externalControlOwned(java.util.function.BooleanSupplier owned) {
        externalControlOwned = java.util.Objects.requireNonNull(owned);
    }
    private boolean externalOwned() {
        try { return externalControlOwned.getAsBoolean(); }
        catch (RuntimeException unavailable) { return true; }
    }
    public boolean observingExternalControl() { return externalOwned(); }
    private void cancelOwnedResponses() {
        if (responseHandler.isResponding() || responder.hasActiveLocalReaction()
                || generalPolicyCompletion != null || stopAfterFailsafeResponse
                || (failsafeResponse != null && failsafeResponse.active())) resetResponses();
    }

    /** Root callback for general VOID/MENTION/player/suspicious triggers, independent of Hydra opt-in. */
    public boolean beginGeneralResponse(boolean scripting, FailsafeResponseType type, Runnable policyCompletion) {
        boolean emergencyVoid = type == FailsafeResponseType.VOID;
        if (externalOwned() || (!scripting && !emergencyVoid) || failsafeResponse == null || generalPolicyCompletion != null || failsafeResponse.active()
                || type == null || policyCompletion == null) return false;
        if (!(emergencyVoid ? failsafeResponse.onEmergencyVoid() : failsafeResponse.onTrigger(type))) return false;
        responseHandler.reset();
        responder.reset();
        capturedResponsePermissions = null;
        generalPolicyCompletion = policyCompletion;
        generalResponseType = type;
        return true;
    }

    /** Source identity only; unknown implementations are rejected by the native Root proof. */
    public Object nativeResponderIdentity(){return responder;}
    /** Callback-free actual private response fields. Does not invoke the responder interface. */
    public boolean nativeIdleCurrent(Object expectedResponder){return expectedResponder!=null&&responder==expectedResponder
        &&generalPolicyCompletion==null&&(failsafeResponse==null||!failsafeResponse.active());}

    public boolean hasActiveGeneralResponse() { return generalPolicyCompletion != null && failsafeResponse != null && failsafeResponse.active(); }
    public boolean cancelGeneralResponse(FailsafeResponseType type) {
        if (generalPolicyCompletion == null || generalResponseType != type) return false;
        resetResponses();
        return true;
    }
    /** Root uses this exact owned path only; no detector, reply chain, or unrelated developer routine advances. */
    public void tickGeneralResponse() {
        if (externalOwned()) { cancelOwnedResponses(); return; }
        if (generalPolicyCompletion != null) advanceFailsafe();
    }

    /** Wire the local failsafe automated-response controller (bootstrap only; may be null). */
    public void setFailsafeResponseController(FailsafeResponseController controller) {
        this.failsafeResponse = controller;
    }

    /** Map a fired anti-staff event type to the failsafe response trigger it should drive, or null. */
    private static FailsafeResponseType failsafeTypeFor(HydraEvent.Type type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case STAFF_NEARBY -> FailsafeResponseType.PLAYER_NEARBY;
            case TELEPORT_ANOMALY -> FailsafeResponseType.BOSS_TELEPORTED;
            case TELEPORT_INTO_BLOCK -> FailsafeResponseType.TELEPORTED_INTO_BLOCK;
            case INVALID_MOVEMENT -> FailsafeResponseType.INVALID_MOVEMENT;
            case FORCED_ADVENTURE -> FailsafeResponseType.FORCED_ADVENTURE;
            case ROUTING_TEXT -> FailsafeResponseType.ROUTING_TEXT;
            case LIMBO_ROUTING -> FailsafeResponseType.LIMBO_ROUTING;
            case FLOOR_CHANGE -> FailsafeResponseType.FLOOR_CHANGE;
            case BARRIER -> FailsafeResponseType.BARRIER;
            case WORLD_ANOMALY -> FailsafeResponseType.REPEATED_SUSPICIOUS;
        };
    }

    public HydraAntiStaff(ConfigService config,
                          Consumer<String> chat,
                          Runnable stopMacro,
                          Runnable releaseInput,
                          HydraAlertSink alertSink) {
        this(config, chat, stopMacro, releaseInput, alertSink, HydraResponder.NONE, () -> 0.0);
    }

    public HydraAntiStaff(ConfigService config,
                          Consumer<String> chat,
                          Runnable stopMacro,
                          Runnable releaseInput,
                          HydraAlertSink alertSink,
                          HydraResponder responder,
                          DoubleSupplier rng) {
        this(config, new HydraDetector(), chat, stopMacro, releaseInput, alertSink, responder, rng);
    }

    HydraAntiStaff(ConfigService config,
                   HydraDetector detector,
                   Consumer<String> chat,
                   Runnable stopMacro,
                   Runnable releaseInput,
                   HydraAlertSink alertSink,
                   HydraResponder responder,
                   DoubleSupplier rng) {
        this.config = config;
        this.detector = detector;
        this.chat = chat;
        this.stopMacro = stopMacro;
        this.releaseInput = releaseInput;
        this.alertSink = alertSink == null ? HydraAlertSink.NONE : alertSink;
        this.responder = responder == null ? HydraResponder.NONE : responder;
        this.responseHandler = new HydraResponseHandler(rng);
    }

    /** Build a fresh settings snapshot from config (used by the adapter when assembling a tick). */
    public HydraSettings settings() {
        return HydraSettings.fromConfig(config);
    }

    /** Build the whitelist from config (used by the adapter when assembling a tick). */
    public Whitelist whitelist() {
        return Whitelist.parse(config.get(HydraConfig.LOCAL_WHITELIST));
    }

    /**
     * Run the detector for one tick and apply any local reaction. Returns the result (for diagnostics /
     * HUD). Side effects (chat/title/sound/stop) are all local.
     *
     * <p><b>Global macro gate.</b> Hydra is entirely inert unless a Veltraph macro is running
     * ({@link HydraTick#scripting()}). With {@code scripting=false} nothing is detected, no alert/stop/chat/
     * command/fix-angle/move/route fires, and no queued or smoothly-eased response work advances. On the
     * macro-running → no-macro edge all transient state is reset (detector anomaly baselines + proximity
     * machine + last-check, the pure response queue/state, and the smooth look + movement runner, releasing
     * any held input). The watcher's own {@code enabled} switch still applies on top while scripting.
     */
    public HydraResult tick(HydraTick t) {
        if (externalOwned()) {
            cancelOwnedResponses();
            if (t == null) return HydraResult.of(detector.state(), null, detector.nearestPlayerName());
            // The external helper is itself active automation. This flag is for detection only;
            // it never grants a reply/input/command executor ownership.
            HydraTick observed = t.scripting() ? t : new HydraTick(t.settings(), t.whitelist(), t.nowMillis(),
                    t.nearbyPlayers(), true, t.worldPresent(), t.area(), t.tickMoveDistance(), t.dimensionChanged(),
                    t.lagbackDistance(), t.teleportEnclosed(), t.gameMode(), t.routingTextMatch(), t.floorChanged(),
                    t.lookPitch(), t.lookYaw(), t.barrierDetected());
            macroActive = true;
            HydraResult result = detector.tick(observed);
            // This branch captures the external veto for the whole tick. Passive STOP remains
            // authoritative even if Root's stop callback releases the external owner.
            result.event().ifPresent(event -> react(event, false));
            return result;
        }
        boolean scripting = t != null && t.scripting();
        if (!scripting && generalPolicyCompletion != null && generalResponseType == FailsafeResponseType.VOID) {
            if (t == null || !t.worldPresent()) resetResponses();
            return HydraResult.of(detector.state(), null, detector.nearestPlayerName());
        }
        if (!scripting) {
            if (macroActive || generalPolicyCompletion != null) {
                // Edge: a macro just stopped → reset all transient Hydra state so nothing lingers.
                detector.reset();
                responseHandler.reset();
                responder.reset(); // stop smooth look + movement runner, release any held input
                if (failsafeResponse != null) {
                    failsafeResponse.reset(); // abort any running failsafe routine + release its input
                }
                stopAfterFailsafeResponse = false;
                generalPolicyCompletion = null;
                generalResponseType = null;
                capturedResponsePermissions = null;
                macroActive = false;
                VeltraphLog.basic("Hydra", "macro stopped — anti-staff inert (no macro)");
            }
            // Inert: no detection, no reaction, no eased/queued work. HUD reads the idle detector state.
            return HydraResult.of(detector.state(), null, detector.nearestPlayerName());
        }
        macroActive = true;
        if (generalPolicyCompletion != null) {
            // Camera ownership cannot hide a newly observed movement/world anomaly. Keep baselines and
            // detection live; a more severe event aborts the general routine before applying its policy.
            HydraResult result = detector.tick(t);
            if (result.event().isPresent()) {
                HydraEvent event = result.event().get();
                if (event.type() != HydraEvent.Type.STAFF_NEARBY) {
                    resetResponses();
                    react(event, false);
                    return result;
                }
                react(event, true);
            }
            runResponse(t, result);
            return result;
        }
        HydraResult result = detector.tick(t);
        result.event().ifPresent(ev -> {
            // Schedule the local failsafe routine before any auto-stop side effect. When one actually starts,
            // the stop is deferred until the short routine finishes so it can visibly run under the macro gate.
            boolean deferStop = tryStartFailsafeResponse(ev);
            react(ev, deferStop);
        });
        runResponse(t, result);
        return result;
    }

    /**
     * Drive the LOCAL_INCLUDE staff-response layer: when a relevant event fired, consider starting an
     * automated reaction (chat reply chain / fix-angle / re-route), then execute any actions that are due
     * this tick via the {@link HydraResponder}. All local + in-game; gated by {@link HydraResponsePolicy}
     * (default OFF). When the watcher is disabled, pending reactions are dropped.
     */
    private void runResponse(HydraTick t, HydraResult result) {
        if (generalPolicyCompletion != null) {
            advanceFailsafe();
            return;
        }
        if (!t.settings().enabled()) {
            // Watcher disabled while a macro runs: tear down any in-flight response ONCE, then stay quiet —
            // no per-tick reset spam. Reaching here with nothing pending means it's already clean.
            if (responseHandler.state() != HydraResponseState.INACTIVE || responseHandler.isResponding()
                    || responder.hasActiveLocalReaction()) {
                responseHandler.reset();
                responder.reset(); // abort any in-flight smooth look / movement and release held inputs
            }
            if (failsafeResponse != null && failsafeResponse.active()) {
                failsafeResponse.reset();
            }
            stopAfterFailsafeResponse = false;
            capturedResponsePermissions = null;
            return;
        }
        // One camera/input owner: an automated failsafe preempts the lower-priority response.
        if (failsafeResponse != null && failsafeResponse.active()) {
            advanceFailsafe();
            return;
        }
        responseHandler.arm();
        HydraResponseSettings rs = HydraResponseSettings.fromConfig(config);
        if (rs.revokes(capturedResponsePermissions)) {
            responseHandler.reset();
            responder.reset();
            capturedResponsePermissions = null;
            responseHandler.arm();
        }
        result.event().ifPresent(ev -> {
            responseHandler.onCheck(HydraResponseTrigger.fromEvent(ev),
                    HydraReactionTrigger.fromType(ev.type()), t.lookYaw(), t.lookPitch(),
                    t.nowMillis(), rs);
        });
        if(responseHandler.isResponding()||responseHandler.state()==HydraResponseState.RESPONDING_TEMPORARILY_DISABLED)capturedResponsePermissions=rs;
        for (HydraResponseAction a : responseHandler.tick(t.nowMillis())) {
            VeltraphLog.basic("HydraResponse", "executing " + a.kind() + describe(a));
            if (a.kind() == HydraResponseAction.Kind.STOP_MACRO) {
                if (stopMacro != null) {
                    stopMacro.run();
                }
                if (releaseInput != null) {
                    releaseInput.run();
                }
            } else {
                responder.execute(a);
            }
        }
        // Advance any in-progress smooth fix-angle ease / movement reaction every tick (independent of
        // whether a new action fired this tick).
        responder.tick();
        if(!responseHandler.isResponding()&&!responder.hasActiveLocalReaction()
                &&responseHandler.state()!=HydraResponseState.RESPONDING_TEMPORARILY_DISABLED)capturedResponsePermissions=null;
        if (failsafeResponse != null) {
            advanceFailsafe();
        }
        finishDeferredStopIfReady();
    }

    private boolean tryStartFailsafeResponse(HydraEvent event) {
        if (event == null || failsafeResponse == null) {
            return false;
        }
        boolean started;
        try { started = failsafeResponse.onTrigger(failsafeTypeFor(event.type())); }
        catch (RuntimeException failure) {
            VeltraphLog.basic("Failsafe", "Response start failed; applying passive policy: " + failure.getClass().getSimpleName());
            return false;
        }
        if (started) {
            responseHandler.reset();
            responder.reset();
        }
        return started;
    }

    private void finishDeferredStopIfReady() {
        if (!stopAfterFailsafeResponse || (failsafeResponse != null && failsafeResponse.active())) {
            return;
        }
        stopAfterFailsafeResponse = false;
        VeltraphLog.basic("HydraAntiStaff.react", "auto-stopping macros after failsafe response");
        if (stopMacro != null) {
            stopMacro.run();
        }
        if (releaseInput != null) {
            releaseInput.run();
        }
    }

    private void advanceFailsafe() {
        try {
            failsafeResponse.tick();
        } finally {
            if (!failsafeResponse.active() && generalPolicyCompletion != null) {
                Runnable completion = generalPolicyCompletion;
                generalPolicyCompletion = null;
                generalResponseType = null;
                completion.run();
            }
            finishDeferredStopIfReady();
        }
    }

    private static String describe(HydraResponseAction a) {
        return switch (a.kind()) {
            case FIX_ANGLE -> " yaw=" + fmt(a.yaw()) + " pitch=" + fmt(a.pitch());
            case MOVE -> " reaction=" + a.reaction() + " steps=" + a.steps().size();
            case STOP_MACRO -> "";
            default -> " '" + a.text() + "'";
        };
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "keep" : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private void react(HydraEvent event) {
        react(event, false);
    }

    private void react(HydraEvent event, boolean deferStop) {
        VeltraphLog.basic("Hydra", "anti-staff event: " + event.type() + " — " + event.message()
                + (event.stopRequested() ? " (auto-stop)" : ""));
        // (a) local chat — never sent to the server
        if (chat != null) {
            chat.accept(CHAT_PREFIX + event.message());
        }
        HydraSettings s = settings();
        // (b) optional local title
        if (s.title()) {
            alertSink.showTitle("Anti-Staff", event.message());
        }
        // (c) optional single local sound ping
        if (s.sound()) {
            alertSink.playPing();
        }
        // (d) optional auto-stop + release input ownership
        if (event.stopRequested()) {
            if (deferStop) {
                stopAfterFailsafeResponse = true;
                VeltraphLog.basic("HydraAntiStaff.react",
                        "deferring macro stop until failsafe response completes: " + event.message());
                return;
            }
            VeltraphLog.basic("HydraAntiStaff.react", "auto-stopping macros: " + event.message());
            if (stopMacro != null) {
                stopMacro.run();
            }
            if (releaseInput != null) {
                releaseInput.run();
            }
        }
    }

    // ---- local developer test-harness hooks (no network) ----

    /** Build a synthetic event of {@code type} with representative params (dev test-harness only). */
    private HydraEvent synthetic(HydraEvent.Type type, boolean stop) {
        return switch (type) {
            case STAFF_NEARBY ->
                    HydraEvent.staffNearby("TestStaff", alertDistanceBlocks(), HydraSeverity.DANGEROUS, stop);
            case TELEPORT_ANOMALY -> HydraEvent.teleportAnomaly(12.0, stop);
            case WORLD_ANOMALY -> HydraEvent.worldAnomaly(stop);
            case LIMBO_ROUTING -> HydraEvent.limboRouting(stop, "Limbo (test)");
            case INVALID_MOVEMENT -> HydraEvent.invalidMovement(3.0, stop);
            case TELEPORT_INTO_BLOCK -> HydraEvent.teleportIntoBlock(15.0, stop);
            case FORCED_ADVENTURE -> HydraEvent.forcedAdventure(stop);
            case ROUTING_TEXT -> HydraEvent.routingText("evacuat", stop);
            case FLOOR_CHANGE -> HydraEvent.floorChange(stop);
            case BARRIER -> HydraEvent.barrier(stop);
        };
    }

    /**
     * Dev test-harness: run the real local anti-staff alert path for a synthetic event (no auto-stop), so
     * the failsafe/Hydra alert reactions can be exercised without real staff. Local only (chat/title/sound).
     */
    public String simulateAlert(HydraEvent.Type type) {
        HydraEvent ev = synthetic(type, false);
        react(ev);
        HydraSettings s = settings();
        return ev.type() + "/" + ev.severity() + " alert [chat"
                + (s.title() ? "+title" : "") + (s.sound() ? "+sound" : "") + "] — " + ev.message();
    }

    /**
     * Dev test-harness: classify a synthetic event's rare/parity predicates + response eligibility for the
     * current parity mode (local; emits booleans + reason only, never message-list contents).
     */
    public String describeClassification(HydraEvent.Type type) {
        return classifyCheck(synthetic(type, false), config.get(HydraConfig.HYDRA_PARITY_MODE));
    }

    /** Dev test-harness: run the fix-angle responder action (local camera only, smooth ease). */
    public void runFixAngle(double yaw, double pitch) {
        if (generalPolicyCompletion != null || externalOwned()) return;
        responder.fixAngle(yaw, pitch);
    }

    /**
     * Dev test-harness: begin a local movement reaction ({@link HydraReaction}) using the configured
     * durations, regardless of the master toggle (so it can be exercised without staff). Local-only — drives
     * the vanilla movement inputs; {@link HydraResponder#tick()} advances it over the following ticks.
     */
    public String runReaction(HydraReaction reaction) {
        if (generalPolicyCompletion != null || externalOwned()) return "exclusive control currently owns input";
        if (reaction == null) {
            return "no reaction";
        }
        HydraResponseSettings rs = HydraResponseSettings.fromConfig(config);
        java.util.List<dev.veltraph.client.hydra.response.HydraMovementStep> steps =
                dev.veltraph.client.hydra.response.HydraReactionPlanner.plan(reaction, rs.reactionPlannerSettings());
        responder.move(reaction, steps);
        return reaction + " reaction queued (" + steps.size() + " step"
                + (steps.size() == 1 ? "" : "s") + ", local movement)";
    }

    /**
     * Dev test-harness: queue a local failsafe automated-response plan for a trigger type (bypasses the
     * master/per-trigger gate + cooldown so the harness works without staff). Local only; advanced by the
     * dev-test tick path. Returns a summary, or a no-op note if the controller is unwired.
     */
    public String runFailsafeResponse(FailsafeResponseType type) {
        if (generalPolicyCompletion != null || externalOwned()) return "exclusive control currently owns input";
        return failsafeResponse == null ? "failsafe response unavailable" : failsafeResponse.devRun(type);
    }

    /** Dev test-harness: queue a focused failsafe routine (look sweep / jump+sneak / clicks / move / full). */
    public String runFailsafeRoutine(dev.veltraph.client.failsafe.response.FailsafeRoutine routine) {
        if (generalPolicyCompletion != null || externalOwned()) return "exclusive control currently owns input";
        return failsafeResponse == null ? "failsafe response unavailable" : failsafeResponse.devRunRoutine(routine);
    }

    /** A one-line status for the local failsafe automated-response routine ({@code /vdebug failsafe}). */
    public String failsafeResponseStatus() {
        return failsafeResponse == null ? "Failsafe response: unavailable" : failsafeResponse.status();
    }

    /** Dev test-harness / tick: advance any in-flight smooth look / movement reaction one tick. */
    public void tickResponder() {
        if (externalOwned()) { cancelOwnedResponses(); return; }
        if (generalPolicyCompletion != null) return;
        if (failsafeResponse != null && failsafeResponse.active()) {
            if (responder.hasActiveLocalReaction()) responder.reset();
            failsafeResponse.tick();
        } else {
            responder.tick();
        }
    }

    /**
     * Whether a local look or movement reaction (Hydra responder OR failsafe routine) is currently in flight
     * (read API). The Developer Mode dev-test tick path uses this to advance an explicitly dev-queued
     * reaction <b>only while active</b>, so the path is bounded and never runs detection/auto-response — that
     * stays macro-gated in {@link #tick}.
     */
    public boolean hasActiveLocalReaction() {
        return responder.hasActiveLocalReaction() || (failsafeResponse != null && failsafeResponse.active());
    }

    // ---- HUD read API ----

    public boolean enabled() {
        return config.get(HydraConfig.STAFF_ALERT_ENABLED);
    }

    public HydraState state() {
        return detector.state();
    }

    public HydraEvent lastEvent() {
        return detector.lastEvent();
    }

    public String nearestPlayerName() {
        return detector.nearestPlayerName();
    }

    public double alertDistanceBlocks() {
        return config.get(HydraConfig.STAFF_ALERT_DISTANCE_BLOCKS);
    }

    /** Enable/disable the watcher (persisted by the caller). Resets transient state on a change. */
    public void setEnabled(boolean on) {
        boolean was = enabled();
        config.set(HydraConfig.STAFF_ALERT_ENABLED, on);
        if (was != on) {
            detector.reset();
            responseHandler.reset();
            responder.reset();
            if (failsafeResponse != null && generalPolicyCompletion == null) {
                failsafeResponse.reset(); // abort any running failsafe routine + release its input
            }
            stopAfterFailsafeResponse = false;
            capturedResponsePermissions = null;
            VeltraphLog.basic("Hydra", "anti-staff " + (on ? "enabled" : "disabled"));
        }
    }

    /** The current Veltraph UI response policy (read API / status). */
    public HydraResponsePolicy responsePolicy() {
        return config.get(HydraConfig.STAFF_RESPONSE_POLICY);
    }

    /** The current response state-machine state (read API for debug/HUD/status). */
    public HydraResponseState responseState() {
        return responseHandler.state();
    }

    /**
     * The effective original-policy concept for the current config (REACT/STOP_ALL/IGNORE): the configured
     * {@code HYDRA_ORIGINAL_POLICY} in parity mode, otherwise the UI policy mapped onto the original concept.
     */
    public HydraOriginalPolicy effectiveOriginalPolicy() {
        return HydraResponseSettings.fromConfig(config).effectivePolicy();
    }

    /**
     * A one-line summary of the LOCAL_INCLUDE staff-response config (for {@code /vdebug hydra response}).
     * All local + in-game; reflects the live policy, parity mode, the effective original-policy concept,
     * and the per-behaviour toggles.
     */
    public String responseStatus() {
        HydraResponsePolicy p = responsePolicy();
        boolean parity = config.get(HydraConfig.HYDRA_PARITY_MODE);
        HydraOriginalPolicy eff = effectiveOriginalPolicy();
        boolean fixAllowed = (parity ? eff == HydraOriginalPolicy.REACT : p.allowsFixAngle())
                && config.get(HydraConfig.FIX_ANGLE_ENABLED);
        boolean rerouteAllowed = parity ? eff == HydraOriginalPolicy.REACT : p.allowsReroute();
        return "Anti-Staff response: ui-policy=" + p
                + " | parity=" + onOff(parity)
                + " | effective=" + eff
                + " | state=" + responseState()
                + " | rare-always-react=" + onOff(config.get(HydraConfig.HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS))
                + " | parity-templates=" + onOff(parity || config.get(HydraConfig.PARITY_TEMPLATES_ENABLED))
                + " | auto-reply=" + onOff(eff == HydraOriginalPolicy.REACT)
                + (config.get(HydraConfig.AUTO_REPLY_EXTREME) ? " (extreme)" : "")
                + " | fix-angle=" + onOff(fixAllowed)
                + " band[" + config.get(HydraConfig.FIX_ANGLE_MIN_PITCH) + ".."
                + config.get(HydraConfig.FIX_ANGLE_MAX_PITCH) + "]"
                + " reentry=" + config.get(HydraConfig.FIX_ANGLE_REENTRY_FRACTION)
                + " yaw-jitter=" + config.get(HydraConfig.FIX_ANGLE_YAW_JITTER)
                + " (smooth-ease)"
                + " | move-reactions=" + onOff(config.get(HydraConfig.MOVEMENT_REACTIONS_ENABLED))
                + " (CAGE=" + config.get(HydraConfig.REACTION_CAGE_TICKS)
                + " BARRIER=" + config.get(HydraConfig.REACTION_BARRIER_TICKS)
                + " FLOOR=" + config.get(HydraConfig.REACTION_FLOOR_CHANGE_TICKS)
                + " BACKUP=" + config.get(HydraConfig.REACTION_BACKUP_TICKS) + "t)"
                + " | avoid-evac=" + onOff(rerouteAllowed && config.get(HydraConfig.AVOID_EVACUATION_ENABLED))
                + " (" + emptyDash(config.get(HydraConfig.AVOID_EVACUATION_COMMAND)) + ")"
                + " | after-disconnect=" + onOff(rerouteAllowed && config.get(HydraConfig.AFTER_DISCONNECT_ENABLED))
                + " (" + emptyDash(config.get(HydraConfig.AFTER_DISCONNECT_COMMAND)) + ")"
                + " | realign-msgs=" + listCount(config.get(HydraConfig.REALIGNMENT_MESSAGES))
                + " | look-msgs=" + listCount(config.get(HydraConfig.LOOK_MESSAGES))
                + " | barrier-msgs=" + listCount(config.get(HydraConfig.BARRIER_MESSAGES))
                + " | floor-msgs=" + listCount(config.get(HydraConfig.FLOOR_CHANGE_MESSAGES))
                + " | last-check=" + lastCheckClassification(parity);
    }

    /**
     * The last detected event's check-class rare classification (for {@code /vdebug hydra response}):
     * its {@code forceReactRare} / {@code thresholdRare} predicates (booleans, never message contents) and
     * whether it is response-eligible in the current mode + why. Derived from {@link #lastEvent()} via
     * {@link HydraResponseTrigger#fromEvent} — no extra state, no network.
     */
    private String lastCheckClassification(boolean parity) {
        return classifyCheck(lastEvent(), parity);
    }

    /**
     * Format a check's rare classification + response eligibility for {@code /vdebug hydra response}, given
     * the event and the current parity mode. Package-private + static so it is directly unit-testable for
     * every event type (incl. parity-response checks such as {@code TELEPORT_INTO_BLOCK} and
     * {@code FLOOR_CHANGE}). Emits the {@code forceReactRare}/{@code thresholdRare} booleans and
     * the eligibility reason — <b>never</b> message-list contents.
     */
    static String classifyCheck(HydraEvent last, boolean parity) {
        if (last == null) {
            return "none";
        }
        HydraResponseTrigger tr = HydraResponseTrigger.fromEvent(last);
        boolean eligible = tr.staffCheck() || (parity && tr.parityResponseCheck());
        String why = tr.staffCheck() ? "staff-check"
                : tr.parityResponseCheck() ? (parity ? "parity-check" : "parity-check(needs parity)")
                : tr.evacuation() ? "reroute(evac)"
                : tr.disconnect() ? "reroute(disconnect)"
                : "passive-alert";
        return last.type() + " forceReactRare=" + tr.forceReactRare() + " thresholdRare=" + tr.thresholdRare()
                + " response-eligible=" + eligible + " (" + why + ")";
    }

    /**
     * Compact indicator for an optional {@code |}-joined local message list (REALIGNMENT/LOOK): the number
     * of configured lines, or {@code off} when blank. Avoids printing the full list on the one-line status;
     * the lines themselves are JSON-editable in {@code config.json}.
     */
    private static String listCount(String csv) {
        if (csv == null || csv.isBlank()) {
            return "off";
        }
        int n = 0;
        for (String part : csv.split("\\|")) {
            if (!part.isBlank()) {
                n++;
            }
        }
        return n == 0 ? "off" : Integer.toString(n);
    }

    /** Set the automated-response policy (persisted by the caller). */
    public void setResponsePolicy(HydraResponsePolicy policy) {
        if (policy != null) {
            config.set(HydraConfig.STAFF_RESPONSE_POLICY, policy);
            responseHandler.reset();
            VeltraphLog.basic("Hydra", "anti-staff response policy -> " + policy);
        }
    }

    private static String emptyDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** Drop any pending automated reactions (called on world stop so a new world starts clean). */
    public void resetResponses() {
        responseHandler.reset();
        responder.reset();
        if (failsafeResponse != null) {
            failsafeResponse.reset();
        }
        stopAfterFailsafeResponse = false;
        generalPolicyCompletion = null;
        generalResponseType = null;
        capturedResponsePermissions = null;
    }

    /** Whitelist size for the status line. */
    public int whitelistSize() {
        return whitelist().size();
    }

    /** Whether a Veltraph macro was running on the last tick — the global runtime gate state (read API). */
    public boolean macroActive() {
        return macroActive;
    }

    /** A one-line human-readable status (for {@code /vdebug hydra status}). */
    public String status() {
        HydraEvent last = lastEvent();
        // Hydra only runs while a macro is active; surface that clearly even when the watcher is enabled.
        String runtime = !enabled() ? "OFF"
                : macroActive ? "ACTIVE (macro running)"
                : "INACTIVE (no macro — enabled, idle)";
        return "Anti-Staff: " + runtime
                + " | state=" + state()
                + " | distance=" + alertDistanceBlocks() + "b"
                + " (danger<=" + config.get(HydraConfig.STAFF_ALERT_DANGEROUS_DISTANCE_BLOCKS) + "b)"
                + " | nearby=" + (nearestPlayerName() == null ? "none" : nearestPlayerName())
                + " | whitelist=" + whitelistSize()
                + " | auto-stop=" + config.get(HydraConfig.STAFF_ALERT_AUTO_STOP_MACRO)
                + (last == null ? "" : " | last=" + last.type() + "/" + last.severity());
    }

    /**
     * A short last-event label for the HUD (e.g. {@code "STAFF_NEARBY/DANGEROUS"}), or null when none.
     * Lets the HUD line surface the last trigger + severity without re-deriving it.
     */
    public String lastEventLabel() {
        HydraEvent last = lastEvent();
        return last == null ? null : last.type() + "/" + last.severity();
    }

    /** A one-line summary of which defensive triggers are enabled (for {@code /vdebug hydra triggers}). */
    public String triggerStatus() {
        return "Anti-Staff triggers: lagback=" + onOff(config.get(HydraConfig.DETECT_LAGBACK))
                + " (>" + config.get(HydraConfig.LAGBACK_DISTANCE_BLOCKS) + "b)"
                + " | teleport-into-block=" + onOff(config.get(HydraConfig.DETECT_TELEPORT_INTO_BLOCK))
                + " | forced-adventure=" + onOff(config.get(HydraConfig.DETECT_FORCED_ADVENTURE))
                + " | routing-text=" + onOff(config.get(HydraConfig.DETECT_ROUTING_TEXT))
                + " | floor-change=" + onOff(config.get(HydraConfig.DETECT_FLOOR_CHANGE))
                + " | barrier=" + onOff(config.get(HydraConfig.DETECT_BARRIER))
                + " (<=" + config.get(HydraConfig.BARRIER_STATIONARY_SPEED) + "b/t)"
                + " | teleport=>" + config.get(HydraConfig.TELEPORT_DISTANCE_BLOCKS) + "b";
    }

    private static String onOff(boolean b) {
        return b ? "on" : "off";
    }
}
