package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.CropBlocks;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.FakeWorldService;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module-level traversal behavior for the recovered sugarcane back/opposite sweep (§5c): smooth aim
 * hold, ground gating, and the lane-switch that toggles configured-side &harr; BACK and physically
 * strafes the return leg.
 */
class SugarcaneFlowersFarmModuleTest {

    private FakeClock clock;
    private CaneWorld world;
    private FakePlayerService player;
    private RecordingInputService input;
    private CaneBreaking breaking;
    private FakeRotationService rotation;
    private MapConfigService config;
    private SugarcaneFlowersFarmModule module;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        world = new CaneWorld();
        player = new FakePlayerService();
        player.onGround = true;
        player.blockPos = new BlockPos(0, 64, 0);
        player.position = new dev.veltraph.client.world.Vec3(0.5,64,0.5);
        input = new RecordingInputService();
        breaking = new CaneBreaking();
        rotation = new FakeRotationService(44f, 0f); // yaw 0 -> snapToCardinal 0 -> baseHeading SOUTH
        config = new MapConfigService();
        FarmServices services = new FarmServices(clock, player, world, input,
                rotation, breaking, new NoOpInventoryService());
        module = new SugarcaneFlowersFarmModule(services, config, FarmLayoutRegistry.createDefault());
    }

    /** baseHeading SOUTH (dz+1); cane straight ahead so the row is NOT ended. */
    private void caneAhead() {
        world.cane.clear();
        world.cane.add(new BlockPos(0,65,1));
        for (int i = 1; i <= 5; i++) {
            world.cane.add(new BlockPos(0, 64, i));
        }
    }

    @Test
    void startsOnConfiguredLeftSide() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.LEFT);
        module.setEnabled(true);
        assertEquals(MacroState.RUNNING, module.state());
        assertEquals(TurnDirection.LEFT, module.currentDir());
        assertFalse(module.debugState().contains("back"));
    }

    @Test
    void holdsOnlyConfiguredStrafeAndBreakWhileRowHasCane() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.LEFT);
        module.setEnabled(true);
        caneAhead();
        module.onTick();
        // Physically traverses: forward + break + the configured-side lateral strafe.
        assertFalse(input.forward, "source applies LEFT literally, without forward");
        assertTrue(breaking.breaking, "should break the cane in front");
        assertTrue(input.left, "configured LEFT leg should strafe left");
        assertFalse(input.right);
        // Smooth aim toward the recovered diagonal (baseYaw 0 + 45), not an instant snap to 45.
        assertTrue(rotation.aimCalls >= 1, "aim should be driven each tick");
        assertTrue(rotation.yaw() >= 44f && rotation.yaw() <= 45.15f,
                "smooth aim steps toward 45 without snapping, got " + rotation.yaw());
    }

    @Test
    void cropAbsenceDoesNotToggleOrJitterDirection() {
        module.setEnabled(true);
        for (int i=0;i<20;i++) module.onTick();
        assertEquals(TurnDirection.LEFT, module.currentDir());
        assertTrue(input.left);
        assertFalse(input.back);
    }

    @Test
    void stationaryObstructionSelectsLiteralBackAndCannotToggleAgainInSamePlace() {
        module.setEnabled(true);
        player.position = new dev.veltraph.client.world.Vec3(0.5,64,0.8);
        world.walls.add(new BlockPos(0,64,1));
        module.onTick();
        assertEquals(TurnDirection.LEFT,module.currentDir(), "first blocked observation latches position");
        module.onTick();
        assertEquals(TurnDirection.BACK,module.currentDir());
        assertTrue(input.back);
        assertFalse(input.forward);
        assertFalse(input.left);
        assertFalse(input.right);
        world.walls.add(new BlockPos(0,64,0));
        for(int i=0;i<10;i++) module.onTick();
        assertEquals(TurnDirection.BACK,module.currentDir(), "same-position obstruction cannot toggle repeatedly");
    }

    @Test
    void slidingAlongWallDoesNotCountAsStationaryCorner() {
        module.setEnabled(true);
        for(int x=0;x<4;x++) world.walls.add(new BlockPos(x,64,1));
        for(int tick=0;tick<10;tick++) {
            player.position=new dev.veltraph.client.world.Vec3(0.5+tick*0.16,64,0.8);
            player.blockPos=player.position.toBlockPos();
            module.onTick();
        }
        assertEquals(TurnDirection.LEFT,module.currentDir());
        assertTrue(input.left);
        assertFalse(input.back);
    }

    @Test
    void airborneTickBreaksStationaryObservationContinuity() {
        module.setEnabled(true);
        player.position=new dev.veltraph.client.world.Vec3(0.5,64,0.8);
        world.walls.add(new BlockPos(0,64,1));
        module.onTick();
        player.onGround=false;module.onTick();
        player.onGround=true;module.onTick();
        assertEquals(TurnDirection.LEFT,module.currentDir(),"landing starts a new blocked observation");
        module.onTick();assertEquals(TurnDirection.BACK,module.currentDir());
    }

    @Test
    void rewarpResetsOldTurnAndAllHeldInputs() {
        module.setEnabled(true);
        player.position=new dev.veltraph.client.world.Vec3(0.5,64,0.8);
        world.walls.add(new BlockPos(0,64,1));
        module.onTick();module.onTick();assertTrue(input.back);
        module.suspendForRewarp();module.onTick();
        assertFalse(input.forward||input.back||input.left||input.right||breaking.breaking);
        module.resumeAfterRewarp();module.onTick();
        assertEquals(TurnDirection.LEFT,module.currentDir(),"resume starts configured direction with fresh stillness");
        module.onTick();assertEquals(TurnDirection.BACK,module.currentDir(),"old transition block does not suppress new traversal");
        module.resumeAfterRewarp();
        assertEquals(TurnDirection.BACK,module.currentDir(),"duplicate unsuspended resume cannot reset an active leg");
    }

    @Test
    void unsupportedLaneReleasesAllMotionRatherThanInventingARowEnd() {
        module.setEnabled(true); world.solidFloor=false;
        module.onTick(); module.onTick();
        assertEquals(TurnDirection.LEFT,module.currentDir());
        assertFalse(input.left || input.right || input.back || input.forward);
        assertFalse(breaking.breaking);
    }

    @Test
    void rightConfigStartsRightAndStrafesRight() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.RIGHT);
        rotation.yaw = -44f;
        module.setEnabled(true);
        caneAhead();
        module.onTick();
        assertEquals(TurnDirection.RIGHT, module.currentDir());
        assertTrue(input.right);
        assertFalse(input.left);
        // RIGHT config aims baseYaw - 45 = -45; smooth step heads negative.
        assertTrue(rotation.yaw() <= -44f && rotation.yaw() >= -45.15f,
                "RIGHT config aims toward -45, got " + rotation.yaw());
    }

    @Test
    void doesNotMoveWhileAirborne() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.LEFT);
        module.setEnabled(true);
        caneAhead();
        player.onGround = false;
        module.onTick();
        assertFalse(input.forward, "must not walk while airborne");
        assertFalse(input.left);
        assertFalse(input.right);
        assertFalse(breaking.breaking, "must not break while airborne");
    }

    @Test
    void disableReleasesInputsAndStops() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.LEFT);
        module.setEnabled(true);
        caneAhead();
        module.onTick();
        module.setEnabled(false);
        assertEquals(MacroState.STOPPED, module.state());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(input.forward);
        assertFalse(input.left);
        assertFalse(input.right);
    }

    @Test
    void diagnosticsReportDirBackLegAndAimTarget() {
        config.set(FarmingConfig.SUGARCANE_DIR, LaneSide.LEFT);
        module.setEnabled(true);
        caneAhead();
        module.onTick();
        String diag = module.diagnostics();
        assertTrue(diag.contains("dir=LEFT"), diag);
        assertTrue(diag.contains("heading=SOUTH"), diag);
        assertTrue(diag.contains("diagonal=45"), diag);
        assertTrue(diag.contains("strafe="), diag);
        assertTrue(diag.contains("blocker="), diag);
    }

    @Test void basesAndStructuresReleaseAttackWithoutChangingTraversal() {
        module.setEnabled(true);caneAhead();module.onTick();assertTrue(breaking.breaking);
        breaking.target=new BlockPos(0,64,1);module.onTick();assertFalse(breaking.breaking);assertTrue(input.left);
        breaking.id="minecraft:stone";module.onTick();assertFalse(breaking.breaking);
    }
    private static final class CaneBreaking implements dev.veltraph.client.service.BlockBreakingService {
        boolean breaking;String id="minecraft:sugar_cane";BlockPos target=new BlockPos(0,65,1);
        public void setBreaking(boolean value){breaking=value;}
        public boolean isBreaking(){return breaking;}
        public String targetBlockId(){return id;}
        public java.util.Optional<BlockPos> targetBlockPos(){return java.util.Optional.of(target);}
    }

    /** {@link FakeWorldService} that reports sugar cane at a controllable set of positions. */
    private static final class CaneWorld extends FakeWorldService {
        final Set<BlockPos> cane = new HashSet<>();
        final Set<BlockPos> walls = new HashSet<>();
        @Override public boolean isPassable(BlockPos pos) { return !walls.contains(pos); }

        @Override
        public String blockId(BlockPos pos) {
            return cane.contains(pos) ? CropBlocks.SUGAR_CANE : "minecraft:air";
        }
    }
}
