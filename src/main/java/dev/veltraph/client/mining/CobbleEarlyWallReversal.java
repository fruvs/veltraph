package dev.veltraph.client.mining;

import dev.veltraph.client.world.*;

/** Bounded modern extension: coordinate the native .8m safety sweep with source wall reversal. */
public final class CobbleEarlyWallReversal {
    public static final double STEP=.8;
    private static final double EPS=1e-4;
    private CobbleEarlyWallReversal() {}
    public interface Terrain {
        /** Exact existing native support/body/hazard sweep; false is never blanket permission. */
        boolean walkClear(Vec3 from,Vec3 to);
        /** Loaded full-cube cobblestone at feet+head, with a safe loaded floor underneath. */
        boolean supportedCobbleColumn(BlockPos feet);
    }
    public static boolean blockedByEndWall(Vec3 p,Direction direction,double radius,double height,Terrain terrain){
        if(p==null||direction==null||!direction.isHorizontal()||terrain==null
                ||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())
                ||!Double.isFinite(radius)||radius<.29999||radius>.30001
                ||!Double.isFinite(height)||height<=0||height>2||Math.abs(p.y()-Math.floor(p.y()))>EPS)return false;
        var ahead=new Vec3(p.x()+direction.dx()*STEP,p.y(),p.z()+direction.dz()*STEP);
        if(terrain.walkClear(p,ahead))return false;
        var reverse=new Vec3(p.x()-direction.dx()*STEP,p.y(),p.z()-direction.dz()*STEP);
        if(!terrain.walkClear(p,reverse))return false;
        boolean xAxis=direction.dx()!=0;int sign=xAxis?direction.dx():direction.dz();
        double along=xAxis?p.x():p.z(),lateral=xAxis?p.z():p.x();
        int first=(int)Math.floor(along+sign*radius),last=(int)Math.floor(along+sign*(STEP+radius));
        int latCell=(int)Math.floor(lateral),feet=(int)Math.floor(p.y());
        // A single full-width wall column must cover the whole body, not just the crosshair centre.
        if(lateral-radius<latCell-EPS||lateral+radius>latCell+1+EPS)return false;
        for(int cell=first;sign*(cell-last)<=0;cell+=sign){
            var wall=xAxis?new BlockPos(cell,feet,latCell):new BlockPos(latCell,feet,cell);
            if(!terrain.supportedCobbleColumn(wall))continue;
            double plane=sign>0?cell:cell+1,contact=sign*(plane-along)-radius;
            if(contact< -EPS||contact>STEP+EPS)return false;
            double prefix=Math.max(0,contact-EPS);
            var beforeWall=new Vec3(p.x()+direction.dx()*prefix,p.y(),p.z()+direction.dz()*prefix);
            // A ledge, fluid, unloaded cell or unrelated body obstacle before the wall still denies reversal.
            return terrain.walkClear(p,beforeWall);
        }
        return false;
    }
}
