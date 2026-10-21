package dev.veltraph.client.garden.visitor;

import java.util.List;

/**
 * A snapshot of one Garden visitor's offer, read from the open visitor {@code HandledScreen}: the
 * visitor's name, its rarity tier, whether it is a "new" (first-seen) visitor, and the list of items it
 * requires. Pure value type — no Minecraft, no network.
 *
 * <p>Source: {@code reports/_spec_parts/VisitorGui.md} (visitor accept/serve GUI — required items are read
 * by display name) + {@code reports/MACRO_INVENTORY.md} §1 (accept filters ALL/VALUABLE/NEW/PROFITABLE).
 *
 * @param visitorName    visitor display name (color-stripped)
 * @param rarity         rarity tier (drives the VALUABLE filter)
 * @param isNew          whether this is a newly-arrived visitor (drives the NEW filter)
 * @param requiredItems  items the visitor wants (name + amount + optional price)
 * @param estimatedReward rough reward value in coins if known ({@code -1} = unknown; drives PROFITABLE)
 */
public record VisitorOffer(
        String visitorName,
        VisitorRarity rarity,
        boolean isNew,
        List<VisitorItem> requiredItems,
        long estimatedReward) {

    public VisitorOffer {
        visitorName = visitorName == null ? "" : visitorName.trim();
        rarity = rarity == null ? VisitorRarity.UNKNOWN : rarity;
        requiredItems = requiredItems == null ? List.of() : List.copyOf(requiredItems);
    }

    /** Convenience: an offer with unknown reward. */
    public static VisitorOffer of(String name, VisitorRarity rarity, boolean isNew, List<VisitorItem> items) {
        return new VisitorOffer(name, rarity, isNew, items, -1L);
    }

    public boolean hasRequiredItems() {
        return !requiredItems.isEmpty();
    }

    /**
     * Fail-closed exact display-name match (color-stripped, case-insensitive), used by the named-visitor
     * filters (Beth/Seymour). Returns {@code false} for a blank/unreadable visitor name or a null query —
     * so a name-matched filter never accepts a visitor whose label could not be confirmed.
     */
    public boolean matchesVisitorName(String wanted) {
        if (wanted == null || visitorName == null || visitorName.isBlank()) {
            return false;
        }
        return visitorName.trim().equalsIgnoreCase(wanted.trim());
    }

    /** Whether every required item has a resolved Bazaar price (needed to prove affordability). */
    public boolean allItemPricesKnown() {
        for (VisitorItem it : requiredItems) {
            if (!it.hasPrice() || it.totalCost()<0) {
                return false;
            }
        }
        return !requiredItems.isEmpty();
    }

    /** Total coins to buy every required item, or {@code -1} if any item's price is unknown. */
    public long totalItemCost() {
        long sum = 0;
        for (VisitorItem it : requiredItems) {
            if (!it.hasPrice() || it.totalCost()<0) {
                return -1L;
            }
            try { sum=Math.addExact(sum,it.totalCost()); }
            catch(ArithmeticException overflow) { return -1L; }
        }
        return sum;
    }

    /** The first required item whose resolved cost exceeds {@code maxCost}, or null (unknown prices skipped). */
    public VisitorItem firstItemOverBudget(long maxCost) {
        for (VisitorItem it : requiredItems) {
            if (it.hasPrice() && it.totalCost() > maxCost) {
                return it;
            }
        }
        return null;
    }
}
