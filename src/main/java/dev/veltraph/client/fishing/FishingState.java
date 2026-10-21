package dev.veltraph.client.fishing;





public enum FishingState {
    /** Macro off / idle. */
    IDLE,
    /** Ready to cast the rod (will emit a CAST this tick). */
    NEED_CAST,
    /** Rod is out; polling the bobber for a bite (gated by the min-cast-time). */
    WAITING_BITE,
    /** Reeled in; holding the post-catch delay before recasting. */
    POST_CATCH_DELAY,
    /** Reel removal was not observed within the configured acknowledgment timeout. */
    FAILED
}
