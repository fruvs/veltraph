package dev.veltraph.client.rewarp;

/** Command request -> observed arrival -> continuous settle -> guarded one-tick resume. */
public enum AutoRewarpState {
    IDLE,
    /** Command sent; no arrival inferred. */
    WARPING,
    /** Poll actual known area and local world/position evidence until the total deadline. */
    WAITING_GARDEN,
    /** Observed Garden arrival; grounded/tool/slow-motion conditions must hold continuously. */
    SETTLING,
    /** Command settled; travel back to the captured interrupted farming pose. */
    RETURNING,
    /** Revalidate context and actual farm pose before invoking the farming resume callback. */
    RESUME
}
