package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HydraOriginalPolicyTest {

    @Test
    void uiPolicyMapsOntoOriginalConcepts() {
        // OFF → IGNORE (the safe default); every reply-capable UI policy → REACT.
        assertEquals(HydraOriginalPolicy.IGNORE, HydraOriginalPolicy.fromUiPolicy(HydraResponsePolicy.OFF));
        assertEquals(HydraOriginalPolicy.IGNORE, HydraOriginalPolicy.fromUiPolicy(null));
        assertEquals(HydraOriginalPolicy.REACT, HydraOriginalPolicy.fromUiPolicy(HydraResponsePolicy.REPLY));
        assertEquals(HydraOriginalPolicy.REACT,
                HydraOriginalPolicy.fromUiPolicy(HydraResponsePolicy.REPLY_AND_FIX_ANGLE));
        assertEquals(HydraOriginalPolicy.REACT, HydraOriginalPolicy.fromUiPolicy(HydraResponsePolicy.FULL));
    }

    @Test
    void parseAcceptsSynonyms() {
        assertEquals(HydraOriginalPolicy.REACT, HydraOriginalPolicy.parse("react"));
        assertEquals(HydraOriginalPolicy.REACT, HydraOriginalPolicy.parse("Reply"));
        assertEquals(HydraOriginalPolicy.STOP_ALL, HydraOriginalPolicy.parse("stop_all"));
        assertEquals(HydraOriginalPolicy.STOP_ALL, HydraOriginalPolicy.parse("STOP"));
        assertEquals(HydraOriginalPolicy.IGNORE, HydraOriginalPolicy.parse("ignore"));
        assertEquals(HydraOriginalPolicy.IGNORE, HydraOriginalPolicy.parse("off"));
        assertNull(HydraOriginalPolicy.parse("nope"));
        assertNull(HydraOriginalPolicy.parse(null));
    }

    @Test
    void effectivePolicyFollowsParityModeToggle() {
        // Parity off → UI mapping (OFF → IGNORE).
        HydraResponseSettings off = settings(false, HydraResponsePolicy.OFF, HydraOriginalPolicy.REACT);
        assertEquals(HydraOriginalPolicy.IGNORE, off.effectivePolicy());
        // Parity on → use the configured original policy regardless of UI policy.
        HydraResponseSettings parity = settings(true, HydraResponsePolicy.OFF, HydraOriginalPolicy.REACT);
        assertEquals(HydraOriginalPolicy.REACT, parity.effectivePolicy());
    }

    private static HydraResponseSettings settings(boolean parity, HydraResponsePolicy ui, HydraOriginalPolicy orig) {
        return new HydraResponseSettings(ui, parity, orig, false, false, false, "", "", "",
                0.5, 0.3, 50, 2000, 30_000, 250, true, -38.0, 38.0,
                0.5, 20.0, false, 10, 6, 8, 6, false, "lobby", true, "", true, "", "", "", "");
    }
}
