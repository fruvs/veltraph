package dev.veltraph.client.ui;

import dev.veltraph.client.ui.widget.Widget;

/**
 * A scrollable content viewport. Owns the viewport rectangle (screen space), the content height, and
 * the scroll offset, and provides the geometry needed to (a) render only the visible rows and (b)
 * dispatch clicks <b>only</b> to widgets actually inside the viewport.
 *
 * <p>Content is laid out in "content space" (y measured from the top of the content, 0-based);
 * {@link #screenY(int)} maps a content-space top to a screen-space top using the current scroll. This
 * is the single source of truth shared by the screen's render and click paths, so scrolled-off rows
 * can never receive clicks. Pure (no Minecraft dependency).
 */
public final class ScrollViewport {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private int contentHeight;
    private int scroll;

    public ScrollViewport(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int scroll() {
        return scroll;
    }

    public int contentHeight() {
        return contentHeight;
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        clampScroll();
    }

    public int maxScroll() {
        return Math.max(0, contentHeight - height);
    }

    public boolean canScroll() {
        return maxScroll() > 0;
    }

    public void setScroll(int scroll) {
        this.scroll = clamp(scroll, 0, maxScroll());
    }

    public void scrollBy(int delta) {
        setScroll(scroll + delta);
    }

    private void clampScroll() {
        this.scroll = clamp(scroll, 0, maxScroll());
    }

    /** Screen-space top of a content-space row top, accounting for scroll. */
    public int screenY(int contentTopY) {
        return y + contentTopY - scroll;
    }

    /** True if the point is inside the viewport rectangle. */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** True if a row drawn at the given screen-space top with height {@code rowH} overlaps the viewport. */
    public boolean rowVisible(int screenTopY, int rowH) {
        return screenTopY + rowH > y && screenTopY < y + height;
    }

    /** True if a content-space row [contentTopY, +rowH) is at least partly visible. */
    public boolean rowVisibleAtContent(int contentTopY, int rowH) {
        return rowVisible(screenY(contentTopY), rowH);
    }

    /**
     * Whether a click should reach {@code widget}: the click must land inside the viewport <i>and</i>
     * on the widget. A widget scrolled out of (or clipped by) the viewport therefore never receives a
     * click even though its bounds may otherwise be hit.
     */
    public boolean clickReaches(Widget widget, double mouseX, double mouseY) {
        return contains(mouseX, mouseY) && widget.isMouseOver(mouseX, mouseY);
    }

    /** Scrollbar thumb rectangle [topY, bottomY) in screen space, or null when no scrolling is needed. */
    public int[] thumb(int scrollbarX, int scrollbarW) {
        if (!canScroll()) {
            return null;
        }
        int trackH = height;
        int thumbH = Math.max(12, (int) ((long) trackH * height / Math.max(1, contentHeight)));
        thumbH = Math.min(thumbH, trackH);
        int range = trackH - thumbH;
        int thumbTop = y + (range <= 0 ? 0 : (int) ((long) range * scroll / maxScroll()));
        return new int[]{scrollbarX, thumbTop, scrollbarW, thumbH};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
