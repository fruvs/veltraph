package dev.veltraph.client.combat.heal;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealItemSelectorTest {
    private static HealItemSelector.Item item(String id, String name) {
        return new HealItemSelector.Item("minecraft:" + id, name, false);
    }
    private static List<HealItemSelector.Item> hotbar(HealItemSelector.Item... items) {
        var result = new ArrayList<HealItemSelector.Item>(List.of(items));
        while (result.size() < 9) result.add(item("air", ""));
        return result;
    }
    @Test void higherPriorityTypeWinsEvenInLaterHotbarSlot() {
        var selected = new HealItemSelector().select(hotbar(item("stick", "Wand of Atonement"),
                item("golden_sword", "Florid Zombie Sword"), item("iron_sword", "Heroic Hyperion")),
                HealStackingMode.OFF, 100000).orElseThrow();
        assertEquals(new HealItemSelector.Selection(HealItemType.HYPERION, 2), selected);
    }
    @Test void laterDuplicateTypeOverwritesEarlierIncludingAnotherWitherVariant() {
        var selected = new HealItemSelector().select(hotbar(item("iron_sword", "Hyperion"),
                item("stick", "Wand of Mending"), item("iron_sword", "Astraea")),
                HealStackingMode.LAZY, 100000).orElseThrow();
        assertEquals(new HealItemSelector.Selection(HealItemType.HYPERION, 2), selected);
    }
    @Test void offVetoesWholeScanEvenWhenAnEligibleItemWasAlreadySeen() {
        var selector = new HealItemSelector();
        selector.recordEmittedUse(HealItemType.WAND_OF_MENDING, 100000);
        assertTrue(selector.select(hotbar(item("golden_sword", "Florid"),
                item("stick", "Wand of Mending")), HealStackingMode.OFF, 100001).isEmpty());
    }
    @Test void bothStackingModesSkipCoolingItemAndChooseEligibleType() {
        for (var mode : List.of(HealStackingMode.LAZY, HealStackingMode.SPAM)) {
            var selector = new HealItemSelector();
            selector.recordEmittedUse(HealItemType.HYPERION, 100000);
            assertEquals(new HealItemSelector.Selection(HealItemType.FLORID, 1), selector.select(
                    hotbar(item("iron_sword", "Hyperion"), item("golden_sword", "Ornate Zombie Sword")),
                    mode, 100001).orElseThrow());
        }
    }
    @Test void cooldownBoundaryIsStrictAndAllWitherVariantsShareIt() {
        var selector = new HealItemSelector();
        selector.recordEmittedUse(HealItemType.HYPERION, 100000);
        var stacks = hotbar(item("iron_sword", "Scylla"));
        assertTrue(selector.select(stacks, HealStackingMode.SPAM, 105499).isEmpty());
        assertEquals(0, selector.select(stacks, HealStackingMode.SPAM, 105500).orElseThrow().slot());
    }
    @Test void floridHasNoPositiveCooldownAndWandsHaveIndependentSixSecondTimers() {
        var selector = new HealItemSelector();
        selector.recordEmittedUse(HealItemType.FLORID, 100000);
        assertFalse(selector.cooling(HealItemType.FLORID, 100000));
        selector.recordEmittedUse(HealItemType.WAND_OF_ATONEMENT, 100000);
        assertTrue(selector.cooling(HealItemType.WAND_OF_ATONEMENT, 105999));
        assertFalse(selector.cooling(HealItemType.WAND_OF_ATONEMENT, 106000));
        assertFalse(selector.cooling(HealItemType.WAND_OF_RESTORATION, 100001));
        assertFalse(selector.cooling(HealItemType.WAND_OF_MENDING, 100001));
    }
    @Test void selectorNeverRecordsUseAndUnknownItemsDoNotTriggerOffVeto() {
        var selector = new HealItemSelector();
        selector.recordEmittedUse(HealItemType.WAND_OF_ATONEMENT, 100000);
        var chosen = selector.select(hotbar(item("diamond_sword", "Hyperion"),
                item("stick", "wand of atonement"), item("golden_sword", "Florid")),
                HealStackingMode.OFF, 100001).orElseThrow();
        assertEquals(HealItemType.FLORID, chosen.type());
        assertEquals(0, selector.lastEmittedUse(HealItemType.FLORID));
    }
    @Test void onlyFirstNineSlotsParticipateAndNoEligibleItemMeansEmpty() {
        var stacks = hotbar(item("air", ""));
        stacks.add(item("iron_sword", "Hyperion"));
        assertTrue(new HealItemSelector().select(stacks, HealStackingMode.LAZY, 100000).isEmpty());
        assertTrue(new HealItemSelector().select(List.of(), HealStackingMode.LAZY, 100000).isEmpty());
    }
    @Test void backwardClockRemainsCoolingAsInSourceSubtraction() {
        var selector = new HealItemSelector();
        selector.recordEmittedUse(HealItemType.HYPERION, 100000);
        assertTrue(selector.cooling(HealItemType.HYPERION, 99999));
    }
    @Test void replacementHandlerCannotBypassSharedTypeCooldownAndScanDoesNotSpendIt() {
        var ledger = new HealUseLedger();
        var firstHandler = new HealItemSelector(ledger);
        firstHandler.recordEmittedUse(HealItemType.HYPERION, 100000);
        var secondHandler = new HealItemSelector(ledger);
        var items = hotbar(item("iron_sword", "Valkyrie"), item("golden_sword", "Florid"));
        assertTrue(secondHandler.select(items, HealStackingMode.OFF, 100001).isEmpty());
        assertEquals(HealItemType.FLORID, secondHandler.select(items, HealStackingMode.LAZY, 100001).orElseThrow().type());
        assertEquals(100000, secondHandler.lastEmittedUse(HealItemType.HYPERION));
        assertEquals(0, secondHandler.lastEmittedUse(HealItemType.FLORID));
        assertEquals(HealItemType.HYPERION, secondHandler.select(items, HealStackingMode.OFF, 105500).orElseThrow().type());
    }
}
