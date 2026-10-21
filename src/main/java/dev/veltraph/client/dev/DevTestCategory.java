package dev.veltraph.client.dev;

/**
 * Grouping for {@link DevTestAction}s in the Developer page and {@code /vdebug dev} listing.
 */
public enum DevTestCategory {
    /** Macro / input lifecycle + general probes. */
    GENERAL("General"),
    /** Failsafe / anomaly-class simulations (location gate, GUI-stop, world/teleport/floor anomalies). */
    FAILSAFE("Failsafe"),
    /** Hydra / anti-staff reaction simulations (alert, stop, chat reply, reroute, fix-angle, parity). */
    HYDRA("Hydra / Anti-Staff"),
    /** Chat / command output tests (local + dry-run + explicit server-bound). */
    CHAT("Chat / Command");

    private final String displayName;

    DevTestCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
