package dev.veltraph.client.combat.heal;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealFloridFeedbackTest {
    @Test void exactPrefixSetsFutureTimestampAndStrictFifteenSecondHoldDespiteZeroFloridCooldown(){
        var selector=new HealItemSelector(new HealUseLedger());
        assertTrue(HealFloridFeedback.record("No more charges!",100000,selector));
        assertEquals(115000,selector.lastEmittedUse(HealItemType.FLORID));
        assertTrue(selector.cooling(HealItemType.FLORID,114999));
        assertFalse(selector.cooling(HealItemType.FLORID,115000));
        assertFalse(selector.cooling(HealItemType.HYPERION,100000));
    }
    @Test void nonPrefixCaseChangesAndNullDoNotAcquireHold(){
        var selector=new HealItemSelector();
        for(var text:List.of("no more charges","Someone said No more charges"," No more charges"))
            assertFalse(HealFloridFeedback.record(text,100000,selector));
        assertFalse(HealFloridFeedback.record(null,100000,selector));
        assertEquals(0,selector.lastEmittedUse(HealItemType.FLORID));
    }
    @Test void heldFloridVetoesOffButOtherModesSkipItAcrossHandlers(){
        var ledger=new HealUseLedger();var first=new HealItemSelector(ledger);var second=new HealItemSelector(ledger);
        HealFloridFeedback.record("No more charges",100000,first);
        var items=List.of(new HealItemSelector.Item("minecraft:golden_sword","Florid",false),
                new HealItemSelector.Item("minecraft:stick","Wand of Mending",false));
        assertTrue(second.select(items,HealStackingMode.OFF,100001).isEmpty());
        assertEquals(HealItemType.WAND_OF_MENDING,second.select(items,HealStackingMode.LAZY,100001).orElseThrow().type());
    }
}
