package dev.veltraph.client.failsafe.response;













public enum FailsafeResponseType {
    /** Recovered: {@code bossTeleported} / "Boss Teleported". */
    BOSS_TELEPORTED,
    /** Recovered: {@code teleportedIntoBlock} / "Teleported Into Block". */
    TELEPORTED_INTO_BLOCK,
    /** Recovered: {@code invalidMovement} / "Invalid Movement" (lagback). */
    INVALID_MOVEMENT,
    /** Recovered: {@code forcedAdventureMode} / "Forced Adventure Mode". */
    FORCED_ADVENTURE,
    /** Recovered: {@code repeatedSuspicious} / "Repeated Suspicious Activity". */
    REPEATED_SUSPICIOUS,
    /** Local: fell into the void. */
    VOID,
    /** Local: the player was mentioned in chat. */
    MENTION,
    /** Local: a non-whitelisted player is nearby (staff proximity). */
    PLAYER_NEARBY,
    /** Local: a routing/kick keyword was received. */
    ROUTING_TEXT,
    /** Local: routed to Limbo / disconnect. */
    LIMBO_ROUTING,
    /** Local: the floor/blocks under the player changed repeatedly. */
    FLOOR_CHANGE,
    /** Local: a barrier newly appeared in the forward path. */
    BARRIER
}
