package dev.veltraph.client.scheduling;

import dev.veltraph.client.world.Vec3;
import java.util.Objects;
import java.util.UUID;

/** Read-only captured arrival policy. No engine, readiness, recovery, or input callbacks. */
public final class ScheduledArrivalProof {
    private ScheduledArrivalProof(){}
    public record Metadata(String profile,String settings,String schedule){}
    public record NativeContext(Object player,Object world,Object connection,UUID playerId,String dimension,Object area){}
    public record Pose(Vec3 position,float yaw,float pitch,Object posture,int slot){}
    public record Saved(Object owner,Object ticket,Metadata metadata,NativeContext departure,Pose pose){}
    public record Observation(Metadata metadata,NativeContext context,Pose pose,boolean authorizedPhase,
                              boolean ownerEnabled,boolean matchingTarget,boolean healthyKnownArea,
                              boolean closedMenu,boolean protectedLoadout,boolean geometryLoaded,
                              boolean groundedStill,boolean recoveryReady,boolean settled,boolean exclusiveOwner){}

    public static boolean permits(Saved saved,NativeContext joined,Observation now,Object owner,Object ticket){
        if(saved==null||joined==null||now==null||owner==null||ticket==null
                ||owner!=saved.owner()||ticket!=saved.ticket()||!now.authorizedPhase()||!now.ownerEnabled()
                ||!now.matchingTarget()||!now.healthyKnownArea()||!now.closedMenu()||!now.protectedLoadout()
                ||!now.geometryLoaded()||!now.groundedStill()||!now.recoveryReady()||!now.settled()||!now.exclusiveOwner()
                ||!Objects.equals(saved.metadata(),now.metadata()))return false;
        var original=saved.departure();var nativeNow=now.context();var expected=saved.pose();var pose=now.pose();
        if(original==null||nativeNow==null||expected==null||pose==null||pose.position()==null||expected.position()==null)return false;
        return nativeNow.player()!=null&&nativeNow.world()!=null&&nativeNow.connection()!=null
                &&nativeNow.player()==joined.player()&&nativeNow.world()==joined.world()&&nativeNow.connection()==joined.connection()
                &&nativeNow.player()!=original.player()&&nativeNow.world()!=original.world()&&nativeNow.connection()!=original.connection()
                &&Objects.equals(nativeNow.playerId(),original.playerId())&&original.playerId()!=null
                &&Objects.equals(nativeNow.dimension(),original.dimension())&&Objects.equals(nativeNow.area(),original.area())
                &&pose.position().distanceTo(expected.position())<.35&&angle(pose.yaw(),expected.yaw())<1.5
                &&Math.abs(pose.pitch()-expected.pitch())<1.5&&Objects.equals(pose.posture(),expected.posture())&&pose.slot()==expected.slot();
    }
    private static float angle(float a,float b){float d=(a-b)%360;return Math.abs(d>180?d-360:d< -180?d+360:d);}
}
