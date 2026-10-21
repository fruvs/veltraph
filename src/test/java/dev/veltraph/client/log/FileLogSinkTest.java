package dev.veltraph.client.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file sink creates the directory + session/latest files locally and writes lines to both. */
class FileLogSinkTest {

    @Test
    void createsDirectoryAndFilesAndWritesBoth(@TempDir Path tmp) throws Exception {
        Path logsRoot = tmp.resolve("logs").resolve("veltraph");
        FileLogSink sink = new FileLogSink(logsRoot, "2026-06-17-12-00-00");
        try {
            assertTrue(Files.isDirectory(logsRoot), "logs/veltraph created");
            assertTrue(Files.exists(sink.sessionFile()), "session file created");
            assertEquals("veltraph-2026-06-17-12-00-00.log", sink.sessionFile().getFileName().toString());
            assertEquals("latest.log", sink.latestFile().getFileName().toString());

            sink.write("line one");
            sink.write("line two");

            List<String> session = Files.readAllLines(sink.sessionFile());
            List<String> latest = Files.readAllLines(sink.latestFile());
            assertEquals(List.of("line one", "line two"), session);
            assertEquals(session, latest, "latest mirrors the session log");
        } finally {
            sink.close();
        }
    }

    @Test
    void latestIsTruncatedPerSession(@TempDir Path tmp) throws Exception {
        Path logsRoot = tmp.resolve("logs").resolve("veltraph");
        Files.createDirectories(logsRoot);
        Files.writeString(logsRoot.resolve("latest.log"), "stale from a previous run\n");

        FileLogSink sink = new FileLogSink(logsRoot, "2026-06-17-13-00-00");
        try {
            sink.write("fresh");
            assertEquals(List.of("fresh"), Files.readAllLines(sink.latestFile()),
                    "latest.log is truncated at the start of a new session");
        } finally {
            sink.close();
        }
    }

    @Test
    void integratesWithVeltraphLogger(@TempDir Path tmp) throws Exception {
        Path logsRoot = tmp.resolve("logs").resolve("veltraph");
        FileLogSink sink = new FileLogSink(logsRoot, "2026-06-17-14-00-00");
        VeltraphLogger log = new VeltraphLogger(sink, () -> "14:00:00.000", msg -> { });
        log.setEnabled(true);
        log.setMode(DebugLogMode.DETAILS);
        log.basic("Init", "started");
        log.verbose("noise", "skip");
        log.close();

        List<String> lines = Files.readAllLines(sink.sessionFile());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[BASIC] [Init] started"));
    }
}
