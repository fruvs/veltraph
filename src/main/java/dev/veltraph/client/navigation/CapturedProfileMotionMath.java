package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.navigation.mc.GroundProfileMotionCarrier.NativePose;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;

/** Pure consistency helpers. A caller-produced NativePose or Boolean confers no native authority. */
public final class CapturedProfileMotionMath {
    private CapturedProfileMotionMath() {}
    public enum Match { MATCHED, INPUT_MISMATCH, JUMP_COUNT_MISMATCH, FREE_MOTION_MISMATCH, CONTACT_MISMATCH }
    public static Vec3 nativeTravelInput(Input input) {
        float[] local=CapturedProfileTrajectory.localInput(input);
        return new Vec3(local[0],0D,local[1]);
    }
    public static double yawDifference(float a,float b) {
        if(!Float.isFinite(a)||!Float.isFinite(b))return Double.POSITIVE_INFINITY;
        double d=((double)a-b)%360D;if(d>180D)d-=360D;if(d< -180D)d+=360D;return Math.abs(d);
    }
    /** Mathematical exception only; the active producer must retain the same opaque PWC/frame scope. */
    public static boolean yawIndependent(Input input,Vec3 actualTravelInput,Phase phase,boolean actualSprinting) {
        return input==Input.NEUTRAL&&new Vec3(0D,0D,0D).equals(actualTravelInput)
                &&(phase!=Phase.GROUNDED_TAKEOFF||!actualSprinting);
    }
    public static boolean vectorClose(Vec3 a,Vec3 b) {
        return GroundLocomotionMotion.finite(a)&&GroundLocomotionMotion.finite(b)
                &&near(a.x(),b.x())&&near(a.y(),b.y())&&near(a.z(),b.z());
    }
    private static boolean near(double a,double b) {
        return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=Math.max(1E-8D,4D*Math.max(Math.ulp(a),Math.ulp(b)));
    }
    public static Match inputAndJump(Input input,Phase phase,Vec3 actualTravelInput,int actualJumpCalls) {
        if(!nativeTravelInput(input).equals(actualTravelInput))return Match.INPUT_MISMATCH;
        return actualJumpCalls==(phase==Phase.GROUNDED_TAKEOFF?1:0)?Match.MATCHED:Match.JUMP_COUNT_MISMATCH;
    }
    public static Match freeMotion(Sample expected,NativePose after) {
        return expected!=null&&after!=null&&vectorClose(expected.position(),after.position())
                &&vectorClose(expected.velocity(),after.velocity())?Match.MATCHED:Match.FREE_MOTION_MISMATCH;
    }
    /** Exact native axis-clamped contact: full XZ movement, Y support clamp, then cached air drag. */
    public static Match landingContact(Vec3 before,Sample free,NativePose actual,Vec3 target,double radius) {
        if(free==null||actual==null||!GroundLocomotionMotion.finite(before)||!GroundLocomotionMotion.finite(target)
                ||!actual.onGround()||free.phase()!=Phase.OBSERVED_AIRBORNE&&free.phase()!=Phase.GROUNDED_EXIT
                ||free.intendedMove().y()>=0D||before.y()<target.y()-1E-6D||free.position().y()>target.y()+1E-6D
                ||Math.abs(actual.position().y()-target.y())>1E-6D||actual.position().horizontalDistanceTo(target)>radius)
            return Match.CONTACT_MISMATCH;
        var expectedPosition=new Vec3(free.position().x(),target.y(),free.position().z());
        var expectedVelocity=new Vec3(free.velocity().x(),-.08D*(double).98F,free.velocity().z());
        return vectorClose(expectedPosition,actual.position())&&vectorClose(expectedVelocity,actual.velocity())
                ?Match.MATCHED:Match.CONTACT_MISMATCH;
    }
    public static boolean originalDrop(Vec3 original,Vec3 landing) {
        if(!GroundLocomotionMotion.finite(original)||!GroundLocomotionMotion.finite(landing)
                ||original.y()-landing.y()<=.6D||original.y()-landing.y()>1.001D
                ||original.horizontalDistanceTo(landing)<.35D||original.horizontalDistanceTo(landing)>1.65D)return false;
        var from=GroundPathfinder.node(original);var to=GroundPathfinder.node(landing);
        return from.y()-to.y()==1&&Math.abs(from.x()-to.x())+Math.abs(from.z()-to.z())==1;
    }
    public static boolean held(Controls controls,LegacyTravelRetirement.Key key) {
        if(controls==null||key==null)return false;
        return switch(key) {
            case FORWARD -> controls.input().forward()>0;
            case BACK -> controls.input().forward()<0;
            case LEFT -> controls.input().left()>0;
            case RIGHT -> controls.input().left()<0;
            case JUMP -> controls.jump()==JumpIntent.PRESS;
            case SPRINT -> controls.sprint()==SprintIntent.PRESS;
            default -> false;
        };
    }
}
