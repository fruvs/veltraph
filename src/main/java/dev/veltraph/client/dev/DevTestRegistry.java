package dev.veltraph.client.dev;

import dev.veltraph.client.log.VeltraphLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered registry of {@link DevTestAction}s, shared by the Settings Developer page and the
 * {@code /vdebug dev} command so both surfaces stay in lock-step. Pure (no Minecraft) — it only routes
 * to the injected {@link DevTestContext} and logs via the static {@link VeltraphLog} facade (a no-op in
 * tests).
 *
 * <p>{@link #run} is the single choke point for risk gating: an action runs only when Developer Mode is
 * enabled, its context predicate passes, and — for {@link DevTestRisk#SERVER_BOUND} actions — the caller
 * explicitly allows server-bound execution. Every attempt is logged with the action id, category/risk,
 * area/context, module state, and the result (incl. dry-run vs executed).
 */
public final class DevTestRegistry {

    private static final String TAG = "DevTest";

    private final Map<String, DevTestAction> byId = new LinkedHashMap<>();

    /** Register an action. Throws on a duplicate id (catches the spaghetti before it ships). */
    public void register(DevTestAction action) {
        if (action == null) {
            throw new IllegalArgumentException("null action");
        }
        if (byId.putIfAbsent(action.id(), action) != null) {
            throw new IllegalArgumentException("Duplicate dev-test action id: " + action.id());
        }
    }

    public DevTestAction byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /** All actions, in registration order. */
    public List<DevTestAction> actions() {
        return List.copyOf(byId.values());
    }

    /** Actions in one category, in registration order. */
    public List<DevTestAction> byCategory(DevTestCategory category) {
        List<DevTestAction> out = new ArrayList<>();
        for (DevTestAction a : byId.values()) {
            if (a.category() == category) {
                out.add(a);
            }
        }
        return out;
    }

    public boolean has(String id) {
        return id != null && byId.containsKey(id);
    }

    public int size() {
        return byId.size();
    }

    /**
     * Run an action through the gating choke point.
     *
     * @param id               the action id
     * @param ctx              the execution seam
     * @param devModeEnabled   whether Developer Mode is on (everything is blocked when off)
     * @param allowServerBound whether SERVER_BOUND actions may actually send (the explicit "Execute" path)
     */
    public DevTestResult run(String id, DevTestContext ctx, boolean devModeEnabled, boolean allowServerBound) {
        DevTestAction action = byId(id);
        if (action == null) {
            VeltraphLog.basic(TAG, "run rejected: unknown action id '" + id + "'");
            return DevTestResult.unavailable("unknown dev-test action: " + id);
        }
        DevTestResult result = gateAndRun(action, ctx, devModeEnabled, allowServerBound);
        logAttempt(action, ctx, result);
        return result;
    }

    private DevTestResult gateAndRun(DevTestAction action, DevTestContext ctx,
                                     boolean devModeEnabled, boolean allowServerBound) {
        if (!devModeEnabled) {
            return DevTestResult.blocked("Developer Mode is OFF — enable it first");
        }
        if (ctx == null) {
            return DevTestResult.unavailable("dev-test context unavailable");
        }
        if (!action.isEnabled(ctx)) {
            return DevTestResult.blocked(action.id() + " is not available in this context");
        }
        if (action.risk() == DevTestRisk.SERVER_BOUND && !allowServerBound) {
            return DevTestResult.blocked(action.label()
                    + " is SERVER_BOUND — use the explicit Execute control to send it");
        }
        try {
            DevTestResult r = action.run(ctx);
            return r == null ? DevTestResult.noOp("(no result)") : r;
        } catch (Throwable t) {
            return DevTestResult.unavailable(action.id() + " threw: " + t.getClass().getSimpleName());
        }
    }

    private static void logAttempt(DevTestAction action, DevTestContext ctx, DevTestResult result) {
        String area = ctx == null ? "?" : safe(ctx::areaName);
        String modules = ctx == null ? "?" : safe(ctx::moduleStateSummary);
        VeltraphLog.basic(TAG, "action=" + action.id()
                + " category=" + action.category()
                + " risk=" + action.risk()
                + " area=" + area
                + " modules=[" + modules + "]"
                + " -> " + result.status()
                + (result.wasDryRun() ? " (dry-run)" : result.wasExecuted() ? " (executed)" : "")
                + " : " + result.message());
    }

    private interface StringCall {
        String get();
    }

    private static String safe(StringCall call) {
        try {
            String s = call.get();
            return s == null ? "?" : s;
        } catch (Throwable t) {
            return "?";
        }
    }
}
