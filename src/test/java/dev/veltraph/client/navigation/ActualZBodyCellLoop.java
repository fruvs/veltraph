package dev.veltraph.client.navigation;

/** Generated exact source loop header, linked to its original body by source reversal proof. */
final class ActualZBodyCellLoop {
    interface Visit { boolean cell(int x,int y,int z); }
    static final class Bounds {
        final double minX,minY,minZ,maxX,maxY,maxZ;
        Bounds(double ax,double ay,double az,double bx,double by,double bz){minX=ax;minY=ay;minZ=az;maxX=bx;maxY=by;maxZ=bz;}
    }
    static boolean scan(Bounds box,Visit visit) {
        double cellMinX=Math.floor(box.minX),cellMaxX=Math.floor(box.maxX);
        double cellMinY=Math.floor(box.minY),cellMaxY=Math.floor(box.maxY);
        double cellMinZ=Math.floor(box.minZ),cellMaxZ=Math.floor(box.maxZ);
        for(int x=(int)cellMinX;x<=cellMaxX;x++)
            for(int y=(int)cellMinY;y<=cellMaxY;y++)
                for(int z=(int)cellMinZ;z<=cellMaxZ;z++) {
                    if(!visit.cell(x,y,z))return false;
                }
        return true;
    }
}
