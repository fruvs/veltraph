package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.service.stub.NoOpRotationService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeWorldService;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the recovered cocoa lane-switch machine + same-tick sneak latch (§5b) and the pure
 * {@code laneDirFor} mapping (FarmingCocoaLayout.md:20-26).
 */
class CocoaPlannerTest {

    /** Controllable world for the planner. */
    private static final class FakeWorld implements CocoaPlanner.World {
        LaneSide laneSide = LaneSide.LEFT;
        boolean atRowEnd;
        boolean laneAligned;

        @Override
        public LaneSide detectedLaneSide() {
            return laneSide;
        }

        @Override
        public boolean atRowEnd() {
            return atRowEnd;
        }

        @Override
        public boolean laneAligned() {
            return laneAligned;
        }
    }

    private MapConfigService config;
    private FakeWorld world;
    private RecordingInputService input;
    private RecordingBlockBreaking breaking;
    private CocoaPlanner planner;

    @BeforeEach
    void setUp() {
        config = new MapConfigService();
        world = new FakeWorld();
        input = new RecordingInputService();
        breaking = new RecordingBlockBreaking();
        FarmServices services = new FarmServices(new FakeClock(), new FakePlayerService(),
                new FakeWorldService(), input, new NoOpRotationService(), breaking,
                new NoOpInventoryService());
        planner = new CocoaPlanner(config, services, world);
    }

    // ---- laneDirFor mapping (pure, recovered table §5b) ----

    @Test
    void laneDirForMatchesRecoveredTable() {
        assertEquals(TurnDirection.FORWARD, CocoaPlanner.laneDirFor(LaneSide.LEFT, LaneSide.RIGHT),
                "LEFT & switch RIGHT -> FORWARD");
        assertEquals(TurnDirection.BACK, CocoaPlanner.laneDirFor(LaneSide.LEFT, LaneSide.LEFT),
                "LEFT & else -> BACK");
        assertEquals(TurnDirection.BACK, CocoaPlanner.laneDirFor(LaneSide.RIGHT, LaneSide.RIGHT),
                "RIGHT & switch RIGHT -> BACK");
        assertEquals(TurnDirection.FORWARD, CocoaPlanner.laneDirFor(LaneSide.RIGHT, LaneSide.LEFT),
                "RIGHT & else -> FORWARD");
        assertEquals(TurnDirection.FORWARD, CocoaPlanner.laneDirFor(null, LaneSide.RIGHT),
                "side null -> FORWARD");
    }

    @Test
    void directionSurvivesTemporarySideProbeLossAndReversesAtNextLane() {
        planner.start(); world.laneSide = LaneSide.RIGHT; planner.tick();
        assertTrue(input.back);
        world.laneSide = null; planner.tick();
        assertEquals(TurnDirection.BACK, planner.heading());
        assertTrue(input.back);
        world.atRowEnd = true; planner.tick();
        assertTrue(input.right, "actual lateral movement on switch entry");
        assertFalse(input.back);
        world.atRowEnd = false; world.laneAligned = true; planner.tick();
        assertEquals(TurnDirection.FORWARD, planner.heading());
        assertTrue(input.forward, "resume without a stopped intermediate tick");
        assertFalse(input.right);
    }

    // ---- FARMING -> SWITCHING_ROW state machine ----

    @Test
    void startsInFarmingAndDrivesForwardBreak() {
        planner.start();
        assertEquals(CocoaPlanner.CocoaState.FARMING, planner.state());
        planner.tick();
        assertTrue(input.forward, "auto-walk holds forward while FARMING (default COCOA_PLACER_AUTO_WALK=true)");
        assertTrue(breaking.breaking, "breaks while FARMING");
    }

    @Test
    void autoWalkOffDoesNotHoldForward() {
        config.set(FarmingConfig.COCOA_PLACER_AUTO_WALK, false);
        planner.start();
        planner.tick();
        assertFalse(input.forward, "no auto-walk -> forward not held");
        assertTrue(breaking.breaking, "still breaks while FARMING");
    }

    @Test
    void atRowEndEntersSwitchingRow() {
        planner.start();
        world.atRowEnd = true;
        planner.tick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, planner.state());
        assertFalse(input.forward, "forward released entering SWITCHING_ROW");
        assertFalse(breaking.breaking, "breaking released entering SWITCHING_ROW");
    }

    // ---- same-tick sneak latch ----

    @Test
    void sneakLatchPressesSameTickWhenConfigured() {
        config.set(FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING, true);
        planner.start();
        world.atRowEnd = true;
        planner.tick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, planner.state());
        assertTrue(planner.sneakLatched(), "sneak latched the same tick the machine enters SWITCHING_ROW");
        assertTrue(input.sneak, "sneak held same tick (no timer)");
    }

    @Test
    void sneakNotPressedWhenDisabled() {
        // default COCOA_SNEAK_WHEN_SWITCHING = false
        planner.start();
        world.atRowEnd = true;
        planner.tick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, planner.state());
        assertFalse(planner.sneakLatched());
        assertFalse(input.sneak, "sneak never pressed when disabled");
    }

    @Test
    void sneakLatchReleasesOnFarmingReentry() {
        config.set(FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING, true);
        planner.start();
        world.atRowEnd = true;
        planner.tick(); // -> SWITCHING_ROW, sneak pressed
        assertTrue(input.sneak);

        world.atRowEnd = false;
        world.laneAligned = true;
        planner.tick(); // lane aligned -> FARMING, sneak released
        assertEquals(CocoaPlanner.CocoaState.FARMING, planner.state());
        assertFalse(planner.sneakLatched(), "sneak released on FARMING re-entry");
        assertFalse(input.sneak, "sneak dropped the same tick the machine re-enters FARMING");
    }

    @Test
    void staysSwitchingUntilLaneAligned() {
        planner.start();
        world.atRowEnd = true;
        planner.tick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, planner.state());
        assertTrue(input.right);

        // Not aligned yet -> hold in SWITCHING_ROW across ticks.
        world.atRowEnd = false;
        world.laneAligned = false;
        planner.tick();
        planner.tick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, planner.state());

        world.laneAligned = true;
        planner.tick();
        assertEquals(CocoaPlanner.CocoaState.FARMING, planner.state());
    }

    @Test
    void stopReleasesInputsAndResetsState() {
        config.set(FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING, true);
        planner.start();
        world.atRowEnd = true;
        planner.tick();
        planner.stop();
        assertEquals(CocoaPlanner.CocoaState.FARMING, planner.state());
        assertFalse(planner.sneakLatched());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
    }
}
