package dev.veltraph.client.dev;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.DebugConfig;
import dev.veltraph.client.config.DeveloperConfig;
import dev.veltraph.client.log.DebugLogController;
import dev.veltraph.client.log.DebugLogMode;
import dev.veltraph.client.log.VeltraphLog;

/**
 * Owns the persistent Developer Mode state and its effect on logging. Enabling Developer Mode forces
 * verbose file logging immediately ({@code DEBUG_LOG_ENABLED=true}, {@code DEBUG_LOG_MODE=VERBOSE}) and —
 * because both Developer Mode and the logging keys persist — that verbose state survives launches.
 *
 * <p><b>Non-destructive.</b> Turning Developer Mode on first <i>snapshots</i> the user's current logging
 * settings into {@link DeveloperConfig#DEV_LOG_PREV_ENABLED} / {@link DeveloperConfig#DEV_LOG_PREV_MODE};
 * turning it off <i>restores</i> them. So the user's chosen logging level is never silently erased.
 *
 * <p>Pure-ish: it routes logging changes through the injected {@link DebugLogController} (which updates the
 * live logger and persists the keys) and reads/writes the {@link ConfigService}. No Minecraft.
 */
public final class DeveloperModeController {

    private static final String TAG = "DevMode";

    private final ConfigService config;
    private final DebugLogController debugLog;

    public DeveloperModeController(ConfigService config, DebugLogController debugLog) {
        this.config = config;
        this.debugLog = debugLog;
    }

    public boolean enabled() {
        return config.get(DeveloperConfig.DEV_MODE_ENABLED);
    }

    /**
     * Apply the persisted Developer Mode state at startup. If it is on, re-force verbose logging (without
     * touching the snapshot, which was taken when the user first enabled it) so verbose starts immediately
     * on every launch.
     */
    public void initFromConfig() {
        if (enabled()) {
            forceVerbose();
            VeltraphLog.basic(TAG, "Developer Mode is ON at startup -> verbose logging forced");
        }
    }

    /** Enable/disable Developer Mode. Snapshots logging on the off→on edge, restores on on→off. */
    public boolean setEnabled(boolean on) {
        boolean was = enabled();
        if (on == was) {
            if (on) {
                forceVerbose(); // idempotent re-assert
            }
            return on;
        }
        if (on) {
            // snapshot the user's current logging settings before forcing verbose
            config.set(DeveloperConfig.DEV_LOG_PREV_ENABLED, config.get(DebugConfig.DEBUG_LOG_ENABLED));
            config.set(DeveloperConfig.DEV_LOG_PREV_MODE, config.get(DebugConfig.DEBUG_LOG_MODE));
            config.set(DeveloperConfig.DEV_MODE_ENABLED, true);
            forceVerbose();
            VeltraphLog.basic(TAG, "Developer Mode ENABLED -> verbose logging "
                    + "(snapshot: enabled=" + config.get(DeveloperConfig.DEV_LOG_PREV_ENABLED)
                    + " mode=" + config.get(DeveloperConfig.DEV_LOG_PREV_MODE) + ")");
        } else {
            config.set(DeveloperConfig.DEV_MODE_ENABLED, false);
            DebugLogMode prevMode = config.get(DeveloperConfig.DEV_LOG_PREV_MODE);
            boolean prevEnabled = config.get(DeveloperConfig.DEV_LOG_PREV_ENABLED);
            if (debugLog != null) {
                debugLog.setMode(prevMode);
                debugLog.setEnabled(prevEnabled);
            } else {
                config.set(DebugConfig.DEBUG_LOG_MODE, prevMode);
                config.set(DebugConfig.DEBUG_LOG_ENABLED, prevEnabled);
            }
            VeltraphLog.basic(TAG, "Developer Mode disabled -> restored logging "
                    + "enabled=" + prevEnabled + " mode=" + prevMode);
        }
        return on;
    }

    private void forceVerbose() {
        if (debugLog != null) {
            debugLog.setMode(DebugLogMode.VERBOSE);
            debugLog.setEnabled(true);
        } else {
            config.set(DebugConfig.DEBUG_LOG_MODE, DebugLogMode.VERBOSE);
            config.set(DebugConfig.DEBUG_LOG_ENABLED, true);
        }
    }

    /** Whether SERVER_BOUND dev-test actions may run (persisted convenience; consulted by Execute controls). */
    public boolean allowServerBound() {
        return config.get(DeveloperConfig.DEV_ALLOW_SERVER_BOUND);
    }

    /** One-line status for the Developer page + {@code /vdebug dev status}. */
    public String status() {
        String log = debugLog != null ? debugLog.status()
                : "log enabled=" + config.get(DebugConfig.DEBUG_LOG_ENABLED)
                        + " mode=" + config.get(DebugConfig.DEBUG_LOG_MODE).lower();
        return "Developer Mode: " + (enabled() ? "ON" : "OFF")
                + " | server-bound=" + (allowServerBound() ? "allowed" : "blocked")
                + " | " + log;
    }
}
