package dev.veltraph.client.garden.visitor;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.VisitorConfig;
import dev.veltraph.client.log.VeltraphLog;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Local, advisory Garden-visitor watcher (Batch 4 — Garden Visitors family). Source:
 * {@code reports/_spec_parts/VisitorGui.md} + {@code reports/MACRO_INVENTORY.md} §1.
 *
 * <p>When enabled and the player is in the Garden, this reads the open visitor menu (via a read-only
 * {@link VisitorScreenSource}), evaluates the offer against the configured {@link VisitorPolicy}, and
 * surfaces a local <b>accept/skip recommendation</b> once per distinct visitor. It is <b>advisory</b>:
 * it never clicks, buys, or serves. The separate explicitly started McVisitorServeModule handles
 * a bounded inventory-ready serving subset with strict quantity/value/menu acknowledgment guards.
 * Bazaar buying and barn navigation remain unimplemented.
 *
 * <p>It is a tick-driven service rather than a {@code ModuleManager} macro on purpose: the bootstrap
 * stops macros when a GUI opens, but this feature needs the visitor GUI open to do its job. Pure logic
 * (no Minecraft, no clock) — every input is injected — so the whole watcher is unit-testable.
 */
public final class VisitorAdvisor {

    private final ConfigService config;
    private final VisitorScreenSource source;
    private final Consumer<String> chat; // local client-side chat only (never sent to the server)

    public static final String CHAT_PREFIX = "[Veltraph Visitors] ";

    private dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Newness lastAnnouncedHistory;
    private String lastAnnouncedVisitor; // the visitor name we last announced (de-dupes per screen)
    private VisitorOffer lastOffer;
    private VisitorDecision lastDecision;
    private boolean menuOpen;
    private dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Observation lastHistory=
            dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN.observe("");

    public VisitorAdvisor(ConfigService config, VisitorScreenSource source, Consumer<String> chat) {
        this.config = config;
        this.source = source == null ? VisitorScreenSource.NONE : source;
        this.chat = chat;
    }

    public boolean enabled() {
        return config.get(VisitorConfig.VISITOR_ADVISOR_ENABLED);
    }

    /** Advance one tick. {@code inGarden} gates the watcher to the Garden (area gate). */
    public void tick(boolean inGarden) {
        if (!enabled() || !inGarden) {
            clearTransient();
            return;
        }
        Optional<VisitorOffer> read = source.read();
        lastHistory=source.historyObservation();
        if(lastHistory==null)lastHistory=dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN.observe("");
        if (read.isEmpty()) {
            // No visitor menu open right now — forget the last announcement so re-opening re-announces.
            menuOpen = false;
            lastAnnouncedVisitor = null;
            return;
        }
        menuOpen = true;
        VisitorOffer offer = read.get();
        // Re-evaluate current policy; only the local announcement is deduplicated.
        boolean announceChanged=!sameAsLastAnnounced(offer);
        VisitorSettings settings = VisitorSettings.fromConfig(config);
        VisitorDecision decision = VisitorPolicy.decide(offer, settings);
        lastOffer = offer;
        lastDecision = decision;
        if(!announceChanged)return;
        lastAnnouncedHistory=lastHistory.newness();
        lastAnnouncedVisitor = offer.visitorName();
        announce(offer, decision);

    }

    private boolean sameAsLastAnnounced(VisitorOffer offer) {
        return lastAnnouncedVisitor != null && lastAnnouncedVisitor.equalsIgnoreCase(offer.visitorName()) && lastAnnouncedHistory==lastHistory.newness();
    }

    private void announce(VisitorOffer offer, VisitorDecision decision) {
        String visitor = offer.visitorName().isEmpty() ? "Visitor" : offer.visitorName();
        String verb = switch (decision.outcome()) {
            case ACCEPT -> "ACCEPT";
            case SKIP -> "SKIP";
            case NEEDS_PRICE -> "CHECK";
        };
        String msg = visitor + " → " + verb + " (" + decision.reason() + ") | history="+lastHistory.newness();
        VeltraphLog.basic("Visitors", msg);
        if (chat != null) {
            chat.accept(CHAT_PREFIX + msg);
        }
    }

    private void clearTransient() {
        menuOpen = false;
        lastAnnouncedVisitor = null;
        lastHistory=dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN.observe("");
    }

    // ---- read API (status / HUD / debug) ----

    public boolean menuOpen() {
        return menuOpen;
    }

    public VisitorOffer lastOffer() {
        return lastOffer;
    }

    public VisitorDecision lastDecision() {
        return lastDecision;
    }

    /** One-line status for {@code /vdebug visitor}. */
    public String status() {
        VisitorSettings s = VisitorSettings.fromConfig(config);
        StringBuilder sb = new StringBuilder("Garden Visitors: ")
                .append(enabled() ? "ON" : "OFF")
                .append(" | filters=").append(filtersLabel(s))
                .append(" | maxCost=").append(s.haveMaxPrices() ? s.maxCost() : "off")
                .append(" | order=").append(s.order())
                .append(" | menu=").append(menuOpen ? "open" : "closed")
                .append(" | history=").append(lastHistory.newness());
        if (lastOffer != null && lastDecision != null) {
            sb.append(" | last=").append(lastOffer.visitorName().isEmpty() ? "Visitor" : lastOffer.visitorName())
                    .append('/').append(lastDecision.outcome());
        }
        return sb.toString();
    }

    private static String filtersLabel(VisitorSettings s) {
        if (s.noFilterEnabled()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (VisitorAcceptFilter f : VisitorAcceptFilter.values()) {
            if (s.isEnabled(f)) {
                if (sb.length() > 0) {
                    sb.append('+');
                }
                sb.append(f.name());
            }
        }
        return sb.toString();
    }

    /**
     * Multi-line diagnostic for {@code /vdebug visitor debug}: the policy inputs, the parsed offer
     * (visitor, rarity/new, required items + resolved prices), and the last decision + reason. Local only.
     */
    public java.util.List<String> debugDump() {
        VisitorSettings s = VisitorSettings.fromConfig(config);
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("Garden Visitors debug");
        out.add("  enabled=" + enabled() + " | autoServe=" + config.get(VisitorConfig.VISITOR_AUTO_SERVE)
                + " (inventory-ready module opt-in) | autoSellAfter=" + config.get(VisitorConfig.VISITOR_AUTO_SELL_AFTER));
        out.add("  filters=" + filtersLabel(s) + " | enforceMaxCost=" + s.haveMaxPrices()
                + " | maxCost=" + s.maxCost() + " | order=" + s.order());
        out.add("  menu=" + (menuOpen ? "open" : "closed"));
        out.add("  history="+lastHistory.newness()+" | "+lastHistory.provenance());
        if (lastOffer != null) {
            out.add("  visitor=" + (lastOffer.visitorName().isEmpty() ? "Visitor" : lastOffer.visitorName())
                    + " | rarity=" + lastOffer.rarity() + " | history=" + lastHistory.newness()
                    + " | reward=" + (lastOffer.estimatedReward() < 0 ? "unknown" : lastOffer.estimatedReward()));
            if (lastOffer.requiredItems().isEmpty()) {
                out.add("  items: (none)");
            } else {
                for (VisitorItem it : lastOffer.requiredItems()) {
                    out.add("  item: " + it.display() + (it.hasPrice() ? "" : " [price unknown]"));
                }
            }
            long total = lastOffer.totalItemCost();
            out.add("  totalItemCost=" + (total < 0 ? "unknown" : total));
        } else {
            out.add("  (no visitor read yet)");
        }
        if (lastDecision != null) {
            out.add("  decision=" + lastDecision.outcome() + " — " + lastDecision.reason());
        }
        return out;
    }

    /** A short HUD label, e.g. {@code "Visitor: ACCEPT"}, or null when nothing recent. */
    public String hudLabel() {
        if (lastDecision == null) {
            return null;
        }
        return "Visitor: " + lastDecision.outcome()+" | History: "+lastHistory.newness();
    }
}
