package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.service.stub.NoOpWorldService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakeInventoryService;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.FakeRowGeometry;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementDirectionEngineTest {

    private FakeClock clock;
    private RecordingInputService input;
    private RecordingBlockBreaking breaking;
    private FakeRowGeometry geometry;
    private FakePlayerService player;
    private FakeRotationService rotation;
    private ConfigService config;
    private MovementDirectionEngine engine;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        input = new RecordingInputService();
        breaking = new RecordingBlockBreaking();
        geometry = new FakeRowGeometry();
        config = new MapConfigService();
        // Deterministic aim for assertions: bounded LERP at 30°/tick, no GCD / jitter / accel ramp.
        config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD, false);
        config.set(FarmingConfig.ROTATION_JITTER, false);
        config.set(FarmingConfig.ROTATION_ACCELERATION, 1.0);
        config.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK, 30.0);
        config.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK, 30.0);
        config.set(FarmingConfig.ROTATION_TOLERANCE_DEG, 4.0);
        config.set(FarmingConfig.ROTATION_PAUSE_ERROR_DEG, 25.0);
        player = new FakePlayerService();
        player.onGround = true;
        rotation = new FakeRotationService();
        FarmServices services = new FarmServices(clock, player, new NoOpWorldService(),
                input, rotation, breaking, new NoOpInventoryService());
        engine = new MovementDirectionEngine(config, services, geometry);
    }

    /** start → PREPARE_TOOL → AIMING → FARMING (lane valid, grounded, aim aligned at 0). */
    private void driveToFarming() {
        clock.setMillis(0);
        geometry.forwardLaneValid = true;
        engine.start();
        engine.tick(); // PREPARE_TOOL -> AIMING
        engine.tick(); // AIMING -> FARMING
        assertEquals(MovementState.FARMING, engine.state());
    }

    @Test
    void startEntersPrepareTool() {
        clock.setMillis(0);
        engine.start();
        assertEquals(MovementState.PREPARE_TOOL, engine.state());
    }

    @Test
    void reachesFarmingAndDrivesForward() {
        driveToFarming();
        engine.tick(); // FARMING drives forward
        assertTrue(input.forward, "farming drives forward once aimed + lane valid");
        assertTrue(breaking.breaking);
    }

    @Test
    void aimNeverSnaps() {
        // A 90° correction must take several ticks (bounded), never reach the target in one.
        rotation.yaw = 90f;
        geometry.overrideLaneYaw = true;
        geometry.laneYawValue = 0f;
        geometry.forwardLaneValid = true;
        clock.setMillis(0);
        engine.start();
        engine.tick(); // -> AIMING
        engine.tick(); // first aim step
        assertNotEquals(0f, rotation.yaw, "aim does not snap to the target");
        assertTrue(rotation.yaw < 90f && rotation.yaw > 0f, "stepped partway: " + rotation.yaw);
    }

    @Test
    void wrongInitialYawIsCorrectedBeforeForward() {
        rotation.yaw = 90f;
        geometry.overrideLaneYaw = true;
        geometry.laneYawValue = 0f;
        geometry.forwardLaneValid = true;

        clock.setMillis(0);
        engine.start();
        engine.tick(); // -> AIMING
        assertEquals(MovementState.AIMING, engine.state());

        engine.tick(); // first aim step toward 0 (30°/tick)
        assertEquals(60f, rotation.yaw, 0.001f, "aim steps toward the lane heading");
        assertFalse(input.forward, "must not move forward while aim is wrong");

        for (int i = 0; i < 8 && engine.state() != MovementState.FARMING; i++) {
            assertFalse(input.forward, "no forward until aim is within tolerance");
            engine.tick();
        }
        assertEquals(MovementState.FARMING, engine.state());
        assertEquals(0f, rotation.yaw, 0.001f);

        engine.tick();
        assertTrue(input.forward, "forward only after the aim is corrected");
    }

    @Test
    void smallDriftIsCorrectedWhileForwardAndBreakContinue() {
        driveToFarming();
        clock.setMillis(0); // keep the turn on cooldown so we stay in FARMING
        rotation.yaw = 10f; // small drift: 10° (> tolerance 4, < pause 25)
        engine.tick();
        assertEquals(MovementState.FARMING, engine.state(), "small drift does not pause the row");
        assertTrue(input.forward, "forward stays held while small drift is corrected");
        assertTrue(breaking.breaking, "break stays held while small drift is corrected");
        assertEquals(0f, rotation.yaw, 0.001f, "the drift was corrected this same tick");
    }

    @Test
    void largeDriftPausesMovementButKeepsCorrecting() {
        driveToFarming();
        clock.setMillis(0);
        rotation.yaw = 90f; // large drift (> pause threshold 25)
        engine.tick();
        assertEquals(MovementState.AIMING, engine.state(), "large drift pauses to AIMING");
        assertFalse(input.forward, "forward released while paused for a large correction");
        assertFalse(breaking.breaking, "break released while paused for a large correction");
        assertEquals(60f, rotation.yaw, 0.001f, "aim still corrects while paused");
    }

    @Test
    void noForwardWhilePlayerNotGrounded() {
        driveToFarming();
        player.onGround = false;
        engine.tick();
        assertEquals(MovementState.AIMING, engine.state(), "leaving the ground pauses to AIMING");
        assertFalse(input.forward);
    }

    @Test
    void farmingTurnsAtRowEndAfterCooldown() {
        driveToFarming();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.rightMovable = true;
        engine.tick();
        assertEquals(MovementState.TURNING, engine.state());
        assertEquals(TurnDirection.RIGHT, engine.currentTurnDirection());
    }

    @Test
    void turnHoldsLateralKeyAndKeepsBreaking() {
        config.set(FarmingConfig.SSHAPE_FLIP_HEADING_ON_TURN, false);
        driveToFarming();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.rightMovable = true;
        engine.tick(); // -> TURNING
        assertEquals(MovementState.TURNING, engine.state());

        // While turning, the lateral (right) key is held and breaking is NOT fully stopped.
        engine.tick();
        assertTrue(input.right, "row-end turn holds the lateral key");
        assertFalse(input.left);
        assertFalse(input.forward, "no forward while strafing the lane switch");
        assertTrue(breaking.breaking, "break stays held through the lane switch");
    }

    @Test
    void turnFlowsThroughReaimAndGoingForwardBackToFarming() {
        config.set(FarmingConfig.SSHAPE_FLIP_HEADING_ON_TURN, false);
        driveToFarming();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.rightMovable = true;
        engine.tick(); // FARMING -> TURNING
        assertEquals(MovementState.TURNING, engine.state());

        // The lane is reachable again after the switch.
        geometry.forwardLaneValid = true;
        for (int i = 0; i < MovementDirectionEngine.LANE_STEP_TICKS + 1
                && engine.state() == MovementState.TURNING; i++) {
            engine.tick();
        }
        assertEquals(MovementState.REAIM_AFTER_TURN, engine.state());

        engine.tick(); // REAIM -> GOING_FORWARD (aim already aligned, flip disabled)
        assertEquals(MovementState.GOING_FORWARD, engine.state());

        geometry.atRowEnd = false;
        engine.tick(); // GOING_FORWARD drives forward and re-validates -> FARMING
        assertTrue(input.forward);
        assertEquals(MovementState.FARMING, engine.state());
    }

    @Test
    void turnFlipsHeading180WhenConfigured() {
        config.set(FarmingConfig.SSHAPE_FLIP_HEADING_ON_TURN, true);
        driveToFarming();
        float before = engine.controlledYaw();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.rightMovable = true;
        engine.tick(); // -> TURNING, heading flipped
        assertEquals(Angles.wrapDegrees(before + 180f), engine.controlledYaw(), 0.001f);
    }

    @Test
    void turnDoesNotFireWhileOnCooldown() {
        driveToFarming();
        clock.setMillis(100); // < 14900 cooldown
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.rightMovable = true;
        engine.tick();
        assertEquals(MovementState.FARMING, engine.state(), "must not turn before cooldown elapses");

        clock.setMillis(20_000);
        engine.tick();
        assertEquals(MovementState.TURNING, engine.state());
    }

    @Test
    void sideSelectionProbesRightBeforeLeft() {
        driveToFarming();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.leftMovable = true;
        geometry.rightMovable = true;
        engine.tick();
        assertEquals(TurnDirection.RIGHT, engine.currentTurnDirection(), "right is probed first");
    }

    @Test
    void fallsBackToLeftWhenRightBlocked() {
        driveToFarming();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.leftMovable = true;
        geometry.rightMovable = false;
        engine.tick();
        assertEquals(TurnDirection.LEFT, engine.currentTurnDirection());
    }

    @Test
    void neitherSideMovableLeadsToRecovering() {
        driveToFarming();
        clock.setMillis(20_000);
        geometry.atRowEnd = true;
        geometry.forwardLaneValid = false;
        geometry.leftMovable = false;
        geometry.rightMovable = false;
        engine.tick(); // FARMING -> TURNING (no side)
        engine.tick(); // TURNING: no side -> RECOVERING
        assertEquals(MovementState.RECOVERING, engine.state());
    }

    @Test
    void stopReleasesAllInputs() {
        driveToFarming();
        engine.tick();
        engine.stop();
        assertEquals(MovementState.STOPPED, engine.state());
        assertTrue(input.releaseAllCount >= 1);
        assertFalse(breaking.breaking);
        assertFalse(input.forward);
    }

    @Test
    void toolNotReadyHoldsInPrepareTool() {
        boolean[] ready = {false};
        FarmServices services = new FarmServices(clock, player, new NoOpWorldService(),
                input, rotation, breaking, new FakeInventoryService());
        MovementDirectionEngine gated = new MovementDirectionEngine(
                config, services, geometry, FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, () -> ready[0]);
        clock.setMillis(0);
        geometry.forwardLaneValid = true;
        gated.start();
        gated.tick();
        assertEquals(MovementState.PREPARE_TOOL, gated.state(), "stays in PREPARE_TOOL until tool ready");
        assertFalse(input.forward);
        ready[0] = true;
        gated.tick();
        assertEquals(MovementState.AIMING, gated.state());
    }
}
