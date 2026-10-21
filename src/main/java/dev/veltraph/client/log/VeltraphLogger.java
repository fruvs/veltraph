package dev.veltraph.client.log;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Veltraph's local debug logger: a small mode-gated API ({@link #basic}, {@link #details},
 * {@link #verbose}) over a pluggable {@link LogSink}. Cheap no-op when disabled (the enabled/mode check
 * happens before any message is built — use the {@link Supplier} overloads on hot paths so the string
 * is never constructed when the level is off). Fail-safe: if the sink throws, the logger emits one
 * warning through the injected handler and disables file logging rather than crashing the mod.
 *
 * <p>Pure (no Minecraft dependency) so it is fully unit-testable with a recording sink and a fixed
 * timestamp supplier.
 */
public final class VeltraphLogger {

    private volatile boolean enabled;
    private volatile DebugLogMode mode = DebugLogMode.BASIC;
    private LogSink sink;
    private final Supplier<String> timestamp;
    private final Consumer<String> onError;
    private boolean failed;

    public VeltraphLogger(LogSink sink, Supplier<String> timestamp, Consumer<String> onError) {
        this.sink = sink;
        this.timestamp = timestamp != null ? timestamp : () -> "";
        this.onError = onError != null ? onError : msg -> { };
    }

    /** A permanently-disabled, sink-less logger (the global default before bootstrap configures one). */
    public static VeltraphLogger disabled() {
        return new VeltraphLogger(null, () -> "", null);
    }

    // ---- Configuration ----

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled && sink != null && !failed;
    }

    public void setMode(DebugLogMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    public DebugLogMode mode() {
        return mode;
    }

    public void setSink(LogSink sink) {
        this.sink = sink;
        this.failed = false;
    }

    public String sinkDescription() {
        return sink == null ? "none" : sink.describe();
    }

    /** Whether a message at {@code level} would currently be written (for guarding expensive logs). */
    public boolean shouldLog(DebugLogMode level) {
        return isEnabled() && mode.includes(level);
    }

    // ---- Logging API ----

    public void basic(String tag, String message) {
        log(DebugLogMode.BASIC, tag, message);
    }

    public void details(String tag, String message) {
        log(DebugLogMode.DETAILS, tag, message);
    }

    public void verbose(String tag, String message) {
        log(DebugLogMode.VERBOSE, tag, message);
    }

    public void basic(String tag, Supplier<String> message) {
        if (shouldLog(DebugLogMode.BASIC)) {
            log(DebugLogMode.BASIC, tag, message.get());
        }
    }

    public void details(String tag, Supplier<String> message) {
        if (shouldLog(DebugLogMode.DETAILS)) {
            log(DebugLogMode.DETAILS, tag, message.get());
        }
    }

    public void verbose(String tag, Supplier<String> message) {
        if (shouldLog(DebugLogMode.VERBOSE)) {
            log(DebugLogMode.VERBOSE, tag, message.get());
        }
    }

    private void log(DebugLogMode level, String tag, String message) {
        if (!shouldLog(level)) {
            return;
        }
        String line = "[" + timestamp.get() + "] [" + level.name() + "] ["
                + (tag == null ? "?" : tag) + "] " + (message == null ? "" : message);
        try {
            sink.write(line);
        } catch (Throwable t) {
            // Fail-safe: never crash the mod for logging. Warn once and disable file logging.
            failed = true;
            onError.accept("Veltraph: debug logging failed (" + t.getClass().getSimpleName()
                    + "); file logging disabled.");
        }
    }

    public void close() {
        if (sink != null) {
            try {
                sink.close();
            } catch (Throwable ignored) {
                // closing is best-effort
            }
        }
    }
}
