package dev.veltraph.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure HUD panel geometry: sizing, corner placement, recovered width clamp, on-screen bounds. */
class HudPanelLayoutTest {

    @Test
    void sizesPanelFromTextBlockPlusPadding() {
        HudPanelLayout.Rect r = HudPanelLayout.compute(3, 9, 80, 5, 2, 400, 220, 4, true, true);
        // content height = 3 lines * 9 + 2 gaps * 2 = 31; panel = +2*pad.
        assertEquals(31, r.contentHeight());
        assertEquals(31 + 10, r.height());
        assertEquals(80 + 10, r.width());
        assertEquals(80, r.contentWidth());
        // content origin is inset by the padding from the panel.
        assertEquals(r.x() + 5, r.contentX());
        assertEquals(r.y() + 5, r.contentY());
    }

    @Test
    void topLeftCornerSitsAtTheMargin() {
        HudPanelLayout.Rect r = HudPanelLayout.compute(1, 9, 50, 5, 2, 400, 220, 6, true, true);
        assertEquals(6, r.x());
        assertEquals(6, r.y());
    }

    @Test
    void bottomRightCornerIsFlushToTheOppositeMargin() {
        int screenW = 400, screenH = 220, margin = 6;
        HudPanelLayout.Rect r = HudPanelLayout.compute(2, 9, 50, 5, 2, screenW, screenH, margin, false, false);
        assertEquals(screenW - r.width() - margin, r.x());
        assertEquals(screenH - r.height() - margin, r.y());
    }

    @Test
    void widthHonoursRecoveredMaxClamp() {
        // A very wide text block on a wide screen is clamped to the recovered HUD max width (300px total).
        HudPanelLayout.Rect r = HudPanelLayout.compute(1, 9, 5000, 5, 2, 1920, 1080, 4, true, true);
        assertTrue(r.width() <= HudPanelLayout.MAX_WIDTH, "panel never exceeds the recovered 300px cap");
    }

    @Test
    void neverDrawsOffScreenOnTinyWindows() {
        HudPanelLayout.Rect r = HudPanelLayout.compute(4, 9, 999, 5, 2, 120, 90, 4, false, false);
        assertTrue(r.x() >= 0 && r.y() >= 0, "origin stays on-screen");
        assertTrue(r.width() <= 120, "panel fits the narrow window");
    }

    @Test
    void emptyLineBlockStillProducesOneLineHigh() {
        HudPanelLayout.Rect r = HudPanelLayout.compute(0, 9, 0, 5, 2, 400, 220, 4, true, true);
        assertEquals(9, r.contentHeight(), "at least one line tall");
    }
}
