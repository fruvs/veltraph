package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.*;
/** Bounded deterministic ordinary-key approach; no forced state, random draw or route restart. */
public final class GroundJumpApproach {
    public enum Key { NEUTRAL, LEFT, RIGHT, BACK, BACK_LEFT, BACK_RIGHT }
    public record Plan(Key key,Vec3 next,Vec3 nextVelocity,Vec3 coast,double score,List<Key> sequence){
        public Plan{sequence=List.copyOf(sequence);if(sequence.isEmpty()||sequence.size()>3||sequence.getFirst()!=key)throw new IllegalArgumentException("Invalid finite approach sequence");}
        public Plan(Key key,Vec3 next,Vec3 nextVelocity,Vec3 coast,double score){this(key,next,nextVelocity,coast,score,List.of(key));}
    }
    private GroundJumpApproach(){}
    private static final double MULTIPLIER=(double).6F*(double).91F,ACCEL=(double).1F*(double).98F;
    public static boolean currentClock(long began,long last,long now){return began>=0&&now>=began&&now>=last&&now-began<=1200;}
    public static boolean sameSourceCell(dev.veltraph.client.world.BlockPos source,Vec3 point){return source!=null&&GroundLocomotionMotion.finite(point)&&source.equals(GroundPathfinder.node(point));}
    public static boolean newEdgePhase(GroundLocomotionPolicy.Phase phase){return phase==GroundLocomotionPolicy.Phase.READY||phase==GroundLocomotionPolicy.Phase.LANDED;}
    public static List<Plan> candidates(Vec3 actual,Vec3 velocity,float yaw,Vec3 target){
        if(!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(velocity)||!Float.isFinite(yaw))return List.of();
        var a=GroundPathfinder.node(actual);var b=GroundPathfinder.node(target);boolean alongZ=a.x()==b.x()&&Math.abs(a.z()-b.z())==1;
        boolean alongX=a.z()==b.z()&&Math.abs(a.x()-b.x())==1;if(!alongZ&&!alongX)return List.of();
        double sideGoal=alongZ?target.x():target.z();var best=new EnumMap<Key,Plan>(Key.class);
        for(Key first:Key.values())for(Key second:Key.values())for(Key third:Key.values()){
            Vec3 pos=actual,v=velocity,next=null,nextV=null;
            for(Key key:new Key[]{first,second,third}){
                Vec3 impulse=impulse(key,yaw);pos=new Vec3(pos.x()+v.x()+impulse.x(),pos.y(),pos.z()+v.z()+impulse.z());
                v=new Vec3((v.x()+impulse.x())*MULTIPLIER,velocity.y(),(v.z()+impulse.z())*MULTIPLIER);
                if(next==null){next=pos;nextV=v;}
            }
            Vec3 end=new Vec3(pos.x()+v.x()/(1-MULTIPLIER),pos.y(),pos.z()+v.z()/(1-MULTIPLIER));
            double side=alongZ?end.x():end.z(),backDistance=end.horizontalDistanceTo(actual);
            // Candidate ranking is not permission. Native caller proves only the actual next
            // step and complete coast, then recomputes from actual state on the following tick.
            double score=Math.abs(side-sideGoal)*30+backDistance*.15+(first==Key.NEUTRAL?-.01:0);
            var plan=new Plan(first,next,nextV,end,score,List.of(first,second,third));var old=best.get(first);if(old==null||score<old.score())best.put(first,plan);
        }
        return best.values().stream().sorted(Comparator.comparingDouble(Plan::score).thenComparing(p->p.key().ordinal())).toList();
    }
    /** Remaining commitment is recomputed from actual state and must earn a fresh native step proof. */
    public static Plan remaining(Vec3 actual,Vec3 velocity,float yaw,List<Key> sequence){
        if(sequence==null||sequence.isEmpty()||sequence.size()>3||!GroundLocomotionMotion.finite(actual)||!GroundLocomotionMotion.finite(velocity)||!Float.isFinite(yaw))throw new IllegalArgumentException("Invalid remaining approach");
        Vec3 p=actual,v=velocity,next=null,nextV=null;
        for(Key key:sequence){Vec3 impulse=impulse(key,yaw);p=new Vec3(p.x()+v.x()+impulse.x(),p.y(),p.z()+v.z()+impulse.z());v=new Vec3((v.x()+impulse.x())*MULTIPLIER,velocity.y(),(v.z()+impulse.z())*MULTIPLIER);if(next==null){next=p;nextV=v;}}
        var coast=new Vec3(p.x()+v.x()/(1-MULTIPLIER),p.y(),p.z()+v.z()/(1-MULTIPLIER));return new Plan(sequence.getFirst(),next,nextV,coast,0,sequence);
    }
    public static Vec3 impulse(Key key,float yaw){
        double left=key==Key.LEFT||key==Key.BACK_LEFT?1:key==Key.RIGHT||key==Key.BACK_RIGHT?-1:0;
        double back=key==Key.BACK||key==Key.BACK_LEFT||key==Key.BACK_RIGHT?1:0;double norm=Math.max(1,Math.hypot(left,back));
        double angle=Math.toRadians(yaw),cos=Math.cos(angle),sin=Math.sin(angle);
        // KeyboardInput supplies +localX for LEFT and -localZ for BACK.
        // Entity.getInputVector rotates worldX=localX*cos-localZ*sin,
        // worldZ=localZ*cos+localX*sin.
        return new Vec3((left*cos+back*sin)*ACCEL/norm,0,(left*sin-back*cos)*ACCEL/norm);
    }
    /** TravelHeading admits at most12 degrees at multiplier2; include complete friction tail of that impulse error. */
    public static double stepUncertainty(){return 2*ACCEL*Math.sin(Math.toRadians(12)/2)+.000001;}
    public static double coastUncertainty(){return .025+(.025+stepUncertainty())/(1-MULTIPLIER);}
    public static Vec3 immediateCoast(Plan plan){return new Vec3(plan.next().x()+plan.nextVelocity().x()/(1-MULTIPLIER),plan.next().y(),plan.next().z()+plan.nextVelocity().z()/(1-MULTIPLIER));}
    public static boolean lateralSettled(Vec3 velocity,Vec3 actual,Vec3 target){var a=GroundPathfinder.node(actual);var b=GroundPathfinder.node(target);return Math.abs(a.x()==b.x()?velocity.x():velocity.z())<=.006;}
}
