package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.planner.CocoaPlanner;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the cocoa module integration: macro-owned heading capture, smooth-aim per tick toward the
 * fixed -45 pitch, ground gating, the real {@code player.up(2)} cocoa probes, and the disable safety
 * contract. State-machine + sneak-latch detail is covered by {@link CocoaPlanner} tests.
 */
class CocoaFarmModuleTest {

    /** World that reports cocoa at an explicit set of block positions; everything else is air/passable. */
    private static final class CocoaWorldService implements WorldService {
        final Set<BlockPos> cocoa = new HashSet<>();
        final Set<BlockPos> walls = new HashSet<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        final Set<Integer> floorLevels = new HashSet<>(Set.of(63));
        @Override public boolean isSolidFloor(BlockPos pos) { return floorLevels.contains(pos.y()); }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return !unloaded.contains(pos);
        }

        @Override
        public boolean isPassable(BlockPos pos) {
            return !isSolidFloor(pos) && !cocoa.contains(pos) && !walls.contains(pos);
        }

        @Override
        public boolean isStandable(BlockPos pos) {
            return false;
        }

        @Override
        public String blockId(BlockPos pos) {
            return cocoa.contains(pos) ? "minecraft:cocoa" : "minecraft:air";
        }
    }

    private FakeClock clock;
    private FakePlayerService player;
    private CocoaWorldService world;
    private RecordingInputService input;
    private RecordingBlockBreaking breaking;
    private FakeRotationService rotation;
    private MapConfigService config;
    private CocoaFarmModule cocoa;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        player = new FakePlayerService();
        player.onGround = true;
        player.blockPos = new BlockPos(0, 64, 0);
        player.position = new dev.veltraph.client.world.Vec3(0.5, 64.0, 0.5);
        world = new CocoaWorldService();
        input = new RecordingInputService();
        breaking = new RecordingBlockBreaking();
        rotation = new FakeRotationService();
        config = new MapConfigService();
        FarmServices services = new FarmServices(clock, player, world, input, rotation, breaking,
                new NoOpInventoryService());
        cocoa = new CocoaFarmModule(services, config, FarmLayoutRegistry.createDefault());
    }

    @Test
    void profileIsCocoa() {
        assertEquals(FarmLayoutRegistry.COCOA, cocoa.activeProfile().id());
    }

    @Test
    void enableStartsRunningInFarming() {
        cocoa.setEnabled(true);
        assertEquals(MacroState.RUNNING, cocoa.state());
        assertEquals(CocoaPlanner.CocoaState.FARMING, cocoa.cocoaState());
    }

    @Test
    void capturesMacroHeadingAndAimsSmoothlyTowardFixedPitch() {
        // Start facing yaw 0 (SOUTH) with the camera elsewhere; the aim should ease toward -45 pitch.
        rotation.yaw = 0f;
        rotation.pitch = 0f;
        cocoa.setEnabled(true);
        float firstPitch = rotation.pitch;
        cocoa.onTick();
        // Smooth aim moved the pitch toward -45 (negative = up) but did not snap all the way in one tick.
        assertTrue(rotation.aimCalls >= 1, "aim() called each tick");
        assertTrue(rotation.pitch < firstPitch, "pitch eased upward (negative) toward -45");
        assertTrue(rotation.pitch > -45.0f, "no one-tick snap to the target pitch");
    }

    @Test
    void aimContinuesEachTickWithoutSnap() {
        cocoa.setEnabled(true);
        float prev = rotation.pitch;
        for (int i = 0; i < 3; i++) {
            cocoa.onTick();
            assertTrue(rotation.pitch <= prev, "pitch keeps easing toward -45 each tick");
            prev = rotation.pitch;
        }
        assertNotEquals(0f, rotation.pitch, "aim progressed off the starting pitch");
    }

    @Test
    void offGroundReleasesInputsButKeepsAiming() {
        cocoa.setEnabled(true);
        player.onGround = false;
        int callsBefore = rotation.aimCalls;
        cocoa.onTick();
        assertTrue(rotation.aimCalls > callsBefore, "still aims while off ground");
        assertFalse(input.forward, "movement released while off ground");
        assertFalse(breaking.breaking, "breaking released while off ground");
    }

    @Test
    void onGroundFarmingDrivesForwardAndBreak() {
        cocoa.setEnabled(true);
        cocoa.onTick();
        assertTrue(input.forward, "auto-walk holds forward while FARMING");
        assertTrue(breaking.breaking, "breaks while FARMING");
    }

    @Test
    void wallStartsLateralSwitchAndOnlyNewLaneUpperPodsCompleteIt() {
        rotation.yaw = 0f; // SOUTH, switch RIGHT = WEST
        world.walls.add(new BlockPos(0,64,1));
        cocoa.setEnabled(true); cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, cocoa.cocoaState());
        assertTrue(input.right);
        cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, cocoa.cocoaState(), "same block centre is not a new lane");
        player.blockPos = new BlockPos(-2,64,0);
        player.position = new dev.veltraph.client.world.Vec3(-1.5,64,0.5);
        world.cocoa.add(new BlockPos(-2,65,-2)); // old downward scan would accept this
        cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, cocoa.cocoaState());
        world.cocoa.add(new BlockPos(-2,69,-2)); // up(2) + 3, opposite old travel
        cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.FARMING, cocoa.cocoaState());
        assertTrue(input.back, "new row reverses travel without rotating the camera");
        assertFalse(input.right);
    }

    @Test
    void unloadedLaneCannotDriveThePlayerIntoUnknownSpace() {
        world.walls.add(new BlockPos(0,64,1));
        world.unloaded.add(new BlockPos(-1,64,0));
        cocoa.setEnabled(true); cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, cocoa.cocoaState());
        assertFalse(input.right);
        assertFalse(input.forward);
        assertFalse(breaking.breaking);
    }

    @Test
    void podsAloneDoNotStartRowSwitchBeforeAnObstruction() {
        world.cocoa.add(new BlockPos(0,66,2));
        cocoa.setEnabled(true); cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.FARMING, cocoa.cocoaState());
        assertTrue(input.forward);
    }

    @Test
    void rewarpDropsTheOldLaneSwitchAndSneakLatch() {
        config.set(FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING,true);
        world.walls.add(new BlockPos(0,64,1));
        cocoa.setEnabled(true); cocoa.onTick();
        assertTrue(input.right && input.sneak);
        cocoa.suspendForRewarp(); cocoa.onTick();
        assertFalse(input.right || input.sneak || breaking.breaking);
        player.blockPos=new BlockPos(10,64,10);
        player.position=new dev.veltraph.client.world.Vec3(10.5,64,10.5);
        cocoa.resumeAfterRewarp(); cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.FARMING,cocoa.cocoaState());
        assertTrue(input.forward);
        assertFalse(input.right || input.sneak);
    }

    @Test
    void airborneHeightLatchResetsOldSwitchAndReversesNextLateralTransferOnce() {
        config.set(FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING, true);
        world.walls.add(new BlockPos(0,64,1));
        cocoa.setEnabled(true); cocoa.onTick();
        assertTrue(input.right && input.sneak);
        world.floorLevels.clear(); world.floorLevels.add(61);
        player.onGround = false;
        player.position = new dev.veltraph.client.world.Vec3(0.5,63.5,0.5);
        player.blockPos = new BlockPos(0,63,0);
        cocoa.onTick();
        assertFalse(input.right || input.sneak || breaking.breaking);
        player.position = new dev.veltraph.client.world.Vec3(0.5,62,0.5);
        player.blockPos = new BlockPos(0,62,0);
        player.onGround = true;
        cocoa.onTick();
        assertEquals(CocoaPlanner.CocoaState.FARMING, cocoa.cocoaState());
        assertTrue(input.forward);
        assertFalse(input.sneak || input.right);
        world.walls.add(new BlockPos(0,62,1));
        cocoa.onTick();
        assertTrue(input.left && input.sneak, "lower level transfer reverses the progression side");
        cocoa.onTick();
        assertTrue(input.left, "same grounded level must not toggle the side again");
    }

    @Test
    void unloadedAirborneHeightProbeCannotReverseTraversal() {
        world.walls.add(new BlockPos(0,64,1));
        cocoa.setEnabled(true); cocoa.onTick();
        player.onGround = false;
        player.position = new dev.veltraph.client.world.Vec3(0.5,63.5,0.5);
        player.blockPos = new BlockPos(0,63,0);
        world.unloaded.add(new BlockPos(0,63,0));
        cocoa.onTick();
        player.onGround = true;
        player.position = new dev.veltraph.client.world.Vec3(0.5,64,0.5);
        player.blockPos = new BlockPos(0,64,0);
        world.unloaded.clear(); cocoa.onTick();
        assertTrue(input.right, "unknown geometry does not create a completed level change");
        assertEquals(CocoaPlanner.CocoaState.SWITCHING_ROW, cocoa.cocoaState());
    }

    @Test
    void disableReleasesInputsAndStops() {
        cocoa.setEnabled(true);
        cocoa.onTick();
        cocoa.setEnabled(false);
        assertEquals(MacroState.STOPPED, cocoa.state());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(input.forward);
    }

    @Test
    void diagnosticsReportsStateAndAim() {
        cocoa.setEnabled(true);
        cocoa.onTick();
        String diag = cocoa.diagnostics();
        assertTrue(diag.startsWith("Cocoa state="), "diagnostics line for /vdebug macro");
        assertTrue(diag.contains("heading="));
        assertTrue(diag.contains("aim tgt="));
    }
}
