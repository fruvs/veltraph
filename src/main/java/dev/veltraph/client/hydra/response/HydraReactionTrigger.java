package dev.veltraph.client.hydra.response;

import dev.veltraph.client.hydra.HydraEvent;

/**
 * Maps a detected {@link HydraEvent.Type} to the local movement {@link HydraReaction} it warrants (or
 * {@code null} when none). Separate from the chat-reply {@link HydraResponseTrigger} so the movement layer
 * has its own, narrow event→situation table. Pure.
 *
 * <ul>
 *   <li>{@link HydraEvent.Type#TELEPORT_INTO_BLOCK} → {@link HydraReaction#CAGE} (encased in solid blocks).</li>
 *   <li>{@link HydraEvent.Type#FLOOR_CHANGE} → {@link HydraReaction#FLOOR_CHANGE} (floor swapped underneath).</li>
 *   <li>{@link HydraEvent.Type#INVALID_MOVEMENT} → {@link HydraReaction#BACKUP} (lagback / rubber-band push).</li>
 *   <li>{@link HydraEvent.Type#BARRIER} → {@link HydraReaction#BARRIER} (a block newly appeared in front).</li>
 * </ul>
 *
 * <p>As of the BARRIER detector ({@link dev.veltraph.client.hydra.BarrierWatcher}) every situation now has a
 * local, client-visible trigger. The original's per-situation <i>detection</i> came from the C2-fed pool
 * (EXCLUDE_NETWORK); these are clean-room local analogues, gated by their own config toggles.
 */
public final class HydraReactionTrigger {

    private HydraReactionTrigger() {
    }

    /** The movement reaction for an event type, or {@code null} when the type drives no movement reaction. */
    public static HydraReaction fromType(HydraEvent.Type type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case TELEPORT_INTO_BLOCK -> HydraReaction.CAGE;
            case FLOOR_CHANGE -> HydraReaction.FLOOR_CHANGE;
            case INVALID_MOVEMENT -> HydraReaction.BACKUP;
            case BARRIER -> HydraReaction.BARRIER;
            default -> null;
        };
    }
}
