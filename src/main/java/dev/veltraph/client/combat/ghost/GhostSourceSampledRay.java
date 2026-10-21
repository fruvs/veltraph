package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Objects;

/** Source sampled-ray ordering; native snapshot provider must supply the observed passable registry. */
public final class GhostSourceSampledRay {
    public record Point(double x,double y,double z) {
        public Point { if (!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalArgumentException("Finite ray point required"); }
        Point add(Point d,double n){return new Point(x+d.x*n,y+d.y*n,z+d.z*n);}
        double distance(Point p){return Math.sqrt(Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2));}
        Cell cell(){return new Cell((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}
    }
    public record Cell(int x,int y,int z) {}
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box { if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("Finite ordered native box required"); }
        boolean contains(Point p,double inflation){return p.x>=minX-inflation&&p.x<maxX+inflation&&p.y>=minY-inflation&&p.y<maxY+inflation&&p.z>=minZ-inflation&&p.z<maxZ+inflation;}
    }
    public record Actor(Box box,Point position,boolean living,boolean invisible,boolean localPlayer,boolean selectedTarget) {}
    public record Block(boolean available,boolean sourcePassable,Box unionCollisionBounds,boolean fluidNonEmpty) {}
    public interface Snapshot {
        /** False means source registry has not been bound; it must not silently become an empty set. */
        boolean passableRegistryObserved();
        Block block(Cell cell);
        List<Actor> actors();
    }
    public record Mode(double inflation,boolean blockActors,boolean denseSampling,boolean rejectNonPassable) {
        public static Mode visibility(){return new Mode(.6,false,false,false);}
        public static Mode standing(){return new Mode(0,true,true,true);}
    }
    private GhostSourceSampledRay() {}
    /** Source direction is observed/computed through its float yaw/pitch helper, not an invented LOS. */
    public static boolean hits(Point eye,Point targetPoint,Box targetBox,Point sourceUnitDirection,Mode mode,Snapshot snapshot){
        Objects.requireNonNull(eye);Objects.requireNonNull(targetPoint);Objects.requireNonNull(targetBox);Objects.requireNonNull(sourceUnitDirection);Objects.requireNonNull(mode);Objects.requireNonNull(snapshot);
        if(!Double.isFinite(mode.inflation())||mode.inflation()<0)throw new IllegalArgumentException("Finite nonnegative source inflation required");
        if(!snapshot.passableRegistryObserved())return false;
        int limit=(int)Math.ceil(eye.distance(targetPoint)+3);
        if(limit>1000)throw new IllegalArgumentException("Source ray limit exceeds 1000");
        int density=mode.denseSampling()?10:2;
        Point step=new Point(sourceUnitDirection.x/density,sourceUnitDirection.y/density,sourceUnitDirection.z/density);
        var blockers=snapshot.actors().stream().filter(a->a.living()&&!a.invisible()&&!a.localPlayer()&&!a.selectedTarget()&&a.position().distance(eye)<limit).toList();
        Cell previous=eye.cell();
        for(int index=0;index<=limit*density;index++){
            Point point=eye.add(step,index);Cell cell=point.cell();
            if(cell.equals(previous)&&targetPoint.distance(point)>2)continue;
            previous=cell;
            // Source authorizes target volume before actors or terrain at the same sampled point.
            if(targetBox.contains(point,mode.inflation()))return true;
            if(mode.blockActors()&&blockers.stream().anyMatch(a->a.box().contains(point,mode.inflation())))return false;
            Block block=snapshot.block(cell);
            if(block==null||!block.available())return false; // Qualified clean-room loaded-data boundary.
            if(block.sourcePassable())continue;
            if(mode.rejectNonPassable())return false;
            Box collision=block.unionCollisionBounds();
            if(collision==null&&block.fluidNonEmpty())collision=new Box(cell.x,cell.y,cell.z,cell.x+1,cell.y+1,cell.z+1);
            if(collision!=null&&collision.contains(point,0))return false;
        }
        return false;
    }
}
