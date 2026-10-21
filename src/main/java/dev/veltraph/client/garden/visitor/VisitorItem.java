package dev.veltraph.client.garden.visitor;

import java.util.Locale;

/**
 * One required item line in a Garden visitor's offer: an item name and the amount the visitor wants, plus
 * an optional per-unit Bazaar buy price (in coins; {@code -1} = unknown / not yet read). Pure value type —
 * no Minecraft, no network.
 *
 * <p>The visitor menu lists required items by display name (the original matches them by name, not by a
 * fixed salted slot index — see {@code reports/_spec_parts/VisitorGui.md}). The unit price is filled by
 * the MC adapter when a Bazaar price is available; the pure {@link VisitorPolicy} treats {@code -1} as
 * "price unknown" and never claims affordability it cannot prove.
 *
 * @param name      item display name (color-stripped)
 * @param amount    quantity the visitor requires (≥ 1)
 * @param unitPrice per-unit Bazaar buy price in coins, or {@code -1} when unknown
 */
public record VisitorItem(String name, int amount, long unitPrice) {

    public VisitorItem {
        name = name == null ? "" : name.trim();
        if (amount < 1) {
            amount = 1;
        }
    }

    /** An item with an unknown price. */
    public static VisitorItem of(String name, int amount) {
        return new VisitorItem(name, amount, -1L);
    }

    /** Whether a Bazaar unit price has been resolved for this item. */
    public boolean hasPrice() {
        return unitPrice >= 0;
    }

    /** Total coins to buy {@code amount} of this item, or {@code -1} when the price is unknown. */
    public long totalCost() {
        if(!hasPrice()) return -1L;
        try { return Math.multiplyExact(unitPrice,(long)amount); }
        catch(ArithmeticException overflow) { return -1L; }
    }

    /** A copy with the resolved per-unit price. */
    public VisitorItem withUnitPrice(long price) {
        return new VisitorItem(name, amount, price < 0 ? -1L : price);
    }

    public String display() {
        return amount + "x " + name + (hasPrice() ? " (" + totalCost() + " coins)" : "");
    }

    /** Case-insensitive name match (for slot name-matching in the adapter). */
    public boolean nameMatches(String other) {
        return other != null && !other.isBlank() && name.equalsIgnoreCase(other.trim());
    }
}
