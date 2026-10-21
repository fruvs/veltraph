package dev.veltraph.client.log;

import dev.veltraph.client.testutil.RecordingLogSink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mode filtering, no-op-when-disabled, timestamp/tag formatting, and fail-safe disabling. */
class VeltraphLoggerTest {

    private VeltraphLogger logger(RecordingLogSink sink) {
        return logger(sink, new ArrayList<>());
    }

    private VeltraphLogger logger(RecordingLogSink sink, List<String> warnings) {
        return new VeltraphLogger(sink, () -> "12:00:00.000", warnings::add);
    }

    @Test
    void disabledLoggerWritesNothing() {
        RecordingLogSink sink = new RecordingLogSink();
        VeltraphLogger log = logger(sink);
        log.setMode(DebugLogMode.VERBOSE);
        // enabled defaults to false
        log.basic("t", "a");
        log.details("t", "b");
        log.verbose("t", "c");
        assertTrue(sink.lines.isEmpty(), "nothing written while disabled");
        assertFalse(log.isEnabled());
    }

    @Test
    void basicModeEmitsOnlyBasic() {
        RecordingLogSink sink = new RecordingLogSink();
        VeltraphLogger log = logger(sink);
        log.setEnabled(true);
        log.setMode(DebugLogMode.BASIC);
        log.basic("tag", "major");
        log.details("tag", "detail");
        log.verbose("tag", "spam");
        assertEquals(1, sink.lines.size());
        assertTrue(sink.lines.get(0).contains("[BASIC]"));
        assertTrue(sink.lines.get(0).contains("major"));
    }

    @Test
    void detailsModeEmitsBasicAndDetailsButNotVerbose() {
        RecordingLogSink sink = new RecordingLogSink();
        VeltraphLogger log = logger(sink);
        log.setEnabled(true);
        log.setMode(DebugLogMode.DETAILS);
        log.basic("tag", "a");
        log.details("tag", "b");
        log.verbose("tag", "c");
        assertEquals(2, sink.lines.size());
        assertTrue(sink.hasLineContaining("[BASIC]", "a"));
        assertTrue(sink.hasLineContaining("[DETAILS]", "b"));
        assertFalse(sink.hasLineContaining("[VERBOSE]"));
    }

    @Test
    void verboseModeEmitsEverything() {
        RecordingLogSink sink = new RecordingLogSink();
        VeltraphLogger log = logger(sink);
        log.setEnabled(true);
        log.setMode(DebugLogMode.VERBOSE);
        log.basic("tag", "a");
        log.details("tag", "b");
        log.verbose("tag", "c");
        assertEquals(3, sink.lines.size());
    }

    @Test
    void linesAreTimestampedAndTagged() {
        RecordingLogSink sink = new RecordingLogSink();
        VeltraphLogger log = logger(sink);
        log.setEnabled(true);
        log.basic("MyTag", "hello");
        assertEquals("[12:00:00.000] [BASIC] [MyTag] hello", sink.lines.get(0));
    }

    @Test
    void supplierMessageNotBuiltWhenLevelOff() {
        RecordingLogSink sink = new RecordingLogSink();
        VeltraphLogger log = logger(sink);
        log.setEnabled(true);
        log.setMode(DebugLogMode.BASIC);
        boolean[] built = {false};
        log.verbose("tag", () -> {
            built[0] = true;
            return "expensive";
        });
        assertFalse(built[0], "verbose supplier not invoked in BASIC mode");
        assertTrue(sink.lines.isEmpty());
    }

    @Test
    void sinkFailureWarnsOnceAndDisablesWithoutCrashing() {
        RecordingLogSink sink = new RecordingLogSink();
        sink.throwOnWrite = true;
        List<String> warnings = new ArrayList<>();
        VeltraphLogger log = logger(sink, warnings);
        log.setEnabled(true);
        log.basic("tag", "boom"); // sink throws -> caught
        assertEquals(1, warnings.size(), "one warning emitted");
        assertFalse(log.isEnabled(), "file logging disabled after failure");
    }
}
