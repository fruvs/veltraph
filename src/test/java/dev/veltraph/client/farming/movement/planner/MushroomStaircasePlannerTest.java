package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.ProbeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the recovered FOUND/BLOCKED/NULL decision table (FarmingMovementPlannerHotspots.md §5d). */
class MushroomStaircasePlannerTest {

    private MapConfigService config;
    private MushroomStaircasePlanner planner;

    @BeforeEach
    void setUp() {
        config = new MapConfigService();
        planner = new MushroomStaircasePlanner(config);
    }

    /** Probe returning {@code north} for the current dir (NORTH) and {@code south} for its opposite. */
    private MushroomStaircasePlanner.StaircaseProbe probe(ProbeResult north, ProbeResult south) {
        return dir -> dir == Direction.NORTH ? north : dir == Direction.SOUTH ? south : ProbeResult.NONE;
    }

    @Test
    void foundOnCurrentDirReturnsMushroomDir() {
        assertEquals(TurnDirection.LEFT,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.FOUND, ProbeResult.NONE), true));
    }

    @Test
    void blockedOnCurrentDirReturnsForward() {
        assertEquals(TurnDirection.FORWARD,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.BLOCKED, ProbeResult.NONE), true));
    }

    @Test
    void nullThenFoundReturnsForward() {
        assertEquals(TurnDirection.FORWARD,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.NONE, ProbeResult.FOUND), true));
    }

    @Test
    void nullThenBlockedReturnsMushroomDir() {
        assertEquals(TurnDirection.LEFT,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.NONE, ProbeResult.BLOCKED), true));
    }

    @Test
    void nullNullWithForwardFallback() {
        assertEquals(TurnDirection.FORWARD,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.NONE, ProbeResult.NONE), true));
    }

    @Test
    void nullNullWithoutForwardFallback() {
        assertEquals(TurnDirection.LEFT,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.NONE, ProbeResult.NONE), false));
    }

    @Test
    void mushroomDirConfigShiftsTheTurnSide() {
        config.set(FarmingConfig.MUSHROOM_DIR, LaneSide.RIGHT);
        assertEquals(TurnDirection.RIGHT,
                planner.selectDirection(Direction.NORTH, probe(ProbeResult.FOUND, ProbeResult.NONE), true));
    }

    // ---- EXTREME / SLIGHT path-mode (§5d step builder, src 160: down-forward walkable ? EXTREME) ----

    @Test
    void downForwardWalkableIsExtreme() {
        assertEquals(MushroomStaircasePlanner.PathMode.EXTREME,
                planner.classifyPathMode(Direction.NORTH, dir -> true));
    }

    @Test
    void downForwardNotWalkableIsSlight() {
        assertEquals(MushroomStaircasePlanner.PathMode.SLIGHT,
                planner.classifyPathMode(Direction.NORTH, dir -> false));
    }

    @Test
    void pathModeProbesAlongTheGivenMovementDir() {
        // Walkable only to the EAST; classifying NORTH must read NORTH (false -> SLIGHT).
        MushroomStaircasePlanner.DownForwardWalkable w = dir -> dir == Direction.EAST;
        assertEquals(MushroomStaircasePlanner.PathMode.SLIGHT, planner.classifyPathMode(Direction.NORTH, w));
        assertEquals(MushroomStaircasePlanner.PathMode.EXTREME, planner.classifyPathMode(Direction.EAST, w));
    }
}
