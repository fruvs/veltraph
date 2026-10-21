package dev.veltraph.client.ui.font;

import dev.veltraph.client.ui.render.UiRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The custom bitmap font is the primary menu text path (no Minecraft TextRenderer). */
class VeltraphFontTest {

    /** Records {@code fill} calls so we can verify glyphs rasterize within their cell. */
    private static final class RecordingRenderer implements UiRenderer {
        final List<int[]> fills = new ArrayList<>();

        @Override public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[]{x, y, w, h}); }
        @Override public void gradientV(int x, int y, int w, int h, int t, int b) { }
        @Override public void border(int x, int y, int w, int h, int argb) { }
        @Override public void roundedRect(int x, int y, int w, int h, int r, int argb) { }
        @Override public void text(String t, int x, int y, int argb) { }
        @Override public void text(String t, int x, int y, int argb, boolean s) { }
        @Override public int textWidth(String t) { return 0; }
        @Override public int lineHeight() { return 0; }
        @Override public void pushScissor(int x, int y, int w, int h) { }
        @Override public void popScissor() { }
        @Override public void texture(String p, int x, int y, int w, int h, int tw, int th) { }
    }

    @Test
    void widthAndHeightAreMonospaced() {
        assertEquals(0, VeltraphFont.width(""));
        assertEquals(VeltraphFont.ADVANCE - 1, VeltraphFont.width("A"));
        assertEquals(2 * VeltraphFont.ADVANCE - 1, VeltraphFont.width("AB"));
        assertEquals(VeltraphFont.GLYPH_H, VeltraphFont.height());
        // The improved font is taller than the old blocky 6x9 cell.
        assertTrue(VeltraphFont.GLYPH_H >= 10, "AA font uses a taller cell");
    }

    @Test
    void scaledTextIsLargerAndStaysInTheScaledCell() {
        assertEquals(2 * VeltraphFont.width("Veltraph"), VeltraphFont.width("Veltraph", 2));
        assertEquals(2 * VeltraphFont.GLYPH_H, VeltraphFont.height(2));

        RecordingRenderer r = new RecordingRenderer();
        VeltraphFont.draw(r, "A", 0, 0, 0xFFFFFFFF, 2);
        assertTrue(!r.fills.isEmpty());
        for (int[] f : r.fills) {
            assertTrue(f[0] >= 0 && f[0] + f[2] <= VeltraphFont.GLYPH_W * 2, "x within 2x cell");
            assertTrue(f[1] >= 0 && f[1] + f[3] <= VeltraphFont.GLYPH_H * 2, "y within 2x cell");
            assertEquals(0, f[2] % 2, "scaled fills are 2px-aligned in width");
            assertEquals(2, f[3], "scaled glyph rows are 2px tall");
        }
    }

    @Test
    void coverageModulatesTextAlpha() {
        // Anti-aliased edge pixels must emit partially transparent fills (not just full-alpha).
        AlphaRecordingRenderer r = new AlphaRecordingRenderer();
        VeltraphFont.draw(r, "a", 0, 0, 0xFFFFFFFF);
        boolean sawPartial = false;
        for (int argb : r.colors) {
            int a = (argb >>> 24) & 0xFF;
            assertTrue(a > 0 && a <= 0xFF, "coverage alpha in range");
            if (a < 0xFF) {
                sawPartial = true;
            }
        }
        assertTrue(sawPartial, "an AA glyph emits at least one partial-coverage fill");
    }

    private static final class AlphaRecordingRenderer implements UiRenderer {
        final List<Integer> colors = new ArrayList<>();

        @Override public void fill(int x, int y, int w, int h, int argb) { colors.add(argb); }
        @Override public void gradientV(int x, int y, int w, int h, int t, int b) { }
        @Override public void border(int x, int y, int w, int h, int argb) { }
        @Override public void roundedRect(int x, int y, int w, int h, int r, int argb) { }
        @Override public void text(String t, int x, int y, int argb) { }
        @Override public void text(String t, int x, int y, int argb, boolean s) { }
        @Override public int textWidth(String t) { return 0; }
        @Override public int lineHeight() { return 0; }
        @Override public void pushScissor(int x, int y, int w, int h) { }
        @Override public void popScissor() { }
        @Override public void texture(String p, int x, int y, int w, int h, int tw, int th) { }
    }

    @Test
    void spaceDrawsNothingButLettersDraw() {
        RecordingRenderer space = new RecordingRenderer();
        VeltraphFont.draw(space, " ", 0, 0, 0xFFFFFFFF);
        assertTrue(space.fills.isEmpty(), "space glyph is blank");

        for (char c : new char[]{'A', 'V', 'g', '0', ':', '?'}) {
            RecordingRenderer r = new RecordingRenderer();
            VeltraphFont.draw(r, String.valueOf(c), 0, 0, 0xFFFFFFFF);
            assertTrue(!r.fills.isEmpty(), "glyph '" + c + "' should produce pixels");
        }
    }

    @Test
    void everyPrintableAsciiGlyphRastersWithinItsCell() {
        for (int c = 33; c < 127; c++) {
            RecordingRenderer r = new RecordingRenderer();
            VeltraphFont.draw(r, String.valueOf((char) c), 0, 0, 0xFFFFFFFF);
            assertTrue(!r.fills.isEmpty(), "printable glyph " + c + " has coverage");
            for (int[] f : r.fills) {
                assertTrue(f[0] >= 0 && f[0] + f[2] <= VeltraphFont.GLYPH_W, "x within cell for " + c);
                assertTrue(f[1] >= 0 && f[1] + f[3] <= VeltraphFont.GLYPH_H, "y within cell for " + c);
            }
        }
    }

    @Test
    void drawingIsOffsetByPosition() {
        RecordingRenderer r = new RecordingRenderer();
        VeltraphFont.draw(r, "A", 100, 50, 0xFFFFFFFF);
        for (int[] f : r.fills) {
            assertTrue(f[0] >= 100 && f[0] < 100 + VeltraphFont.GLYPH_W);
            assertTrue(f[1] >= 50 && f[1] < 50 + VeltraphFont.GLYPH_H);
        }
    }
}
