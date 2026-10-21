package dev.veltraph.client.farming.movement;

/**
 * Predicate that answers "is the player at the end of the current row?". The shared engine consults
 * this (gated by the turn cooldown and {@code DROP_AND_TURN}) to decide when to turn.
 */
@FunctionalInterface
public interface RowEndPredicate {
    boolean atRowEnd();
}
