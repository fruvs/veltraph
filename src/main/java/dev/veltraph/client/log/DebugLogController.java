package dev.veltraph.client.log;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.DebugConfig;

import java.util.function.Consumer;

/**
 * Ties the debug-log {@link ConfigService} keys to the live {@link VeltraphLogger} and its file
 * {@link LogSink}. Applies parsed {@code /vdebug log …} intents (enable/disable, set mode) and reports
 * status. The file sink is created lazily on first enable (and only once) through an injected
 * {@link SinkFactory}, so this class stays pure/testable (no Minecraft / no filesystem of its own).
 */
public final class DebugLogController {

    /** Creates the local file sink on demand. May throw; the controller catches and warns. */
    @FunctionalInterface
    public interface SinkFactory {
        LogSink create() throws Exception;
    }

    private final ConfigService config;
    private final VeltraphLogger logger;
    private final SinkFactory sinkFactory;
    private final Consumer<String> notify;
    private boolean sinkReady;

    public DebugLogController(ConfigService config, VeltraphLogger logger, SinkFactory sinkFactory,
                              Consumer<String> notify) {
        this.config = config;
        this.logger = logger;
        this.sinkFactory = sinkFactory;
        this.notify = notify != null ? notify : msg -> { };
    }

    /** Initialise the logger from persisted config at startup (creates the sink if logging is on). */
    public void initFromConfig() {
        logger.setMode(config.get(DebugConfig.DEBUG_LOG_MODE));
        boolean enabled = config.get(DebugConfig.DEBUG_LOG_ENABLED);
        if (enabled) {
            ensureSink();
        }
        logger.setEnabled(enabled && sinkReady);
    }

    /** Enable/disable file logging (persists to config). Returns true if logging is active afterwards. */
    public boolean setEnabled(boolean enabled) {
        config.set(DebugConfig.DEBUG_LOG_ENABLED, enabled);
        if (enabled) {
            ensureSink();
        }
        boolean active = enabled && sinkReady;
        logger.setEnabled(active);
        if (enabled && !sinkReady) {
            notify.accept("Veltraph: could not open the debug log file; logging stays off.");
        } else {
            notify.accept("Veltraph: debug logging " + (active ? "ENABLED" : "disabled")
                    + " (mode " + logger.mode().lower() + ").");
        }
        if (active) {
            logger.basic("VeltraphLog", "logging enabled, mode=" + logger.mode().lower());
        }
        return active;
    }

    /** Set the verbosity mode (persists to config). */
    public void setMode(DebugLogMode mode) {
        config.set(DebugConfig.DEBUG_LOG_MODE, mode);
        logger.setMode(mode);
        logger.basic("VeltraphLog", "mode set to " + mode.lower());
        notify.accept("Veltraph: debug log mode set to " + mode.lower() + ".");
    }

    /** A one-line status string: enabled/disabled, mode, and the active log path. */
    public String status() {
        return "Veltraph debug log: " + (logger.isEnabled() ? "ENABLED" : "disabled")
                + " | mode " + logger.mode().lower()
                + " | file " + (sinkReady ? logger.sinkDescription() : "(none yet)");
    }

    private void ensureSink() {
        if (sinkReady) {
            return;
        }
        try {
            logger.setSink(sinkFactory.create());
            sinkReady = true;
        } catch (Throwable t) {
            notify.accept("Veltraph: failed to create debug log file (" + t.getClass().getSimpleName()
                    + "); logging stays off.");
        }
    }
}
