package dev.veltraph.client.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Local file {@link LogSink}: writes Veltraph debug lines into {@code <baseDir>/logs/veltraph/}. Each
 * session gets a timestamped file ({@code veltraph-yyyy-MM-dd-HH-mm-ss.log}) and a mirrored, truncated
 * {@code latest.log}. <b>Local files only — no network, telemetry, or external sends.</b>
 *
 * <p>Created lazily-safe: the directory is made on construction; each write appends and flushes so a
 * crash still leaves the log on disk. Thread-safe via a monitor (logging is on the client thread, but
 * commands/render may also touch it).
 */
public final class FileLogSink implements LogSink {

    private final Path sessionFile;
    private final Path latestFile;
    private BufferedWriter session;
    private BufferedWriter latest;

    /**
     * @param logsRoot the {@code logs/veltraph} directory (created if missing)
     * @param sessionStamp filename timestamp, e.g. {@code 2026-06-17-16-30-00}
     */
    public FileLogSink(Path logsRoot, String sessionStamp) throws IOException {
        Files.createDirectories(logsRoot);
        this.sessionFile = logsRoot.resolve("veltraph-" + sessionStamp + ".log");
        this.latestFile = logsRoot.resolve("latest.log");
        this.session = Files.newBufferedWriter(sessionFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        this.latest = Files.newBufferedWriter(latestFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public synchronized void write(String line) {
        try {
            session.write(line);
            session.write('\n');
            session.flush();
            latest.write(line);
            latest.write('\n');
            latest.flush();
        } catch (IOException e) {
            throw new RuntimeException(e); // surfaced to VeltraphLogger, which disables logging
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly(session);
        closeQuietly(latest);
        session = null;
        latest = null;
    }

    @Override
    public String describe() {
        return sessionFile.toString();
    }

    public Path sessionFile() {
        return sessionFile;
    }

    public Path latestFile() {
        return latestFile;
    }

    private static void closeQuietly(BufferedWriter w) {
        if (w != null) {
            try {
                w.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }
}
