package dev.veltraph.client.garden.pests;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Stable4.0.30 first-enabled assigned entry and exact continuous cardinal spawn-relative box. */
public final class PestBoxGeometry {
    private PestBoxGeometry(){}
    public enum Status { UNDETERMINED, ENABLED, DISABLED }
    public record Entry(int plotId,Status status){public Entry {Objects.requireNonNull(status);if(plotId<0||plotId>24)throw new IllegalArgumentException("Invalid Garden plot ID");}}
    public record Plot(int plotId,Vec3 spawn,float yaw,float pitch){public Plot {if(plotId<1||plotId>24||spawn==null||!finite(spawn)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch<-90||pitch>90
        ||!GardenPlotBounds.contains(plotId,spawn.x(),spawn.z()))throw new IllegalArgumentException("Exact non-barn assigned plot spawn/yaw/pitch required");}}
    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        /** Native AABB.contains uses inclusive minimum and exclusive maximum. */
        public boolean contains(Vec3 point){return finite(point)&&point.x()>=minX&&point.x()<maxX&&point.y()>=minY&&point.y()<maxY&&point.z()>=minZ&&point.z()<maxZ;}
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    /** Supplied rows preserve the source grid's flatten order; first ENABLED must resolve uniquely. */
    public static Optional<Plot> assigned(List<List<Entry>> rows,List<Plot> plots){
        var first=rows.stream().flatMap(Collection::stream).filter(Objects::nonNull).filter(e->e.status()==Status.ENABLED).findFirst();if(first.isEmpty())return Optional.empty();
        var matches=plots.stream().filter(p->p.plotId()==first.get().plotId()).toList();return matches.size()==1?Optional.of(matches.getFirst()):Optional.empty();
    }
    public static boolean atSpawn(Plot plot,Vec3 player){return finite(player)&&plot.spawn().distanceTo(player)<=1.0;}
    public static Bounds bounds(Plot plot){
        // Direction.fromYRot: SOUTH, WEST, NORTH, EAST. Counter-clockwise is the left vector.
        int facing=Math.floorMod((int)Math.floor(plot.yaw()/90.0+0.5),4);int[] dx={0,-1,0,1},dz={1,0,-1,0};int fx=dx[facing],fz=dz[facing],lx=fz,lz=-fx;
        var s=plot.spawn();double ax=s.x()+lx*12-fx,az=s.z()+lz*12-fz,bx=s.x()-lx*12+fx*15,bz=s.z()-lz*12+fz*15;
        return new Bounds(Math.min(ax,bx),s.y()-4,Math.min(az,bz),Math.max(ax,bx),s.y()+6,Math.max(az,bz));
    }
}
