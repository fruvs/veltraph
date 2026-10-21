package dev.veltraph.client.garden.visitor;

/**
 * The result of evaluating a {@link VisitorOffer} against the configured {@link VisitorSettings}. Pure
 * value type. The MC layer surfaces this as a local recommendation (chat / HUD / debug); whether it is
 * acted on automatically is gated separately (auto-serve is deferred — see {@code VisitorModule}).
 *
 * @param outcome {@link Outcome#ACCEPT} / {@link Outcome#SKIP} / {@link Outcome#NEEDS_PRICE}
 * @param reason  short human-readable explanation for the recommendation
 */
public record VisitorDecision(Outcome outcome, String reason) {

    public enum Outcome {
        /** The visitor passes the filter and is within budget — recommend serving. */
        ACCEPT,
        /** The visitor is filtered out or over budget — recommend skipping. */
        SKIP,
        /** The decision needs a Bazaar price that has not been read yet (advisory: don't auto-serve). */
        NEEDS_PRICE
    }

    public static VisitorDecision accept(String reason) {
        return new VisitorDecision(Outcome.ACCEPT, reason);
    }

    public static VisitorDecision skip(String reason) {
        return new VisitorDecision(Outcome.SKIP, reason);
    }

    public static VisitorDecision needsPrice(String reason) {
        return new VisitorDecision(Outcome.NEEDS_PRICE, reason);
    }

    public boolean isAccept() {
        return outcome == Outcome.ACCEPT;
    }
}
