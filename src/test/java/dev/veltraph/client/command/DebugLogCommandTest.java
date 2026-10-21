package dev.veltraph.client.command;

import dev.veltraph.client.log.DebugLogMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented /vdebug log form, including the `ture` typo and status. */
class DebugLogCommandTest {

    @Test
    void trueAndFalseToggleEnable() {
        DebugLogCommand on = DebugLogCommand.parse("true");
        assertTrue(on.valid());
        assertEquals(Boolean.TRUE, on.enable());
        assertNull(on.mode());
        assertFalse(on.status());

        DebugLogCommand off = DebugLogCommand.parse("false");
        assertEquals(Boolean.FALSE, off.enable());
    }

    @Test
    void tureTypoIsTreatedAsTrue() {
        DebugLogCommand c = DebugLogCommand.parse("ture mode verbose");
        assertTrue(c.valid());
        assertEquals(Boolean.TRUE, c.enable());
        assertEquals(DebugLogMode.VERBOSE, c.mode());
    }

    @Test
    void boolWithModeSetsBoth() {
        DebugLogCommand c = DebugLogCommand.parse("true mode details");
        assertEquals(Boolean.TRUE, c.enable());
        assertEquals(DebugLogMode.DETAILS, c.mode());

        DebugLogCommand d = DebugLogCommand.parse("false mode basic");
        assertEquals(Boolean.FALSE, d.enable());
        assertEquals(DebugLogMode.BASIC, d.mode());
    }

    @Test
    void modeOnlySetsModeWithoutTouchingEnable() {
        DebugLogCommand c = DebugLogCommand.parse("mode verbose");
        assertTrue(c.valid());
        assertNull(c.enable());
        assertEquals(DebugLogMode.VERBOSE, c.mode());
    }

    @Test
    void statusAndEmptyAreStatus() {
        assertTrue(DebugLogCommand.parse("status").status());
        assertTrue(DebugLogCommand.parse("").status());
        assertTrue(DebugLogCommand.parse(null).status());
    }

    @Test
    void unknownModeOrTokenIsInvalidWithUsage() {
        DebugLogCommand badMode = DebugLogCommand.parse("mode sideways");
        assertFalse(badMode.valid());
        assertTrue(badMode.error().contains("Usage"));

        DebugLogCommand badTok = DebugLogCommand.parse("maybe");
        assertFalse(badTok.valid());

        DebugLogCommand badTail = DebugLogCommand.parse("true verbose"); // missing 'mode'
        assertFalse(badTail.valid());
    }

    @Test
    void caseInsensitive() {
        DebugLogCommand c = DebugLogCommand.parse("TRUE MODE Verbose");
        assertEquals(Boolean.TRUE, c.enable());
        assertEquals(DebugLogMode.VERBOSE, c.mode());
    }
}
