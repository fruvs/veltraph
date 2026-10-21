package dev.veltraph.client.garden.visitor;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Pure helpers that read a Garden visitor's traits from already-extracted, color-stripped text — the
 * visitor's display name and the "Accept Offer" item lore. Kept Minecraft-free so the brittle
 * version-specific text parsing is unit-tested on a plain JDK; the MC adapter
 * ({@code garden/visitor/mc/McVisitorScreenSource}) just feeds it the strings it scraped.
 *
 * <p>Source for the facts being read: {@code reports/_spec_parts/VisitorGui.md} (offers are read by
 * display name / lore, never fixed salted slots) and {@code reports/MACRO_INVENTORY.md} §1 (filters
 * VALUABLE / NEW / PROFITABLE-via-copper). All readers degrade to "unknown" rather than guessing, so the
 * {@link VisitorPolicy} never claims a trait it could not confirm.
 */
public final class VisitorLoreReader {

    private VisitorLoreReader() {
    }

    /** True if the visitor is flagged "new" (a {@code NEW VISITOR} / {@code New visitor!} marker in lore). */
    public static boolean parseNew(List<String> lore) {
        if (lore == null) {
            return false;
        }
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("new visitor")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Best-effort rarity from an explicit rarity word in the visitor name or lore (Hypixel renders the
     * visitor's rarity as a colored word). Returns {@link VisitorRarity#UNKNOWN} when no rarity word is
     * present — the {@code VALUABLE_DROPS} filter then treats the visitor as not-valuable (fail-safe).
     */
    public static VisitorRarity parseRarity(String visitorName, List<String> lore) {
        VisitorRarity fromName = scanRarity(visitorName);
        if (fromName != VisitorRarity.UNKNOWN) {
            return fromName;
        }
        if (lore != null) {
            for (String line : lore) {
                VisitorRarity r = scanRarity(line);
                if (r != VisitorRarity.UNKNOWN) {
                    return r;
                }
            }
        }
        return VisitorRarity.UNKNOWN;
    }

    private static VisitorRarity scanRarity(String text) {
        if (text == null) {
            return VisitorRarity.UNKNOWN;
        }
        String t = text.toUpperCase(Locale.ROOT);
        // Order matters: check the higher tiers / multi-word labels first.
        if (t.contains("SPECIAL")) {
            return VisitorRarity.SPECIAL;
        }
        if (t.contains("MYTHIC")) {
            return VisitorRarity.MYTHIC;
        }
        if (t.contains("LEGENDARY")) {
            return VisitorRarity.LEGENDARY;
        }
        if (t.contains("EPIC")) return VisitorRarity.EPIC;
        if (t.contains("UNCOMMON")) {
            return VisitorRarity.UNCOMMON;
        }
        if (t.contains("RARE")) {
            return VisitorRarity.RARE;
        }
        if (t.contains("COMMON")) {
            return VisitorRarity.COMMON;
        }
        return VisitorRarity.UNKNOWN;
    }

    /**
     * Parse an explicit coin reward line (e.g. {@code "+12,000 Coins"}) from the offer lore, in coins.
     * Returns empty when the offer pays no flat coins — the common case, since visitor rewards are usually
     * Garden Copper + items, whose coin value needs a live Bazaar/copper price (a documented follow-up; we
     * do not invent a conversion). PROFITABLE then stays fail-safe (NEEDS_PRICE) until a reward is known.
     */
    public static OptionalLong parseCoinReward(List<String> lore) {
        return scanRewardWithUnit(lore, "coin");
    }

    /** Parse a Garden Copper reward amount (e.g. {@code "+1,234 Copper"}) from the offer lore. */
    public static OptionalLong parseCopperReward(List<String> lore) {
        return scanRewardWithUnit(lore, "copper");
    }

    private static OptionalLong scanRewardWithUnit(List<String> lore, String unit) {
        if (lore == null) {
            return OptionalLong.empty();
        }
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String l = line.toLowerCase(Locale.ROOT);
            // Reward lines are additive ("+N unit"); require both the plus and the unit to avoid matching
            // a "Required items" count line.
            if (l.contains(unit) && l.indexOf('+') >= 0) {
                OptionalLong v = BazaarPriceParser.extractNumber(line);
                if (v.isPresent()) {
                    return v;
                }
            }
        }
        return OptionalLong.empty();
    }
}
