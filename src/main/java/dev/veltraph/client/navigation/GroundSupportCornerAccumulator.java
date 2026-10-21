package dev.veltraph.client.navigation;

/** Pure per-supportHeight scratch. Reset for each original corner and consume every box in
 * original native forAllBoxes order. No shape, cell, world, actor, permission or shared cache.
 * Raw bounds normalize exactly as original AABB(DDDDDD), including NaN and signed zero. */
public final class GroundSupportCornerAccumulator {
    private double localX,localZ,corner=Double.NaN;
    private int blockY;
    public GroundSupportCornerAccumulator() {}
    public void reset(double originalLocalX,double originalLocalZ,int originalBlockY){
        localX=originalLocalX;localZ=originalLocalZ;blockY=originalBlockY;corner=Double.NaN;
    }
    public void accept(double x1,double y1,double z1,double x2,double y2,double z2){
        double minX=Math.min(x1,x2),minY=Math.min(y1,y2),minZ=Math.min(z1,z2);
        double maxX=Math.max(x1,x2),maxY=Math.max(y1,y2),maxZ=Math.max(z1,z2);
        if(localX>=minX&&localX<=maxX&&localZ>=minZ&&localZ<=maxZ)
            corner=Double.isNaN(corner)?blockY+maxY:Math.max(corner,blockY+maxY);
    }
    public double height(){return corner;}
}
