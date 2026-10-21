package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;

/** Finite endpoint pulse search. Root still proves the first frame plus its entire neutral coast;
 * only that first control is published, and every following decision replans from real native motion.
 */
public final class CapturedProfileGroundPlan {
    private CapturedProfileGroundPlan() {}
    public static final class Plan {
        private final Trace trace;private Plan(Trace trace){this.trace=trace;}
        public Trace trace(){return trace;}public Input firstInput(){return trace.samples().getFirst().input();}
    }
    private record Candidate(Vec3 p,Vec3 v,List<Input> inputs,double score,int order){}
    public static Optional<Plan> plan(GroundNativePhysicsProfile profile,Vec3 position,Vec3 velocity,
            Vec3 target,float friction,float actualYaw,double radius,int remainingEffects) {
        if(profile==null||profile.sprinting()||!GroundLocomotionMotion.finite(position)||!GroundLocomotionMotion.finite(target)
                ||!GroundLocomotionMotion.finite(velocity)||!Double.isFinite(radius)||radius<=0||radius>.35
                ||Math.abs(target.y()-position.y())>.05||position.horizontalDistanceTo(target)>1.75)return Optional.empty();
        var neutral=forecast(profile,position,velocity,friction,actualYaw,Phase.SUPPORTED_GROUND,
                java.util.Collections.nCopies(12,Input.NEUTRAL),remainingEffects);
        if(neutral.isPresent()){var end=neutral.orElseThrow().samples().getLast();
            if(end.position().horizontalDistanceTo(target)<=radius&&Math.hypot(end.velocity().x(),end.velocity().z())<.003D)
                return Optional.of(new Plan(neutral.orElseThrow()));}
        double drag=(double)(friction*.91F);float acceleration=profile.groundAcceleration(friction);
        if(!Float.isFinite(acceleration)||!(drag>0D&&drag<1D))return Optional.empty();
        var normalized=CapturedProfileTrajectory.playerMomentum(velocity);
        double wantedX=target.x()-position.x()-normalized.x()/(1-drag);
        double wantedZ=target.z()-position.z()-normalized.z()/(1-drag);
        Input[] positive={Input.FORWARD,Input.LEFT,Input.FORWARD_LEFT,Input.FORWARD_RIGHT};
        Input[] negative={Input.BACKWARD,Input.RIGHT,Input.BACKWARD_RIGHT,Input.BACKWARD_LEFT};
        var vectors=new Vec3[4];for(int i=0;i<4;i++)vectors[i]=CapturedProfileTrajectory.inputAcceleration(positive[i],acceleration,actualYaw);
        record Counts(int a,int b,int c,int d,int size,double error){}
        var counts=new ArrayList<Counts>();
        for(int a=-6;a<=6;a++)for(int b=-6;b<=6;b++)for(int c=-6;c<=6;c++)for(int d=-6;d<=6;d++) {
            int size=Math.abs(a)+Math.abs(b)+Math.abs(c)+Math.abs(d);if(size==0||size>16)continue;
            double x=(a*vectors[0].x()+b*vectors[1].x()+c*vectors[2].x()+d*vectors[3].x())/(1-drag);
            double z=(a*vectors[0].z()+b*vectors[1].z()+c*vectors[2].z()+d*vectors[3].z())/(1-drag);
            double error=Math.hypot(x-wantedX,z-wantedZ);if(error<=radius+.025D)counts.add(new Counts(a,b,c,d,size,error));
        }
        counts.sort(Comparator.comparingDouble((Counts c)->c.error()+.0001D*c.size()));
        int attempted=0;
        for(var count:counts) {
            if(++attempted>128)break;
            int[] left={count.a(),count.b(),count.c(),count.d()};var inputs=new ArrayList<Input>();Vec3 p=position,v=velocity;
            while(inputs.size()<count.size()) {
                int chosen=-1;Sample best=null;double score=Double.POSITIVE_INFINITY;
                for(int axis=0;axis<4;axis++)if(left[axis]!=0) {
                    Input key=left[axis]>0?positive[axis]:negative[axis];
                    var option=frame(profile,p,v,friction,actualYaw,key,Phase.SUPPORTED_GROUND);if(option.isEmpty())continue;
                    var sample=option.orElseThrow();double next=sample.position().horizontalDistanceTo(target);
                    if(next<score){score=next;chosen=axis;best=sample;}
                }
                if(chosen<0)return Optional.empty();
                inputs.add(left[chosen]>0?positive[chosen]:negative[chosen]);left[chosen]+=left[chosen]>0?-1:1;
                p=best.position();v=best.velocity();
            }
            for(int i=0;i<12;i++)inputs.add(Input.NEUTRAL);
            var t=forecast(profile,position,velocity,friction,actualYaw,Phase.SUPPORTED_GROUND,inputs,remainingEffects);
            if(t.isEmpty())continue;var end=t.orElseThrow().samples().getLast();
            if(end.position().horizontalDistanceTo(target)<=radius&&Math.hypot(end.velocity().x(),end.velocity().z())<.003D)
                return Optional.of(new Plan(t.orElseThrow()));
        }
        return Optional.empty();
    }
}
