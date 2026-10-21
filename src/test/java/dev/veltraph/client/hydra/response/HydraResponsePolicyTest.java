package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydraResponsePolicyTest {

    @Test
    void capabilitiesEscalateWithPolicy() {
        assertFalse(HydraResponsePolicy.OFF.allowsReply());
        assertFalse(HydraResponsePolicy.OFF.allowsFixAngle());
        assertFalse(HydraResponsePolicy.OFF.allowsReroute());

        assertTrue(HydraResponsePolicy.REPLY.allowsReply());
        assertFalse(HydraResponsePolicy.REPLY.allowsFixAngle());
        assertFalse(HydraResponsePolicy.REPLY.allowsReroute());

        assertTrue(HydraResponsePolicy.REPLY_AND_FIX_ANGLE.allowsReply());
        assertTrue(HydraResponsePolicy.REPLY_AND_FIX_ANGLE.allowsFixAngle());
        assertFalse(HydraResponsePolicy.REPLY_AND_FIX_ANGLE.allowsReroute());

        assertTrue(HydraResponsePolicy.FULL.allowsReply());
        assertTrue(HydraResponsePolicy.FULL.allowsFixAngle());
        assertTrue(HydraResponsePolicy.FULL.allowsReroute());
    }

    @Test
    void parseAcceptsSynonymsAndRejectsUnknown() {
        assertEquals(HydraResponsePolicy.OFF, HydraResponsePolicy.parse("off"));
        assertEquals(HydraResponsePolicy.OFF, HydraResponsePolicy.parse("ALERT_ONLY"));
        assertEquals(HydraResponsePolicy.REPLY, HydraResponsePolicy.parse("reply"));
        assertEquals(HydraResponsePolicy.REPLY_AND_FIX_ANGLE, HydraResponsePolicy.parse("reply_fix"));
        assertEquals(HydraResponsePolicy.REPLY_AND_FIX_ANGLE, HydraResponsePolicy.parse("fixangle"));
        assertEquals(HydraResponsePolicy.FULL, HydraResponsePolicy.parse("full"));
        assertEquals(HydraResponsePolicy.FULL, HydraResponsePolicy.parse("  All "));
        assertNull(HydraResponsePolicy.parse("nope"));
        assertNull(HydraResponsePolicy.parse(null));
    }
}
