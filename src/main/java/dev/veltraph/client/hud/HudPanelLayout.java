package dev.veltraph.client.hud;











public final class HudPanelLayout {

    /** Recovered HUD width cap (px) and the recovered window-edge reserve (px). */
    public static final int MAX_WIDTH = 300;
    public static final int EDGE_RESERVE = 50;

    public record Rect(int x, int y, int width, int height, int contentX, int contentY,
                       int contentWidth, int contentHeight) {
    }

    private HudPanelLayout() {
    }

    public static Rect compute(int lineCount, int lineHeight, int maxTextWidth,
                               int pad, int gap, int screenW, int screenH, int margin,
                               boolean cornerLeft, boolean cornerTop) {
        int lines = Math.max(1, lineCount);
        int lh = Math.max(1, lineHeight);
        int p = Math.max(0, pad);
        int g = Math.max(0, gap);
        int m = Math.max(0, margin);

        // Recovered width cap, then never wider than the screen minus both margins.
        int maxPanelW = Math.min(screenW - EDGE_RESERVE, MAX_WIDTH);
        maxPanelW = Math.max(40, Math.min(maxPanelW, Math.max(1, screenW - 2 * m)));

        int contentW = Math.max(0, Math.min(Math.max(0, maxTextWidth), maxPanelW - 2 * p));
        int panelW = contentW + 2 * p;
        int contentH = lines * lh + (lines - 1) * g;
        int panelH = contentH + 2 * p;

        int x = cornerLeft ? m : Math.max(0, screenW - panelW - m);
        int y = cornerTop ? m : Math.max(0, screenH - panelH - m);

        return new Rect(x, y, panelW, panelH, x + p, y + p, contentW, contentH);
    }
}
