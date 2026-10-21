package dev.veltraph.client.combat.grind;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadableDaedalusNativeProducerPolicyTest {
    @Test void publicIdsUseCurrentGoldSwordMaterialAndDoNotUseNameGuess() {
        assertTrue(DaedalusNativeItemIdentity.modernFinishingTool("GOLD_SWORD","SWORD","DAEDALUS_AXE"));
        assertTrue(DaedalusNativeItemIdentity.modernFinishingTool("GOLD_SWORD","SWORD","STARRED_DAEDALUS_AXE"));
        for(String id:new String[]{"DAEDALUS_STICK","DAEDALUS_NOTES","Daedalus Blade","daedalus_axe","OTHER",null})
            assertFalse(DaedalusNativeItemIdentity.modernFinishingTool("GOLD_SWORD","SWORD",id));
        assertFalse(DaedalusNativeItemIdentity.modernFinishingTool("GOLD_AXE","SWORD","DAEDALUS_AXE"));
        assertFalse(DaedalusNativeItemIdentity.modernFinishingTool("GOLD_SWORD","AXE","DAEDALUS_AXE"));
    }
    @Test void lastTokenHeartFormatAndSourceCaseSensitiveCompactScales() {
        assertEquals(1250f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 1.25k❤").orElseThrow());
        assertEquals(1_500_000f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 1.5M❤").orElseThrow());
        assertEquals(2_000_000_000f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 2B❤").orElseThrow());
        assertEquals(3_000_000_000_000f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 3T❤").orElseThrow());
        assertEquals(4_000_000_000_000_000f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 4P❤").orElseThrow());
        assertEquals(5_000_000_000_000_000_000f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 5E❤").orElseThrow());
        assertTrue(ReadableDaedalusHealth.parseAssociatedPlate("Boss 1.25K❤").isEmpty());
        assertTrue(ReadableDaedalusHealth.parseAssociatedPlate("Boss 1.25m❤").isEmpty());
    }
    @Test void integersCommasFormattingAndHalfUpRoundingAreSupported() {
        assertEquals(1_234f,ReadableDaedalusHealth.parseAssociatedPlate("§aBoss §c1,234❤").orElseThrow());
        assertEquals(1001f,ReadableDaedalusHealth.parseAssociatedPlate("Boss 1.0005k❤").orElseThrow());
    }
    @Test void nativeHpApproximationOrUnrelatedHealthTextDoesNotParseAsBossPlate() {
        for(String label:new String[]{"20","Player 20/20❤","Boss 100HP","Spawned by: Player0","1.5M❤ trailing","Boss ❤","Boss ♥", "Boss 0❤", "Boss -1❤"})
            assertTrue(ReadableDaedalusHealth.parseAssociatedPlate(label).isEmpty(),label);
    }
    @Test void malformedNonfiniteOverflowAndDecimalWithoutScaleDeny() {
        for(String label:new String[]{"Boss NaN❤","Boss Infinity❤","Boss 100E❤","Boss 9223372036854775808❤","Boss 1.5❤","Boss 1x❤","Boss 1e100k❤"})
            assertTrue(ReadableDaedalusHealth.parseAssociatedPlate(label).isEmpty(),label);
        assertTrue(ReadableDaedalusHealth.parseAssociatedPlate(null).isEmpty());
        assertTrue(ReadableDaedalusHealth.parseAssociatedPlate("x".repeat(1025)).isEmpty());
    }
}
