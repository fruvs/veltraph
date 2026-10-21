package dev.veltraph.client.ui.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure nine-slice decomposition: splits a {@code texW x texH} source texture into up to 9
 * (source → destination) rectangles so a small rounded texture can skin a surface of any size without
 * distorting its border. The corner is described by two insets: the <b>source</b> inset (how many texels
 * of the texture are the corner) and the <b>destination</b> inset (how many on-screen pixels the corner
 * occupies). When the source inset is larger than the destination inset the corner is <i>downscaled</i>,
 * which bilinearly anti-aliases a high-resolution baked corner into a crisp small radius — the
 * blocky-corner fix. Edges stretch on one axis; the centre stretches on both. No Minecraft dependency,
 * so it is unit-testable; {@link DrawContextRenderer} feeds each region to the scaling texture blit.
 */
public final class NineSlice {

    /** A source rectangle {@code (u,v,sw,sh)} drawn into a destination rectangle {@code (dx,dy,dw,dh)}. */
    public record Region(int dx, int dy, int dw, int dh, int u, int v, int sw, int sh) {
    }

    private NineSlice() {
    }

    /** Symmetric nine-slice (source corner texels == destination corner pixels). */
    public static List<Region> regions(int x, int y, int width, int height, int texW, int texH, int inset) {
        return regions(x, y, width, height, texW, texH, inset, inset);
    }

    /**
     * Nine-slice with independent source/destination corner insets. {@code srcInset} is the texel size
     * of the baked corner in the source image; {@code dstInset} is the on-screen pixel size of the drawn
     * corner. {@code srcInset > dstInset} downscales the corner (anti-aliased crisp radius).
     */
    public static List<Region> regions(int x, int y, int width, int height,
                                       int texW, int texH, int srcInset, int dstInset) {
        List<Region> out = new ArrayList<>(9);
        if (width <= 0 || height <= 0 || texW <= 0 || texH <= 0) {
            return out;
        }
        int sc = Math.max(0, Math.min(srcInset, Math.min(texW, texH) / 2));
        int dc = Math.max(0, Math.min(dstInset, Math.min(width, height) / 2));
        if (sc == 0 || dc == 0) {
            out.add(new Region(x, y, width, height, 0, 0, texW, texH));
            return out;
        }
        int sMidW = texW - 2 * sc;
        int sMidH = texH - 2 * sc;
        int dMidW = width - 2 * dc;
        int dMidH = height - 2 * dc;

        // Corners: source corner (sc x sc) → destination corner (dc x dc).
        out.add(new Region(x, y, dc, dc, 0, 0, sc, sc));
        out.add(new Region(x + width - dc, y, dc, dc, texW - sc, 0, sc, sc));
        out.add(new Region(x, y + height - dc, dc, dc, 0, texH - sc, sc, sc));
        out.add(new Region(x + width - dc, y + height - dc, dc, dc, texW - sc, texH - sc, sc, sc));
        // Top / bottom edges.
        if (dMidW > 0 && sMidW > 0) {
            out.add(new Region(x + dc, y, dMidW, dc, sc, 0, sMidW, sc));
            out.add(new Region(x + dc, y + height - dc, dMidW, dc, sc, texH - sc, sMidW, sc));
        }
        // Left / right edges.
        if (dMidH > 0 && sMidH > 0) {
            out.add(new Region(x, y + dc, dc, dMidH, 0, sc, sc, sMidH));
            out.add(new Region(x + width - dc, y + dc, dc, dMidH, texW - sc, sc, sc, sMidH));
        }
        // Centre.
        if (dMidW > 0 && dMidH > 0 && sMidW > 0 && sMidH > 0) {
            out.add(new Region(x + dc, y + dc, dMidW, dMidH, sc, sc, sMidW, sMidH));
        }
        return out;
    }
}
