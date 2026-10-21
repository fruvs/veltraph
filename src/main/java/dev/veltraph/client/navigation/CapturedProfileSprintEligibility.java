package dev.veltraph.client.navigation;
import dev.veltraph.client.navigation.CapturedProfileTrajectory.Input;
/** Pure additional refusal predicates. This does not confer a native movement/key capability. */
public final class CapturedProfileSprintEligibility {
    private CapturedProfileSprintEligibility(){}
    public record Traits(boolean alive,boolean spectator,boolean passenger,boolean usingItem,boolean scoping,
            boolean water,boolean lava,boolean flying,boolean blind,int food,boolean nativeCapability,
            boolean movingSlowly,boolean fallFlying,boolean horizontalCollision,boolean minorHorizontalCollision,
            boolean grounded){}
    /** Original S3 food >6 remains unconditional, including when native mayfly would bypass hunger. */
    public static boolean mayRequest(Traits t) {
        return t!=null&&t.alive()&&!t.spectator()&&!t.passenger()&&!t.usingItem()&&!t.scoping()
                &&!t.water()&&!t.lava()&&!t.flying()&&!t.blind()&&t.food()>6&&t.nativeCapability()
                &&!t.movingSlowly()&&!t.fallFlying()&&(!t.horizontalCollision()||t.minorHorizontalCollision())&&t.grounded();
    }
    /** Exact KeyboardInput Vec2 normalization observed by ClientInput.hasForwardImpulse in 26.3. */
    public static float nativeForwardScalar(Input candidate){
        if(candidate==null)return Float.NaN;float x=candidate.left(),z=candidate.forward();
        float length=(float)Math.sqrt(x*x+z*z);return length<1E-4F?0F:z/length;
    }
    /** Candidate direction supplies upcoming forward input, rather than a stale prior native input. */
    public static boolean mayPress(Traits t,Input candidate) {
        return candidate!=null&&nativeForwardScalar(candidate)>1E-5F&&mayRequest(t);
    }
}
