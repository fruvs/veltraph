package dev.veltraph.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Responsive layout must stay within the window and keep rail/content/header well-formed at any size. */
class UiLayoutTest {

    /** Representative GUI-scaled window sizes: small (high GUI scale), normal, and large. */
    private static final int[][] SIZES = {
            {320, 240}, {427, 240}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}, {3440, 1440}
    };

    @Test
    void panelAlwaysFitsWithinScreen() {
        for (int[] s : SIZES) {
            LayoutMetrics m = UiLayout.compute(s[0], s[1]);
            String at = "at " + s[0] + "x" + s[1];
            assertTrue(m.panelX() >= 0 && m.panelY() >= 0, "panel origin non-negative " + at);
            assertTrue(m.panelRight() <= s[0], "panel right within screen " + at);
            assertTrue(m.panelBottom() <= s[1], "panel bottom within screen " + at);
            assertTrue(m.panelW() > 0 && m.panelH() > 0, "panel has positive size " + at);
        }
    }

    @Test
    void railContentAndHeaderAreWellFormed() {
        for (int[] s : SIZES) {
            LayoutMetrics m = UiLayout.compute(s[0], s[1]);
            String at = "at " + s[0] + "x" + s[1];
            assertTrue(m.headerH() >= 24 && m.headerH() <= 36, "header height clamped " + at);
            assertTrue(m.railRight() <= m.panelRight(), "rail within panel " + at);
            assertTrue(m.contentX() >= m.railRight(), "content starts right of rail " + at);
            assertTrue(m.contentX() + m.contentW() <= m.panelRight() + 1, "content within panel " + at);
            assertTrue(m.contentInnerW() > 0, "content inner width positive " + at);
            assertTrue(m.contentH() > 0, "content height positive " + at);
            assertTrue(m.scrollbarX() + m.scrollbarW() <= m.panelRight() + 1, "scrollbar within panel " + at);
        }
    }

    @Test
    void panelSizeIsClampedAtLargeResolutions() {
        LayoutMetrics big = UiLayout.compute(3440, 1440);
        assertTrue(big.panelW() <= UiLayout.MAX_PANEL_W, "panel width capped");
        assertTrue(big.panelH() <= UiLayout.MAX_PANEL_H, "panel height capped");
    }

    @Test
    void panelScalesUpFromSmallToLarge() {
        LayoutMetrics small = UiLayout.compute(427, 240);
        LayoutMetrics large = UiLayout.compute(1920, 1080);
        assertTrue(large.panelW() >= small.panelW(), "larger window yields a panel at least as wide");
        assertTrue(large.panelH() >= small.panelH(), "larger window yields a panel at least as tall");
    }
}
