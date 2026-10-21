package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.VisitorConfig;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable per-evaluation snapshot of the visitor config, decoupling the pure {@link VisitorPolicy} from
 * {@link ConfigService}. Built via {@link #fromConfig(ConfigService)}.
 *
 * <p>The accept filters are the recovered <b>six independent toggles</b> (see {@link VisitorAcceptFilter})
 * rather than a single-select value; {@link #filters} is the set that are enabled. A visitor passes the
 * filter gate if it satisfies <i>any</i> enabled filter (OR semantics).
 *
 * @param filters       the enabled accept-filter dimensions (may be empty = "advisory off / skip all")
 * @param maxCost       per-item coin budget cap (enforced only when {@link #haveMaxPrices})
 * @param haveMaxPrices whether the budget cap is enforced ({@code Config.BAZAAR_HAVE_MAX_PRICES})
 * @param order         serve order ({@code Config.VISITOR_ORDER}); advisory display only
 */
public record VisitorSettings(Set<VisitorAcceptFilter> filters, long maxCost, boolean haveMaxPrices,
                              SortOrder order) {

    public VisitorSettings {
        filters = filters == null ? EnumSet.noneOf(VisitorAcceptFilter.class)
                : EnumSet.copyOf(filters.isEmpty() ? EnumSet.noneOf(VisitorAcceptFilter.class) : filters);
        order = order == null ? SortOrder.DESCENDING : order;
    }

    /** Convenience for tests: a settings with the given filters, no cap, descending order. */
    public static VisitorSettings of(long maxCost, boolean haveMaxPrices, VisitorAcceptFilter... enabled) {
        EnumSet<VisitorAcceptFilter> set = EnumSet.noneOf(VisitorAcceptFilter.class);
        for (VisitorAcceptFilter f : enabled) {
            set.add(f);
        }
        return new VisitorSettings(set, maxCost, haveMaxPrices, SortOrder.DESCENDING);
    }

    public boolean isEnabled(VisitorAcceptFilter filter) {
        return filters.contains(filter);
    }

    /** Whether no accept filter is enabled (the policy then recommends skipping every visitor). */
    public boolean noFilterEnabled() {
        return filters.isEmpty();
    }

    public static VisitorSettings fromConfig(ConfigService config) {
        EnumSet<VisitorAcceptFilter> enabled = EnumSet.noneOf(VisitorAcceptFilter.class);
        if (config.get(VisitorConfig.VISITOR_ACCEPT_FILTER_ALL)) {
            enabled.add(VisitorAcceptFilter.ALL);
        }
        if (config.get(VisitorConfig.VISITOR_ACCEPT_FILTER_VALUABLE_DROPS)) {
            enabled.add(VisitorAcceptFilter.VALUABLE_DROPS);
        }
        if (config.get(VisitorConfig.VISITOR_ACCEPT_FILTER_NEW_VISITORS)) {
            enabled.add(VisitorAcceptFilter.NEW_VISITORS);
        }
        if (config.get(VisitorConfig.VISITOR_ACCEPT_FILTER_PROFITABLE)) {
            enabled.add(VisitorAcceptFilter.PROFITABLE);
        }
        if (config.get(VisitorConfig.VISITOR_ACCEPT_FILTER_BETH)) {
            enabled.add(VisitorAcceptFilter.BETH);
        }
        if (config.get(VisitorConfig.VISITOR_ACCEPT_FILTER_SEYMOUR)) {
            enabled.add(VisitorAcceptFilter.SEYMOUR);
        }
        return new VisitorSettings(
                enabled,
                config.get(VisitorConfig.VISITOR_MAX_COST),
                config.get(VisitorConfig.VISITOR_HAVE_MAX_PRICES),
                config.get(VisitorConfig.VISITOR_ORDER));
    }
}
