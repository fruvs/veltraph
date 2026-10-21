package dev.veltraph.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented {@code /vdebug heal} form. */
class HealCommandTest {

    @Test
    void blankAndStatusAreStatus() {
        assertEquals(HealCommand.Action.STATUS, HealCommand.parse("").action());
        assertEquals(HealCommand.Action.STATUS, HealCommand.parse(null).action());
        assertEquals(HealCommand.Action.STATUS, HealCommand.parse("status").action());
    }

    @Test
    void onOffParse() {
        assertEquals(HealCommand.Action.ON, HealCommand.parse("on").action());
        assertEquals(HealCommand.Action.ON, HealCommand.parse("enable").action());
        assertEquals(HealCommand.Action.OFF, HealCommand.parse("off").action());
        assertEquals(HealCommand.Action.OFF, HealCommand.parse("disable").action());
    }

    @Test
    void invalidForms() {
        assertFalse(HealCommand.parse("frobnicate").valid());
        assertTrue(HealCommand.parse("frobnicate").error().contains("Usage"));
        assertFalse(HealCommand.parse("on off").valid());
    }
}
