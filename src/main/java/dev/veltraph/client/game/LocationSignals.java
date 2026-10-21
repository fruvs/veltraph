package dev.veltraph.client.game;

import java.util.ArrayList;
import java.util.List;

/**
 * All client-visible location signals collected for a single resolve, with both the <b>raw</b> lines
 * (preserved verbatim for debugging) and their <b>normalized</b> forms (color-stripped, sanitized,
 * lowercased — see {@link LocationText#normalize(String)}) used for matching.
 *
 * <p>This mirrors the original mod's signal set without copying its code:
 * <ul>
 *   <li>{@code serverBrand} / {@code serverAddress} — Hypixel detection.</li>
 *   <li>scoreboard sidebar title + rows — primary location source.</li>
 *   <li>tab (player-list) footer + player display names — fallback source.</li>
 * </ul>
 *
 * <p>Pure: built by the Minecraft-backed service but holds only strings, so it is fully testable.
 */
public final class LocationSignals {

    private final String serverBrand;
    private final String serverAddress;

    private final String sidebarTitleRaw;
    private final String sidebarTitleNorm;
    private final List<String> sidebarRowsRaw;
    private final List<String> sidebarRowsNorm;

    private final String tabFooterRaw;
    private final String tabFooterNorm;
    private final List<String> tabLinesRaw;
    private final List<String> tabLinesNorm;

    private LocationSignals(String serverBrand, String serverAddress,
                            String sidebarTitleRaw, List<String> sidebarRowsRaw,
                            String tabFooterRaw, List<String> tabLinesRaw) {
        this.serverBrand = serverBrand;
        this.serverAddress = serverAddress;
        this.sidebarTitleRaw = orEmpty(sidebarTitleRaw);
        this.sidebarTitleNorm = LocationText.normalize(sidebarTitleRaw);
        this.sidebarRowsRaw = copyRaw(sidebarRowsRaw);
        this.sidebarRowsNorm = normalizeAll(sidebarRowsRaw);
        this.tabFooterRaw = orEmpty(tabFooterRaw);
        this.tabFooterNorm = LocationText.normalize(tabFooterRaw);
        this.tabLinesRaw = copyRaw(tabLinesRaw);
        this.tabLinesNorm = normalizeAll(tabLinesRaw);
    }

    /** Build from raw signals; normalization is computed here. Any argument may be null. */
    public static LocationSignals of(String serverBrand, String serverAddress,
                                     String sidebarTitleRaw, List<String> sidebarRowsRaw,
                                     String tabFooterRaw, List<String> tabLinesRaw) {
        return new LocationSignals(serverBrand, serverAddress, sidebarTitleRaw, sidebarRowsRaw,
                tabFooterRaw, tabLinesRaw);
    }

    /** Convenience for the legacy scoreboard-only path (no tab data). */
    public static LocationSignals ofScoreboard(String serverBrand, String serverAddress,
                                               String sidebarTitleRaw, List<String> sidebarRowsRaw) {
        return new LocationSignals(serverBrand, serverAddress, sidebarTitleRaw, sidebarRowsRaw, null, null);
    }

    public String serverBrand() {
        return serverBrand;
    }

    public String serverAddress() {
        return serverAddress;
    }

    public String sidebarTitleRaw() {
        return sidebarTitleRaw;
    }

    public String sidebarTitleNorm() {
        return sidebarTitleNorm;
    }

    public List<String> sidebarRowsRaw() {
        return sidebarRowsRaw;
    }

    public List<String> sidebarRowsNorm() {
        return sidebarRowsNorm;
    }

    public String tabFooterRaw() {
        return tabFooterRaw;
    }

    public String tabFooterNorm() {
        return tabFooterNorm;
    }

    public List<String> tabLinesRaw() {
        return tabLinesRaw;
    }

    public List<String> tabLinesNorm() {
        return tabLinesNorm;
    }

    /** Every normalized signal (title, rows, footer, tab lines) in resolution-priority order. */
    public List<String> allNormalized() {
        List<String> all = new ArrayList<>();
        if (!sidebarTitleNorm.isEmpty()) {
            all.add(sidebarTitleNorm);
        }
        all.addAll(sidebarRowsNorm);
        if (!tabFooterNorm.isEmpty()) {
            all.add(tabFooterNorm);
        }
        all.addAll(tabLinesNorm);
        return all;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static List<String> copyRaw(List<String> in) {
        if (in == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            if (s != null) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static List<String> normalizeAll(List<String> in) {
        if (in == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            String n = LocationText.normalize(s);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return List.copyOf(out);
    }
}
