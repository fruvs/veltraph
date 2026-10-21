package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;


public final class ReachableMatureCropSelector {
    public record Scope(Object owner,Object player,Object world,Object connection,long epoch,long tick) {
        public boolean sameFrame(Scope s){return s!=null&&owner==s.owner&&player==s.player&&world==s.world&&connection==s.connection&&epoch==s.epoch&&tick==s.tick;}
    }
    public record Crop(BlockPos cell,Vec3 point,boolean loaded,boolean mature,boolean legalSoil,boolean unobstructed,boolean protectedForwardException) {
        public Crop(BlockPos cell,Vec3 point,boolean loaded,boolean mature,boolean legalSoil,boolean unobstructed){this(cell,point,loaded,mature,legalSoil,unobstructed,false);}
    }
    public record Selection(Scope scope,BlockPos cell,Vec3 point,double distance) {}
    private ReachableMatureCropSelector(){}
    public static Optional<Selection> select(Scope scope,Vec3 eye,double reach,List<Crop> crops,BlockPos avoid){
        if(scope==null||scope.owner()==null||scope.player()==null||scope.world()==null||scope.connection()==null||eye==null||!Double.isFinite(reach)||reach<=0||reach>64)return Optional.empty();
        Selection best=null;
        for(var c:crops){if(c==null||c.cell()==null||c.point()==null||!c.loaded()||!c.mature()||!c.legalSoil()||!c.unobstructed()||c.cell().equals(avoid))continue;
            double d=eye.distanceTo(c.point());if(!Double.isFinite(d)||d>reach)continue;
            var candidate=new Selection(scope,c.cell(),c.point(),d);
            if(best==null||d<best.distance()||d==best.distance()&&order(c.cell(),best.cell())<0)best=candidate;
        }
        return Optional.ofNullable(best);
    }
    /** Preserve ordinary forward-sector crops; only behind or truly lateral points need completion. */
    public static boolean missedSector(Vec3 eye,Vec3 point,float rowYaw){if(eye==null||point==null||!Float.isFinite(rowYaw))return false;double yaw=Math.toRadians(rowYaw);double dot=(point.x()-eye.x())*-Math.sin(yaw)+(point.z()-eye.z())*Math.cos(yaw);return dot<=1e-9;}
    public static Optional<Selection> selectMissed(Scope scope,Vec3 eye,double reach,List<Crop> crops,BlockPos avoid,float rowYaw){return select(scope,eye,reach,crops.stream().filter(c->c!=null&&(missedSector(eye,c.point(),rowYaw)||c.protectedForwardException())).toList(),avoid);}
    public static boolean current(Selection selected,Scope fresh,Vec3 eye,double reach,Crop nativeFresh){return selected!=null&&selected.scope().sameFrame(fresh)&&nativeFresh!=null&&selected.cell().equals(nativeFresh.cell())&&nativeFresh.loaded()&&nativeFresh.mature()&&nativeFresh.legalSoil()&&nativeFresh.unobstructed()&&Double.isFinite(reach)&&reach>0&&eye.distanceTo(nativeFresh.point())<=reach;}
    private static int order(BlockPos a,BlockPos b){int c=Integer.compare(a.x(),b.x());if(c==0)c=Integer.compare(a.y(),b.y());if(c==0)c=Integer.compare(a.z(),b.z());return c;}
}
