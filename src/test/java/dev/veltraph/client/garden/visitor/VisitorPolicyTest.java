package dev.veltraph.client.garden.visitor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.veltraph.client.garden.visitor.VisitorAcceptFilter.ALL;
import static dev.veltraph.client.garden.visitor.VisitorAcceptFilter.BETH;
import static dev.veltraph.client.garden.visitor.VisitorAcceptFilter.NEW_VISITORS;
import static dev.veltraph.client.garden.visitor.VisitorAcceptFilter.PROFITABLE;
import static dev.veltraph.client.garden.visitor.VisitorAcceptFilter.SEYMOUR;
import static dev.veltraph.client.garden.visitor.VisitorAcceptFilter.VALUABLE_DROPS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure accept/skip policy tests (no Minecraft). Covers the recovered six independent filter toggles (OR
 * semantics), the budget guard, the unknown-price fail-closed rule, and Beth/Seymour name matching.
 */
class VisitorPolicyTest {

    private static VisitorOffer offer(String name, VisitorRarity rarity, boolean isNew, long reward,
                                      VisitorItem... items) {
        return new VisitorOffer(name, rarity, isNew, List.of(items), reward);
    }

    private static VisitorDecision.Outcome decide(VisitorOffer o, VisitorSettings s) {
        return VisitorPolicy.decide(o, s).outcome();
    }

    @Test
    void noItemsIsSkip() {
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(new VisitorOffer("X", VisitorRarity.COMMON, false, List.of(), -1L),
                        VisitorSettings.of(1_000_000L, false, ALL)));
    }

    @Test
    void noFilterEnabledSkipsEveryVisitor() {
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.MYTHIC, true, -1L, VisitorItem.of("Wheat", 1)),
                        VisitorSettings.of(1_000_000L, false)));
    }

    @Test
    void filterAllAcceptsAnyVisitorWhenCapOff() {
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, VisitorItem.of("Wheat", 64)),
                        VisitorSettings.of(1_000_000L, false, ALL)));
    }

    @Test
    void valuableDropsAcceptsHighTiersAndSkipsLow() {
        VisitorSettings s = VisitorSettings.of(1_000_000L, false, VALUABLE_DROPS);
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.RARE, false, -1L, VisitorItem.of("X", 1)), s));
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.SPECIAL, false, -1L, VisitorItem.of("X", 1)), s));
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), s));
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.UNKNOWN, false, -1L, VisitorItem.of("X", 1)), s));
    }

    @Test
    void newVisitorsOnlyAcceptsNew() {
        VisitorSettings s = VisitorSettings.of(1_000_000L, false, NEW_VISITORS);
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.COMMON, true, -1L, VisitorItem.of("X", 1)), s));
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), s));
    }

    @Test
    void profitableNeedsPricesThenComparesRewardToCost() {
        VisitorSettings s = VisitorSettings.of(10_000_000L, false, PROFITABLE);
        // unknown item price -> NEEDS_PRICE
        assertEquals(VisitorDecision.Outcome.NEEDS_PRICE,
                decide(offer("V", VisitorRarity.COMMON, false, 5000L, VisitorItem.of("X", 1)), s));
        // known cost, unknown reward -> NEEDS_PRICE
        assertEquals(VisitorDecision.Outcome.NEEDS_PRICE,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, new VisitorItem("X", 1, 100L)), s));
        // reward > cost -> ACCEPT
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.COMMON, false, 5000L, new VisitorItem("X", 2, 100L)), s));
        // reward <= cost -> SKIP
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.COMMON, false, 100L, new VisitorItem("X", 2, 100L)), s));
    }

    @Test
    void filtersAreOredTogether() {
        // VALUABLE_DROPS + NEW_VISITORS both on: a common-but-new visitor passes via NEW_VISITORS.
        VisitorSettings s = VisitorSettings.of(1_000_000L, false, VALUABLE_DROPS, NEW_VISITORS);
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.COMMON, true, -1L, VisitorItem.of("X", 1)), s));
        // neither valuable nor new -> SKIP
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), s));
    }

    @Test
    void bethAndSeymourAreNameMatchedAndFailClosed() {
        VisitorSettings beth = VisitorSettings.of(1_000_000L, false, BETH);
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("Beth", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), beth));
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("beth", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), beth)); // case-insensitive
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("Jacob", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), beth));
        // blank / unreadable name -> fail closed (no match)
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), beth));

        VisitorSettings seymour = VisitorSettings.of(1_000_000L, false, SEYMOUR);
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("Seymour", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), seymour));
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("Beth", VisitorRarity.COMMON, false, -1L, VisitorItem.of("X", 1)), seymour));
    }

    @Test
    void budgetGuardSkipsOverPricedItemsOnlyWhenEnabled() {
        VisitorItem pricey = new VisitorItem("Enchanted Hay", 10, 200_000L); // 2,000,000 total
        // cap on, over budget -> SKIP
        assertEquals(VisitorDecision.Outcome.SKIP,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, pricey),
                        VisitorSettings.of(1_000_000L, true, ALL)));
        // cap off -> ACCEPT (budget ignored)
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(offer("V", VisitorRarity.COMMON, false, -1L, pricey),
                        VisitorSettings.of(1_000_000L, false, ALL)));
    }

    @Test
    void unknownPriceFailsClosedWhenCapEnforced() {
        VisitorOffer mystery = offer("V", VisitorRarity.COMMON, false, -1L, VisitorItem.of("Mystery", 64));
        // ALL passes the filter, but with the cap ON and an unknown price we cannot confirm "under max
        // cost" -> NEEDS_PRICE (won't auto-serve), NOT accept.
        assertEquals(VisitorDecision.Outcome.NEEDS_PRICE,
                decide(mystery, VisitorSettings.of(10_000_000L, true, ALL)));
        // cap OFF -> no affordability claim needed -> ACCEPT.
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(mystery, VisitorSettings.of(10_000_000L, false, ALL)));
    }

    @Test
    void knownAffordablePriceAcceptsUnderCap() {
        VisitorOffer cheap = offer("V", VisitorRarity.COMMON, false, -1L, new VisitorItem("Hay", 64, 10L));
        assertEquals(VisitorDecision.Outcome.ACCEPT,
                decide(cheap, VisitorSettings.of(1_000_000L, true, ALL)));
    }
}
