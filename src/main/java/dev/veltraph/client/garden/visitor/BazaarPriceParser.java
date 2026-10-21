package dev.veltraph.client.garden.visitor;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Pure parser for a Bazaar "Coins" price line, the clean-room equivalent of the original
 * {@code BazaarConstants} lore-price reader ({@code reports/_spec_parts/BazaarConstants.md}: "reads its
 * lore, locates the 'Coins' price line, parses the numeric coin value", then gates it against a max cost).
 * No Minecraft, no network — it takes the (possibly still color-coded) lore lines and returns the coins.
 *
 * <p>Handles: Minecraft {@code §x} color-code stripping, grouping commas, a decimal part (floored), and
 * optional {@code k}/{@code m}/{@code b} shorthand. Returns empty / {@link Result#MISSING} when no coins
 * line is present or the number can't be parsed — callers treat "no price" as "don't claim it's
 * affordable" (the original's null-vs-Pair "skip this slot" semantics, see {@code BazaarConstants.md}).
 */
public final class BazaarPriceParser {

    private BazaarPriceParser() {
    }

    /**
     * The result of a price-with-cap evaluation, mirroring the original's {@code Pair<Long,Boolean>?}:
     * {@link #MISSING} = no parseable coins line ("skip this slot"); otherwise a known {@link #price} with
     * {@link #tooExpensive} set when the cap is enforced and exceeded.
     */
    public record Result(boolean present, long price, boolean tooExpensive) {
        public static final Result MISSING = new Result(false, -1L, false);

        public static Result of(long price, boolean tooExpensive) {
            return new Result(true, price, tooExpensive);
        }

        public boolean affordable() {
            return present && !tooExpensive;
        }
    }

    /**
     * Read a coin price from the lore and gate it against {@code maxCost}.
     *
     * @param loreLines    item lore lines (color codes tolerated)
     * @param maxCost      per-item budget cap
     * @param enforceCap   whether the cap is active ({@code Config.BAZAAR_HAVE_MAX_PRICES})
     * @return {@link Result#MISSING} when unparseable; else {@code (price, tooExpensive)} where
     *         {@code tooExpensive} is true only when {@code enforceCap} and {@code price > maxCost}.
     */
    public static Result priceWithCap(List<String> loreLines, long maxCost, boolean enforceCap) {
        OptionalLong coins = parseCoins(loreLines);
        if (coins.isEmpty()) {
            return Result.MISSING;
        }
        long price = coins.getAsLong();
        boolean tooExpensive = enforceCap && price > maxCost;
        return Result.of(price, tooExpensive);
    }

    /** Parse the first lore line that mentions "coins" into a coin amount. */
    public static OptionalLong parseCoins(List<String> loreLines) {
        if (loreLines == null) {
            return OptionalLong.empty();
        }
        for (String raw : loreLines) {
            if (raw == null) {
                continue;
            }
            String line = stripColorCodes(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("coin")) {
                OptionalLong v = extractNumber(line);
                if (v.isPresent()) {
                    return v;
                }
            }
        }
        return OptionalLong.empty();
    }

    /** Strip Minecraft {@code §x} formatting codes (defensive — lore is usually pre-stripped). */
    static String stripColorCodes(String s) {
        if (s == null || s.indexOf('§') < 0) {
            return s == null ? "" : s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++; // skip the formatting code char too
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Extract the first number (with commas / decimal / k-m-b suffix) from a line, as a floored long. */
    static OptionalLong extractNumber(String line) {
        if (line == null) {
            return OptionalLong.empty();
        }
        int i = 0;
        int n = line.length();
        // find the start of the first digit
        while (i < n && !Character.isDigit(line.charAt(i))) {
            i++;
        }
        if (i >= n) {
            return OptionalLong.empty();
        }
        StringBuilder digits = new StringBuilder();
        boolean seenDot = false;
        int j = i;
        for (; j < n; j++) {
            char c = line.charAt(j);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (c == ',') {
                // grouping separator — skip
            } else if (c == '.' && !seenDot) {
                seenDot = true;
                digits.append('.');
            } else {
                break;
            }
        }
        if (digits.length() == 0 || (digits.length() == 1 && digits.charAt(0) == '.')) {
            return OptionalLong.empty();
        }
        double value;
        try {
            value = Double.parseDouble(digits.toString());
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
        // optional shorthand suffix immediately after the number (k/m/b)
        if (j < n) {
            char suffix = Character.toLowerCase(line.charAt(j));
            switch (suffix) {
                case 'k' -> value *= 1_000d;
                case 'm' -> value *= 1_000_000d;
                case 'b' -> value *= 1_000_000_000d;
                default -> { /* no suffix */ }
            }
        }
        return OptionalLong.of((long) Math.floor(value));
    }
}
