package dev.veltraph.client.log;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.farming.movement.SShapeRowEngine;
import dev.veltraph.client.farming.movement.SShapeState;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.service.stub.NoOpWorldService;
import dev.veltraph.client.testutil.FakeClock;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.testutil.FakeRotationService;
import dev.veltraph.client.testutil.FakeSShapeGeometry;
import dev.veltraph.client.testutil.RecordingBlockBreaking;
import dev.veltraph.client.testutil.RecordingInputService;
import dev.veltraph.client.testutil.RecordingLogSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The macro engine emits the expected DETAILS/VERBOSE events through the global logger facade. */
class MacroLoggingTest {

    private RecordingLogSink sink;
    private VeltraphLogger logger;
    private SShapeRowEngine engine;
    private FakeSShapeGeometry geometry;

    @BeforeEach
    void setUp() {
        sink = new RecordingLogSink();
        logger = new VeltraphLogger(sink, () -> "00:00:00.000", msg -> { });
        logger.setEnabled(true);
        VeltraphLog.set(logger);

        FakeClock clock = new FakeClock();
        ConfigService config = new MapConfigService();
        config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD, false);
        config.set(FarmingConfig.ROTATION_JITTER, false);
        config.set(FarmingConfig.ROTATION_ACCELERATION, 1.0);
        config.set(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK, 90.0);
        config.set(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK, 90.0);
        FakePlayerService player = new FakePlayerService();
        player.onGround = true;
        geometry = new FakeSShapeGeometry();
        FarmServices services = new FarmServices(clock, player, new NoOpWorldService(),
                new RecordingInputService(), new FakeRotationService(), new RecordingBlockBreaking(),
                new NoOpInventoryService());
        engine = new SShapeRowEngine(config, services, geometry, () -> true);
    }

    @AfterEach
    void tearDown() {
        VeltraphLog.set(null); // back to the disabled no-op default
    }

    private void run(int ticks) {
        engine.start();
        for (int i = 0; i < ticks; i++) {
            engine.tick();
        }
    }

    @Test
    void detailsModeEmitsStateTransitionsAndAimTargets() {
        logger.setMode(DebugLogMode.DETAILS);
        run(6);
        assertTrue(engine.state() == SShapeState.FARMING_SIDEWAYS || sink.hasLineContaining("SShapeRowEngine", "->"),
                "reached farming or logged a transition");
        assertTrue(sink.hasLineContaining("[DETAILS]", "SShapeRowEngine", "->"),
                "state transition logged at DETAILS");
        assertTrue(sink.hasLineContaining("[DETAILS]", "SShapeRowEngine.aim", "target="),
                "aim target vs current logged at DETAILS");
        assertTrue(sink.hasLineContaining("[DETAILS]", "break"),
                "breaking state change logged at DETAILS");
        assertFalse(sink.hasLineContaining("[VERBOSE]"), "no verbose lines in DETAILS mode");
    }

    @Test
    void verboseModeEmitsMethodLevelAndInputEvents() {
        logger.setMode(DebugLogMode.VERBOSE);
        run(6);
        assertTrue(sink.hasLineContaining("[VERBOSE]", "SShapeRowEngine.tick", "state="),
                "per-tick method-level decision logged at VERBOSE");
        assertTrue(sink.hasLineContaining("[VERBOSE]", "SShapeRowEngine.input"),
                "input set/release logged at VERBOSE");
    }

    @Test
    void basicModeDoesNotEmitEngineDetailOrVerbose() {
        logger.setMode(DebugLogMode.BASIC);
        run(6);
        assertFalse(sink.hasLineContaining("SShapeRowEngine", "->"), "no detail transitions in BASIC");
        assertFalse(sink.hasLineContaining("[VERBOSE]"), "no verbose in BASIC");
    }
}
