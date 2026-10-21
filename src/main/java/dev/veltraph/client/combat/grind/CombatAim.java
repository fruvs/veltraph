package dev.veltraph.client.combat.grind;

import dev.veltraph.client.navigation.TravelHeading;
import dev.veltraph.client.navigation.WalkController;

/** A soft angular window: correct only the excess error, preserving a valid off-center view. */
public final class CombatAim {
    public record Angles(float yaw,float pitch) {}
    private final TravelHeading yawMotion=new TravelHeading(),pitchMotion=new TravelHeading();
    private boolean observed;private float lastYaw,lastPitch;
    public void reset() { yawMotion.reset();pitchMotion.reset();observed=false; }

    public Angles step(float yaw,float pitch,float targetYaw,float targetPitch,double yawWindow,double pitchWindow,double speed) {
        float angularYaw=observed?WalkController.wrap(targetYaw-lastYaw):0,angularPitch=observed?targetPitch-lastPitch:0;
        lastYaw=targetYaw;lastPitch=targetPitch;observed=true;
        // Feed-forward follows angular motion without raising the turn cap or chasing packet-sized jumps.
        float predictedYaw=targetYaw+(float)Math.max(-24,Math.min(24,angularYaw*5));
        float predictedPitch=targetPitch+(float)Math.max(-16,Math.min(16,angularPitch*5));
        float dy=WalkController.wrap(predictedYaw-yaw),dp=predictedPitch-pitch;
        float desiredYaw=yaw+excess(dy,yawWindow),desiredPitch=pitch+excess(dp,pitchWindow);
        return new Angles(yawMotion.step(yaw,desiredYaw,speed),Math.max(-85,Math.min(85,pitchMotion.step(pitch,desiredPitch,speed))));
    }
    private static float excess(float error,double window) {
        return (float)Math.copySign(Math.max(0,Math.abs(error)-Math.max(0,window)),error);
    }
}
