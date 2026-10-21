package dev.veltraph.client.combat.heal;

/**
 * The Hyperion auto-heal state machine (`reports/_spec_parts/GhostAutoHeal.md` — the recovered
 * {@code { IDLE, LOOK_DOWN, CHECK_CLEAR, CAST, WAIT, RESTORE }} "6-step" heal). CAST is the transition
 * from {@link #CHECK_CLEAR} to {@link #WAIT}, so it has no resident state.
 */
public enum HealState {
    /** Not healing. */
    IDLE,
    /** Pitched the camera straight down so the Wither Impact AOE lands on the player. */
    LOOK_DOWN,
    /** Verifying clear space above/around the player before casting. */
    CHECK_CLEAR,
    /** Cast; holding the post-cast wait before restoring the camera. */
    WAIT,
    /** Restoring the camera pitch and arming the cooldown. */
    RESTORE
}
