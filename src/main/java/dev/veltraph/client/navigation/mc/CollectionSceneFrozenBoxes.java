package dev.veltraph.client.navigation.mc;

import java.util.ArrayList;
import java.util.List;
import dev.veltraph.client.navigation.FlightFootprintCoverage;

/** Frozen native shape samples and pure represented-coordinate body/foot geometry. */
final class CollectionSceneFrozenBoxes {
    static final int MAX_BOXES=4096;
    record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    record Border(double minX,double minZ,double maxX,double maxZ) {}
    private CollectionSceneFrozenBoxes() {}
    static boolean finite(Box b) {
        return b != null && Double.isFinite(b.minX) && Double.isFinite(b.minY) && Double.isFinite(b.minZ)
            && Double.isFinite(b.maxX) && Double.isFinite(b.maxY) && Double.isFinite(b.maxZ)
            && b.minX < b.maxX && b.minY < b.maxY && b.minZ < b.maxZ;
    }
    static boolean finite(Border b) {
        return b != null && Double.isFinite(b.minX) && Double.isFinite(b.minZ)
            && Double.isFinite(b.maxX) && Double.isFinite(b.maxZ) && b.minX < b.maxX && b.minZ < b.maxZ;
    }
    static boolean clear(Box body,double x,double footY,double z,Box region,Border border,
                         List<Box> collisions,List<Box> supports) {
        if (!finite(body) || !finite(region) || !finite(border) || !Double.isFinite(x)
            || !Double.isFinite(footY) || !Double.isFinite(z) || collisions == null || supports == null
            || collisions.size()+supports.size() > MAX_BOXES
            || body.maxX-body.minX > 4 || body.maxZ-body.minZ > 4 || body.maxY-body.minY > 8
            || Math.abs(body.minY-footY) > 0.000001
            || Math.abs((body.minX+body.maxX)*0.5-x) > 0.000001
            || Math.abs((body.minZ+body.maxZ)*0.5-z) > 0.000001
            || body.minX < region.minX || body.maxX > region.maxX
            || body.minY < region.minY || body.maxY > region.maxY
            || body.minZ < region.minZ || body.maxZ > region.maxZ
            || body.minX < border.minX || body.maxX > border.maxX
            || body.minZ < border.minZ || body.maxZ > border.maxZ) return false;
        for (Box box : collisions) {
            if (!finite(box) || CollectionSceneWatch.intersects(body.minX,body.minY,body.minZ,
                body.maxX,body.maxY,body.maxZ,box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)) return false;
        }
        // UP faces are taken from actual native getBlockSupportShape AABBs at capture.
        // Filter to the live footprint before the existing kernel's 512-face bound.
        var faces=new ArrayList<FlightFootprintCoverage.Face>();
        for (Box box : supports) {
            if (!finite(box)) return false;
            if (Math.abs(box.maxY-footY) > .001 || box.maxX <= body.minX || box.minX >= body.maxX
                || box.maxZ <= body.minZ || box.minZ >= body.maxZ) continue;
            if (faces.size() >= 512) return false;
            faces.add(new FlightFootprintCoverage.Face(box.minX,box.maxX,box.minZ,box.maxZ));
        }
        return FlightFootprintCoverage.covers(
            new FlightFootprintCoverage.Face(body.minX,body.maxX,body.minZ,body.maxZ),faces);
    }
}
