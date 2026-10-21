package dev.veltraph.client.garden.visitor.history;

import java.util.Objects;

/** Caller supplies evidence from its actual confirmed profile/Garden producer; UNKNOWN is absence. */
public record VisitorHistoryContext(VisitorHistoryScope scope, Object player, Object world,
        Object connection, long confirmationSequence, long epoch, String confirmationProvenance) {
    public VisitorHistoryContext {
        Objects.requireNonNull(scope); Objects.requireNonNull(player); Objects.requireNonNull(world);
        Objects.requireNonNull(connection);
        if (confirmationSequence < 1 || epoch < 1 || confirmationProvenance == null
                || confirmationProvenance.isBlank() || confirmationProvenance.length() > 512)
            throw new IllegalArgumentException("Unconfirmed visitor history context");
    }
    public boolean sameSession(VisitorHistoryContext other) {
        return other != null && scope.equals(other.scope) && player == other.player && world == other.world
                && connection == other.connection && confirmationSequence == other.confirmationSequence
                && epoch == other.epoch;
    }
}
