package dev.veltraph.client.combat.grind;

import dev.veltraph.client.navigation.WalkController;

/** One bounded camera frame shared across a quest's combat, pickup and menu handoffs. */
public final class CombatCamera {
    public record Angles(float yaw,float pitch) {}
    private final NativeAxis yaw=new NativeAxis(),pitch=new NativeAxis();
    private long appliedTick=Long.MIN_VALUE;
    private Angles applied;
    public void reset(){yaw.reset();pitch.reset();appliedTick=Long.MIN_VALUE;applied=null;}
    public Angles apply(long tick,float currentYaw,float currentPitch,float requestedYaw,float requestedPitch,double speed,double pitchSpeed){
        if(appliedTick==tick)return applied;
        applied=new Angles(yaw.apply(currentYaw,requestedYaw,speed),Math.max(-85,Math.min(85,pitch.apply(currentPitch,requestedPitch,pitchSpeed))));
        appliedTick=tick;return applied;
    }

    /** Rate limits the actual float pose, including rounding after a long accumulated turn. */
    private static final class NativeAxis {
        private double velocity;
        void reset(){velocity=0;}
        float apply(float current,float requested,double speed){
            double cap=6*Math.max(.25,Math.min(2,Double.isFinite(speed)?speed:1));
            double wanted=Math.max(-cap,Math.min(cap,WalkController.wrap(requested-current)));
            double low=Math.max(velocity-1.2,Math.min(-cap,velocity));
            double high=Math.min(velocity+1.2,Math.max(cap,velocity));
            double desired=Math.max(low,Math.min(high,wanted));
            float next=(float)(current+desired);
            // Float addition can round outward even when the intended rate is exactly legal.
            // Measure the pose the player receives and move it one representable step inward.
            double actual=(double)next-current;
            if(actual>high)next=Math.nextDown(next);
            else if(actual<low)next=Math.nextUp(next);
            velocity=(double)next-current;
            return next;
        }
    }
}
