package dev.veltraph.client.fishing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FishingManaParserTest {
    @Test void readsCurrentAndOverflowInsteadOfMaximumMana(){
        assertEquals(209,FishingManaParser.parse("§b209/500✎ Mana").orElseThrow());
        assertEquals(210,FishingManaParser.parse("200/500✎ 10ʬⓩ").orElseThrow());
        assertEquals(1500,FishingManaParser.parse("1,000/2,000✎ Mana 500ʬⓄ").orElseThrow());
    }
    @Test void ignoresUnrelatedAndOverflowingActionBars(){
        assertTrue(FishingManaParser.parse("210/500 Health").isEmpty());
        assertTrue(FishingManaParser.parse("2147483647/500✎ 1ʬ").isEmpty());
        assertTrue(FishingManaParser.parse(null).isEmpty());
    }
    @Test void readsStableOverflowWithoutChangingMaximum(){
        var mana=FishingManaParser.detailed("§b500/1,000✎ Mana §350\uE017ⓩ").orElseThrow();
        assertEquals(550,mana.current());assertEquals(1000,mana.maximum());
        assertEquals(1500,FishingManaParser.parse("1,000/2,000✎ 500\uE017Ⓞ").orElseThrow());
    }
    @Test void rejectsMalformedOrAmbiguousOverflowAndMana(){
        for(String row:new String[]{"500/1000✎ -50\uE017","500/1000✎ bad\uE017","500/1000✎ 50\uE017 10ʬ","500/1000✎ 50\uE017 10\uE017","500/1000✎ 600/1000✎","5,,00/1000✎","500/1000✎ 5,,0\uE017","2147483647/1000✎ 1\uE017"})
            assertTrue(FishingManaParser.detailed(row).isEmpty(),row);
    }
}
