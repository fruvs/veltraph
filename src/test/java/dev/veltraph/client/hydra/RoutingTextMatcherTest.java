package dev.veltraph.client.hydra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for the read-only routing/disconnect-text matcher. It only INSPECTS received lines — these
 * tests confirm matching, the consume-once semantics, the false-positive guard (no match → no fire), and
 * config-extendable keywords.
 */
class RoutingTextMatcherTest {

    @Test
    void matchesDefaultRoutingPhraseCaseInsensitiveSubstring() {
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer("[Server] We are SENDING YOU TO LIMBO now, please wait.");
        assertTrue(m.hasPendingMatch());
        assertEquals("sending you to limbo", m.pollMatch());
    }

    @Test
    void consumeOnceThenNullUntilNextMatch() {
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer("You were kicked from the server.");
        assertEquals("you were kicked", m.pollMatch());
        // Already consumed — a second poll returns null (so the detector fires the line exactly once).
        assertNull(m.pollMatch());
    }

    @Test
    void nonMatchingLineNeverFires() {
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer("Player Steve joined the lobby!");
        m.offer("You earned 5 coins.");
        assertFalse(m.hasPendingMatch());
        assertNull(m.pollMatch());
    }

    @Test
    void nullAndBlankLinesAreIgnored() {
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer(null);
        m.offer("   ");
        assertNull(m.pollMatch());
    }

    @Test
    void latestMatchWins() {
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer("you were kicked");
        m.offer("evacuating to hub"); // later match overwrites the pending one
        assertEquals("evacuat", m.pollMatch());
    }

    @Test
    void customKeywordsParseAndMatch() {
        RoutingTextMatcher m = RoutingTextMatcher.parse("teleporting you away, frozen by staff");
        m.offer("You are being FROZEN BY STAFF for inspection.");
        assertEquals("frozen by staff", m.pollMatch());
        assertEquals(2, m.keywordCount());
    }

    @Test
    void blankConfigFallsBackToDefaults() {
        RoutingTextMatcher m = RoutingTextMatcher.parse("   ");
        assertEquals(RoutingTextMatcher.DEFAULT_KEYWORDS.size(), m.keywordCount());
        m.offer("you have been banned");
        assertEquals("you have been banned", m.pollMatch());
    }

    @Test
    void newDefaultRoutingTriggersMatch() {
        // SkyBlock-lobby routing / disconnect-exception / desync / dynamic-pool / no-destination.
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer("Sending you to the SkyBlock lobby...");
        assertEquals("sending you to the skyblock lobby", m.pollMatch());
        m.offer("An exception occurred in your connection, please reconnect.");
        assertEquals("an exception occurred in your connection", m.pollMatch());
        m.offer("Your location is DESYNCED, fixing...");
        assertEquals("desync", m.pollMatch());
        m.offer("Dynamic pool error: could not route.");
        assertEquals("dynamic pool", m.pollMatch());
        m.offer("Routing failed: no destination available.");
        assertEquals("no destination", m.pollMatch());
    }

    @Test
    void resetClearsPendingAndHistory() {
        RoutingTextMatcher m = RoutingTextMatcher.withDefaults();
        m.offer("you were kicked");
        m.reset();
        assertFalse(m.hasPendingMatch());
        assertNull(m.pollMatch());
    }
}
