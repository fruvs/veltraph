package dev.veltraph.client.garden.plotcleaner;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Caller-supplied immutable bounds; never infer a deletion region from a crosshair or menu slot. */
public record PlotCleanerPlan(int plot,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public PlotCleanerPlan {
        if(plot<1||plot>24||minX>maxX||minY>maxY||minZ>maxZ||minX< -240||maxX>239||minZ< -240||maxZ>239
                ||minY<66||maxY>319||maxX-minX>=96||maxZ-minZ>=96||maxY-minY>=32||volume(minX,minY,minZ,maxX,maxY,maxZ)>131072)
            throw new IllegalArgumentException("Explicit Garden plot 1..24 bounds, floor>=66, span<=96x32x96 and volume<=131072 required");
    }
    private static long volume(int a,int b,int c,int d,int e,int f){return (long)(d-a+1)*(e-b+1)*(f-c+1);}
    public long volume(){return volume(minX,minY,minZ,maxX,maxY,maxZ);}
    public boolean contains(BlockPos p){return p.x()>=minX&&p.x()<=maxX&&p.y()>=minY&&p.y()<=maxY&&p.z()>=minZ&&p.z()<=maxZ;}
    public String plotMarker(){return "Plot: "+plot;}
    public String scopeMarker(){return "Bounds: "+minX+","+minY+","+minZ+" .. "+maxX+","+maxY+","+maxZ;}
    public boolean cleanup(BlockPos p){return p.y()>70;}
    public static boolean natural(String id){return id.equals("minecraft:dirt")||id.equals("minecraft:grass_block");}
    public List<BlockPos> scanOrder(){var r=new ArrayList<BlockPos>();for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++)for(int y=maxY;y>=minY;y--)r.add(new BlockPos(x,y,z));return List.copyOf(r);}
    /** Source uses closest corner then adjacent rows; choose an explicit local X-axis snake. */
    public Comparator<BlockPos> rowOrder(double x,double z){
        boolean east=Math.abs(x-minX)<=Math.abs(x-maxX),south=Math.abs(z-minZ)<=Math.abs(z-maxZ);
        return Comparator.comparingInt((BlockPos p)->south?p.z()-minZ:maxZ-p.z())
                .thenComparingInt(p->{int row=south?p.z()-minZ:maxZ-p.z();boolean forward=(row%2==0)==east;return forward?p.x()-minX:maxX-p.x();})
                .thenComparing(Comparator.comparingInt(BlockPos::y).reversed());
    }
}
