package dev.veltraph.client.game;

import java.util.List;

/**
 * Supplies the current {@link GameContext}. Implementations must derive it from client-visible data
 * only (server brand/address, scoreboard, tab) — never network calls.
 */
public interface GameContextService {

    GameContext current();

    /**
     * The most recent full resolution, including provenance (which signal decided it). Default
     * implementations that don't track signals return a {@link LocationResolution} wrapping
     * {@link #current()} with {@link LocationSource#NONE}.
     */
    default LocationResolution resolution() {
        return new LocationResolution(current(), LocationSource.NONE, "", List.of());
    }

    /** The raw + normalized signals behind {@link #resolution()} (for the debug command). */
    default LocationSignals signals() {
        return LocationSignals.ofScoreboard(null, null, null, List.of());
    }
}
