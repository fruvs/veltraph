package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.MushroomFarmDesign;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.domain.FarmMovementMode;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.planner.MushroomStaircasePlanner;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
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
 * Module-level test of the recovered mushroom staircase step + classical traversal, the macro-owned
 * heading capture, and the on-ground/tool gating. Uses a per-position world so the forward/diagonal
 * staircase probes can be steered independently.
 */
class MushroomFarmModuleTest {

    private FakeClock clock;
    private TestWorld world;
    private FakePlayerService player;
    private FakeRotationService rotation;
    private RecordingInputService input;
    private MushroomBreaking breaking;
    private MapConfigService config;
    private MushroomFarmModule mushroom;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        world = new TestWorld();
        player = new FakePlayerService();
        player.onGround = true;
        player.blockPos = new BlockPos(0, 64, 0);
        rotation = new FakeRotationService();
        rotation.yaw = 210f; // snapToCardinal(180)=180 -> Direction.fromYaw(180)=NORTH (macro heading)
        input = new RecordingInputService();
        breaking = new MushroomBreaking();
        config = new MapConfigService();
        config.set(FarmingConfig.MUSHROOM_RING_START_FORWARD,false);
        FarmServices services = new FarmServices(clock, player, world, input,
                rotation, breaking, new NoOpInventoryService());
        mushroom = new MushroomFarmModule(services, config, FarmLayoutRegistry.createDefault());
    }

    // ---- public API contract (unchanged) ----

    @Test
    void idAndDefaultModeAndModeApiUnchanged() {
        assertEquals("mushroom", MushroomFarmModule.ID);
        assertEquals(FarmMovementMode.CLASSICAL, mushroom.mode());
        mushroom.setMode(FarmMovementMode.STAIRCASE);
        assertEquals(FarmMovementMode.STAIRCASE, mushroom.mode());
    }

    @Test
    void enableCapturesMacroHeadingFromYaw() {
        mushroom.setEnabled(true);
        assertEquals(MacroState.RUNNING, mushroom.state());
        assertEquals(Direction.NORTH, mushroom.heading());
    }

    // ---- mid-row vs gating ----

    @Test
    void midRowHoldsForwardAndBreaks() {
        world.allWalkable = true; // forward lane valid -> not a row end
        mushroom.setEnabled(true);
        mushroom.onTick();
        assertTrue(input.forward, "mid-row holds forward");
        assertTrue(breaking.breaking, "mid-row keeps breaking");
        assertFalse(input.left);
        assertFalse(input.right);
    }

    @Test
    void notOnGroundPausesMotionAndBreaking() {
        world.allWalkable = true;
        mushroom.setEnabled(true);
        player.onGround = false;
        mushroom.onTick();
        assertFalse(input.forward, "airborne pauses forward");
        assertFalse(breaking.breaking, "airborne stops breaking");
    }

    // ---- classical fixed-side row-end turn ----

    @Test
    void classicalRowEndStepsToFixedMushroomDirSide() {
        config.set(FarmingConfig.MUSHROOM_DIR, LaneSide.LEFT);
        // Forward (NORTH) blocked -> row end; LEFT diagonal standable so the side+forward commits.
        world.allWalkable = false;
        world.standable.add(new BlockPos(0, 63, 0)); // floor under the player
        BlockPos leftDiag = new BlockPos(0, 64, 0)
                .offset(Direction.NORTH.rotateYCounterclockwise()) // LEFT of NORTH = WEST
                .offset(Direction.NORTH);
        world.standable.add(leftDiag.down());
        mushroom.setEnabled(true);
        mushroom.onTick();
        assertEquals(TurnDirection.LEFT, mushroom.lastSelection());
        assertTrue(input.left, "classical row-end strafes to the fixed MUSHROOM_DIR side");
        assertFalse(input.right);
    }

    // ---- staircase probe-driven step ----

    @Test
    void staircaseRowEndKeepsMushroomDirWhenProbeFound() {
        config.set(FarmingConfig.MUSHROOM_DIR, LaneSide.LEFT);
        mushroom.setMode(FarmMovementMode.STAIRCASE);
        // Row end: the immediate forward step (NORTH, i=1) is clear overhead but has NO standable floor
        // (neither body.down nor body), so forwardStep() fails -> atRowEnd, and the probe reads NONE at
        // i=1. Two steps ahead (i=2) a standable floor continues the staircase, so the depth-3 probe
        // returns FOUND for the current dir and the selector returns MUSHROOM_DIR (LEFT).
        world.allWalkable = false;
        // i=1 ahead: passable body+head but not standable -> probe NONE, forwardStep false (row end).
        world.passable.add(new BlockPos(0, 64, -1));
        world.passable.add(new BlockPos(0, 65, -1));
        // i=2 ahead: passable body+head + standable floor -> probe FOUND at depth 2.
        world.passable.add(new BlockPos(0, 64, -2));
        world.passable.add(new BlockPos(0, 65, -2));
        world.standable.add(new BlockPos(0, 63, -2));
        // LEFT diagonal continuation standable so the side+forward stair step commits.
        BlockPos leftDiag = new BlockPos(0, 64, 0)
                .offset(Direction.NORTH.rotateYCounterclockwise()) // LEFT of NORTH = WEST
                .offset(Direction.NORTH);
        world.standable.add(leftDiag.down());
        mushroom.setEnabled(true);
        mushroom.onTick();
        assertEquals(TurnDirection.LEFT, mushroom.lastSelection());
        assertTrue(input.left, "staircase FOUND probe steps to MUSHROOM_DIR side");
        assertTrue(input.forward, "side+forward stair step commits onto the standable continuation");
    }

    @Test
    void staircaseRowEndGoesForwardWhenProbeBlocked() {
        mushroom.setMode(FarmMovementMode.STAIRCASE);
        // Forward heading at a row end (no standable floor ahead), and the body/head ahead are walls ->
        // BLOCKED for the current dir -> selector returns FORWARD.
        world.allWalkable = false; // nothing passable/standable -> first probe step is BLOCKED
        world.standable.add(new BlockPos(0, 63, 0)); // floor under player so we are grounded-valid
        mushroom.setEnabled(true);
        mushroom.onTick();
        assertEquals(TurnDirection.FORWARD, mushroom.lastSelection());
        assertTrue(input.forward, "staircase BLOCKED probe continues forward/up");
        assertFalse(input.left);
        assertFalse(input.right);
    }

    // ---- path mode ----

    @Test
    void pathModeExtremeWhenDownForwardWalkable() {
        world.allWalkable = true;
        world.standable.add(new BlockPos(0, 63, -1)); // playerPos.down().offset(NORTH) standable
        mushroom.setEnabled(true);
        mushroom.onTick();
        assertEquals(MushroomStaircasePlanner.PathMode.EXTREME, mushroom.pathMode());
    }

    @Test
    void disableReleasesInputs() {
        world.allWalkable = true;
        mushroom.setEnabled(true);
        mushroom.onTick();
        mushroom.setEnabled(false);
        assertEquals(MacroState.STOPPED, mushroom.state());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(input.forward);
    }

    @Test void recoveredAimMatchesDecodedSideOffsetAndZeroPitch() {
        mushroom.setEnabled(true);mushroom.onTick();assertEquals(0f,dev.veltraph.client.farming.movement.Angles.signedDelta(210f,rotation.yaw),.15);
        assertEquals(0f,rotation.pitch,.15);assertTrue(input.forward);
        mushroom.setEnabled(false);config.set(FarmingConfig.MUSHROOM_DIR,LaneSide.RIGHT);rotation.yaw=150;
        mushroom.setEnabled(true);mushroom.onTick();assertEquals(150f,rotation.yaw,.15);assertTrue(input.forward);
        breaking.id="minecraft:quartz_slab";mushroom.onTick();assertFalse(breaking.breaking);
    }
    @Test void distantRecoveredAimPausesUntilSmoothAlignment() {
        rotation.yaw=180;mushroom.setEnabled(true);mushroom.onTick();assertFalse(input.forward);assertFalse(breaking.breaking);
        for(int tick=0;tick<40;tick++)mushroom.onTick();assertTrue(input.forward);assertTrue(breaking.breaking);
    }
    private static final class MushroomBreaking implements dev.veltraph.client.service.BlockBreakingService {
        boolean breaking;String id="minecraft:brown_mushroom";
        public void setBreaking(boolean value){breaking=value;}
        public boolean isBreaking(){return breaking;}
        public String targetBlockId(){return id;}
    }

    private void startStraightRing(boolean trapdoor) {
        world.authorQuartz=true;world.ringTrapdoor=trapdoor;rotation.yaw=-116.57f;
        player.position=new Vec3(2.5,64.5,.5);
        mushroom.setDesign(MushroomFarmDesign.RING_CURRENT);mushroom.setEnabled(true);
    }
    @Test void ringSelectsAuthorLaneRoleAndNeverBlindlyReverses() {
        startStraightRing(false);
        for(int tick=0;tick<40;tick++)mushroom.onTick();
        assertTrue(input.forward);assertFalse(input.back);assertEquals(0,mushroom.ringTurns());
        world.ringTrapdoor=true;mushroom.onTick();
        assertTrue(input.back);assertFalse(input.forward);assertEquals(1,mushroom.ringTurns());
        for(int tick=0;tick<40;tick++)mushroom.onTick();assertTrue(input.back);assertEquals(1,mushroom.ringTurns());
        assertEquals(0,dev.veltraph.client.farming.movement.Angles.signedDelta(-116.57f,rotation.yaw),.2);
        world.ringCorner=true;mushroom.onTick();assertFalse(input.forward);assertFalse(input.back);assertFalse(breaking.breaking);
        mushroom.setEnabled(false);assertFalse(input.forward);assertFalse(input.back);
    }
    @Test void ringActualCropIntentReleasesStructuresAndUnknownTerrain() {
        startStraightRing(true);mushroom.onTick();assertTrue(breaking.breaking);
        breaking.id="minecraft:mycelium";mushroom.onTick();assertFalse(breaking.breaking);
        breaking.id="minecraft:red_mushroom";mushroom.onTick();assertTrue(breaking.breaking);
        world.loaded=false;mushroom.onTick();assertFalse(input.forward);assertFalse(input.back);assertFalse(breaking.breaking);
    }
    @Test void ringWrongCropFacingPausesInsteadOfWalkingAcrossKnownVoid() {
        startStraightRing(false);world.ringCropVoid=true;mushroom.onTick();
        assertFalse(input.forward);assertFalse(input.back);assertFalse(breaking.breaking);
        assertTrue(mushroom.diagnostics().contains("WRONG_SIDE"));
        world.ringCropVoid=false;mushroom.onTick();assertTrue(input.forward);
    }
    @Test void authoredRingHoldsItsViewDespiteFrequentGlobalJitterPreference() {
        config.set(FarmingConfig.ROTATION_JITTER,true);
        config.set(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS,1);
        config.set(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS,1);
        config.set(FarmingConfig.ROTATION_JITTER_HOLD_TICKS,4);
        config.set(FarmingConfig.ROTATION_JITTER_MAX_DEG,5.0);
        startStraightRing(false);
        for(int tick=0;tick<128;tick++) {
            mushroom.onTick();assertEquals(-116.57f,rotation.yaw,.0001);assertEquals(0f,rotation.pitch,.0001);
            assertTrue(input.forward);assertFalse(input.back);
        }
        assertTrue(config.get(FarmingConfig.ROTATION_JITTER));
    }
    @Test void ringAirborneAndRewarpRequireFreshLaneContext() {
        startStraightRing(true);mushroom.onTick();assertTrue(input.back);
        player.onGround=false;mushroom.onTick();assertFalse(input.back);
        player.onGround=true;world.ringCorner=true;mushroom.onTick();assertFalse(input.forward);assertFalse(input.back);
        world.ringCorner=false;world.ringTrapdoor=false;mushroom.onTick();assertTrue(input.forward);
        mushroom.suspendForRewarp();mushroom.onTick();assertFalse(input.forward);assertFalse(input.back);
        world.ringTrapdoor=true;mushroom.resumeAfterRewarp();mushroom.onTick();
        assertEquals(0,mushroom.ringTurns());assertTrue(input.back);assertFalse(input.forward);
    }

    /**
     * Per-position {@link WorldService} for the staircase probes. {@code allWalkable} is the default for
     * any block not explicitly listed; {@code passable}/{@code standable} override specific positions.
     */
    private static final class TestWorld implements WorldService {
        boolean allWalkable = true;
        boolean loaded = true;
        boolean authorQuartz,ringTrapdoor,ringCorner,ringCropVoid;
        final Set<BlockPos> passable = new HashSet<>();
        final Set<BlockPos> standable = new HashSet<>();

        @Override
        public boolean isLoaded(BlockPos pos) {
            return loaded;
        }

        @Override
        public boolean isPassable(BlockPos pos) {
            if(authorQuartz && pos.y()==65 && Math.abs(pos.z())==1)return false;
            return passable.contains(pos) || allWalkable;
        }

        @Override
        public boolean isStandable(BlockPos pos) {
            return standable.contains(pos) || allWalkable;
        }

        @Override public boolean bodyClear(Vec3 p,double width,double height) {
            if(!authorQuartz)return WorldService.super.bodyClear(p,width,height);
            return loaded&&Math.abs(p.y()-64.5)<.01&&p.x()>=.3&&p.x()<=4.7&&p.z()>=.3&&p.z()<=4.7;
        }
        @Override public boolean hasGroundSupport(Vec3 p,double width) {
            if(!authorQuartz)return WorldService.super.hasGroundSupport(p,width);
            return bodyClear(p,width,1.8);
        }
        @Override
        public String blockId(BlockPos pos) {
            if(!authorQuartz)return "";
            if(pos.y()==64 && pos.z()==0 && pos.x()>=0 && pos.x()<5 && !(ringCorner&&pos.x()==1))return "minecraft:quartz_slab";
            if(pos.y()==65 && pos.z()==0 && ringTrapdoor)return "minecraft:oak_trapdoor";
            if(pos.y()==65 && Math.abs(pos.z())==1)return ringCropVoid&&pos.z()==-1?"minecraft:white_stained_glass":"minecraft:mycelium";
            return "minecraft:air";
        }
    }
}
