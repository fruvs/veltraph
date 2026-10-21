package dev.veltraph.client.dev;

/**
 * How "loud" a {@link DevTestAction} is — drives gating and UI labelling.
 *
 * <ul>
 *   <li>{@link #LOCAL_ONLY} — purely local effect (local chat HUD line, stop macro, release input, set the
 *       local camera angle, log a decision). Never touches the network.</li>
 *   <li>{@link #DRY_RUN} — would normally produce a server-bound effect, but the test only <i>shows/logs</i>
 *       the chat line or command that would be sent. Nothing leaves the client.</li>
 *   <li>{@link #SERVER_BOUND} — actually sends a chat message or command to the server. Blocked unless the
 *       caller explicitly allows it (the clearly-labeled "Execute" control), so it can never run by
 *       accident.</li>
 * </ul>
 */
public enum DevTestRisk {
    LOCAL_ONLY,
    DRY_RUN,
    SERVER_BOUND
}
