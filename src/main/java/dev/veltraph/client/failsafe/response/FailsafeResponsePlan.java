package dev.veltraph.client.failsafe.response;

import java.util.List;

/**
 * A deterministic, ordered sequence of {@link FailsafeResponseAction}s produced by
 * {@link FailsafeResponsePlanner} for a given trigger (or focused dev routine). Pure value type.
 *
 * @param label   a short human-readable label (the trigger / routine name) for logging + status
 * @param actions the ordered local actions to run
 */
public record FailsafeResponsePlan(String label, List<FailsafeResponseAction> actions) {

    public FailsafeResponsePlan {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    /** Total client ticks the plan runs (sum of action durations; LOOK counts its cap). */
    public int totalTicks() {
        int t = 0;
        for (FailsafeResponseAction a : actions) {
            t += Math.max(1, a.durationTicks());
        }
        return t;
    }
}
