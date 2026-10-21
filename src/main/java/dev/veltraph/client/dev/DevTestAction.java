package dev.veltraph.client.dev;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One local developer test action. Pure: the {@link #run} callback takes a {@link DevTestContext} seam so
 * the decision/dispatch logic is unit-testable without Minecraft. The {@link DevTestRegistry} enforces
 * Dev-Mode + risk gating around {@link #run} — an action body never has to re-check whether it is allowed.
 */
public final class DevTestAction {

    private final String id;
    private final String label;
    private final String description;
    private final DevTestCategory category;
    private final DevTestRisk risk;
    private final Predicate<DevTestContext> enabled;
    private final Function<DevTestContext, DevTestResult> run;

    public DevTestAction(String id, String label, String description, DevTestCategory category,
                         DevTestRisk risk, Predicate<DevTestContext> enabled,
                         Function<DevTestContext, DevTestResult> run) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.description = description == null ? "" : description;
        this.category = Objects.requireNonNull(category, "category");
        this.risk = Objects.requireNonNull(risk, "risk");
        this.enabled = enabled == null ? ctx -> true : enabled;
        this.run = Objects.requireNonNull(run, "run");
    }

    /** Convenience for an always-enabled action. */
    public static DevTestAction of(String id, String label, String description, DevTestCategory category,
                                   DevTestRisk risk, Function<DevTestContext, DevTestResult> run) {
        return new DevTestAction(id, label, description, category, risk, ctx -> true, run);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public DevTestCategory category() {
        return category;
    }

    public DevTestRisk risk() {
        return risk;
    }

    public boolean isEnabled(DevTestContext ctx) {
        try {
            return enabled.test(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Run the action body. The registry has already checked gating; this just executes. */
    public DevTestResult run(DevTestContext ctx) {
        return run.apply(ctx);
    }
}
