package dev.veltraph.client.navigation;

/** Generated exact source loop header, linked to its original body by source reversal proof. */
final class FrozenZBodyCellLoop {
    interface Visit { boolean cell(int x,int y,int z); }
    static final class Bounds {
        final double minX,minY,minZ,maxX,maxY,maxZ;
        Bounds(double ax,double ay,double az,double bx,double by,double bz){minX=ax;minY=ay;minZ=az;maxX=bx;maxY=by;maxZ=bz;}
    }
    static boolean scan(Bounds box,Visit visit) {
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)
            for(int y=(int)Math.floor(box.minY);y<=Math.floor(box.maxY);y++)
                for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++) {
                    if(!visit.cell(x,y,z))return false;
                }
        return true;
    }
}
