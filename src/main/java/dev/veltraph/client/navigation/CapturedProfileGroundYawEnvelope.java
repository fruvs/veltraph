package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;

/** Pure physical bounds only, never input or camera authority. The native queued endpoint is
 * an eligibility bound, NOT a claim that interpolation has no angular overshoot. The complete
 * first-pulse/neutral stopping rectangle encloses ALL yaw headings, including overshoot.
 */
public final class CapturedProfileGroundYawEnvelope {
    private CapturedProfileGroundYawEnvelope() {}
    public static final class Envelope {
        private final GroundNativePhysicsProfile.State state;
        private final Vec3 origin, observedVelocity, minimum, maximum;
        private final Input input; private final float yaw, endpoint, friction;
        private Envelope(GroundNativePhysicsProfile.State state,Vec3 origin,Vec3 velocity,Input input,
                float yaw,float endpoint,float friction,Vec3 minimum,Vec3 maximum){
            this.state=state;this.origin=origin;observedVelocity=velocity;this.input=input;
            this.yaw=yaw;this.endpoint=endpoint;this.friction=friction;this.minimum=minimum;this.maximum=maximum;
        }
        public GroundNativePhysicsProfile.State profileState(){return state;}
        public Vec3 origin(){return origin;} public Vec3 observedVelocity(){return observedVelocity;}
        public Vec3 minimum(){return minimum;} public Vec3 maximum(){return maximum;}
        public Input input(){return input;} public float yaw(){return yaw;} public float friction(){return friction;}
        /** Existing native support helper expands by .3, whereas standing width is a float.
         * Preserve its API and compensate the EXACT actual width difference in this new proof.
         * The existing .025 body uncertainty is unchanged; outward endpoints prevent boundary loss.
         */
        public Optional<Footing> footing(float nativeWidth){
            if(!Float.isFinite(nativeWidth)||nativeWidth<=0F||nativeWidth>1F)return Optional.empty();
            double widthDifference=Math.max(0D,(double)nativeWidth/2D-.3D);
            double padding=.025D+widthDifference;
            Vec3 min=new Vec3(Math.nextDown(minimum.x()-padding),origin.y(),Math.nextDown(minimum.z()-padding));
            Vec3 max=new Vec3(Math.nextUp(maximum.x()+padding),origin.y(),Math.nextUp(maximum.z()+padding));
            return GroundLocomotionMotion.finite(min)&&GroundLocomotionMotion.finite(max)?Optional.of(new Footing(min,max)):Optional.empty();
        }

        /** Only the actual issued queued endpoint may be matched here; no new camera ticket. */
        public boolean endpointMatches(float actual){return Float.isFinite(actual)
                &&CapturedProfileMotionMath.yawDifference(actual,endpoint)<=.5D
                &&CapturedProfileMotionMath.yawDifference(yaw,actual)<=45D;}
        public boolean contains(Vec3 p){return GroundLocomotionMotion.finite(p)&&p.y()==origin.y()
                &&p.x()>=minimum.x()&&p.x()<=maximum.x()&&p.z()>=minimum.z()&&p.z()<=maximum.z();}
    }
    public static final class Footing {
        private final Vec3 minimum,maximum;
        private Footing(Vec3 min,Vec3 max){minimum=min;maximum=max;}
        public Vec3 minimum(){return minimum;} public Vec3 maximum(){return maximum;}
    }
    /** The actual source factory uses the same narrowly selected physics family. Pure agreement
     * is refusal-only; only the private Journey factory and real actor/native receipts own custody. */
    public static boolean directionalProfile(GroundNativePhysicsProfile profile){
        return profile!=null&&GroundOwnedSprintTransition.directionalProfileSupported(profile.state());
    }
    /** Native input normalization/order comes from the existing modern trajectory implementation.
     * Mth sin/cos entries are each in [-1,1], so each rotated acceleration coordinate is bounded
     * by speed*(abs(localX)+abs(localZ)). Existing diagonal normalization can only reduce that.
     * Neutral native dead-zone removes BOTH coordinates simultaneously; it truncates scalar
     * positive geometric coast, never increases/reverses it. Bounds include the full infinite
     * coast, not just the finite 33-frame selected trace. Outward rounding protects the enclosure.
     */
    public static Optional<Envelope> describe(GroundNativePhysicsProfile profile,Vec3 origin,Vec3 observedVelocity,
            float friction,float actualYaw,float endpointYaw,Input input){
        if(!directionalProfile(profile)||input==null||input==Input.NEUTRAL
                ||!GroundLocomotionMotion.finite(origin)||!GroundLocomotionMotion.finite(observedVelocity)
                ||observedVelocity.y()<-.081D||observedVelocity.y()>0D
                ||!Float.isFinite(actualYaw)||!Float.isFinite(endpointYaw)
                ||CapturedProfileMotionMath.yawDifference(actualYaw,endpointYaw)>45D)return Optional.empty();
        float acceleration=profile.groundAcceleration(friction);
        double drag=(double)(friction*.91F);
        if(!Float.isFinite(acceleration)||acceleration<=0F||!(drag>0D&&drag<1D))return Optional.empty();
        float[] local=CapturedProfileTrajectory.localInput(input);
        double component=Math.nextUp((Math.abs((double)local[0])+Math.abs((double)local[1]))*(double)acceleration);
        // Additional outward arithmetic rounding covers native add/multiply and repeated neutral
        // integration. For the admitted finite profiles this dominates 64-step accumulated ulps.
        Vec3 momentum=CapturedProfileTrajectory.playerMomentum(observedVelocity);
        double rounding=128D*Math.max(Math.ulp(component),Math.max(Math.ulp(momentum.x()),Math.ulp(momentum.z())));
        component=Math.nextUp(component+Math.max(1E-12D,rounding));
        double denominator=1D-drag;
        double loX=Math.nextDown((momentum.x()-component)/denominator),hiX=Math.nextUp((momentum.x()+component)/denominator);
        double loZ=Math.nextDown((momentum.z()-component)/denominator),hiZ=Math.nextUp((momentum.z()+component)/denominator);
        Vec3 minimum=new Vec3(Math.nextDown(origin.x()+Math.min(0D,loX)),origin.y(),Math.nextDown(origin.z()+Math.min(0D,loZ)));
        Vec3 maximum=new Vec3(Math.nextUp(origin.x()+Math.max(0D,hiX)),origin.y(),Math.nextUp(origin.z()+Math.max(0D,hiZ)));
        if(!GroundLocomotionMotion.finite(minimum)||!GroundLocomotionMotion.finite(maximum))return Optional.empty();
        return Optional.of(new Envelope(profile.state(),origin,observedVelocity,input,actualYaw,endpointYaw,friction,minimum,maximum));
    }
}
