package dev.veltraph.client.command;

import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.LocationResolution;
import dev.veltraph.client.game.LocationSignals;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure formatter for {@code /veltraphdebug location}: turns the collected {@link LocationSignals} and
 * the {@link LocationResolution} into human-readable lines (printed to chat and the client log). Kept
 * pure and bounded so it is unit-testable and never floods chat.
 */
public final class LocationDebugReport {

    /** Max number of normalized sidebar rows / tab lines to include. */
    private static final int SAMPLE = 12;

    private LocationDebugReport() {
    }

    public static List<String> lines(LocationSignals s, LocationResolution r) {
        List<String> out = new ArrayList<>();
        out.add("Veltraph location debug:");
        if (s == null) {
            out.add("  (no signals collected)");
            return out;
        }
        out.add("  brand: " + show(s.serverBrand()));
        out.add("  address: " + show(s.serverAddress()));
        out.add("  sidebar title raw: " + show(s.sidebarTitleRaw()));
        out.add("  sidebar title norm: " + show(s.sidebarTitleNorm()));
        out.add("  sidebar rows norm: " + sample(s.sidebarRowsNorm()));
        out.add("  tab footer raw: " + show(oneLine(s.tabFooterRaw())));
        out.add("  tab footer norm: " + show(s.tabFooterNorm()));
        out.add("  tab lines norm: " + sample(s.tabLinesNorm()));
        if (r != null) {
            GameContext c = r.context();
            out.add("  resolved: onHypixel=" + c.onHypixel()
                    + " inSkyBlock=" + c.inSkyBlock()
                    + " area=" + c.area().displayName());
            out.add("  source: " + r.source() + "  confidence: " + r.confidence());
            out.add("  matched: " + show(r.matchedText()));
            if (!r.notes().isEmpty()) {
                out.add("  notes: " + String.join("; ", r.notes()));
            }
        }
        return out;
    }

    public static String text(LocationSignals s, LocationResolution r) {
        return String.join("\n", lines(s, r));
    }

    private static String show(String v) {
        return v == null || v.isEmpty() ? "(empty)" : v;
    }

    private static String oneLine(String v) {
        return v == null ? null : v.replace("\n", " / ");
    }

    private static String sample(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        int n = Math.min(SAMPLE, values.size());
        String body = String.join(" | ", values.subList(0, n));
        String more = values.size() > n ? " | (+" + (values.size() - n) + " more)" : "";
        return "[" + body + more + "]";
    }
}
