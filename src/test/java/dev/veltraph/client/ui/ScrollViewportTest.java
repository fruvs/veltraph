package dev.veltraph.client.ui;

import dev.veltraph.client.ui.widget.TextButton;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scroll viewport must clip rendering and gate clicks so off-screen rows can't be interacted with. */
class ScrollViewportTest {

    private static TextButton buttonAt(int x, int y, AtomicInteger clicks) {
        return new TextButton(x, y, 100, 14, () -> "B", () -> 0, clicks::incrementAndGet);
    }

    @Test
    void scrollIsClampedToContent() {
        ScrollViewport vp = new ScrollViewport(0, 50, 100, 100);
        vp.setContentHeight(400);
        assertEquals(300, vp.maxScroll());
        vp.setScroll(1000);
        assertEquals(300, vp.scroll(), "scroll clamps to maxScroll");
        vp.setScroll(-50);
        assertEquals(0, vp.scroll(), "scroll clamps to 0");
        vp.scrollBy(120);
        assertEquals(120, vp.scroll());
    }

    @Test
    void shortContentCannotScroll() {
        ScrollViewport vp = new ScrollViewport(0, 0, 100, 200);
        vp.setContentHeight(150);
        assertFalse(vp.canScroll());
        assertEquals(0, vp.maxScroll());
    }

    @Test
    void scrolledOffButtonIsNotClickableAndVisibleButtonIs() {
        // Viewport occupies y in [50, 150).
        ScrollViewport vp = new ScrollViewport(0, 50, 100, 100);
        vp.setContentHeight(400);
        vp.setScroll(200);

        // Content row at content-top 10 maps above the viewport (screenY = 50 + 10 - 200 = -140).
        AtomicInteger offClicks = new AtomicInteger();
        TextButton offscreen = buttonAt(0, vp.screenY(10), offClicks);
        // A click inside the viewport must NOT reach a scrolled-off widget.
        assertFalse(vp.clickReaches(offscreen, 10, 60), "scrolled-off row must not be clickable");
        assertFalse(vp.rowVisible(offscreen.y(), offscreen.height()), "scrolled-off row is culled from render");

        // Content row at content-top 210 maps into the viewport (screenY = 60).
        AtomicInteger onClicks = new AtomicInteger();
        TextButton visible = buttonAt(0, vp.screenY(210), onClicks);
        assertTrue(vp.rowVisible(visible.y(), visible.height()), "visible row renders");
        assertTrue(vp.clickReaches(visible, 10, 62), "visible row is clickable");
    }

    @Test
    void clickInClippedRegionOfStraddlingRowIsRejected() {
        // Viewport y in [50, 150). A row whose top is above the viewport is partly clipped.
        ScrollViewport vp = new ScrollViewport(0, 50, 100, 100);
        vp.setContentHeight(400);
        vp.setScroll(140); // content-top 190 -> screenY = 50 + 190 - 140 = 100... pick a straddler instead

        // Place a row straddling the top edge: screenY = 44, height 14 -> spans [44, 58); [50,58) visible.
        AtomicInteger clicks = new AtomicInteger();
        TextButton straddler = new TextButton(0, 44, 100, 14, () -> "B", () -> 0, clicks::incrementAndGet);

        // Click on the clipped (above-viewport) part: y=46 is on the widget but outside the viewport.
        assertFalse(vp.clickReaches(straddler, 10, 46), "click in clipped region must be rejected");
        // Click on the visible part: y=54 is on the widget and inside the viewport.
        assertTrue(vp.clickReaches(straddler, 10, 54), "click in visible region is accepted");
    }

    @Test
    void thumbIsNullWhenNotScrollableAndSizedWhenScrollable() {
        ScrollViewport vp = new ScrollViewport(0, 0, 100, 100);
        vp.setContentHeight(80);
        assertEquals(null, vp.thumb(96, 4));

        vp.setContentHeight(400);
        int[] thumb = vp.thumb(96, 4);
        assertTrue(thumb != null && thumb[3] >= 12 && thumb[3] <= 100, "thumb sized within track");
    }
}
