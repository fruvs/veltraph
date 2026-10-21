package dev.veltraph.client.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure resolver from raw client-visible signals to a {@link GameContext}. Kept separate from the
 * Minecraft-backed service so the SkyBlock-vs-BedWars/lobby and Garden-detection logic is fully
 * unit-testable. No network calls.
 *
 * <p>This follows the original mod's layered signal model (recovered from {@code ServerLocationUtil} /
 * {@code LocationTracker} / the scoreboard + tab helpers) without copying its obfuscated code:
 * <ol>
 *   <li><b>Hypixel</b> is detected from the server brand ({@code "Hypixel BungeeCord.*"}) or address.</li>
 *   <li><b>SkyBlock</b> is detected from the scoreboard title <i>or</i> any strong SkyBlock signal
 *       (Purse, Bits, Copper, Garden, Plot, Visitors, Pests, "SkyBlock") in the scoreboard rows or
 *       tab fallback — the title does <i>not</i> have to literally say {@code SKYBLOCK}.</li>
 *   <li>Known non-SkyBlock minigame titles (Bed Wars, SkyWars, Duels, ...) are excluded from SkyBlock
 *       even if a stray token coincides.</li>
 *   <li>The <b>area</b> is resolved from the scoreboard first, then the tab footer / player lines, by
 *       matching {@link GameArea} aliases; the Garden additionally resolves from its tell-tale signals
 *       (Plot / Copper / Visitors / Pests) when no clean "The Garden" line is present.</li>
 * </ol>
 */
public final class GameContextResolver {

    private static final Pattern HYPIXEL_BRAND = Pattern.compile("Hypixel BungeeCord.*");

    /** Tokens that, in any normalized signal, strongly imply Hypixel SkyBlock (never appear in BedWars). */
    private static final List<String> SKYBLOCK_SIGNALS = List.of(
            "skyblock", "sky block", "purse", "bits", "copper", "garden", "plot", "visitors", "pests");

    /** Garden tell-tale tokens (the Garden scoreboard often lacks a clean "The Garden" line). */
    private static final List<String> GARDEN_SIGNALS = List.of(
            "the garden", "garden", "plot", "copper", "visitors", "pests");

    /** Title markers for Hypixel games that are explicitly NOT SkyBlock. BedWars must never resolve SB. */
    private static final List<String> NON_SKYBLOCK_TITLES = List.of(
            "bed wars", "bedwars", "skywars", "sky wars", "murder mystery", "build battle", "duels",
            "mega walls", "blitz", "cops and crims", "wool wars", "wool games", "uhc", "smash heroes",
            "arcade", "the pit", "prototype", "tnt games", "paintball", "quakecraft", "warlords",
            "speed uhc", "crazy walls", "pixel painters", "turbo kart", "limbo");

    private GameContextResolver() {
    }

    // ---- Legacy scoreboard-only entry point (kept for back-compat with existing callers/tests) ----

    public static GameContext resolve(String serverBrand, String serverAddress,
                                      String sidebarTitle, List<String> sidebarLines) {
        return resolve(LocationSignals.ofScoreboard(serverBrand, serverAddress, sidebarTitle, sidebarLines))
                .context();
    }

    // ---- Full signal-model entry point ----

    /** Resolve a full set of {@link LocationSignals} to a {@link GameContext} with provenance. */
    public static LocationResolution resolve(LocationSignals s) {
        List<String> notes = new ArrayList<>();

        boolean onHypixel = isHypixel(s.serverBrand(), s.serverAddress());
        if (!onHypixel) {
            notes.add("brand/address not Hypixel");
            return new LocationResolution(GameContext.NONE, LocationSource.NONE, "", notes);
        }
        notes.add("on Hypixel");

        String title = s.sidebarTitleNorm();
        String minigame = matchAny(title, NON_SKYBLOCK_TITLES);

        boolean titleSkyBlock = title.contains("skyblock") || title.contains("sky block");
        SignalHit sbHit = firstSignal(s, SKYBLOCK_SIGNALS);
        boolean inSkyBlock = (titleSkyBlock || sbHit != null) && minigame == null;

        if (!inSkyBlock) {
            if (minigame != null) {
                notes.add("non-SkyBlock minigame title: '" + minigame + "'");
            } else {
                notes.add("on Hypixel but no SkyBlock signal");
            }
            GameArea lobby = classifyNonSkyBlock(title, s);
            GameContext ctx = new GameContext(true, false, lobby);
            return new LocationResolution(ctx, LocationSource.SCOREBOARD_TITLE, title, notes);
        }

        notes.add(titleSkyBlock ? "SkyBlock title" : "SkyBlock signal: '" + sbHit.token() + "'");

        AreaHit area = resolveArea(s);
        notes.addAll(area.notes());
        GameContext ctx = new GameContext(true, true, area.area());
        return new LocationResolution(ctx, area.source(), area.matched(), notes);
    }

    public static boolean isHypixel(String serverBrand, String serverAddress) {
        if (serverBrand != null && HYPIXEL_BRAND.matcher(serverBrand).matches()) {
            return true;
        }
        if (serverAddress != null) {
            String a = serverAddress.strip().toLowerCase(Locale.ROOT);
            if(a.matches("[^:]+:[0-9]{1,5}")) a=a.substring(0,a.lastIndexOf(':'));
            return a.endsWith(".hypixel.net") || a.equals("hypixel.net");
        }
        return false;
    }

    // ---- Area resolution ----

    /**
     * Resolve the SkyBlock area: scoreboard signals first (title then rows), then the tab fallback
     * (footer then player lines). If no direct {@link GameArea} alias matches but Garden tell-tale
     * signals are present, resolve {@link GameArea#GARDEN}.
     */
    private static AreaHit resolveArea(LocationSignals s) {
        // 1. Scoreboard title.
        GameArea a = matchArea(s.sidebarTitleNorm());
        if (a != null) {
            return AreaHit.of(a, LocationSource.SCOREBOARD_TITLE, s.sidebarTitleNorm());
        }
        // 2. Scoreboard rows.
        for (String row : s.sidebarRowsNorm()) {
            GameArea m = matchArea(row);
            if (m != null) {
                return AreaHit.of(m, LocationSource.SCOREBOARD_LINE, row);
            }
        }
        // 3. Tab footer.
        GameArea f = matchArea(s.tabFooterNorm());
        if (f != null) {
            return AreaHit.of(f, LocationSource.TAB_FOOTER, s.tabFooterNorm());
        }
        // 4. Tab player/header lines.
        for (String line : s.tabLinesNorm()) {
            GameArea m = matchArea(line);
            if (m != null) {
                return AreaHit.of(m, LocationSource.TAB_PLAYER, line);
            }
        }
        // 5. Garden tell-tale fallback (Plot / Copper / Visitors / Pests across scoreboard).
        for (String row : s.sidebarRowsNorm()) {
            String g = matchAny(row, GARDEN_SIGNALS);
            if (g != null) {
                return new AreaHit(GameArea.GARDEN, LocationSource.SCOREBOARD_LINE, row,
                        List.of("Garden tell-tale signal: '" + g + "'"));
            }
        }
        for (String line : s.tabLinesNorm()) {
            String g = matchAny(line, GARDEN_SIGNALS);
            if (g != null) {
                return new AreaHit(GameArea.GARDEN, LocationSource.TAB_PLAYER, line,
                        List.of("Garden tell-tale signal (tab): '" + g + "'"));
            }
        }
        return new AreaHit(GameArea.UNKNOWN, LocationSource.SCOREBOARD_LINE, "",
                List.of("no area alias or Garden signal matched"));
    }

    /** First SkyBlock {@link GameArea} whose alias is contained in the normalized text, else null. */
    private static GameArea matchArea(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        GameArea best=null;int longest=0;
        for (GameArea area : GameArea.values()) if(area.isSkyBlock()) {
            for(String alias:area.aliases()) {
                String token=alias.toLowerCase(Locale.ROOT);
                if(token.length()>longest && Pattern.compile("(?<![a-z])"+Pattern.quote(token)+"(?![a-z])").matcher(normalized).find()) {
                    best=area;longest=token.length();
                }
            }
        }
        return best;
    }

    // ---- Non-SkyBlock lobby classification ----

    private static GameArea classifyNonSkyBlock(String titleNorm, LocationSignals s) {
        if (titleNorm.contains("bed wars") || titleNorm.contains("bedwars")) {
            return GameArea.BEDWARS_LOBBY;
        }
        if (titleNorm.contains("skywars") || titleNorm.contains("sky wars")) {
            return GameArea.SKYWARS_LOBBY;
        }
        if (titleNorm.contains("duels")) {
            return GameArea.DUELS_LOBBY;
        }
        if (titleNorm.contains("prototype")) {
            return GameArea.PROTOTYPE_LOBBY;
        }
        if (titleNorm.contains("limbo")) {
            return GameArea.LIMBO;
        }
        for (String row : s.sidebarRowsNorm()) {
            for (GameArea area : GameArea.values()) {
                if (!area.isSkyBlock() && area.matchesNormalized(row)) {
                    return area;
                }
            }
        }
        return GameArea.UNKNOWN;
    }

    // ---- Helpers ----

    /** First token in {@code tokens} contained in {@code text}, else null. */
    private static String matchAny(String text, List<String> tokens) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (String t : tokens) {
            if (text.contains(t)) {
                return t;
            }
        }
        return null;
    }

    /** First signal (title → rows → footer → tab lines) containing any of {@code tokens}. */
    private static SignalHit firstSignal(LocationSignals s, List<String> tokens) {
        String t = matchAny(s.sidebarTitleNorm(), tokens);
        if (t != null) {
            return new SignalHit(LocationSource.SCOREBOARD_TITLE, s.sidebarTitleNorm(), t);
        }
        for (String row : s.sidebarRowsNorm()) {
            String m = matchAny(row, tokens);
            if (m != null) {
                return new SignalHit(LocationSource.SCOREBOARD_LINE, row, m);
            }
        }
        String f = matchAny(s.tabFooterNorm(), tokens);
        if (f != null) {
            return new SignalHit(LocationSource.TAB_FOOTER, s.tabFooterNorm(), f);
        }
        for (String line : s.tabLinesNorm()) {
            String m = matchAny(line, tokens);
            if (m != null) {
                return new SignalHit(LocationSource.TAB_PLAYER, line, m);
            }
        }
        return null;
    }

    private record SignalHit(LocationSource source, String text, String token) {
    }

    private record AreaHit(GameArea area, LocationSource source, String matched, List<String> notes) {
        static AreaHit of(GameArea area, LocationSource source, String matched) {
            return new AreaHit(area, source, matched,
                    List.of("area '" + area.displayName() + "' from " + source));
        }
    }
}
