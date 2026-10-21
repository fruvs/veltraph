package dev.veltraph.client.hydra;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Pure, local matcher for routing / disconnect / kick text that a server may send right before it moves
 * or removes a botting account ("sending you to limbo", "you were kicked", "evacuating", an AFK warning,
 * …). The MC adapter registers a Fabric {@code ClientReceiveMessageEvents.GAME} listener and pushes each
 * RECEIVED game/system line in here via {@link #offer(String)}; matching is a config-extendable,
 * case-insensitive substring check.
 *
 * <p><b>Read-only by design.</b> This matcher only inspects lines the client already received — it never
 * sends, replies to, or transmits anything. A routing match it surfaces may, when the user opts in, drive
 * the LOCAL_INCLUDE avoid-evacuation / afterdisconnect re-route in {@code hydra/response/} (a vanilla
 * in-game command), but that decision/execution is elsewhere; see {@code docs/HYDRA_ANTISTAFF_NOTES.md}.
 *
 * <p>Pure (no Minecraft, no clock, no network). A tiny ring buffer keeps the last few received lines for
 * the {@code /vdebug hydra} status read; the detector consumes only the latest match via
 * {@link #pollMatch()} so a single routing line fires at most once.
 *
 * <h2>False-positive tradeoff (documented)</h2>
 * Keywords are matched as substrings, so the default list prefers distinctive routing PHRASES
 * ("sending you to", "you were kicked", "evacuat") over single ambiguous words. A bare "afk" can appear
 * in ordinary chat; it is included because an AFK kick is a real staff/idle action, but the alert is
 * always gated to {@code scripting} + the cooldown so a stray mention cannot spam or false-stop a manual
 * session. Operators tune the list via {@code HydraConfig.ROUTING_TEXT_KEYWORDS}.
 */
public final class RoutingTextMatcher {

    /** Default routing/kick keyword list (lower-cased substrings). Config-extendable. */
    public static final List<String> DEFAULT_KEYWORDS = List.of(
            "sending you to limbo",
            "sending you to the limbo",
            "you are being sent to limbo",
            "sending you to the skyblock lobby", // SkyBlock-lobby routing
            "evacuat",            // "Evacuating to Hub", "evacuated"
            "you were kicked",
            "you have been kicked",
            "kicked from the server",
            "you have been banned",
            "you are banned",
            "banned from",
            "afk",                // AFK warning / AFK kick
            "you have been removed",
            "disconnected",
            "an exception occurred in your connection", // disconnect / connection exception
            "disconnect.exceptions",
            "desync",             // desynced location ("you are desynced", "location desync")
            "dynamic pool",       // dynamic lobby pool error
            "no destination");    // routing with no destination resolved

    private static final int RING_CAPACITY = 8;

    private final List<String> keywords;
    private final Deque<String> recent = new ArrayDeque<>(RING_CAPACITY);
    private String pendingMatch; // the most recent matched keyword not yet consumed, or null

    private RoutingTextMatcher(List<String> keywords) {
        this.keywords = keywords;
    }

    /** Matcher with the {@link #DEFAULT_KEYWORDS}. */
    public static RoutingTextMatcher withDefaults() {
        return new RoutingTextMatcher(normalize(DEFAULT_KEYWORDS));
    }

    /**
     * Parse a comma-joined keyword string (e.g. from config) into a matcher. Blank/null → defaults so a
     * misconfigured/empty value still protects the user. Each entry is trimmed + lower-cased.
     */
    public static RoutingTextMatcher parse(String commaJoined) {
        if (commaJoined == null || commaJoined.isBlank()) {
            return withDefaults();
        }
        List<String> out = new ArrayList<>();
        for (String raw : commaJoined.split(",")) {
            String n = raw.trim();
            if (!n.isEmpty()) {
                out.add(n.toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? withDefaults() : new RoutingTextMatcher(List.copyOf(out));
    }

    private static List<String> normalize(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Feed a RECEIVED game/system line. If it contains any keyword (case-insensitive substring) the line
     * is remembered as a pending match for the next {@link #pollMatch()}. Null/blank lines are ignored.
     * Records the raw line in the small ring buffer for the status read.
     */
    public void offer(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String trimmed = line.trim();
        if (recent.size() >= RING_CAPACITY) {
            recent.pollFirst();
        }
        recent.addLast(trimmed);

        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                pendingMatch = kw; // keep the latest match; the detector consumes it next tick
                return;
            }
        }
    }

    /**
     * Consume and return the most recent matched keyword since the last poll, or null if no line matched.
     * Consuming clears it so the same routing line fires exactly once (the cooldown in the detector then
     * spaces any genuinely repeated routing lines).
     */
    public String pollMatch() {
        String m = pendingMatch;
        pendingMatch = null;
        return m;
    }

    /** True if a routing match is pending (not yet consumed) — used by the status read. */
    public boolean hasPendingMatch() {
        return pendingMatch != null;
    }

    /** Number of configured keywords (status read). */
    public int keywordCount() {
        return keywords.size();
    }

    /** Clear pending + history (used on world stop / feature disable). */
    public void reset() {
        recent.clear();
        pendingMatch = null;
    }
}
