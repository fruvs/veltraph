package dev.veltraph.client.garden.visitor;

import java.util.Locale;

/**
 * Order in which the visitor queue is served. Recovered as {@code Config.VISITOR_ORDER}, default
 * {@code SortOrder.DESCENDING}. Stable4.0.30's readable comparator sorts 3D distance from a supplied
 * barn/reference point, farthest first. ASCENDING reverses it and RANDOM shuffles it. Earlier notes
 * interpreting this as rarity/profit order were incorrect.
 */
public enum SortOrder {
    /** Nearest reference-point distance first. */
    ASCENDING,
    /** Farthest reference-point distance first (recovered default). */
    DESCENDING,
    RANDOM;

    /** Lenient parse, default {@link #DESCENDING}. */
    public static SortOrder parse(String raw) {
        if (raw == null) {
            return DESCENDING;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        for (SortOrder o : values()) {
            if (o.name().equals(s)) {
                return o;
            }
        }
        return DESCENDING;
    }
}
