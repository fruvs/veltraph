package dev.veltraph.client.game;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure text-normalization helpers for location signals, modelled on the original mod's text pipeline
 * (recovered from {@code ServerLocationUtil}) but written from scratch:
 *
 * <ul>
 *   <li>{@link #stripColorCodes(String)} removes Minecraft {@code §x} formatting
 *       (original pattern {@code (?i)§[0-9A-FK-OR]}).</li>
 *   <li>{@link #sanitize(String)} drops non-text UI noise, keeping only the characters the original
 *       kept ({@code [a-zA-Z 0-9:/'.,]}); this strips the {@code ⏣} location glyph, icons, etc.</li>
 *   <li>{@link #normalize(String)} runs the full pipeline (strip → sanitize → collapse whitespace →
 *       lowercase) used for matching. Raw lines are preserved separately for debug.</li>
 *   <li>{@link #titleCase(String)} mirrors the original display formatting (each word capitalized).</li>
 * </ul>
 *
 * <p>Pure (no Minecraft dependency) so it is fully unit-testable.
 */
public final class LocationText {

    /** Minecraft legacy formatting codes: {@code §} followed by a hex digit or k-o / r. */
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)§[0-9A-FK-OR]");

    /** Everything except plain text the original kept (letters, digits, space, and {@code :/'.,}). */
    private static final Pattern NON_TEXT = Pattern.compile("[^a-zA-Z 0-9:/'.,]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private LocationText() {
    }

    /** Remove {@code §x} color/format codes. Null-safe (returns "" for null). */
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return COLOR_CODES.matcher(text).replaceAll("");
    }

    /** Strip color codes, then remove non-text UI noise (glyphs/icons), preserving readable characters. */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return NON_TEXT.matcher(stripColorCodes(text)).replaceAll("");
    }

    /**
     * Full match pipeline: strip color codes, drop non-text noise, collapse runs of whitespace, trim,
     * and lowercase. Returns "" for null/blank input. Use this for case-insensitive signal matching.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = sanitize(text);
        String collapsed = WHITESPACE.matcher(sanitized).replaceAll(" ").trim();
        return collapsed.toLowerCase(Locale.ROOT);
    }

    /** Title-case each whitespace-separated word (e.g. {@code "the garden" -> "The Garden"}). */
    public static String titleCase(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
