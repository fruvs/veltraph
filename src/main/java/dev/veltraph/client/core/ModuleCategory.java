package dev.veltraph.client.core;

/**
 * Automation/module categories. Mirrors the source-backed UI categories from {@code REBUILD_PLAN.md}.
 * {@link #INTERNAL} is for helper/service modules and is not shown as a normal Automations category.
 */
public enum ModuleCategory {
    FARMING("Farming"),
    COMBAT("Combat"),
    FORAGING("Foraging"),
    FISHING("Fishing"),
    BUILDERS("Builders"),
    HUNTING("Hunting"),
    MINING("Mining"),
    MISC("Misc"),
    SETUPS("Setups"),
    INTERMEDIARY("Intermediary"),
    INTERNAL("Internal");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
