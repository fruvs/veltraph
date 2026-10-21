package dev.veltraph.client.failsafe.response;

import dev.veltraph.client.config.ConfigService;

import java.util.function.LongSupplier;

/**
 * Orchestrates the local failsafe automated-response routine: it gates scheduling (master toggle +
 * per-trigger toggle + cooldown), drives the pure {@link FailsafeResponseRunner} over the
 * {@link FailsafeActionSink}, and exposes status. Minecraft-free (the sink is the only seam), so it is
 * unit-testable with a recording sink + a fake clock.
 *
 * <p><b>Macro gate is the caller's job:</b> production callers (the Hydra orchestrator) only invoke
 * {@link #onTrigger}/{@link #tick} while a macro is running; the Developer-Mode dev-test path invokes
 * {@link #devRun}/{@link #devRunRoutine}/{@link #tick} without a macro. The controller itself adds no
 * detection and never reads Minecraft.
 */
public final class FailsafeResponseController {

    private final ConfigService config;
    private final FailsafeActionSink sink;
    private final LongSupplier clockMs;
    private final FailsafeResponseRunner runner = new FailsafeResponseRunner();
    private long lastStartMs = Long.MIN_VALUE;
    // Null identifies an explicitly requested developer routine, which bypasses production gates.
    private FailsafeResponseType activeTrigger;
    private FailsafeResponsePlan activePlan;

    public FailsafeResponseController(ConfigService config, FailsafeActionSink sink, LongSupplier clockMs) {
        this.config = config;
        this.sink = sink == null ? FailsafeActionSink.NONE : sink;
        this.clockMs = clockMs == null ? () -> 0L : clockMs;
    }

    private FailsafeResponseSettings settings() {
        return FailsafeResponseSettings.fromConfig(config);
    }

    /**
     * A real failsafe event fired (production path; the caller has already confirmed a macro is running).
     * Schedules the matching plan iff the master toggle + this trigger's toggle are on and the cooldown has
     * elapsed. Returns true if a plan was scheduled.
     */
    public boolean onTrigger(FailsafeResponseType type) {
        FailsafeResponseSettings s = settings();
        if (!s.triggerEnabled(type)) {
            return false;
        }
        long now = clockMs.getAsLong();
        if (lastStartMs != Long.MIN_VALUE && now - lastStartMs < Math.max(0L, s.cooldownMs())) {
            return false;
        }
        boolean scheduled = schedule(FailsafeResponsePlanner.plan(type, s), s, now);
        if (scheduled) activeTrigger = type;
        return scheduled;
    }

    /** Local post-stop VOID adaptation. Only LOOK/WAIT and at most 80 ticks; no gameplay actions. */
    public boolean onEmergencyVoid() {
        FailsafeResponseSettings s = settings();
        if (!s.triggerEnabled(FailsafeResponseType.VOID)) return false;
        long now = clockMs.getAsLong();
        if (lastStartMs != Long.MIN_VALUE && now - lastStartMs < Math.max(0L, s.cooldownMs())) return false;
        var plan = new FailsafeResponsePlan("VOID_EMERGENCY_LOCAL", java.util.List.of(
                FailsafeResponseAction.look(25, 0, 8), FailsafeResponseAction.look(-25, 0, 8),
                FailsafeResponseAction.wait(2)));
        runner.start(plan, Math.min(6, s.lookSpeedPerTick()), Math.min(80, Math.max(1, s.maxTicks())), sink);
        activePlan = plan;
        activeTrigger = FailsafeResponseType.VOID;
        lastStartMs = now;
        return true;
    }

    /**
     * Dev-test: run a trigger's plan regardless of the master/per-trigger toggles and cooldown (so the
     * harness works for verification), still honouring the configured profile/tunables. Local only.
     */
    public String devRun(FailsafeResponseType type) {
        FailsafeResponseSettings s = settings();
        FailsafeResponsePlan plan = FailsafeResponsePlanner.plan(type, s);
        schedule(plan, s, clockMs.getAsLong());
        activeTrigger = null;
        return "failsafe response queued: " + plan.label() + " (" + plan.actions().size() + " actions)";
    }

    /** Dev-test: run a focused routine (look sweep / jump+sneak / clicks / movement / full). Local only. */
    public String devRunRoutine(FailsafeRoutine routine) {
        FailsafeResponseSettings s = settings();
        FailsafeResponsePlan plan = FailsafeResponsePlanner.routine(routine, s);
        schedule(plan, s, clockMs.getAsLong());
        activeTrigger = null;
        return "failsafe routine queued: " + plan.label() + " (" + plan.actions().size() + " actions)";
    }

    private boolean schedule(FailsafeResponsePlan plan, FailsafeResponseSettings s, long now) {
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        runner.start(plan, s.lookSpeedPerTick(), s.maxTicks(), sink);
        activePlan = plan;
        lastStartMs = now;
        return true;
    }

    /** Advance the running plan one client tick (no-op when nothing is running). */
    public void tick() {
        if (runner.active() && activeTrigger != null) {
            FailsafeResponseSettings live = settings();
            if (!live.triggerEnabled(activeTrigger) || activePlan.actions().stream().anyMatch(a -> !allowed(a, live))) {
                reset();
                return;
            }
        }
        runner.tick(sink);
        if (!runner.active()) { activeTrigger = null; activePlan = null; }
    }

    private static boolean allowed(FailsafeResponseAction action, FailsafeResponseSettings settings) {
        return switch (action.kind()) {
            case MOVE -> settings.move() && settings.profile() != FailsafeResponseProfile.MINIMAL;
            case JUMP -> settings.jump() && settings.profile() != FailsafeResponseProfile.MINIMAL;
            case SNEAK -> settings.sneak() && settings.profile() == FailsafeResponseProfile.FULL;
            case ATTACK_CLICK -> settings.attack() && settings.profile() == FailsafeResponseProfile.FULL;
            case USE_CLICK -> settings.use() && settings.profile() == FailsafeResponseProfile.FULL;
            default -> true;
        };
    }

    /** Whether a failsafe response plan is currently running. */
    public boolean active() {
        return runner.active();
    }

    /** Abort any running plan and release all held input (disable / macro stop / world change / disconnect). */
    public void reset() {
        runner.reset(sink);
        activeTrigger = null;
        activePlan = null;
    }

    /** A one-line status (for {@code /vdebug failsafe}); never prints message-list contents. */
    public String status() {
        FailsafeResponseSettings s = settings();
        return "Failsafe response: " + (s.enabled() ? "ON" : "OFF")
                + " profile=" + s.profile()
                + " | running=" + (active() ? runner.label() + " (action " + runner.currentActionIndex()
                        + ", " + runner.remainingTicks() + "t left)" : "no")
                + " | triggers=" + s.enabledTriggers().size() + "/" + FailsafeResponseType.values().length
                + " | look=" + s.lookSweeps() + "x" + (int) s.lookRangeDeg() + "° cooldown=" + s.cooldownMs() + "ms";
    }
}
