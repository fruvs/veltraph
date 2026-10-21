package dev.veltraph.client.command;

import dev.veltraph.client.command.RewarpCommand.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented /veltraph farming rewarp form + bad input. */
class RewarpCommandTest {

    @Test
    void addParses() {
        RewarpCommand c = RewarpCommand.parse("add");
        assertTrue(c.valid());
        assertEquals(Action.ADD, c.action());
        assertTrue(c.index().isEmpty());
    }

    @Test
    void clearParses() {
        RewarpCommand c = RewarpCommand.parse("clear");
        assertTrue(c.valid());
        assertEquals(Action.CLEAR, c.action());
    }

    @Test
    void listParses() {
        RewarpCommand c = RewarpCommand.parse("list");
        assertTrue(c.valid());
        assertEquals(Action.LIST, c.action());
    }

    @Test
    void warpParses() {
        RewarpCommand c = RewarpCommand.parse("warp");
        assertTrue(c.valid());
        assertEquals(Action.WARP, c.action());
    }

    @Test
    void replaceWithoutIndexDefaultsToLast() {
        RewarpCommand c = RewarpCommand.parse("replace");
        assertTrue(c.valid());
        assertEquals(Action.REPLACE, c.action());
        assertTrue(c.index().isEmpty());
    }

    @Test
    void replaceWithIndex() {
        RewarpCommand c = RewarpCommand.parse("replace 2");
        assertTrue(c.valid());
        assertEquals(Action.REPLACE, c.action());
        assertEquals(2, c.index().orElseThrow());
    }

    @Test
    void caseInsensitive() {
        assertEquals(Action.ADD, RewarpCommand.parse("ADD").action());
        assertEquals(Action.REPLACE, RewarpCommand.parse("Replace 0").action());
        assertEquals(Action.WARP, RewarpCommand.parse("Warp").action());
    }

    @Test
    void emptyOrNullIsInvalidWithUsage() {
        RewarpCommand a = RewarpCommand.parse("");
        assertFalse(a.valid());
        assertTrue(a.error().contains("Usage"));
        assertFalse(RewarpCommand.parse(null).valid());
        assertFalse(RewarpCommand.parse("   ").valid());
    }

    @Test
    void unknownSubcommandIsInvalid() {
        RewarpCommand c = RewarpCommand.parse("teleport");
        assertFalse(c.valid());
        assertTrue(c.error().contains("Unknown sub-command"));
    }

    @Test
    void replaceWithBadIndexIsInvalid() {
        assertFalse(RewarpCommand.parse("replace abc").valid());
        assertFalse(RewarpCommand.parse("replace -1").valid());
        assertFalse(RewarpCommand.parse("replace 1 2").valid());
    }

    @Test
    void extraArgsOnAtomicCommandsAreInvalid() {
        assertFalse(RewarpCommand.parse("add now").valid());
        assertFalse(RewarpCommand.parse("clear all").valid());
        assertFalse(RewarpCommand.parse("list 1").valid());
        assertFalse(RewarpCommand.parse("warp now").valid());
    }
}
