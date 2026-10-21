package dev.veltraph.client.hunting;

import dev.veltraph.client.world.Vec3;
import java.util.UUID;

/** Bounded ordinary aim holding; this policy never authorizes a projectile or outcome. */
public final class HuntingAimHold {
    public enum Decision { HOLD, REAPPROACH, MOVE_BEFORE_HOLD }
    private UUID target;private int hits;private String pose;private double eyeHeight,bestAngle;
    private Vec3 targetOrigin,rejectedPlayer;private long lastTick,progressTick;
    public boolean active(){return target!=null;}
    public void reset(){target=null;rejectedPlayer=null;}
    public void reject(Vec3 player){target=null;rejectedPlayer=player;}
    private static boolean moved(Vec3 a,Vec3 b){return Math.hypot(a.x()-b.x(),a.z()-b.z())>.12||Math.abs(a.y()-b.y())>.15;}
    public boolean candidateAllowed(Vec3 candidate){return rejectedPlayer==null||moved(candidate,rejectedPlayer);}
    public Decision observe(UUID id,int nativeHits,String nativePose,double nativeEyeHeight,Vec3 player,Vec3 nativeTarget,double angle,long nativeTick){
        if(rejectedPlayer!=null){if(!moved(player,rejectedPlayer))return Decision.MOVE_BEFORE_HOLD;rejectedPlayer=null;}
        if(target!=null&&(!target.equals(id)||hits!=nativeHits||!pose.equals(nativePose)||eyeHeight!=nativeEyeHeight||targetOrigin.distanceTo(nativeTarget)>.75)){reject(player);return Decision.REAPPROACH;}
        if(target==null){target=id;hits=nativeHits;pose=nativePose;eyeHeight=nativeEyeHeight;targetOrigin=nativeTarget;bestAngle=angle;lastTick=progressTick=nativeTick;return Decision.HOLD;}
        if(nativeTick<=lastTick)return Decision.HOLD;lastTick=nativeTick;
        if(angle<bestAngle-1e-4){bestAngle=angle;progressTick=nativeTick;}
        // One complete source FIFO window without angular improvement is enough to request
        // another ordinary body pose. This is a control bound, not a change to target/flight time.
        if(nativeTick-progressTick>=40){reject(player);return Decision.REAPPROACH;}
        return Decision.HOLD;
    }
}
