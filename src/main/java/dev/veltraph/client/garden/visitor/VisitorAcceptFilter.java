package dev.veltraph.client.garden.visitor;

import java.util.Locale;






















public enum VisitorAcceptFilter {

    /** {@code VISITOR_ACCEPT_FILTER_ALL} — accept every visitor (still subject to the budget guard). */
    ALL("Accept All", "VISITOR_ACCEPT_FILTER_ALL"),

    /**
     * {@code VISITOR_ACCEPT_FILTER_VALUABLE_DROPS} — accept visitors whose rarity tier is
     * {@link VisitorRarity#isValuable() valuable} (RARE+ / SPECIAL). Recovered default ON.
     */
    VALUABLE_DROPS("Valuable Drops", "VISITOR_ACCEPT_FILTER_VALUABLE_DROPS"),

    /** {@code VISITOR_ACCEPT_FILTER_NEW_VISITORS} — accept newly-arrived ("new") visitors. */
    NEW_VISITORS("New Visitors", "VISITOR_ACCEPT_FILTER_NEW_VISITORS"),

    /**
     * {@code VISITOR_ACCEPT_FILTER_PROFITABLE_VIA_COPPER_VALUE} — accept visitors whose estimated reward
     * value (the Garden-Copper-denominated reward, converted to coins) exceeds the Bazaar cost of the
     * required items. Needs both the reward and the item prices to be read first.
     */
    PROFITABLE("Profitable (copper value)", "VISITOR_ACCEPT_FILTER_PROFITABLE_VIA_COPPER_VALUE"),

    /**
     * {@code VISITOR_ACCEPT_FILTER_BETH} — accept the named special visitor "Beth". Matched by
     * <b>display name</b> (color-stripped, exact, case-insensitive) and fail-closed (never matches an
     * unreadable / differently-named visitor).
     */
    BETH("Beth", "VISITOR_ACCEPT_FILTER_BETH", "Beth"),

    /** {@code VISITOR_ACCEPT_FILTER_SEYMOUR} — accept the named special visitor "Seymour" (name-matched). */
    SEYMOUR("Seymour", "VISITOR_ACCEPT_FILTER_SEYMOUR", "Seymour");

    private final String label;
    private final String configKeyName;
    private final String namedVisitor; // non-null only for the name-matched (Beth/Seymour) filters

    VisitorAcceptFilter(String label, String configKeyName) {
        this(label, configKeyName, null);
    }

    VisitorAcceptFilter(String label, String configKeyName, String namedVisitor) {
        this.label = label;
        this.configKeyName = configKeyName;
        this.namedVisitor = namedVisitor;
    }

    /** Human-friendly Settings label. */
    public String label() {
        return label;
    }

    /** The recovered {@code Config} enum-constant name this dimension corresponds to. */
    public String configKeyName() {
        return configKeyName;
    }

    /**
     * The exact visitor display name this filter accepts (Beth/Seymour), or {@code null} for the
     * trait-based filters (ALL/VALUABLE_DROPS/NEW_VISITORS/PROFITABLE).
     */
    public String namedVisitor() {
        return namedVisitor;
    }

    public boolean isNamedVisitorFilter() {
        return namedVisitor != null;
    }

    /** Lenient parse of a filter name; accepts a few aliases. Returns {@code null} when unknown. */
    public static VisitorAcceptFilter parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "VALUABLE", "VALUABLE_DROPS", "VALUABLE-DROPS" -> {
                return VALUABLE_DROPS;
            }
            case "NEW", "NEW_VISITORS", "NEW-VISITORS" -> {
                return NEW_VISITORS;
            }
            case "PROFITABLE", "PROFITABLE_VIA_COPPER_VALUE", "COPPER" -> {
                return PROFITABLE;
            }
            default -> {
                for (VisitorAcceptFilter f : values()) {
                    if (f.name().equals(s)) {
                        return f;
                    }
                }
                return null;
            }
        }
    }
}
