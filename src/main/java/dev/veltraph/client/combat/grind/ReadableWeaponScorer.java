package dev.veltraph.client.combat.grind;

import java.util.List;
import java.util.regex.Pattern;

/** Readable shared stable4030 terms only; no native preguard/name score or invented map values. */
public final class ReadableWeaponScorer {
    private ReadableWeaponScorer() {}
    public record NameWeight(String name, int value) {}
    /** Presence declares the source interface contract; null means no contextual interface. */
    public record ContextNames(List<String> names) {
        public ContextNames { names = List.copyOf(names); }
    }
    public record Score(int total, int firstNameWeight, boolean contextualOverride, int globalBonus, int itemBonus) {}
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");

    /** The caller supplies an observed name-map iteration order, not a fabricated sorted priority.
     * Production context contracts use an empty map because its population is not recovered. */
    public static Score score(BestiaryWeaponSelector.Candidate item, String globalPreferred,
                              List<NameWeight> observedNameWeights, ContextNames context) {
        if (item == null || item.empty() || item.kind() == null) return new Score(0, 0, false, 0, 0);
        int score = 0, weight = 0, global = 0;
        boolean contextual = false;
        if (item.customName() != null) {
            String name = FORMATTING.matcher(item.customName()).replaceAll("");
            for (NameWeight entry : observedNameWeights) {
                if (name.contains(entry.name())) { weight = entry.value(); score += weight; break; }
            }
            if (context != null) for (String entry : context.names()) {
                if (name.contains(entry)) { score = 13; contextual = true; break; }
            }
            if (globalPreferred != null && !globalPreferred.isEmpty() && name.contains(globalPreferred)) {
                score += 500; global = 500;
            }
        }
        // This addition is after the contextual reset: a matching sword is16, not13.
        int itemBonus = switch (item.kind()) { case SWORD -> 3; case AXE -> 2; case PICKAXE -> 1; case OTHER -> 0; };
        return new Score(score + itemBonus, weight, contextual, global, itemBonus);
    }
}
