package dev.veltraph.client.hydra;

/**
 * The anti-staff watcher state, per the spec state machine ({@code HydraAntiStaffLocal.md}):
 *
 * <pre>
 *   IDLE --(nearby non-whitelisted player &lt;= distance)--&gt; ALERTED
 *   ALERTED --(no nearby player for cooldownMs)--&gt; IDLE
 * </pre>
 */
public enum HydraState {
    /** No threat: no non-whitelisted player within the alert distance. */
    IDLE,
    /** A non-whitelisted player is (or was recently) within the alert distance. */
    ALERTED
}
