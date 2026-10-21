package dev.veltraph.client.hydra;

import java.util.Optional;

/**
 * The pure {@link HydraDetector}'s output for a single tick: the post-tick {@link HydraState}, an
 * optional {@link HydraEvent} that fired this tick (only when a fresh reaction should occur — cooldown
 * already applied), and the nearest non-whitelisted player's name (for the HUD read API), if any.
 *
 * @param state             watcher state after this tick
 * @param event             the event to react to this tick, or empty if nothing fresh fired
 * @param nearestPlayerName the closest non-whitelisted player within distance, or null
 * @param stopRequested     whether the macro should be stopped this tick (convenience mirror of the event)
 */
public record HydraResult(
        HydraState state,
        Optional<HydraEvent> event,
        String nearestPlayerName,
        boolean stopRequested) {

    static HydraResult of(HydraState state, HydraEvent event, String nearestPlayerName) {
        return new HydraResult(state, Optional.ofNullable(event), nearestPlayerName,
                event != null && event.stopRequested());
    }
}
