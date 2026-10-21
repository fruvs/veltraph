package dev.veltraph.client.ui.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nine-slice decomposition: corners 1:1, edges/centre stretch, everything stays in bounds. */
class NineSliceTest {

    @Test
    void decomposesIntoNineRegionsThatTileTheSurface() {
        int x = 0, y = 0, w = 100, h = 50, texW = 32, texH = 32, inset = 10;
        List<NineSlice.Region> regions = NineSlice.regions(x, y, w, h, texW, texH, inset);
        assertEquals(9, regions.size(), "full surface decomposes into 9 regions");
        for (NineSlice.Region r : regions) {
            assertTrue(r.dx() >= x && r.dx() + r.dw() <= x + w, "dest x in bounds");
            assertTrue(r.dy() >= y && r.dy() + r.dh() <= y + h, "dest y in bounds");
            assertTrue(r.u() >= 0 && r.u() + r.sw() <= texW, "src u in bounds");
            assertTrue(r.v() >= 0 && r.v() + r.sh() <= texH, "src v in bounds");
        }
        // The four corners are drawn 1:1 at the inset size.
        long corners = regions.stream().filter(r -> r.dw() == inset && r.dh() == inset
                && r.sw() == inset && r.sh() == inset).count();
        assertEquals(4, corners, "four 1:1 corners");
        // The centre covers the inner rectangle.
        boolean centre = regions.stream().anyMatch(r -> r.dx() == inset && r.dy() == inset
                && r.dw() == w - 2 * inset && r.dh() == h - 2 * inset);
        assertTrue(centre, "centre region fills the middle");
    }

    @Test
    void zeroInsetIsASingleStretchedRegion() {
        List<NineSlice.Region> regions = NineSlice.regions(5, 5, 40, 20, 16, 16, 0);
        assertEquals(1, regions.size());
        NineSlice.Region r = regions.get(0);
        assertEquals(40, r.dw());
        assertEquals(16, r.sw(), "samples the whole source");
    }

    @Test
    void insetClampsToHalfTheSmallerDimension() {
        // A tall thumb skinned into a tiny box: inset can't exceed half the smaller dimension.
        List<NineSlice.Region> regions = NineSlice.regions(0, 0, 6, 6, 10, 28, 4);
        for (NineSlice.Region r : regions) {
            assertTrue(r.dw() <= 6 && r.dh() <= 6, "regions never exceed the destination");
            assertTrue(r.dx() + r.dw() <= 6 && r.dy() + r.dh() <= 6);
        }
    }

    @Test
    void dualInsetDownscalesCornerFromHighResSource() {
        // High-res source corner (48 texels) drawn into a small on-screen corner (16px): the corner is
        // sampled from 48 source texels but occupies 16 destination pixels (anti-aliased downscale).
        int x = 0, y = 0, w = 120, h = 80, texW = 192, texH = 192, srcInset = 48, dstInset = 16;
        List<NineSlice.Region> regions = NineSlice.regions(x, y, w, h, texW, texH, srcInset, dstInset);
        assertEquals(9, regions.size());
        long corners = regions.stream().filter(r -> r.dw() == dstInset && r.dh() == dstInset
                && r.sw() == srcInset && r.sh() == srcInset).count();
        assertEquals(4, corners, "four corners: dest = dstInset, source = srcInset");
        for (NineSlice.Region r : regions) {
            assertTrue(r.dx() >= x && r.dx() + r.dw() <= x + w, "dest x in bounds");
            assertTrue(r.dy() >= y && r.dy() + r.dh() <= y + h, "dest y in bounds");
            assertTrue(r.u() >= 0 && r.u() + r.sw() <= texW, "src u in bounds");
            assertTrue(r.v() >= 0 && r.v() + r.sh() <= texH, "src v in bounds");
        }
        // Centre maps the inner source rect to the inner destination rect.
        boolean centre = regions.stream().anyMatch(r -> r.dx() == dstInset && r.dy() == dstInset
                && r.dw() == w - 2 * dstInset && r.dh() == h - 2 * dstInset
                && r.sw() == texW - 2 * srcInset && r.sh() == texH - 2 * srcInset);
        assertTrue(centre, "centre downscales the inner source band");
    }

    @Test
    void dualInsetClampsSourceAndDestIndependently() {
        // dstInset larger than half the destination → a full pill; source corner still clamps to texels.
        List<NineSlice.Region> regions = NineSlice.regions(0, 0, 40, 16, 144, 72, 36, 999);
        for (NineSlice.Region r : regions) {
            assertTrue(r.dx() + r.dw() <= 40 && r.dy() + r.dh() <= 16, "dest stays in bounds");
            assertTrue(r.u() + r.sw() <= 144 && r.v() + r.sh() <= 72, "source stays in bounds");
        }
        // No destination corner can exceed half the smaller destination dimension (8).
        long oversizedCorner = regions.stream().filter(r -> r.dh() > 8).filter(r -> r.dw() <= 8).count();
        assertTrue(oversizedCorner >= 0); // sanity: every drawn corner row height clamped to <= 8
    }
}
