package dev.veltraph.client.hydra.response;

import java.util.List;

/**
 * Expands a {@link HydraReaction} situation into a short, ordered list of local {@link HydraMovementStep}s.
 * Pure (no Minecraft); the MC seam runs the steps over the vanilla movement inputs.
 *
 * <p><b>Clean-room defaults — no recovered movement semantics to copy.</b> Tracing the original
 * {@code CAGE/BARRIER/FLOOR_CHANGE/BACKUP} enum ({@code llIlIIlIlllIlIll….java}) showed its third field is
 * a 3D <b>position/vector</b> ({@code CAGE(3,21,2)} = the vector {@code (3,21,2)}), and every consumer uses
 * the enum for <b>recorded-file classification</b> (filename-prefix bucketing + a formatted log string),
 * with <b>no</b> movement/key-input usage anywhere — it belongs to the {@code recordmasshydras} recording
 * subsystem (EXCLUDE_NETWORK). See {@link HydraReaction} for the full trace. Treating these situation names
 * as local <i>movement</i> reactions is therefore a Veltraph clean-room design choice, not a port; durations
 * come from {@link Settings} (config keys), and the project rule (never invent exact recovered values) holds
 * because no exact movement values exist. The shapes:
 * <ul>
 *   <li>{@link HydraReaction#BACKUP} — back-pedal away from where you were pushed.</li>
 *   <li>{@link HydraReaction#BARRIER} — back off the barrier, then strafe around it (hopping).</li>
 *   <li>{@link HydraReaction#CAGE} — jump (try to break the encasement) then back-hop out.</li>
 *   <li>{@link HydraReaction#FLOOR_CHANGE} — back-hop to stable ground.</li>
 * </ul>
 */
public final class HydraReactionPlanner {

    /** Per-reaction primary durations in client ticks (20 ticks ≈ 1s). All clamped to &ge; 1 on use. */
    public record Settings(int backupTicks, int barrierTicks, int cageTicks, int floorChangeTicks) {

        public static Settings defaults() {
            return new Settings(10, 6, 8, 6);
        }
    }

    private HydraReactionPlanner() {
    }

    /** Build the movement step list for a reaction, or an empty list when {@code reaction} is null. */
    public static List<HydraMovementStep> plan(HydraReaction reaction, Settings s) {
        if (reaction == null) {
            return List.of();
        }
        Settings cfg = s == null ? Settings.defaults() : s;
        return switch (reaction) {
            case BACKUP -> List.of(
                    HydraMovementStep.back(t(cfg.backupTicks()), false));
            case BARRIER -> List.of(
                    HydraMovementStep.back(t(cfg.barrierTicks()), false),
                    HydraMovementStep.strafe(true, t(cfg.barrierTicks()), true));
            case CAGE -> List.of(
                    HydraMovementStep.jump(t(cfg.cageTicks())),
                    HydraMovementStep.back(t(cfg.cageTicks()), true));
            case FLOOR_CHANGE -> List.of(
                    HydraMovementStep.back(t(cfg.floorChangeTicks()), true));
        };
    }

    private static int t(int ticks) {
        return Math.max(1, ticks);
    }
}
