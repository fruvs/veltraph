package dev.veltraph.client.ui.render;

/**
 * Clean renderer abstraction over the underlying graphics layer. Screens and widgets draw through
 * this interface instead of touching Minecraft's {@code DrawContext} directly, so the UI code stays
 * decoupled from the rendering backend.
 *
 * <p>The default backend is {@code DrawContextRenderer} (pure Minecraft/Fabric rendering APIs).
 * Veltraph deliberately does <b>not</b> use the original {@code flux-renderer.dylib} or any native
 * renderer; this abstraction is the clean replacement.
 *
 * <p>This interface is intentionally pure (primitives + {@code String} only) so widgets built on it
 * are unit-testable on a plain JVM with a fake renderer.
 *
 * <p>Colors are ARGB ints ({@code 0xAARRGGBB}).
 */
public interface UiRenderer {

    /** Fill a rectangle (x, y, width, height) with an ARGB color. */
    void fill(int x, int y, int width, int height, int argb);

    /** Fill a vertical gradient rectangle from {@code topArgb} to {@code bottomArgb}. */
    void gradientV(int x, int y, int width, int height, int topArgb, int bottomArgb);

    /** Draw a 1px border around the rectangle. */
    void border(int x, int y, int width, int height, int argb);

    /**
     * Approximate a rounded-rectangle fill (corners trimmed by {@code radius}). The default backend
     * draws a cross of fills; this is enough for a clean panel look without textures.
     */
    void roundedRect(int x, int y, int width, int height, int radius, int argb);

    /** Draw text with a drop shadow at (x, y). */
    void text(String text, int x, int y, int argb);

    /** Draw text, optionally with a drop shadow. */
    void text(String text, int x, int y, int argb, boolean shadow);

    /** Width of {@code text} in pixels in the current font. */
    int textWidth(String text);

    /** Font line height in pixels. */
    int lineHeight();

    /**
     * Draw {@code text} at an integer {@code scale} (1 = body, 2 = header) through the Veltraph font.
     * Default delegates to {@link dev.veltraph.client.ui.font.VeltraphFont}, which renders only via
     * {@link #fill}, so every backend (and test fake) supports it without extra work.
     */
    default void text(String text, int x, int y, int argb, boolean shadow, int scale) {
        if (scale <= 1) {
            text(text, x, y, argb, shadow);
            return;
        }
        if (shadow) {
            dev.veltraph.client.ui.font.VeltraphFont.draw(this, text, x + scale, y + scale, 0xBF000000, scale);
        }
        dev.veltraph.client.ui.font.VeltraphFont.draw(this, text, x, y, argb, scale);
    }

    /** Width of {@code text} at the given integer scale. */
    default int textWidth(String text, int scale) {
        return dev.veltraph.client.ui.font.VeltraphFont.width(text, scale);
    }

    /** Font line height at the given integer scale. */
    default int lineHeight(int scale) {
        return dev.veltraph.client.ui.font.VeltraphFont.height(scale);
    }

    /**
     * Draw {@code text} scaled by a (possibly fractional) factor through the <b>smooth</b> primary font.
     * The default rounds to the nearest integer scale and routes through {@link #text(String, int, int,
     * int, boolean, int)}; the real backend ({@code DrawContextRenderer}) overrides this to scale the
     * Minecraft {@code TextRenderer} via the GUI matrix, so headers stay anti-aliased at any size.
     */
    default void textScaled(String text, int x, int y, int argb, boolean shadow, float scale) {
        text(text, x, y, argb, shadow, Math.max(1, Math.round(scale)));
    }

    /** Measured pixel width of {@code text} at a fractional scale (smooth font in the real backend). */
    default int textWidthScaled(String text, float scale) {
        return Math.round(textWidth(text) * Math.max(0.01f, scale));
    }

    /** Line height in pixels at a fractional scale (smooth font in the real backend). */
    default int lineHeightScaled(float scale) {
        return Math.round(lineHeight() * Math.max(0.01f, scale));
    }

    /**
     * Draw a soft drop shadow behind a rounded surface. Default approximates it with the standard
     * rounded-rect fill (a single layer); the real backend can layer translucent passes for a softer
     * edge. ({@code argb} should already be a low-alpha shadow colour.)
     */
    default void softShadow(int x, int y, int width, int height, int radius, int argb) {
        roundedRect(x, y, width, height, radius, argb);
    }

    /** Clip subsequent drawing to this rectangle until {@link #popScissor()}. */
    void pushScissor(int x, int y, int width, int height);

    /** Pop the most recent scissor region. */
    void popScissor();

    /**
     * Draw a bundled Veltraph UI texture (see {@code UiTextures}) at (x, y). The full {@code texW×texH}
     * image is sampled into the {@code width×height} target. Implementations must degrade gracefully if
     * the texture backend is unavailable (the caller draws a procedural fallback first).
     *
     * @param texturePath texture path relative to the {@code veltraph} namespace (an Identifier path)
     */
    void texture(String texturePath, int x, int y, int width, int height, int texW, int texH);

    /**
     * Draw a bundled texture as a <b>nine-slice</b>: the {@code inset}px corners are kept 1:1 and the
     * edges/centre are stretched to fill {@code width x height}. Lets one small rounded texture skin a
     * panel/card/button of any size without distorting its border. Default: no-op (the caller draws a
     * procedural fallback first). {@code texW/texH} are the source image dimensions.
     */
    default void nineSlice(String texturePath, int x, int y, int width, int height,
                           int texW, int texH, int inset) {
    }

    /**
     * Nine-slice with independent source/destination corner insets — {@code srcInset} texels of the
     * source corner are drawn into {@code dstInset} on-screen pixels, so a high-resolution baked corner
     * is downscaled (anti-aliased) into a crisp small radius. Default: no-op (the caller draws a
     * procedural fallback first).
     */
    default void nineSlice(String texturePath, int x, int y, int width, int height,
                           int texW, int texH, int srcInset, int dstInset) {
    }

    /**
     * Draw a sub-region {@code (u,v,regionW,regionH)} of a texture atlas, scaled to {@code width x
     * height} at {@code (x,y)} — used for the category-icon atlas. Default: no-op.
     */
    default void textureRegion(String texturePath, int x, int y, int width, int height,
                               int u, int v, int regionW, int regionH, int texW, int texH) {
    }
}
