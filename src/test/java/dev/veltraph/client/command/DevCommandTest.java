package dev.veltraph.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented {@code /vdebug dev} form. */
class DevCommandTest {

    @Test
    void blankAndStatusAreStatus() {
        assertEquals(DevCommand.Action.STATUS, DevCommand.parse("").action());
        assertEquals(DevCommand.Action.STATUS, DevCommand.parse(null).action());
        assertEquals(DevCommand.Action.STATUS, DevCommand.parse("status").action());
    }

    @Test
    void onOffListParse() {
        assertEquals(DevCommand.Action.ON, DevCommand.parse("on").action());
        assertEquals(DevCommand.Action.ON, DevCommand.parse("enable").action());
        assertEquals(DevCommand.Action.OFF, DevCommand.parse("off").action());
        assertEquals(DevCommand.Action.OFF, DevCommand.parse("disable").action());
        assertEquals(DevCommand.Action.LIST, DevCommand.parse("list").action());
    }

    @Test
    void runParsesActionId() {
        DevCommand c = DevCommand.parse("run failsafe.world_change");
        assertTrue(c.valid());
        assertEquals(DevCommand.Action.RUN, c.action());
        assertEquals("failsafe.world_change", c.actionId());
    }

    @Test
    void runRequiresExactlyOneId() {
        assertFalse(DevCommand.parse("run").valid());
        assertFalse(DevCommand.parse("run a b").valid());
        assertNull(DevCommand.parse("run").actionId());
    }

    @Test
    void unknownAndTooManyAreInvalid() {
        assertFalse(DevCommand.parse("frobnicate").valid());
        assertTrue(DevCommand.parse("frobnicate").error().contains("Usage"));
        assertFalse(DevCommand.parse("on off").valid());
    }
}
