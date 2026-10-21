package dev.veltraph.client.combat.heal;

import java.util.OptionalInt;

/** Parser for an already isolated original glyph segment; native modern glyph mapping is unbound. */
public final class HealSourceStatSegment {
    private HealSourceStatSegment() {}
    /** Original health threshold field stores the first integer numerator, with commas removed. */
    public static OptionalInt rawHealth(String segment) {
        if (segment == null) return OptionalInt.empty();
        try { return OptionalInt.of(Integer.parseInt(segment.split(" ")[0].split("/")[0].replace(",", ""))); }
        catch (NumberFormatException | ArrayIndexOutOfBoundsException invalid) { return OptionalInt.empty(); }
    }
    /** Original mana field adds the second-space-token E017 overflow to the integer numerator. */
    public static OptionalInt mana(String segment) {
        if (segment == null) return OptionalInt.empty();
        try {
            int current = Integer.parseInt(segment.split(" ")[0].split("/")[0].replace(",", ""));
            if (segment.contains("\uE017")) current += Integer.parseInt(segment.split(" ")[1]
                    .replace("\uE017", "").replace(",", "").replace("ⓩ", "").replace("Ⓞ", ""));
            return OptionalInt.of(current);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException invalid) { return OptionalInt.empty(); }
    }
}
