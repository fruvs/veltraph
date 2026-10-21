package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.HoldForwardPolicy;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.service.stub.NoOpWorldService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.FakeSShapeGeometry;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lateral S-shape engine: NEVER still moves/breaks, crop-target aim, side-to-side axis, no snap. */
class SShapeRowEngineTest {

    private FakeClock clock;
    private RecordingInputService input;
    private RecordingBlockBreaking breaking;
    private FakeSShapeGeometry geometry;
    private FakePlayerService player;
    private FakeRotationService rotation;
    private ConfigService config;
    private SShapeRowEngine engine;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        input = new RecordingInputService();
        breaking = new RecordingBlockBreaking();
        geometry = new FakeSShapeGeometry();
        config = new MapConfigService();
        // Deterministic, fast aim for state assertions (no gcd / jitter; reaches target quickly).
        config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD, false);
        config.set(FarmingConfig.ROTATION_JITTER, false);
        config.set(FarmingConfig.ROTATION_ACCELERATION, 1.0);
        config.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK, 90.0);
        config.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK, 90.0);
        config.set(FarmingConfig.ROTATION_TOLERANCE_DEG, 4.0);
        config.set(FarmingConfig.ROTATION_PAUSE_ERROR_DEG, 25.0);
        player = new FakePlayerService();
        player.onGround = true;
        rotation = new FakeRotationService();
        engine = newEngine(() -> true);
    }

    private SShapeRowEngine newEngine(java.util.function.BooleanSupplier tool) {
        FarmServices services = new FarmServices(clock, player, new NoOpWorldService(),
                input, rotation, breaking, new NoOpInventoryService());
        return new SShapeRowEngine(config, services, geometry,
                FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, tool);
    }

    private void driveToSideways() {
        clock.setMillis(0);
        engine.start();
        for (int i = 0; i < 6 && engine.state() != SShapeState.FARMING_SIDEWAYS; i++) {
            engine.tick();
        }
        assertEquals(SShapeState.FARMING_SIDEWAYS, engine.state());
    }

    @Test
    void neverPolicyStillStrafesAndBreaks() {
        // Default SSHAPE_HOLD_FORWARD_POLICY is NEVER.
        assertEquals(HoldForwardPolicy.NEVER, config.get(FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY));
        driveToSideways();
        engine.tick(); // a FARMING_SIDEWAYS tick drives inputs
        assertTrue(input.right, "lateral strafe drives the row even with NEVER policy");
        assertFalse(input.left);
        assertFalse(input.forward, "NEVER = no continuous forward during the sideways sweep");
        assertTrue(breaking.breaking, "break is held to harvest the crop in front");
    }

    @Test
    void alwaysPolicyAlsoHoldsForward() {
        config.set(FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, HoldForwardPolicy.ALWAYS);
        driveToSideways();
        engine.tick();
        assertTrue(input.right);
        assertTrue(input.forward, "ALWAYS additionally holds forward through the sweep");
    }

    @Test
    void aimTargetsCropDownwardNotZeroPitch() {
        clock.setMillis(0);
        engine.start();
        engine.tick(); // -> AIMING
        engine.tick(); // pans toward the crop target
        assertTrue(engine.targetPitch() > 5f, "crop target pitch is downward, not 0 (was " + engine.targetPitch() + ")");
        assertTrue(rotation.pitch > 0f, "aim pans the view down toward the crop");
    }

    @Test
    void mainTraversalIsLateralThenForwardThenReverse() {
        driveToSideways();
        engine.tick();
        assertEquals(TurnDirection.RIGHT, engine.lateralSide(), "starts strafing right");
        assertTrue(input.right);

        // Row end to the right; forward step available.
        geometry.canStrafeRight = false;
        geometry.canStepForward = true;
        engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD, engine.state(), "row end -> forward step");
        assertTrue(input.forward, "row transition walks forward into the next row");
        assertFalse(input.right, "strafe released during the forward step");
        assertTrue(breaking.breaking, "break stays held through the transition");

        // Hold forward for the configured ticks, then reverse the lateral direction.
        int t = config.get(FarmingConfig.ROW_TRANSITION_FORWARD_TICKS);
        for (int i = 0; i < t + 1 && engine.state() == SShapeState.ROW_TRANSITION_FORWARD; i++) {
            engine.tick();
        }
        assertEquals(SShapeState.FARMING_SIDEWAYS, engine.state());
        assertEquals(TurnDirection.LEFT, engine.lateralSide(), "reverses to the other lateral direction");
        assertTrue(engine.isReturnSweep());

        geometry.canStrafeLeft = true;
        engine.tick();
        assertTrue(input.left, "return sweep strafes the other way");
        assertFalse(input.right);
    }

    @Test
    void rowTransitionWaitsForNearMiddle() {
        // Recovered atRowEnd() gating: don't step forward into the next row until centred.
        driveToSideways();
        geometry.canStrafeRight = false; // row end on the right
        geometry.canStepForward = true;
        geometry.nearMiddle = false;     // not yet centred
        engine.tick();
        assertEquals(SShapeState.FARMING_SIDEWAYS, engine.state(),
                "must not transition forward while off-centre");
        assertFalse(input.forward, "no forward step until near-middle");
        assertTrue(breaking.breaking, "keeps breaking while settling");
        assertTrue(engine.pauseReason().toLowerCase().contains("near-middle"));

        geometry.nearMiddle = true;      // now centred
        engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD, engine.state(), "transitions once centred");
        assertTrue(input.forward);
    }

    @Test
    void nearMiddleGateCanBeDisabled() {
        config.set(FarmingConfig.REQUIRE_NEAR_MIDDLE_FOR_TURN, false);
        driveToSideways();
        geometry.canStrafeRight = false;
        geometry.canStepForward = true;
        geometry.nearMiddle = false; // off-centre, but the gate is disabled
        engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD, engine.state(),
                "with the gate off, transitions regardless of centring");
    }

    @Test
    void attackCadenceRetapsReleasesTheBreakPeriodically() {
        config.set(FarmingConfig.ATTACK_CADENCE_TICKS, 3);
        driveToSideways();
        // Over a run of farming ticks the break should release on the cadence boundary, not stay held
        // every single tick (proves the re-tap is wired); default behaviour is covered elsewhere.
        boolean sawRelease = false;
        boolean sawHold = false;
        for (int i = 0; i < 9; i++) {
            engine.tick();
            if (breaking.breaking) {
                sawHold = true;
            } else {
                sawRelease = true;
            }
        }
        assertTrue(sawHold, "the break is held most ticks");
        assertTrue(sawRelease, "the break re-taps (releases) on the cadence boundary");
    }

    @Test
    void macroOwnedHeadingIsCapturedOnceAndStable() {
        rotation.yaw = 0f; // snaps to SOUTH (yaw 0)
        clock.setMillis(0);
        engine.start();
        var heading = engine.heading();
        // The camera drifts and the player "faces" elsewhere; the macro heading must not follow it.
        rotation.yaw = 87f;
        player.facing = dev.veltraph.client.world.Direction.WEST;
        engine.tick();
        engine.tick();
        assertEquals(heading, engine.heading(), "heading is macro-owned, not re-derived from the camera");
    }

    @Test
    void setupTurnCannotCompleteInOneTick() {
        // Use the real (slow) rotation defaults; only disable jitter for determinism.
        config.reset(FarmingConfig.ROTATION_ACCELERATION);
        config.reset(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK);
        config.reset(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK);
        config.reset(FarmingConfig.ROTATION_TOLERANCE_DEG);
        rotation.yaw = 0f;
        rotation.pitch = 0f; // must pan down ~58° to the crop target
        clock.setMillis(0);
        engine.start();
        engine.tick(); // -> AIMING
        float targetPitch = engine.targetPitch();
        engine.tick(); // one aim step
        assertNotEquals(targetPitch, rotation.pitch, 0.001f, "a large setup turn cannot finish in one tick");
        assertTrue(rotation.pitch < targetPitch, "it pans partway: " + rotation.pitch + " < " + targetPitch);
        assertEquals(SShapeState.AIMING, engine.state(), "still aiming, not yet farming");
    }

    @Test
    void stuckWithNoLaneRecoversAndReportsReason() {
        driveToSideways();
        geometry.canStrafeRight = false;
        geometry.canStrafeLeft = false;
        geometry.canStepForward = false;
        engine.tick(); // FARMING_SIDEWAYS: row end, no lane -> RECOVERING
        assertEquals(SShapeState.RECOVERING, engine.state());
        assertTrue(engine.pauseReason().toLowerCase().contains("no lane")
                || engine.pauseReason().toLowerCase().contains("recover"),
                "pause reason names the failed predicate: " + engine.pauseReason());
        assertFalse(input.forward);
        assertFalse(breaking.breaking);
    }

    @Test
    void toolNotReadyHoldsInPrepareTool() {
        boolean[] ready = {false};
        SShapeRowEngine gated = newEngine(() -> ready[0]);
        clock.setMillis(0);
        gated.start();
        gated.tick();
        assertEquals(SShapeState.PREPARE_TOOL, gated.state());
        assertFalse(input.forward);
        ready[0] = true;
        gated.tick();
        assertEquals(SShapeState.AIMING, gated.state());
    }

    @Test
    void stopReleasesAllInputs() {
        driveToSideways();
        engine.tick();
        engine.stop();
        assertEquals(SShapeState.STOPPED, engine.state());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(input.forward);
    }


    @Test void actualDestinationCannotCompleteOnElapsedTicksOrWithoutMovement() {
        driveToSideways();
        geometry.canStrafeRight = false;
        geometry.transitionTarget = new dev.veltraph.client.world.Vec3(0.5,64,7.5);
        engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD, engine.state());
        for (int i=0; i<50; i++) engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD, engine.state());
        assertEquals(0, engine.completedRowTransitions());
        player.position = new dev.veltraph.client.world.Vec3(0.5,64,7.4);
        engine.tick();
        assertEquals(SShapeState.FARMING_SIDEWAYS, engine.state());
        assertEquals(1, engine.completedRowTransitions());
        assertEquals(TurnDirection.LEFT, engine.lateralSide());
        assertTrue(input.left);
        assertFalse(input.forward);
    }

    @Test void offCentreEndUsesOrdinaryCorrectiveKeysAndBarePathsDoNotAttack() {
        driveToSideways();
        geometry.canStrafeRight = false;
        geometry.nearMiddle = false;
        geometry.endCenter = new dev.veltraph.client.world.Vec3(0.8,64,0.5);
        geometry.harvestAvailable = false;
        engine.tick();
        assertTrue(input.left, "south-facing left moves east toward the end centre");
        assertTrue(input.sneak, "small centring correction slows before arrival");
        assertFalse(breaking.breaking, "no attack into a bare path/support");
        assertEquals(SShapeState.FARMING_SIDEWAYS,engine.state());
    }

    @Test void returnInvalidatesOldTurnDestinationAndHonoursDirectionSwitchOnce() {
        driveToSideways();
        var heading = engine.heading();
        geometry.canStrafeRight = false;
        geometry.transitionTarget = new dev.veltraph.client.world.Vec3(0.5,64,7.5);
        engine.tick(); assertEquals(SShapeState.ROW_TRANSITION_FORWARD,engine.state());
        config.set(FarmingConfig.NETHERWART_SWITCH_DIRECTION_ON_TP,true);
        rotation.yaw = 90;
        engine.resumeFromObservedPosition();
        assertEquals(heading,engine.heading());
        assertEquals(TurnDirection.LEFT,engine.lateralSide());
        assertEquals(null,engine.transitionTarget());
        assertEquals(SShapeState.AIMING,engine.state());
        assertFalse(input.forward); assertFalse(input.left); assertFalse(input.right);
        assertEquals(0,engine.completedRowTransitions());
    }

    @Test void disabledDropPolicyDoesNotDisableOrdinarySupportedRowCrossings() {
        driveToSideways(); config.set(FarmingConfig.DROP_AND_TURN,false);
        geometry.canStrafeRight = false;
        geometry.transitionTarget = new dev.veltraph.client.world.Vec3(0.5,64,7.5);
        engine.tick();
        assertEquals(SShapeState.ROW_TRANSITION_FORWARD,engine.state());
        assertEquals(TurnDirection.RIGHT,engine.lateralSide());
        assertEquals(0,engine.completedRowTransitions());
        assertTrue(input.forward); assertFalse(input.back); assertFalse(input.right);
    }

    @Test void intentionalDropNeedsObservedLowerGroundAndPreservesOwnedHeading(){
        class DropDriver implements FarmRouteDriver {
            State result=State.ACTIVE;
            public boolean startDrop(dev.veltraph.client.world.Vec3 target,int bound){assertEquals(3,bound);return true;}
            public State tick(){return result;} public void stop(){}
            public String status(){return result.name();}
        }
        var driver=new DropDriver();engine.setDropDriver(driver);driveToSideways();var heading=engine.heading();
        geometry.canStrafeRight=false;geometry.dropTarget=new dev.veltraph.client.world.Vec3(-.5,61,.5);
        engine.tick();assertEquals(SShapeState.INTENTIONAL_DROP,engine.state());assertFalse(breaking.breaking);
        rotation.yaw=90;player.onGround=false;engine.tick();assertEquals(90,rotation.yaw,"farm aim pauses during navigation ownership");
        driver.result=FarmRouteDriver.State.ARRIVED;player.position=geometry.dropTarget;player.onGround=true;
        engine.tick();assertEquals(1,engine.completedHeightDrops());assertEquals(heading,engine.heading());
        assertEquals(TurnDirection.LEFT,engine.lateralSide());assertEquals(SShapeState.AIMING,engine.state());
    }

    @Test
    void diagnosticsReportsStateKeysAndPredicates() {
        driveToSideways();
        engine.tick();
        String d = engine.diagnostics();
        assertTrue(d.contains("FARMING_SIDEWAYS"), d);
        assertTrue(d.contains("break="), d);
        assertTrue(d.contains("pause="), d);
    }
}
