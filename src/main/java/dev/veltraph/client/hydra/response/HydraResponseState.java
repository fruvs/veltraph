package dev.veltraph.client.hydra.response;
















public enum HydraResponseState {
    /** Response layer disabled (policy off / feature disabled). */
    INACTIVE,
    /** Idle, watching for an eligible staff check. The only state that accepts a new check. */
    LISTENING,
    /** A response has been selected/scheduled but not yet played. */
    ABOUT_TO_RESPOND,
    /** Policy disabled active response for this check (STOP_ALL / IGNORE) — transient, returns to LISTENING. */
    RESPONDING_TEMPORARILY_DISABLED,
    /** Correcting the look angle (pitch outside the band) before/with the reply. */
    FIX_PITCH,
    /** Playing the reply message chain. */
    RESPONDING,
    /** Reply finished; settling before re-arming to LISTENING. */
    AFTER_RESPONDING
}
