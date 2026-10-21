package dev.veltraph.client.log;

import dev.veltraph.client.config.DebugConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.testutil.RecordingLogSink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The controller ties config to the logger + lazily-created sink and reports status. */
class DebugLogControllerTest {

    private VeltraphLogger newLogger(RecordingLogSink sink) {
        return new VeltraphLogger(null, () -> "00:00:00.000", msg -> { });
    }

    @Test
    void enableCreatesSinkLazilyAndActivatesLogging() {
        MapConfigService config = new MapConfigService();
        VeltraphLogger logger = newLogger(null);
        RecordingLogSink sink = new RecordingLogSink();
        int[] created = {0};
        DebugLogController c = new DebugLogController(config, logger, () -> {
            created[0]++;
            return sink;
        }, msg -> { });

        c.initFromConfig();
        assertFalse(logger.isEnabled(), "disabled by default -> no sink created");
        assertEquals(0, created[0]);

        boolean active = c.setEnabled(true);
        assertTrue(active);
        assertTrue(logger.isEnabled());
        assertEquals(1, created[0], "sink created once on first enable");
        assertEquals(Boolean.TRUE, config.get(DebugConfig.DEBUG_LOG_ENABLED));

        c.setEnabled(false);
        assertFalse(logger.isEnabled());
        c.setEnabled(true);
        assertEquals(1, created[0], "sink reused, not recreated");
    }

    @Test
    void modeIsPersistedAndApplied() {
        MapConfigService config = new MapConfigService();
        VeltraphLogger logger = newLogger(null);
        DebugLogController c = new DebugLogController(config, logger,
                RecordingLogSink::new, msg -> { });
        c.setMode(DebugLogMode.VERBOSE);
        assertEquals(DebugLogMode.VERBOSE, logger.mode());
        assertEquals(DebugLogMode.VERBOSE, config.get(DebugConfig.DEBUG_LOG_MODE));
    }

    @Test
    void initFromConfigRestoresEnabledAndMode() {
        MapConfigService config = new MapConfigService();
        config.set(DebugConfig.DEBUG_LOG_ENABLED, true);
        config.set(DebugConfig.DEBUG_LOG_MODE, DebugLogMode.DETAILS);
        VeltraphLogger logger = newLogger(null);
        DebugLogController c = new DebugLogController(config, logger,
                RecordingLogSink::new, msg -> { });
        c.initFromConfig();
        assertTrue(logger.isEnabled());
        assertEquals(DebugLogMode.DETAILS, logger.mode());
    }

    @Test
    void sinkFailureKeepsLoggingOffAndNotifies() {
        MapConfigService config = new MapConfigService();
        VeltraphLogger logger = newLogger(null);
        List<String> notes = new ArrayList<>();
        DebugLogController c = new DebugLogController(config, logger, () -> {
            throw new java.io.IOException("no disk");
        }, notes::add);
        boolean active = c.setEnabled(true);
        assertFalse(active, "logging stays off when the sink cannot be created");
        assertFalse(logger.isEnabled());
        assertTrue(notes.stream().anyMatch(n -> n.toLowerCase().contains("fail")
                || n.toLowerCase().contains("could not")));
    }

    @Test
    void statusReportsStateModeAndPath() {
        MapConfigService config = new MapConfigService();
        VeltraphLogger logger = newLogger(null);
        DebugLogController c = new DebugLogController(config, logger,
                RecordingLogSink::new, msg -> { });
        c.setMode(DebugLogMode.VERBOSE);
        c.setEnabled(true);
        String status = c.status();
        assertTrue(status.contains("ENABLED"));
        assertTrue(status.contains("verbose"));
        assertTrue(status.contains("recording"), "status names the active sink/path: " + status);
    }
}
