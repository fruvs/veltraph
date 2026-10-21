package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealItemTypeTest {
    @Test void classifiesAllRecoveredCaptionsOnlyWithExactItemAndCase() {
        for (var name : new String[]{"Astraea", "Hyperion", "Scylla", "Valkyrie"}) {
            assertEquals(HealItemType.HYPERION, HealItemType.classify("minecraft:iron_sword", "Heroic " + name, false));
            assertNull(HealItemType.classify("minecraft:diamond_sword", name, false));
            assertNull(HealItemType.classify("minecraft:iron_sword", name.toLowerCase(java.util.Locale.ROOT), false));
        }
        assertEquals(HealItemType.FLORID, HealItemType.classify("minecraft:golden_sword", "Florid Zombie Sword", false));
        assertEquals(HealItemType.FLORID, HealItemType.classify("minecraft:golden_sword", "Ornate Zombie Sword", false));
        for (var type : new HealItemType[]{HealItemType.WAND_OF_ATONEMENT, HealItemType.WAND_OF_RESTORATION, HealItemType.WAND_OF_MENDING}) {
            String name = switch(type) {
                case WAND_OF_ATONEMENT -> "Wand of Atonement";
                case WAND_OF_RESTORATION -> "Wand of Restoration";
                default -> "Wand of Mending";
            };
            assertEquals(type, HealItemType.classify("minecraft:stick", name, false));
            assertNull(HealItemType.classify("minecraft:blaze_rod", name, false));
        }
    }
    @Test void emptyOrUnknownStacksCannotClassify() {
        assertNull(HealItemType.classify("minecraft:iron_sword", "Hyperion", true));
        assertNull(HealItemType.classify(null, "Hyperion", false));
        assertNull(HealItemType.classify("minecraft:stick", null, false));
    }
    @Test void numericValuesAndEnumPriorityAreRecoveredNotConfiguredSubstrings() {
        assertArrayEquals(new HealItemType[]{HealItemType.HYPERION, HealItemType.FLORID,
                HealItemType.WAND_OF_ATONEMENT, HealItemType.WAND_OF_RESTORATION, HealItemType.WAND_OF_MENDING}, HealItemType.values());
        assertArrayEquals(new long[]{5500,0,6000,6000,6000}, java.util.Arrays.stream(HealItemType.values()).mapToLong(HealItemType::cooldownMillis).toArray());
        assertArrayEquals(new int[]{300,70,240,200,100}, java.util.Arrays.stream(HealItemType.values()).mapToInt(HealItemType::manaCost).toArray());
    }
    @Test void freshDefaultOnlyChoosesOffOrLazyAndNeverSpam() {
        var random = new java.util.Random(17);
        var found = java.util.EnumSet.noneOf(HealStackingMode.class);
        for (int i=0;i<128;i++) found.add(HealStackingMode.freshDefault(random));
        assertEquals(java.util.EnumSet.of(HealStackingMode.OFF, HealStackingMode.LAZY), found);
    }
}
