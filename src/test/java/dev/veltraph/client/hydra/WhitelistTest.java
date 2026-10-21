package dev.veltraph.client.hydra;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The local comma-joined whitelist parses, trims, and matches case-insensitively. */
class WhitelistTest {

    @Test
    void parsesCommaJoinedTrimmedNames() {
        Whitelist wl = Whitelist.parse(" Alice , Bob ,, charlie ");
        assertEquals(3, wl.size());
        assertTrue(wl.contains("alice"));
        assertTrue(wl.contains("BOB"));
        assertTrue(wl.contains("Charlie"));
    }

    @Test
    void nullAndBlankAreEmpty() {
        assertTrue(Whitelist.parse(null).isEmpty());
        assertTrue(Whitelist.parse("   ").isEmpty());
        assertEquals(0, Whitelist.empty().size());
    }

    @Test
    void doesNotMatchUnknownOrBlank() {
        Whitelist wl = Whitelist.parse("Alice");
        assertFalse(wl.contains("Eve"));
        assertFalse(wl.contains(""));
        assertFalse(wl.contains(null));
    }

    @Test
    void addIsCaseInsensitiveIdempotentAndImmutable() {
        Whitelist base = Whitelist.empty();
        Whitelist a = base.add("Alice");
        assertTrue(base.isEmpty(), "add must not mutate the original");
        assertEquals(1, a.size());
        assertTrue(a.contains("ALICE"));
        // Re-adding (different case) is idempotent (names normalize to lower-case).
        assertEquals(1, a.add("alice").size());
        // Null/blank ignored.
        assertEquals(1, a.add("  ").size());
        assertEquals(1, a.add(null).size());
    }

    @Test
    void removeIsCaseInsensitiveAndImmutable() {
        Whitelist wl = Whitelist.parse("Alice,Bob");
        Whitelist after = wl.remove("ALICE");
        assertEquals(2, wl.size(), "remove must not mutate the original");
        assertEquals(1, after.size());
        assertFalse(after.contains("alice"));
        assertTrue(after.contains("bob"));
    }

    @Test
    void toConfigStringRoundTrips() {
        Whitelist wl = Whitelist.empty().add("Alice").add("Bob");
        String s = wl.toConfigString();
        Whitelist back = Whitelist.parse(s);
        assertEquals(2, back.size());
        assertTrue(back.contains("alice"));
        assertTrue(back.contains("bob"));
        assertEquals(List.of("alice", "bob"), wl.names());
    }
}
