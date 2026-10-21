package dev.veltraph.client.hydra.response;

import java.util.ArrayList;
import java.util.List;

/**
 * The local staff-reply template database — the clean-room rebuild of the original
 * {@code antistaff-messages.yml} (three lists: {@code first-message} / {@code occasional-second-message} /
 * {@code occasional-third-message}). The handler picks a random line from the first list, then
 * <i>occasionally</i> one from the second, then the third — a 1–3 message reply chain. The
 * <b>extreme</b> variant swaps a more aggressive first list (the original "Is extreme response" toggle).
 *
 * <p><b>Local-only template data.</b> These are just strings the user can fully override via config; the
 * original loaded the same kind of YAML locally. Nothing is downloaded, and no backend/C2 chat feed is
 * consulted (that path is EXCLUDE_NETWORK).
 *
 * <p>Custom lists are parsed from a {@code |}-separated config string (pipe, not comma, so a message may
 * contain commas). A blank custom value falls back to the built-in defaults so the feature still works
 * out of the box. The built-in defaults intentionally omit the original's command-shaped lines
 * ({@code /chat a}, {@code ?chat a}, …) so a default reply never runs an unexpected command; users who
 * want them can add them via config.
 */
public final class ResponseTemplates {

    /** Calm default first-reply lines (recovered, command-shaped entries excluded — see class doc). */
    public static final List<String> DEFAULT_FIRST =
            List.of("LOL", "?", "nice check", "..", "HAHA", "aight", "nice");

    /** More aggressive first-reply lines used when the extreme variant is enabled. */
    public static final List<String> EXTREME_FIRST =
            List.of("omfg", "omg", "HAHA", "LMFAOOOO", "?", "lmfao");

    /** Default occasional second-reply lines. */
    public static final List<String> DEFAULT_SECOND =
            List.of("can i continue now", "???", "pls", "please", "ty");

    /** Default occasional third-reply lines. */
    public static final List<String> DEFAULT_THIRD =
            List.of("ty", "pce");

    // --- Exact original antistaff-messages.yml lists (parity mode). One combined first list, with the
    //     command-shaped entries (/chat a, /chat all, ?chat a) preserved verbatim. Gated behind
    //     HydraConfig.PARITY_TEMPLATES_ENABLED / parity mode because those lines run in-game commands. ---

    /** Exact original {@code first-message} list (verbatim, incl. command-shaped lines). */
    public static final List<String> PARITY_FIRST = List.of(
            "LOL", "?", "nice check", "/chat a", "..", "/chat all", "HAHA", "aight", "?chat a",
            "nice", "omfg", "omg");

    /** Exact original {@code occasional-second-message} list (verbatim). */
    public static final List<String> PARITY_SECOND = List.of(
            "can i continue now", "???", "pls", "please", "ty", "LMFAOOOO", "lmfao", "idiots");

    /** Exact original {@code occasional-third-message} list (verbatim). */
    public static final List<String> PARITY_THIRD = List.of("ty", "pce");

    /** Separator for the {@code |}-joined config lists (pipe so a message may contain commas). */
    public static final String SEPARATOR = "\\|";

    private final List<String> first;
    private final List<String> second;
    private final List<String> third;

    private ResponseTemplates(List<String> first, List<String> second, List<String> third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    /**
     * Build the template set from (optional) custom config strings. Any blank value falls back to the
     * matching built-in default; the first list additionally honours {@code extreme} when no custom first
     * list is supplied. (Non-parity defaults — see {@link #build(String, String, String, boolean, boolean)}.)
     */
    public static ResponseTemplates build(String customFirst, String customSecond, String customThird,
                                          boolean extreme) {
        return build(customFirst, customSecond, customThird, extreme, false);
    }

    /**
     * Build the template set. When {@code parity} is true the built-in defaults are the <b>exact</b>
     * original {@code antistaff-messages.yml} lists ({@link #PARITY_FIRST}/{@link #PARITY_SECOND}/
     * {@link #PARITY_THIRD}, including the command-shaped lines) and {@code extreme} is ignored (the
     * original YAML has a single combined first list). When {@code parity} is false the safe Veltraph
     * defaults apply, with {@code extreme} swapping the first list. User-supplied (non-blank) custom lists
     * always override either default.
     */
    public static ResponseTemplates build(String customFirst, String customSecond, String customThird,
                                          boolean extreme, boolean parity) {
        List<String> firstDefault = parity ? PARITY_FIRST : (extreme ? EXTREME_FIRST : DEFAULT_FIRST);
        List<String> secondDefault = parity ? PARITY_SECOND : DEFAULT_SECOND;
        List<String> thirdDefault = parity ? PARITY_THIRD : DEFAULT_THIRD;
        return new ResponseTemplates(
                parseOr(customFirst, firstDefault),
                parseOr(customSecond, secondDefault),
                parseOr(customThird, thirdDefault));
    }

    private static List<String> parseOr(String raw, List<String> fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(SEPARATOR)) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    public List<String> first() {
        return first;
    }

    public List<String> second() {
        return second;
    }

    public List<String> third() {
        return third;
    }

    /**
     * Pick a line from {@code list} using {@code roll} in {@code [0,1)}; returns null if the list is empty.
     * Deterministic for a given roll so the handler's choices are unit-testable.
     */
    public static String pick(List<String> list, double roll) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        double r = roll;
        if (Double.isNaN(r) || r < 0) {
            r = 0;
        }
        if (r >= 1) {
            r = 0.9999999;
        }
        int idx = (int) (r * list.size());
        if (idx >= list.size()) {
            idx = list.size() - 1;
        }
        return list.get(idx);
    }
}
