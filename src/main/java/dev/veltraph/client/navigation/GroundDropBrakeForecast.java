package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
/** Bounded no-input descent episode; only actually published neutral input can carry its receipt. */
public final class GroundDropBrakeForecast {
    private GroundDropBrakeForecast(){}
    public record Episode(int tick,int gravityStage,Vec3 origin,Vec3 target,Vec3 actual,Vec3 velocity,List<Vec3> remaining,Vec3 nextVelocity,boolean nextGround,Vec3 landing,Vec3 coast){public Episode{remaining=List.copyOf(remaining);}}
    public static Optional<Episode> begin(int tick,boolean ground,Vec3 actual,Vec3 velocity,Vec3 origin,Vec3 target,double speed){
        if(tick<0||ground||!GroundLocomotionMotion.dropCurrent(actual,velocity,origin,target,speed)||GroundLocomotionMotion.drop(actual,velocity,origin,target,speed))return Optional.empty();
        double y=origin.y(),v=-.08*(double).98F;
        for(int stage=1;stage<=12;stage++){y+=v;v=(v-.08)*(double).98F;
            if(Math.abs(y-actual.y())<=.00001&&Math.abs(v-velocity.y())<=.00001)return build(tick,stage,actual,velocity,origin,target,speed);
        }
        return Optional.empty();
    }
    public static Optional<Episode> resume(Episode old,int tick,boolean ground,Vec3 actual,Vec3 velocity,Vec3 origin,Vec3 target,double speed){
        if(old==null||tick!=old.tick()+1||!old.origin().equals(origin)||!old.target().equals(target)
                ||!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(velocity)||!GroundLocomotionMotion.speedAttribute(speed,false)
                ||ground!=old.nextGround()||old.remaining().isEmpty()||actual.distanceTo(old.remaining().getFirst())>.00001
                ||velocity.distanceTo(old.nextVelocity())>.00001)return Optional.empty();
        if(ground){double tail=1/(1-.600001*(double).91F);var coast=new Vec3(actual.x()+velocity.x()*tail,actual.y(),actual.z()+velocity.z()*tail);
            return Optional.of(new Episode(tick,old.gravityStage()+1,origin,target,actual,velocity,List.of(),velocity,true,actual,coast));}
        return build(tick,old.gravityStage()+1,actual,velocity,origin,target,speed);
    }
    private static Optional<Episode> build(int tick,int stage,Vec3 actual,Vec3 velocity,Vec3 origin,Vec3 target,double speed){
        if(!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(velocity)||!GroundLocomotionMotion.speedAttribute(speed,false)
                ||Math.hypot(velocity.x(),velocity.z())>.16||velocity.y()<-.65||velocity.y()>=0
                ||actual.y()>origin.y()+.081||actual.y()<target.y()||origin.y()-target.y()<=.6||origin.y()-target.y()>1.001)return Optional.empty();
        Vec3 p=actual,v=velocity,nextVelocity=null;boolean nextGround=false;var points=new ArrayList<Vec3>();
        for(int step=1;step<=12-stage;step++){
            boolean landed=p.y()+v.y()<=target.y();
            p=new Vec3(p.x()+v.x(),landed?target.y():p.y()+v.y(),p.z()+v.z());points.add(p);
            v=new Vec3(v.x()*(double).91F,landed?-.08*(double).98F:(v.y()-.08)*(double).98F,v.z()*(double).91F);
            if(step==1){nextVelocity=v;nextGround=landed;}
            if(landed){double tail=1/(1-.600001*(double).91F);var coast=new Vec3(p.x()+v.x()*tail,p.y(),p.z()+v.z()*tail);
                if(actual.distanceTo(p)>2||actual.horizontalDistanceTo(coast)>2||p.distanceTo(target)>.65
                        ||!GroundPathfinder.node(p).equals(GroundPathfinder.node(target)))return Optional.empty();
                return Optional.of(new Episode(tick,stage,origin,target,actual,velocity,points,nextVelocity,nextGround,p,coast));}
        }
        return Optional.empty();
    }
}
