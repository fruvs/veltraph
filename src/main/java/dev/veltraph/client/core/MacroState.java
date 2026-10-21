package dev.veltraph.client.core;

/**
 * Run-lifecycle state of a module/macro, tracked by {@link ModuleManager} and surfaced on the HUD /
 * debug panel. Distinct from the farming movement state machine (which lives in the farming package).
 */
public enum MacroState {
    STOPPED,
    RUNNING,
    PAUSED
}
