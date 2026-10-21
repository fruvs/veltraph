package dev.veltraph.client.garden.visitor;

/**
 * Pure accept/skip decision for a Garden visitor offer — the clean, deterministic equivalent of the
 * original visitor accept/skip filter (whose exact predicate is hardened/behavior-only; see
 * {@code reports/_spec_parts/VisitorGui.md}). No Minecraft, no network — unit-testable on a plain JDK.
 *
 * <p><b>Filter gate (OR over the recovered toggles).</b> The original models accept filters as six
 * independent booleans (see {@link VisitorAcceptFilter}); a visitor passes the gate if it satisfies
 * <i>any</i> enabled filter:
 * <ul>
 *   <li>{@code ALL} — always;</li>
 *   <li>{@code VALUABLE_DROPS} — rarity is {@link VisitorRarity#isValuable() valuable};</li>
 *   <li>{@code NEW_VISITORS} — the visitor is new;</li>
 *   <li>{@code PROFITABLE} — estimated reward (copper value, in coins) &gt; total item cost;</li>
 *   <li>{@code BETH} / {@code SEYMOUR} — the visitor's display name matches (fail-closed).</li>
 * </ul>
 * If no filter is enabled, every visitor is skipped (advisory-only state).
 *
 * <p><b>Budget guard + fail-closed.</b> When the max-cost cap is enforced
 * ({@code Config.BAZAAR_HAVE_MAX_PRICES} / {@link VisitorSettings#haveMaxPrices()}), an offer is only
 * {@link VisitorDecision.Outcome#ACCEPT ACCEPT} once every required item's Bazaar price is known and under
 * the cap. If a price is missing we cannot prove "buyable under max price", so the decision is
 * {@link VisitorDecision.Outcome#NEEDS_PRICE NEEDS_PRICE} — never an accept. This is the
 * <i>unknown-prices-must-not-auto-serve</i> rule: the advisor surfaces it as "CHECK", and any opt-in
 * auto-serve path treats it as a hard stop.
 */
public final class VisitorPolicy {

    private VisitorPolicy() {
    }

    /** Evaluate {@code offer} against {@code settings} and return the recommendation. */
    public static VisitorDecision decide(VisitorOffer offer, VisitorSettings settings) {
        if (offer == null || !offer.hasRequiredItems()) {
            return VisitorDecision.skip("no required items");
        }
        if (settings.noFilterEnabled()) {
            return VisitorDecision.skip("no accept filter enabled (advisory only)");
        }

        // (1) Filter gate: accept if ANY enabled filter matches. Track whether PROFITABLE wanted prices.
        String matchReason = null;
        boolean profitableNeedsPrice = false;
        for (VisitorAcceptFilter f : VisitorAcceptFilter.values()) {
            if (!settings.isEnabled(f)) {
                continue;
            }
            switch (f) {
                case ALL -> matchReason = pick(matchReason, "Accept All");
                case VALUABLE_DROPS -> {
                    if (offer.rarity().isValuable()) {
                        matchReason = pick(matchReason, "valuable (" + offer.rarity() + ")");
                    }
                }
                case NEW_VISITORS -> {
                    if (offer.isNew()) {
                        matchReason = pick(matchReason, "new visitor");
                    }
                }
                case PROFITABLE -> {
                    long cost = offer.totalItemCost();
                    long reward = offer.estimatedReward();
                    if (cost < 0 || reward < 0) {
                        profitableNeedsPrice = true;
                    } else if (reward > cost) {
                        matchReason = pick(matchReason, "profitable (+" + (reward - cost) + " coins)");
                    }
                }
                case BETH -> {
                    if (offer.matchesVisitorName(f.namedVisitor())) {
                        matchReason = pick(matchReason, "named visitor Beth");
                    }
                }
                case SEYMOUR -> {
                    if (offer.matchesVisitorName(f.namedVisitor())) {
                        matchReason = pick(matchReason, "named visitor Seymour");
                    }
                }
            }
        }

        if (matchReason == null) {
            return profitableNeedsPrice
                    ? VisitorDecision.needsPrice("profit unknown (item prices/reward not read yet)")
                    : VisitorDecision.skip("no enabled filter matched");
        }

        // (2) Budget guard + fail-closed on unknown prices (only when the cap is enforced).
        if (settings.haveMaxPrices()) {
            VisitorItem over = offer.firstItemOverBudget(settings.maxCost());
            if (over != null) {
                return VisitorDecision.skip("over budget: " + over.display() + " > " + settings.maxCost()
                        + " coins/item");
            }
            if (!offer.allItemPricesKnown()) {
                return VisitorDecision.needsPrice(
                        "cannot confirm items are under " + settings.maxCost()
                                + " coins (price not read) — won't auto-serve");
            }
        }

        long total = offer.totalItemCost();
        String reason = total >= 0 ? matchReason + ", items cost " + total + " coins" : matchReason;
        return VisitorDecision.accept(reason);
    }

    /** Keep the first concrete match reason (filters are OR'd; the first satisfied one wins the label). */
    private static String pick(String existing, String candidate) {
        return existing != null ? existing : candidate;
    }
}
