package dev.veltraph.client.ui.font;

import dev.veltraph.client.ui.render.UiRenderer;

/**
 * Veltraph's embedded fallback bitmap font. The live GUI/HUD uses the renderer's Minecraft
 * {@code TextRenderer} path; this font is kept for headless tests and as a no-TextRenderer fallback.
 * It rasterizes an embedded {@link VeltraphFontData} glyph table (4-bit per-pixel coverage) directly
 * through {@link UiRenderer#fill}, modulating text alpha by each pixel's coverage.
 *
 * <p>The measurement methods are pure and {@link #draw} only calls {@code fill}, so the fallback is
 * unit-testable on a plain JVM with a recording renderer. Fixed advance keeps a clean monospaced look.
 */
public final class VeltraphFont {

    public static final int GLYPH_W = VeltraphFontData.GLYPH_W;
    public static final int GLYPH_H = VeltraphFontData.GLYPH_H;
    /** Per-character horizontal advance (glyph + 1px gap). */
    public static final int ADVANCE = GLYPH_W + 1;
    private static final int GLYPH_PIXELS = GLYPH_W * GLYPH_H;

    private static final byte[] COVERAGE = VeltraphFontData.decode();
    private static final int FALLBACK = '?' - VeltraphFontData.FIRST_CHAR;

    private VeltraphFont() {
    }

    /** Pixel width of {@code text} (no trailing gap) at 1x. */
    public static int width(String text) {
        return width(text, 1);
    }

    /** Pixel width of {@code text} at the given integer scale. */
    public static int width(String text, int scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() * ADVANCE - 1) * Math.max(1, scale);
    }

    /** Glyph cell height (line height) at 1x. */
    public static int height() {
        return GLYPH_H;
    }

    /** Glyph cell height at the given integer scale. */
    public static int height(int scale) {
        return GLYPH_H * Math.max(1, scale);
    }

    /** Draw {@code text} at (x, y) in {@code argb} at 1x using coverage-modulated horizontal-run fills. */
    public static void draw(UiRenderer r, String text, int x, int y, int argb) {
        draw(r, text, x, y, argb, 1);
    }

    /**
     * Draw {@code text} at an integer {@code scale}. The AA coverage is preserved; each glyph pixel
     * becomes a {@code scale x scale} block, so larger text (e.g. the header title) stays crisp at any
     * GUI scale instead of looking like tiny pixelated bitmap text.
     */
    public static void draw(UiRenderer r, String text, int x, int y, int argb, int scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int s = Math.max(1, scale);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            drawGlyph(r, text.charAt(i), cx, y, argb, s);
            cx += ADVANCE * s;
        }
    }

    private static void drawGlyph(UiRenderer r, char ch, int x, int y, int argb, int s) {
        int idx = (ch >= VeltraphFontData.FIRST_CHAR
                && ch < VeltraphFontData.FIRST_CHAR + VeltraphFontData.GLYPH_COUNT)
                ? ch - VeltraphFontData.FIRST_CHAR : FALLBACK;
        int base = idx * GLYPH_PIXELS;
        int srcAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        for (int row = 0; row < GLYPH_H; row++) {
            int rowBase = base + row * GLYPH_W;
            int col = 0;
            while (col < GLYPH_W) {
                int cov = COVERAGE[rowBase + col] & 0xF;
                if (cov == 0) {
                    col++;
                    continue;
                }
                // Coalesce a run of identical coverage into one (scaled) fill.
                int run = 1;
                while (col + run < GLYPH_W && (COVERAGE[rowBase + col + run] & 0xF) == cov) {
                    run++;
                }
                int outAlpha = Math.round(srcAlpha * cov / 15f);
                if (outAlpha > 0) {
                    r.fill(x + col * s, y + row * s, run * s, s, (outAlpha << 24) | rgb);
                }
                col += run;
            }
        }
    }
}
