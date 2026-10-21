package dev.veltraph.client.hunting;
import dev.veltraph.client.world.Vec3;

/** Ordinary owned key steering after coarse navigation; never pose/velocity/position writes. */
public final class HuntingApproachPrecision {
    private HuntingApproachPrecision() {}
    public record Input(boolean arrived,boolean forward,boolean back,boolean left,boolean right,boolean jump,boolean sneak) {}
    public static Input steer(Vec3 from,Vec3 goal,Vec3 velocity,float yaw,boolean water){
        double dx=goal.x()-from.x(),dy=goal.y()-from.y(),dz=goal.z()-from.z();
        boolean arrived=Math.hypot(dx,dz)<.12&&Math.abs(dy)<.15&&Math.hypot(velocity.x(),velocity.z())<.025;
        if(arrived)return new Input(true,false,false,false,false,false,false);
        // Same desired-velocity/error steering as ordinary WaterTravelController's final leg,
        // with a tighter terminal pose; all firing still requires current native flight feasibility.
        double wx=dx*.16,wz=dz*.16,n=Math.hypot(wx,wz),limit=water?.12:.18;
        if(n>limit){wx*=limit/n;wz*=limit/n;}
        double angle=Math.toRadians(yaw),ex=wx-velocity.x(),ez=wz-velocity.z();
        double forward=-Math.sin(angle)*ex+Math.cos(angle)*ez,side=Math.cos(angle)*ex+Math.sin(angle)*ez;
        return new Input(false,forward>.015,forward<-.015,side>.015,side<-.015,water&&dy>.15,water&&dy<-.15);
    }
}
