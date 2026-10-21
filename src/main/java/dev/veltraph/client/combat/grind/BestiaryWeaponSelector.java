package dev.veltraph.client.combat.grind;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Readable stable4030 weapon-score terms. Native custom-name scoring remains unresolved. */
public final class BestiaryWeaponSelector {
    private BestiaryWeaponSelector() {}
    public enum Kind { SWORD, AXE, PICKAXE, OTHER }
    /** customName is null for an ordinary translated item name; only CUSTOM_NAME receives +500. */
    public record Candidate(int slot, boolean empty, Kind kind, String customName) {}
    public record Choice(int slot, int score) {}
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");

    public static int score(Candidate item, String preferred) {
        if (item == null || item.empty() || item.kind() == null) return 0;
        int score = switch (item.kind()) { case SWORD -> 3; case AXE -> 2; case PICKAXE -> 1; case OTHER -> 0; };
        // Source strips formatting from the custom name only. contains() is case-sensitive,
        // with no trimming, lowercasing, exact-name comparison or translated-name fallback.
        if (item.customName() != null && preferred != null && !preferred.isEmpty()
                && FORMATTING.matcher(item.customName()).replaceAll("").contains(preferred)) score += 500;
        return score;
    }

    /** Local safety scope is hotbar only. Equal scores retain the earliest hotbar slot.
     * Original inventory transfer and equal-score traversal are not recovered parity claims. */
    public static Optional<Choice> select(List<Candidate> items, String preferred) {
        if (items == null || preferred == null) return Optional.empty();
        Candidate[] hotbar = new Candidate[9];
        for (Candidate item : items) {
            if (item == null || item.slot() < 0 || item.slot() > 8) continue;
            if (hotbar[item.slot()] != null) return Optional.empty(); // ambiguous observation
            hotbar[item.slot()] = item;
        }
        Choice best = null;
        for (int slot = 0; slot < hotbar.length; slot++) {
            int score = score(hotbar[slot], preferred);
            if (score > 0 && (best == null || score > best.score())) best = new Choice(slot, score);
        }
        return Optional.ofNullable(best);
    }
}
