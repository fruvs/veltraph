package dev.veltraph.client.log;

/**
 * Destination for formatted Veltraph log lines. The real sink ({@link FileLogSink}) appends to local
 * files; tests use a recording sink. Implementations must be local only — no network/telemetry/external
 * sends (project hard rule).
 */
public interface LogSink {

    /** Append one already-formatted line. May throw; {@link VeltraphLogger} catches and disables. */
    void write(String line);

    /** Flush/close any held resources. */
    default void close() {
    }

    /** A human-readable description of where lines go (e.g. a file path), for {@code status}. */
    default String describe() {
        return "sink";
    }
}
