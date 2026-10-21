package dev.veltraph.client.command;

import dev.veltraph.client.garden.visitor.VisitorAcceptFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented {@code /vdebug visitor} form. */
class VisitorCommandTest {

    @Test
    void blankAndStatusAreStatus() {
        assertEquals(VisitorCommand.Action.STATUS, VisitorCommand.parse("").action());
        assertEquals(VisitorCommand.Action.STATUS, VisitorCommand.parse(null).action());
        assertEquals(VisitorCommand.Action.STATUS, VisitorCommand.parse("status").action());
    }

    @Test
    void onOffAndDebugParse() {
        assertEquals(VisitorCommand.Action.ON, VisitorCommand.parse("on").action());
        assertEquals(VisitorCommand.Action.ON, VisitorCommand.parse("enable").action());
        assertEquals(VisitorCommand.Action.OFF, VisitorCommand.parse("off").action());
        assertEquals(VisitorCommand.Action.OFF, VisitorCommand.parse("disable").action());
        assertEquals(VisitorCommand.Action.DEBUG, VisitorCommand.parse("debug").action());
        assertEquals(VisitorCommand.Action.DEBUG, VisitorCommand.parse("dump").action());
    }

    @Test
    void filterTogglesEachDimensionOnByDefault() {
        for (VisitorAcceptFilter f : VisitorAcceptFilter.values()) {
            VisitorCommand c = VisitorCommand.parse("filter " + f.name().toLowerCase());
            assertTrue(c.valid(), f.name());
            assertEquals(VisitorCommand.Action.FILTER, c.action());
            assertEquals(f, c.filter());
            assertTrue(c.enable(), "no explicit state defaults to on: " + f.name());
        }
    }

    @Test
    void filterAcceptsExplicitOnOffAndAliases() {
        assertFalse(VisitorCommand.parse("filter valuable_drops off").enable());
        assertTrue(VisitorCommand.parse("filter valuable_drops on").enable());
        // aliases
        assertEquals(VisitorAcceptFilter.VALUABLE_DROPS, VisitorCommand.parse("filter valuable").filter());
        assertEquals(VisitorAcceptFilter.NEW_VISITORS, VisitorCommand.parse("filter new").filter());
        assertEquals(VisitorAcceptFilter.PROFITABLE, VisitorCommand.parse("filter profitable off").filter());
    }

    @Test
    void filterRequiresKnownValueAndState() {
        assertFalse(VisitorCommand.parse("filter").valid());
        assertFalse(VisitorCommand.parse("filter bogus").valid());
        assertFalse(VisitorCommand.parse("filter ALL maybe").valid());      // bad state
        assertFalse(VisitorCommand.parse("filter ALL on extra").valid());   // too many tokens
    }

    @Test
    void unknownAndTooManyAreInvalid() {
        assertFalse(VisitorCommand.parse("frobnicate").valid());
        assertTrue(VisitorCommand.parse("frobnicate").error().contains("Usage"));
        assertFalse(VisitorCommand.parse("on off").valid());
    }
}
