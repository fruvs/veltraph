package dev.veltraph.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser for every documented {@code /vdebug hydra} form: status / on / off / blank / invalid. */
class HydraCommandTest {

    @Test
    void blankAndStatusAreStatus() {
        assertEquals(HydraCommand.Action.STATUS, HydraCommand.parse("").action());
        assertEquals(HydraCommand.Action.STATUS, HydraCommand.parse(null).action());
        assertEquals(HydraCommand.Action.STATUS, HydraCommand.parse("status").action());
        assertTrue(HydraCommand.parse("status").valid());
    }

    @Test
    void onEnablesAndOffDisables() {
        assertEquals(HydraCommand.Action.ON, HydraCommand.parse("on").action());
        assertEquals(HydraCommand.Action.OFF, HydraCommand.parse("off").action());
    }

    @Test
    void onOffSynonymsAccepted() {
        assertEquals(HydraCommand.Action.ON, HydraCommand.parse("enable").action());
        assertEquals(HydraCommand.Action.ON, HydraCommand.parse("true").action());
        assertEquals(HydraCommand.Action.OFF, HydraCommand.parse("disable").action());
        assertEquals(HydraCommand.Action.OFF, HydraCommand.parse("false").action());
    }

    @Test
    void triggersSubcommandParses() {
        assertEquals(HydraCommand.Action.TRIGGERS, HydraCommand.parse("triggers").action());
        assertEquals(HydraCommand.Action.TRIGGERS, HydraCommand.parse("trigger").action());
        assertEquals(HydraCommand.Action.TRIGGERS, HydraCommand.parse("TRIGGERS").action());
        assertTrue(HydraCommand.parse("triggers").valid());
    }

    @Test
    void caseInsensitive() {
        assertEquals(HydraCommand.Action.ON, HydraCommand.parse("ON").action());
        assertEquals(HydraCommand.Action.STATUS, HydraCommand.parse("  Status  ").action());
    }

    @Test
    void unknownTokenIsInvalidWithUsage() {
        HydraCommand bad = HydraCommand.parse("frobnicate");
        assertFalse(bad.valid());
        assertNull(bad.action());
        assertTrue(bad.error().contains("Usage"));
    }

    @Test
    void tooManyArgumentsIsInvalid() {
        HydraCommand bad = HydraCommand.parse("on off");
        assertFalse(bad.valid());
        assertTrue(bad.error().contains("Usage"));
    }

    @Test
    void whitelistBareAndListAreList() {
        HydraCommand a = HydraCommand.parse("whitelist");
        assertEquals(HydraCommand.Action.WHITELIST, a.action());
        assertEquals(HydraCommand.WhitelistOp.LIST, a.whitelistOp());
        assertEquals(HydraCommand.WhitelistOp.LIST, HydraCommand.parse("whitelist list").whitelistOp());
        assertEquals(HydraCommand.WhitelistOp.LIST, HydraCommand.parse("wl").whitelistOp());
    }

    @Test
    void whitelistAddRemoveCarryTheName() {
        HydraCommand add = HydraCommand.parse("whitelist add Alice");
        assertTrue(add.valid());
        assertEquals(HydraCommand.WhitelistOp.ADD, add.whitelistOp());
        assertEquals("Alice", add.whitelistName());
        HydraCommand rm = HydraCommand.parse("whitelist remove Bob");
        assertEquals(HydraCommand.WhitelistOp.REMOVE, rm.whitelistOp());
        assertEquals("Bob", rm.whitelistName());
        assertEquals(HydraCommand.WhitelistOp.REMOVE, HydraCommand.parse("wl rm Bob").whitelistOp());
    }

    @Test
    void whitelistClearParses() {
        HydraCommand c = HydraCommand.parse("whitelist clear");
        assertTrue(c.valid());
        assertEquals(HydraCommand.WhitelistOp.CLEAR, c.whitelistOp());
        assertNull(c.whitelistName());
    }

    @Test
    void whitelistAddWithoutNameIsInvalid() {
        HydraCommand bad = HydraCommand.parse("whitelist add");
        assertFalse(bad.valid());
        assertTrue(bad.error().contains("Usage"));
        HydraCommand tooMany = HydraCommand.parse("whitelist clear extra");
        assertFalse(tooMany.valid());
    }

    @Test
    void responseSubcommandParses() {
        assertEquals(HydraCommand.Action.RESPONSE, HydraCommand.parse("response").action());
        assertEquals(HydraCommand.Action.RESPONSE, HydraCommand.parse("responses").action());
        assertTrue(HydraCommand.parse("response").valid());
        // `policy` with no value falls back to printing the response config.
        assertEquals(HydraCommand.Action.RESPONSE, HydraCommand.parse("policy").action());
    }

    @Test
    void policySetParsesEachValueWithSynonyms() {
        assertEquals(dev.veltraph.client.hydra.response.HydraResponsePolicy.OFF,
                HydraCommand.parse("policy off").policy());
        assertEquals(dev.veltraph.client.hydra.response.HydraResponsePolicy.REPLY,
                HydraCommand.parse("policy reply").policy());
        assertEquals(dev.veltraph.client.hydra.response.HydraResponsePolicy.REPLY_AND_FIX_ANGLE,
                HydraCommand.parse("policy reply_fix").policy());
        assertEquals(dev.veltraph.client.hydra.response.HydraResponsePolicy.FULL,
                HydraCommand.parse("policy FULL").policy());
        assertEquals(HydraCommand.Action.POLICY, HydraCommand.parse("policy full").action());
        assertTrue(HydraCommand.parse("policy full").valid());
    }

    @Test
    void policyUnknownOrTooManyArgsIsInvalid() {
        HydraCommand bad = HydraCommand.parse("policy frobnicate");
        assertFalse(bad.valid());
        assertTrue(bad.error().contains("Unknown policy"));
        HydraCommand tooMany = HydraCommand.parse("policy reply full");
        assertFalse(tooMany.valid());
    }

    @Test
    void reactionParsesEachSituationWithSynonyms() {
        assertEquals(dev.veltraph.client.hydra.response.HydraReaction.CAGE,
                HydraCommand.parse("reaction cage").reaction());
        assertEquals(dev.veltraph.client.hydra.response.HydraReaction.BARRIER,
                HydraCommand.parse("reaction barrier").reaction());
        assertEquals(dev.veltraph.client.hydra.response.HydraReaction.FLOOR_CHANGE,
                HydraCommand.parse("reaction floor").reaction());
        assertEquals(dev.veltraph.client.hydra.response.HydraReaction.BACKUP,
                HydraCommand.parse("react backup").reaction());
        assertEquals(HydraCommand.Action.REACTION, HydraCommand.parse("reaction cage").action());
        assertTrue(HydraCommand.parse("reaction cage").valid());
    }

    @Test
    void reactionUnknownOrMissingArgIsInvalid() {
        assertFalse(HydraCommand.parse("reaction").valid());
        HydraCommand bad = HydraCommand.parse("reaction frobnicate");
        assertFalse(bad.valid());
        assertTrue(bad.error().contains("Unknown reaction"));
    }

    @Test
    void failsafeParsesToFailsafeStatus() {
        assertEquals(HydraCommand.Action.FAILSAFE, HydraCommand.parse("failsafe").action());
        assertEquals(HydraCommand.Action.FAILSAFE, HydraCommand.parse("failsafes").action());
        assertTrue(HydraCommand.parse("failsafe").valid());
    }
}
