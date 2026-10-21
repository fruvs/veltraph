package dev.veltraph.client.garden.copy;

import dev.veltraph.client.world.BlockPos;

/** Inclusive explicit bounds, including AIR. Limits are declared local safety bounds. */
public record GardenRegion(int plot,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public GardenRegion {
        if(plot<1||plot>24||minX>maxX||minY>maxY||minZ>maxZ||minX< -240||maxX>239||minZ< -240||maxZ>239||minY<0||maxY>319
                ||maxY-minY>=64||(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)>1000000)
            throw new IllegalArgumentException("Explicit plot1..24/Garden bounds, Y0..319, height<=64 and <=1000000 cells required");
    }
    public int width(){return maxX-minX+1;}public int height(){return maxY-minY+1;}public int depth(){return maxZ-minZ+1;}
    public int cells(){return width()*height()*depth();}
    /** Bottom-up order preserves structural dependencies for additive placement. */
    public BlockPos at(int index){if(index<0||index>=cells())throw new IndexOutOfBoundsException(index);int x=index%width(),z=index/width()%depth(),y=index/(width()*depth());return new BlockPos(minX+x,minY+y,minZ+z);}
    public boolean contains(BlockPos p){return p.x()>=minX&&p.x()<=maxX&&p.y()>=minY&&p.y()<=maxY&&p.z()>=minZ&&p.z()<=maxZ;}
}
