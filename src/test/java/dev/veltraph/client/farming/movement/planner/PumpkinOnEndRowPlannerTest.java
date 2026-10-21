package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.FakeWorldService;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the recovered {@code PumpkinRowState{FARMING, ON_END_ROW}} machine and the new
 * <b>signal-based</b> smooth-aim API: the planner raises a heading-flip intent + selects the open lane
 * side, and the lateral step only commits once the injected {@code aimAligned} gate + on-ground hold.
 */
class PumpkinOnEndRowPlannerTest {

    private RecordingInputService input;
    private RecordingBlockBreaking breaking;
    private FakePlayerService player;
    private FakeRotationService rotation;
    private FarmServices services;

    private boolean atRowEnd;
    private boolean nextLaneStandable;
    private TurnDirection openSide = TurnDirection.RIGHT;

    /** Controllable smooth-aim gate (module supplies this at runtime). */
    private boolean aimAligned = true;
    private dev.veltraph.client.world.Vec3 destination;

    private PumpkinOnEndRowPlanner planner;

    @BeforeEach
    void setUp() {
        input = new RecordingInputService();
        breaking = new RecordingBlockBreaking();
        player = new FakePlayerService();
        player.onGround = true;
        rotation = new FakeRotationService();
        services = new FarmServices(new FakeClock(), player, new FakeWorldService(),
                input, rotation, breaking, new NoOpInventoryService());
        PumpkinOnEndRowPlanner.World world = new PumpkinOnEndRowPlanner.World() {
            @Override
            public boolean atRowEnd() {
                return atRowEnd;
            }

            @Override
            public boolean nextLaneStandable() {
                return nextLaneStandable;
            }

            @Override
            public TurnDirection openLaneSide() {
                return openSide;
            }

            @Override
            public float currentYaw() {
                return rotation.yaw();
            }
            public java.util.Optional<dev.veltraph.client.world.Vec3> nextLaneCenter(TurnDirection side) {
                return java.util.Optional.ofNullable(destination);
            }
        };
        planner = new PumpkinOnEndRowPlanner(services, world, () -> aimAligned);
        planner.start();
    }

    @Test
    void capturedDestinationUsesPhysicalSideAfterTurnAndRequiresActualArrival() {
        destination=new dev.veltraph.client.world.Vec3(-.5,64,.5);
        atRowEnd=true;nextLaneStandable=true;openSide=TurnDirection.RIGHT;rotation.yaw=0;
        planner.tick();planner.tick();assertTrue(planner.consumeHeadingFlip());
        rotation.yaw=180;planner.tick();
        assertTrue(input.left,"physical WEST becomes literal LEFT after SOUTH→NORTH turn");
        assertFalse(input.right||input.forward);
        assertTrue(breaking.breaking,"owning module gates attack to clear a real adjacent fruit cube");
        for(int tick=0;tick<8;tick++)planner.tick();
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.ON_END_ROW,planner.state(),"six ticks cannot invent adjacent-row arrival");
        player.position=destination;planner.tick();
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,planner.state());
        assertFalse(input.left||input.right||input.forward);
    }

    @Test
    void farmingHoldsForwardAndBreaks() {
        atRowEnd = false;
        planner.tick();
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING, planner.state());
        assertTrue(input.forward);
        assertTrue(breaking.breaking);
    }

    @Test
    void rowEndTransitionsToOnEndRowAndReleases() {
        atRowEnd = true;
        planner.tick();
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.ON_END_ROW, planner.state());
        assertFalse(input.forward);
        assertFalse(breaking.breaking);
    }

    @Test
    void onEndRowSignalsHeadingFlipAndLaneSideWithoutSnapping() {
        atRowEnd = true;
        planner.tick(); // -> ON_END_ROW
        atRowEnd = false;
        nextLaneStandable = true;
        openSide = TurnDirection.RIGHT;
        aimAligned = false; // smooth aim not yet aligned

        int aimCallsBefore = rotation.aimCalls;
        planner.tick(); // arm: signal flip + select side, but DO NOT strafe yet

        // The planner must NOT touch the camera itself (no snap) — the module owns rotation.
        assertEquals(aimCallsBefore, rotation.aimCalls, "planner must not call rotation().aim()");
        assertTrue(planner.consumeHeadingFlip(), "heading flip must be signalled to the module");
        assertEquals(TurnDirection.RIGHT, planner.stepSide());
        assertFalse(input.right, "no strafe until the smooth aim reports aligned");
        assertFalse(planner.stepping());
    }

    @Test
    void laneStepWaitsForAimAlignedThenStrafesAndResumes() {
        atRowEnd = true;
        planner.tick(); // -> ON_END_ROW
        atRowEnd = false;
        nextLaneStandable = true;
        openSide = TurnDirection.RIGHT;

        aimAligned = false;
        planner.tick(); // arm (consumes nothing here; module would consume the flip)
        planner.consumeHeadingFlip();
        planner.tick(); // still waiting on aim
        assertFalse(input.right, "lane step blocked while aim not aligned");

        aimAligned = true;
        planner.tick(); // aim aligned -> begin strafing the lane
        assertTrue(input.right, "should strafe toward the open lane once aligned");
        assertTrue(planner.stepping());

        // Run out the remaining step ticks; lands back in FARMING with strafe released.
        for (int i = 0; i < PumpkinOnEndRowPlanner.LANE_STEP_TICKS; i++) {
            planner.tick();
        }
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING, planner.state());
        assertFalse(input.right);
    }

    @Test
    void laneStepBlockedWhileNotOnGround() {
        atRowEnd = true;
        planner.tick(); // -> ON_END_ROW
        atRowEnd = false;
        nextLaneStandable = true;
        openSide = TurnDirection.LEFT;
        aimAligned = true;
        player.onGround = false;

        planner.tick(); // arm
        planner.consumeHeadingFlip();
        planner.tick(); // gated on ground
        assertFalse(input.left, "lane step blocked while airborne even if aim aligned");

        player.onGround = true;
        planner.tick();
        assertTrue(input.left, "lane step proceeds once back on the ground");
        assertEquals(TurnDirection.LEFT, planner.stepSide());
    }

    @Test
    void noLateralLaneSignalsFlipAndResumesForwardImmediately() {
        atRowEnd = true;
        planner.tick(); // -> ON_END_ROW
        atRowEnd = false;
        nextLaneStandable = true;
        openSide = TurnDirection.FORWARD; // no lateral lane detected

        planner.tick();
        assertTrue(planner.consumeHeadingFlip(), "flip still requested even with no lateral lane");
        assertEquals(TurnDirection.FORWARD, planner.stepSide());
        assertSame(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING, planner.state(),
                "resumes FARMING immediately when there is no lateral lane");
        assertFalse(planner.stepping());
    }

    @Test
    void stopReleasesInputsAndResetsState() {
        atRowEnd = true;
        planner.tick(); // -> ON_END_ROW
        planner.stop();
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING, planner.state());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(planner.consumeHeadingFlip());
    }
}
