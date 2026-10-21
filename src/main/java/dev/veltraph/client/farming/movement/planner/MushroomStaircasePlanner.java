package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.ProbeResult;

/**
 * Mushroom staircase direction selector (see {@code FarmingMovementPlannerHotspots.md} §5d and
 * {@code FARMING_LAYOUT_PLANNER_DEEP_RECOVERY.md} §4 / §5d).
 *
 * <p>Recovered + corrected: the selector keys on a FOUND/BLOCKED/NULL staircase probe (NOT on
 * {@code STAIRCASE_MUSHROOM_MAIN_DIR}, which the report's "Verification" note confirms is resolved
 * <i>upstream</i> in the movement-dir-state init, not by this selector) and returns either
 * {@code MUSHROOM_DIR} (default LEFT) or {@link TurnDirection#FORWARD}. It probes the current movement
 * direction first, then the opposite:
 *
 * <pre>
 *   probe1 (dir)   probe2 (opposite)   result
 *   FOUND          -                   MUSHROOM_DIR
 *   BLOCKED        -                   FORWARD
 *   NULL           FOUND               FORWARD
 *   NULL           BLOCKED             MUSHROOM_DIR
 *   NULL           NULL                boolFallback ? FORWARD : MUSHROOM_DIR
 * </pre>
 *
 * <p>The separate <b>EXTREME/SLIGHT path-mode</b> (§5d step-builder, src 160:
 * {@code base = playerPos.down().offset(dir); pathMode = walkable(base) ? EXTREME : SLIGHT}) is a
 * distinct classification from the same down-forward walkability — it does not feed the selector, it
 * parametrises how aggressively the step is taken. Both are pure helpers; probes are injected.
 */
public final class MushroomStaircasePlanner {

    /**
     * Recovered mushroom path-mode enum {@code {EXTREME, SLIGHT}} (§5d clinit default {@code EXTREME}).
     * EXTREME when the down-and-forward block is walkable/supported (a real staircase step continues);
     * SLIGHT otherwise (flatter continuation).
     */
    public enum PathMode {
        EXTREME,
        SLIGHT
    }

    /** Probe a staircase step in a direction, classifying it FOUND / BLOCKED / NONE (recovered: NULL). */
    @FunctionalInterface
    public interface StaircaseProbe {
        ProbeResult probe(Direction dir);
    }

    /**
     * Down-and-forward walkability test for the EXTREME/SLIGHT path-mode (§5d, src 160). Returns true
     * when the block one down and one forward of the player supports a step (the staircase descends/
     * continues), which makes the path EXTREME.
     */
    @FunctionalInterface
    public interface DownForwardWalkable {
        boolean walkable(Direction dir);
    }

    private final ConfigService config;

    public MushroomStaircasePlanner(ConfigService config) {
        this.config = config;
    }

    /**
     * @param movementDir         current movement direction
     * @param probe               staircase probe
     * @param nullFallbackForward the recovered boolean fallback used when both probes are NULL
     * @return {@code MUSHROOM_DIR} (as LEFT/RIGHT) or {@link TurnDirection#FORWARD}
     */
    public TurnDirection selectDirection(Direction movementDir,
                                         StaircaseProbe probe,
                                         boolean nullFallbackForward) {
        ProbeResult first = probe.probe(movementDir);
        switch (first) {
            case FOUND -> {
                return mushroomDir();
            }
            case BLOCKED -> {
                return TurnDirection.FORWARD;
            }
            case NONE -> { /* ambiguous: re-probe the opposite direction */ }
        }

        ProbeResult second = probe.probe(movementDir.opposite());
        return switch (second) {
            case FOUND -> TurnDirection.FORWARD;
            case BLOCKED -> mushroomDir();
            case NONE -> nullFallbackForward ? TurnDirection.FORWARD : mushroomDir();
        };
    }

    /**
     * Classify the staircase path-mode from down-forward walkability (§5d step builder, src 160):
     * {@code base = playerPos.down().offset(dir)} → {@code walkable ? EXTREME : SLIGHT}. Pure; the
     * walkability test is injected so callers wire it to the world/geometry services.
     */
    public PathMode classifyPathMode(Direction movementDir, DownForwardWalkable downForward) {
        return downForward.walkable(movementDir) ? PathMode.EXTREME : PathMode.SLIGHT;
    }

    /** {@code Config.MUSHROOM_DIR} mapped to a lane direction (default LEFT). */
    public TurnDirection mushroomDir() {
        return TurnDirection.fromLaneSide(config.get(FarmingConfig.MUSHROOM_DIR));
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config);
    }
}
