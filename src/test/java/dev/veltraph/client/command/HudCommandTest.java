package dev.veltraph.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented {@code /vdebug hud} form: status / on / off / blank / invalid. */
class HudCommandTest {

    @Test
    void blankAndStatusAreStatus() {
        assertEquals(HudCommand.Action.STATUS, HudCommand.parse("").action());
        assertEquals(HudCommand.Action.STATUS, HudCommand.parse(null).action());
        assertEquals(HudCommand.Action.STATUS, HudCommand.parse("status").action());
        assertTrue(HudCommand.parse("status").valid());
    }

    @Test
    void onEnablesAndOffDisables() {
        assertEquals(HudCommand.Action.ON, HudCommand.parse("on").action());
        assertEquals(HudCommand.Action.OFF, HudCommand.parse("off").action());
    }

    @Test
    void onOffSynonymsAccepted() {
        assertEquals(HudCommand.Action.ON, HudCommand.parse("enable").action());
        assertEquals(HudCommand.Action.ON, HudCommand.parse("true").action());
        assertEquals(HudCommand.Action.OFF, HudCommand.parse("disable").action());
        assertEquals(HudCommand.Action.OFF, HudCommand.parse("false").action());
    }

    @Test
    void caseInsensitive() {
        assertEquals(HudCommand.Action.ON, HudCommand.parse("ON").action());
        assertEquals(HudCommand.Action.STATUS, HudCommand.parse("  Status  ").action());
    }

    @Test
    void unknownTokenIsInvalidWithUsage() {
        HudCommand bad = HudCommand.parse("frobnicate");
        assertFalse(bad.valid());
        assertNull(bad.action());
        assertTrue(bad.error().contains("Usage"));
    }

    @Test
    void tooManyArgumentsIsInvalid() {
        HudCommand bad = HudCommand.parse("on off");
        assertFalse(bad.valid());
        assertTrue(bad.error().contains("Usage"));
    }
}
