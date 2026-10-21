package dev.veltraph.client.garden.visitor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure Bazaar "Coins" lore-line parser + max-cost gate (no Minecraft). */
class BazaarPriceParserTest {

    @Test
    void parsesCommaGroupedCoinsLine() {
        OptionalLong v = BazaarPriceParser.parseCoins(List.of(
                "Enchanted Hay Bale", "Buy price: 1,234,567 Coins", "Click to pick up!"));
        assertEquals(OptionalLong.of(1_234_567L), v);
    }

    @Test
    void parsesDecimalCoinsFloored() {
        assertEquals(OptionalLong.of(12L), BazaarPriceParser.extractNumber("12.9 coins"));
    }

    @Test
    void parsesShorthandSuffixes() {
        assertEquals(OptionalLong.of(2_000L), BazaarPriceParser.extractNumber("2k coins"));
        assertEquals(OptionalLong.of(3_500_000L), BazaarPriceParser.extractNumber("3.5m coins"));
        assertEquals(OptionalLong.of(1_000_000_000L), BazaarPriceParser.extractNumber("1b"));
    }

    @Test
    void picksTheCoinsLineNotOtherNumbers() {
        OptionalLong v = BazaarPriceParser.parseCoins(List.of(
                "Requires 64 of this", "Sells for 999 Coins"));
        assertEquals(OptionalLong.of(999L), v);
    }

    @Test
    void stripsColorCodesBeforeParsing() {
        // A still-color-coded "§6Buy price: §a12,000 Coins" line should parse to 12000.
        assertEquals(OptionalLong.of(12_000L),
                BazaarPriceParser.parseCoins(List.of("§6Buy price: §a12,000 Coins")));
        assertEquals("Buy price: 12,000 Coins",
                BazaarPriceParser.stripColorCodes("§6Buy price: §a12,000 Coins"));
    }

    @Test
    void emptyWhenNoCoinsLine() {
        assertTrue(BazaarPriceParser.parseCoins(List.of("Just a description", "x64")).isEmpty());
        assertTrue(BazaarPriceParser.parseCoins(null).isEmpty());
        assertTrue(BazaarPriceParser.parseCoins(List.of("coins but no number here")).isEmpty());
    }

    @Test
    void priceWithCapMissingWhenUnparseable() {
        BazaarPriceParser.Result r = BazaarPriceParser.priceWithCap(List.of("no price here"), 1_000L, true);
        assertFalse(r.present());
        assertEquals(BazaarPriceParser.Result.MISSING, r);
        assertFalse(r.affordable());
    }

    @Test
    void priceWithCapFlagsTooExpensiveOnlyWhenEnforced() {
        List<String> lore = List.of("Buy price: 5,000 Coins");
        // enforced + over cap -> tooExpensive
        BazaarPriceParser.Result over = BazaarPriceParser.priceWithCap(lore, 1_000L, true);
        assertTrue(over.present());
        assertEquals(5_000L, over.price());
        assertTrue(over.tooExpensive());
        assertFalse(over.affordable());
        // enforced + under cap -> affordable
        BazaarPriceParser.Result under = BazaarPriceParser.priceWithCap(lore, 10_000L, true);
        assertTrue(under.affordable());
        assertFalse(under.tooExpensive());
        // not enforced -> never tooExpensive even if over the (ignored) cap
        BazaarPriceParser.Result uncapped = BazaarPriceParser.priceWithCap(lore, 1_000L, false);
        assertTrue(uncapped.affordable());
        assertFalse(uncapped.tooExpensive());
    }
}
