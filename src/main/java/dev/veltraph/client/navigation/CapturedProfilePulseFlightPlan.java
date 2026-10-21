package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
/** Finite forward-then-back pulse search. One forward onset prevents native double-tap sprint
 * during high boost air control. Actual native timer delay is supplied by the active producer.
 */
public final class CapturedProfilePulseFlightPlan {
    private CapturedProfilePulseFlightPlan(){}
    public static final class Plan {
        private final Trace trace;private final Vec3 target;
        private Plan(Trace trace,Vec3 target){this.trace=trace;this.target=target;}
        public Trace trace(){return trace;}public Vec3 target(){return target;}
        public Input firstInput(){return trace.samples().getFirst().input();}
        public List<Controls> expectedControls(){return trace.samples().stream().map(Sample::controls).toList();}
    }
    public static Optional<Plan> jump(GroundNativePhysicsProfile profile,Vec3 origin,Vec3 velocity,Vec3 target,
            float friction,float yaw,int remainingEffects) {
        if(profile==null||profile.sprinting()||profile.state().jumpAmplifier()!=5||!GroundLocomotionMotion.finite(origin)
                ||!GroundLocomotionMotion.finite(target)||target.y()-origin.y()<=.6D||target.y()-origin.y()>1.001D
                ||origin.horizontalDistanceTo(target)<.35D||origin.horizontalDistanceTo(target)>1.65D
                ||Math.abs(Math.floor(origin.x())-Math.floor(target.x()))+Math.abs(Math.floor(origin.z())-Math.floor(target.z()))!=1D)
            return Optional.empty();
        return search(profile,origin,velocity,target,friction,yaw,Phase.GROUNDED_TAKEOFF,remainingEffects,0,40);
    }
    public static Optional<Plan> descent(GroundNativePhysicsProfile profile,Vec3 origin,Vec3 velocity,Vec3 original,Vec3 target,
            float friction,float yaw,Phase first,int remainingEffects,int minimumForwardDelay) {
        if(profile==null||profile.sprinting()||!CapturedProfileMotionMath.originalDrop(original,target)
                ||!GroundLocomotionMotion.finite(origin)||origin.y()>original.y()+.081D||origin.y()<target.y()-1E-6D
                ||first!=Phase.GROUNDED_EXIT&&first!=Phase.OBSERVED_AIRBORNE||minimumForwardDelay<0||minimumForwardDelay>40)
            return Optional.empty();
        return search(profile,origin,velocity,target,friction,yaw,first,remainingEffects,minimumForwardDelay,16);
    }
    public static Optional<Plan> resume(GroundNativePhysicsProfile profile,Vec3 origin,Vec3 velocity,Vec3 target,
            float friction,float yaw,int remainingEffects,int minimumForwardDelay) {
        if(profile==null||profile.sprinting()||!GroundLocomotionMotion.finite(origin)||!GroundLocomotionMotion.finite(target)
                ||origin.horizontalDistanceTo(target)>2D||minimumForwardDelay<0||minimumForwardDelay>40)return Optional.empty();
        return search(profile,origin,velocity,target,friction,yaw,Phase.OBSERVED_AIRBORNE,remainingEffects,minimumForwardDelay,40);
    }
    private static Optional<Plan> search(GroundNativePhysicsProfile profile,Vec3 origin,Vec3 velocity,Vec3 target,
            float friction,float yaw,Phase first,int remainingEffects,int minimumForwardDelay,int maximum) {
        Vec3 p=origin,v=velocity;int count=0;
        for(int step=1;step<=maximum;step++) {
            var s=frame(profile,p,v,friction,yaw,Input.NEUTRAL,step==1?first:Phase.OBSERVED_AIRBORNE);
            if(s.isEmpty())return Optional.empty();var next=s.orElseThrow();
            if(p.y()>=target.y()&&next.position().y()<=target.y()&&next.intendedMove().y()<0D){count=step;break;}
            p=next.position();v=next.velocity();
        }
        if(count==0||remainingEffects!=-1&&remainingEffects<=count)return Optional.empty();
        int prefix=first==Phase.GROUNDED_TAKEOFF?1:0;
        Trace best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int delay=minimumForwardDelay;delay<=count-prefix;delay++)
            for(int forward=0;forward<=count-prefix-delay;forward++)
                for(int backward=0;backward<=count-prefix-delay-forward;backward++) {
                    var keys=new ArrayList<Input>();for(int i=0;i<prefix+delay;i++)keys.add(Input.NEUTRAL);
                    for(int i=0;i<forward;i++)keys.add(Input.FORWARD);for(int i=0;i<backward;i++)keys.add(Input.BACKWARD);
                    while(keys.size()<count)keys.add(Input.NEUTRAL);
                    var trace=forecast(profile,origin,velocity,friction,yaw,first,keys,remainingEffects);
                    if(trace.isEmpty())continue;var t=trace.orElseThrow();var end=t.samples().getLast();
                    double speed=Math.hypot(end.intendedMove().x(),end.intendedMove().z());
                    if(end.position().horizontalDistanceTo(target)>.075D||speed>.05D)continue;
                    double groundDrag=(double)(friction*.91F);
                    var stopped=new Vec3(end.position().x()+end.velocity().x()/(1-groundDrag),target.y(),
                            end.position().z()+end.velocity().z()/(1-groundDrag));
                    if(stopped.horizontalDistanceTo(target)>.075D)continue;
                    double score=end.position().horizontalDistanceTo(target)+2D*speed;
                    if(score<bestScore){best=t;bestScore=score;}
                }
        return best==null?Optional.empty():Optional.of(new Plan(best,target));
    }
}
