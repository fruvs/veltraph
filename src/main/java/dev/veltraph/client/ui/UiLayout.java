package dev.veltraph.client.ui;

/**
 * Pure responsive layout for Veltraph screens. Replaces the old fixed {@code 420x240} panel: the panel
 * is sized as a fraction of the (GUI-scaled) window with sensible min/max clamps, then centered, then
 * clamped to fit within a small margin. The header, rail, content viewport, and scrollbar gutter are
 * derived from the panel. Fully unit-testable (no Minecraft dependency).
 */
public final class UiLayout {

    // Panel sizing: fraction of the window, clamped to a readable range.
    static final double PANEL_W_FRACTION = 0.78;
    static final double PANEL_H_FRACTION = 0.84;
    public static final int MIN_PANEL_W = 320;
    public static final int MAX_PANEL_W = 620;
    public static final int MIN_PANEL_H = 190;
    public static final int MAX_PANEL_H = 430;

    static final int MARGIN = 8;
    static final int PAD = 8;
    static final int SCROLLBAR_W = 6;

    private UiLayout() {
    }

    public static LayoutMetrics compute(int screenW, int screenH) {
        int sw = Math.max(1, screenW);
        int sh = Math.max(1, screenH);

        int panelW = clamp((int) Math.round(sw * PANEL_W_FRACTION), MIN_PANEL_W, MAX_PANEL_W);
        int panelH = clamp((int) Math.round(sh * PANEL_H_FRACTION), MIN_PANEL_H, MAX_PANEL_H);
        // Never exceed the window (small GUI windows / very high GUI scale).
        panelW = Math.min(panelW, Math.max(1, sw - 2 * MARGIN));
        panelH = Math.min(panelH, Math.max(1, sh - 2 * MARGIN));

        int panelX = Math.max(0, (sw - panelW) / 2);
        int panelY = Math.max(0, (sh - panelH) / 2);

        int headerH = clamp((int) Math.round(panelH * 0.12), 24, 36);
        // Rail proportional to panel width, clamped, but never more than ~40% of the panel.
        int railW = clamp((int) Math.round(panelW * 0.24), 88, 150);
        railW = Math.min(railW, Math.max(40, panelW * 2 / 5));
        int railX = panelX;
        int railY = panelY + headerH;
        int railH = panelH - headerH;

        int contentX = railX + railW + PAD;
        int contentY = panelY + headerH + PAD;
        int contentW = Math.max(1, panelW - railW - 2 * PAD);
        int contentH = Math.max(1, panelH - headerH - 2 * PAD);

        return new LayoutMetrics(sw, sh,
                panelX, panelY, panelW, panelH,
                headerH,
                railX, railY, railW, railH,
                contentX, contentY, contentW, contentH,
                SCROLLBAR_W);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
