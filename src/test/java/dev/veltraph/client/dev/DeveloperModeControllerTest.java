package dev.veltraph.client.dev;

import dev.veltraph.client.config.DebugConfig;
import dev.veltraph.client.config.DeveloperConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.log.DebugLogController;
import dev.veltraph.client.log.DebugLogMode;
import dev.veltraph.client.log.LogSink;
import dev.veltraph.client.log.VeltraphLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Developer Mode persistence/defaults + verbose-logging enable + non-destructive restore. */
class DeveloperModeControllerTest {

    private static final class RecordingSink implements LogSink {
        @Override public void write(String line) { }
        @Override public String describe() { return "test-sink"; }
    }

    private static DeveloperModeController controller(MapConfigService cfg) {
        VeltraphLogger logger = new VeltraphLogger(null, () -> "t", m -> { });
        DebugLogController log = new DebugLogController(cfg, logger, RecordingSink::new, m -> { });
        log.initFromConfig();
        return new DeveloperModeController(cfg, log);
    }

    @Test
    void defaultsOff() {
        MapConfigService cfg = new MapConfigService();
        DeveloperModeController dm = controller(cfg);
        assertFalse(dm.enabled());
        assertFalse(cfg.get(DebugConfig.DEBUG_LOG_ENABLED));
        assertEquals(DebugLogMode.BASIC, cfg.get(DebugConfig.DEBUG_LOG_MODE));
    }

    @Test
    void enablingForcesVerboseLogging() {
        MapConfigService cfg = new MapConfigService();
        DeveloperModeController dm = controller(cfg);
        dm.setEnabled(true);
        assertTrue(dm.enabled());
        assertTrue(cfg.get(DeveloperConfig.DEV_MODE_ENABLED));
        assertTrue(cfg.get(DebugConfig.DEBUG_LOG_ENABLED), "verbose logging must be enabled");
        assertEquals(DebugLogMode.VERBOSE, cfg.get(DebugConfig.DEBUG_LOG_MODE));
    }

    @Test
    void disablingRestoresPreviousLoggingSettings() {
        MapConfigService cfg = new MapConfigService();
        // user had DETAILS logging on before enabling Dev Mode
        cfg.set(DebugConfig.DEBUG_LOG_ENABLED, true);
        cfg.set(DebugConfig.DEBUG_LOG_MODE, DebugLogMode.DETAILS);
        DeveloperModeController dm = controller(cfg);

        dm.setEnabled(true);
        assertEquals(DebugLogMode.VERBOSE, cfg.get(DebugConfig.DEBUG_LOG_MODE));

        dm.setEnabled(false);
        assertFalse(dm.enabled());
        assertTrue(cfg.get(DebugConfig.DEBUG_LOG_ENABLED), "previous enabled=true restored");
        assertEquals(DebugLogMode.DETAILS, cfg.get(DebugConfig.DEBUG_LOG_MODE), "previous DETAILS restored");
    }

    @Test
    void disablingRestoresOffWhenUserHadLoggingOff() {
        MapConfigService cfg = new MapConfigService();
        DeveloperModeController dm = controller(cfg); // defaults: logging off / BASIC
        dm.setEnabled(true);
        dm.setEnabled(false);
        assertFalse(cfg.get(DebugConfig.DEBUG_LOG_ENABLED), "logging restored to OFF (not erased to something else)");
        assertEquals(DebugLogMode.BASIC, cfg.get(DebugConfig.DEBUG_LOG_MODE));
    }

    @Test
    void persistedOnIsReAppliedAtStartup() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(DeveloperConfig.DEV_MODE_ENABLED, true); // as if persisted from a prior launch
        DeveloperModeController dm = controller(cfg);
        dm.initFromConfig();
        assertTrue(dm.enabled());
        assertTrue(cfg.get(DebugConfig.DEBUG_LOG_ENABLED));
        assertEquals(DebugLogMode.VERBOSE, cfg.get(DebugConfig.DEBUG_LOG_MODE));
    }

    @Test
    void enablingTwiceDoesNotClobberTheSnapshot() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(DebugConfig.DEBUG_LOG_ENABLED, true);
        cfg.set(DebugConfig.DEBUG_LOG_MODE, DebugLogMode.DETAILS);
        DeveloperModeController dm = controller(cfg);
        dm.setEnabled(true);
        dm.setEnabled(true); // idempotent — must not snapshot the forced VERBOSE over the real DETAILS
        dm.setEnabled(false);
        assertEquals(DebugLogMode.DETAILS, cfg.get(DebugConfig.DEBUG_LOG_MODE));
    }
}
