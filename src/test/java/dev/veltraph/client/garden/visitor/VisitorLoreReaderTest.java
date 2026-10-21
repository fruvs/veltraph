package dev.veltraph.client.garden.visitor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure visitor-lore trait reader (no Minecraft). */
class VisitorLoreReaderTest {

    @Test
    void parseNewDetectsNewVisitorMarker() {
        assertTrue(VisitorLoreReader.parseNew(List.of("Talk to me!", "NEW VISITOR!")));
        assertTrue(VisitorLoreReader.parseNew(List.of("This is a new visitor to your garden")));
        assertFalse(VisitorLoreReader.parseNew(List.of("Required items", "x64 Wheat")));
        assertFalse(VisitorLoreReader.parseNew(null));
    }

    @Test
    void parseRarityFromNameOrLoreHighestTierFirst() {
        assertEquals(VisitorRarity.SPECIAL, VisitorLoreReader.parseRarity("Beth", List.of("SPECIAL")));
        assertEquals(VisitorRarity.LEGENDARY, VisitorLoreReader.parseRarity("V", List.of("LEGENDARY VISITOR")));
        assertEquals(VisitorRarity.RARE, VisitorLoreReader.parseRarity("V", List.of("Rare")));
        assertEquals(VisitorRarity.EPIC, VisitorLoreReader.parseRarity("V", List.of("EPIC VISITOR")));
        assertEquals(VisitorRarity.UNCOMMON, VisitorLoreReader.parseRarity("V", List.of("Uncommon")));
        assertEquals(VisitorRarity.COMMON, VisitorLoreReader.parseRarity("V", List.of("Common")));
        assertEquals(VisitorRarity.UNKNOWN, VisitorLoreReader.parseRarity("V", List.of("no rarity word")));
    }
    @Test void standardNpcRarityColorIncludesEpicAndFailsUnknown(){assertEquals(VisitorRarity.EPIC,VisitorRarity.fromRgb(0xaa00aa));assertTrue(VisitorRarity.EPIC.isValuable());assertEquals(VisitorRarity.UNKNOWN,VisitorRarity.fromRgb(0x123456));}

    @Test
    void parseRewardReadsAdditiveCoinAndCopperLines() {
        List<String> lore = List.of("Rewards", "+12,000 Coins", "+1,234 Copper", "+5 Garden XP");
        assertEquals(OptionalLong.of(12_000L), VisitorLoreReader.parseCoinReward(lore));
        assertEquals(OptionalLong.of(1_234L), VisitorLoreReader.parseCopperReward(lore));
    }

    @Test
    void rewardEmptyWhenNoAdditiveLine() {
        // A bare count line ("64 Coins worth") without a leading '+' is not a reward line.
        assertTrue(VisitorLoreReader.parseCoinReward(List.of("Requires 64 Coins worth of wheat")).isEmpty());
        assertTrue(VisitorLoreReader.parseCoinReward(null).isEmpty());
        assertTrue(VisitorLoreReader.parseCopperReward(List.of("no copper here")).isEmpty());
    }
}
