package dev.veltraph.client.ui;









public record LayoutMetrics(
        int screenW, int screenH,
        int panelX, int panelY, int panelW, int panelH,
        int headerH,
        int railX, int railY, int railW, int railH,
        int contentX, int contentY, int contentW, int contentH,
        int scrollbarW) {

    public int panelRight() {
        return panelX + panelW;
    }

    public int panelBottom() {
        return panelY + panelH;
    }

    public int headerBottom() {
        return panelY + headerH;
    }

    public int railRight() {
        return railX + railW;
    }

    /** Inner content width available to widgets (content area minus the scrollbar gutter). */
    public int contentInnerW() {
        return Math.max(0, contentW - scrollbarW - 2);
    }

    /** X of the scrollbar gutter (right edge of the content area). */
    public int scrollbarX() {
        return contentX + contentW - scrollbarW;
    }
}
