package dev.veltraph.client.ui.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The renderer's smooth text-scaling helpers. The real backend overrides these to scale the Minecraft
 * {@code TextRenderer} via the GUI matrix; here we exercise the pure {@link UiRenderer} defaults (which
 * any test fake inherits) so the measurement/fallback contract is locked in without Minecraft.
 */
class UiRendererScalingTest {

    /** A fake renderer with a known monospaced metric so scaling math is checkable. */
    private static final class FakeRenderer implements UiRenderer {
        static final int CHAR_W = 6;
        static final int LINE_H = 9;
        final List<String> scaledTextCalls = new ArrayList<>();
        int lastScale;

        @Override public void fill(int x, int y, int w, int h, int argb) { }
        @Override public void gradientV(int x, int y, int w, int h, int t, int b) { }
        @Override public void border(int x, int y, int w, int h, int argb) { }
        @Override public void roundedRect(int x, int y, int w, int h, int r, int argb) { }
        @Override public void text(String t, int x, int y, int argb) { }
        @Override public void text(String t, int x, int y, int argb, boolean s) { }
        @Override public void text(String t, int x, int y, int argb, boolean s, int scale) {
            scaledTextCalls.add(t);
            lastScale = scale;
        }
        @Override public int textWidth(String t) { return t == null ? 0 : t.length() * CHAR_W; }
        @Override public int lineHeight() { return LINE_H; }
        @Override public void pushScissor(int x, int y, int w, int h) { }
        @Override public void popScissor() { }
        @Override public void texture(String p, int x, int y, int w, int h, int tw, int th) { }
    }

    @Test
    void scaledWidthAndHeightScaleTheBaseMetric() {
        FakeRenderer r = new FakeRenderer();
        assertEquals(Math.round("Veltraph".length() * FakeRenderer.CHAR_W * 1.5f), r.textWidthScaled("Veltraph", 1.5f));
        assertEquals(Math.round(FakeRenderer.LINE_H * 1.5f), r.lineHeightScaled(1.5f));
        assertEquals(FakeRenderer.LINE_H, r.lineHeightScaled(1.0f), "scale 1 == base height");
    }

    @Test
    void textScaledFallsBackToTheIntegerScaledTextPath() {
        FakeRenderer r = new FakeRenderer();
        r.textScaled("Hi", 0, 0, 0xFFFFFFFF, false, 1.6f);
        assertEquals(1, r.scaledTextCalls.size(), "default routes through the integer text(..,scale) path");
        assertEquals("Hi", r.scaledTextCalls.get(0));
        assertEquals(2, r.lastScale, "1.6 rounds to nearest integer scale (2)");
    }

    @Test
    void scaledHelpersNeverReturnNegativeForTinyScale() {
        FakeRenderer r = new FakeRenderer();
        assertTrue(r.textWidthScaled("x", 0.0f) >= 0);
        assertTrue(r.lineHeightScaled(0.0f) >= 0);
    }
}
