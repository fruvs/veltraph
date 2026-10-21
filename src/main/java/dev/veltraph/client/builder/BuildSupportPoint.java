package dev.veltraph.client.builder;

import dev.veltraph.client.world.Vec3;

/** Actual outline top, rather than a full-cube assumption, for till/plant support ray endpoints. */
final class BuildSupportPoint {
    private BuildSupportPoint() {}
    static Vec3 top(int x,int y,int z,double minX,double minZ,double maxX,double maxY,double maxZ) {
        if(!Double.isFinite(minX)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>=maxX||minZ>=maxZ||maxY<=0||maxY>2)throw new IllegalArgumentException("Invalid native support outline");
        return new Vec3(x+(minX+maxX)/2,y+maxY,z+(minZ+maxZ)/2);
    }
}
