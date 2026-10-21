package dev.veltraph.client.hud;

import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.HudCategory;
import dev.veltraph.client.hydra.HydraState;

/**
 * A pure snapshot of everything the {@link HudModelBuilder} needs for one frame. Carries no Minecraft
 * and no live service references — the renderer adapter samples the live services into this record, so
 * the line-building decision is fully unit-testable on a plain JVM.
 *
 * @param hudEnabled       the {@code HUD_ENABLED} config flag
 * @param context          the current {@link GameContext} (may be {@link GameContext#NONE})
 * @param activeModule     the running module snapshot, or null when none is running
 * @param hydra            the anti-staff snapshot (never null; use {@link HydraInfo#disabled()})
 * @param rewarp           the rewarp snapshot (never null; use {@link RewarpInfo#none()})
 */
public record HudInputs(
        boolean hudEnabled,
        GameContext context,
        ModuleInfo activeModule,
        HydraInfo hydra,
        RewarpInfo rewarp) {

    /** Whether a macro is currently running (drives the {@code SCRIPT} HUD-category gate). */
    public boolean scripting() {
        return activeModule != null;
    }

    /**
     * Snapshot of the active (running) module.
     *
     * @param displayName the module's display name (e.g. "Wart/Crops")
     * @param state       its run-lifecycle {@link MacroState}
     * @param statusLine  a single concise lane/pause line (from {@code Module.statusLine()})
     * @param hudCategory the {@link HudCategory} whose area gate the macro line respects (e.g. farming
     *                    → {@link HudCategory#GARDEN}, so the macro line hides outside the Garden)
     */
    public record ModuleInfo(String displayName, MacroState state, String statusLine, HudCategory hudCategory) {
    }

    /**
     * Snapshot of the local anti-staff watcher.
     *
     * @param enabled    whether the watcher is on
     * @param state      its {@link HydraState}
     * @param nearbyName the nearest non-whitelisted player name, or null/empty when none
     * @param lastEvent  a short last-event label (e.g. "STAFF_NEARBY"), or null when none
     */
    public record HydraInfo(boolean enabled, HydraState state, String nearbyName, String lastEvent) {
        public static HydraInfo disabled() {
            return new HydraInfo(false, HydraState.IDLE, null, null);
        }
    }

    /**
     * Snapshot of the rewarp / return-to-location service.
     *
     * @param savedCount    number of saved rewarp points
     * @param returnPending whether a return is pending
     */
    public record RewarpInfo(int savedCount, boolean returnPending) {
        public static RewarpInfo none() {
            return new RewarpInfo(0, false);
        }
    }
}
