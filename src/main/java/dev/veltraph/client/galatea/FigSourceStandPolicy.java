package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Readable source preparation choice; native movement executes the resulting supported pose. */
public final class FigSourceStandPolicy {
    private FigSourceStandPolicy(){}
    public static Optional<Vec3> stem(FigSourceGeometry geometry,FigSourceGeometry.World world,
                                     BlockPos target,Vec3 nativePlayer,float nativeYaw,boolean wideYawPenalty) {
        return choose(geometry,world,target,nativePlayer,nativeYaw,wideYawPenalty,false);
    }
    public static Optional<Vec3> throwing(FigSourceGeometry geometry,FigSourceGeometry.World world,
                                         BlockPos target,Vec3 nativePlayer,Vec3 nativePlayerEye,float nativeYaw,boolean standingOnScaffold) {
        if(standingOnScaffold)return Optional.of(nativePlayer);
        return choose(geometry,world,target,nativePlayer,nativeYaw,false,true,nativePlayerEye);
    }
    private static Optional<Vec3> choose(FigSourceGeometry geometry,FigSourceGeometry.World world,
                                        BlockPos target,Vec3 nativePlayer,float nativeYaw,boolean wideYawPenalty,boolean throwing) {
        return choose(geometry,world,target,nativePlayer,nativeYaw,wideYawPenalty,throwing,null);
    }
    private static Optional<Vec3> choose(FigSourceGeometry geometry,FigSourceGeometry.World world,
                                        BlockPos target,Vec3 nativePlayer,float nativeYaw,boolean wideYawPenalty,boolean throwing,Vec3 playerEye) {
        Vec3 center=FigSourceGeometry.center(target),best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(BlockPos p:geometry.pads()) {
            if(!world.dryStanding(p))continue;
            Vec3 stand=new Vec3(p.x()+.5,p.y(),p.z()+.5),eye=new Vec3(stand.x(),stand.y()+1.62,stand.z());
            double distance=eye.distanceTo(center);
            if((!throwing&&distance>4)||!world.sourceRay(eye,target,true))continue;
            double extra=throwing?Math.pow(eye.horizontalDistanceTo(playerEye),2)*3:Math.abs(distance-6);
            double score=stand.distanceTo(center)*.5+extra
                    +Math.pow(stand.horizontalDistanceTo(nativePlayer),1.5)
                    +Math.abs(yawDelta(stand,center,nativeYaw))/(wideYawPenalty?60:20);
            if(target.x()==p.x()&&target.z()==p.z())score+=25;
            else if(center.horizontalDistanceTo(stand)<.75)score+=10;
            if(edge(world,p))score+=10;
            if(!levelNeighbors(world,p))score+=5;
            if(score<bestScore){bestScore=score;best=stand;}
        }
        return Optional.ofNullable(best);
    }
    private static double yawDelta(Vec3 from,Vec3 to,float yaw) {
        double wanted=Math.toDegrees(Math.atan2(to.z()-from.z(),to.x()-from.x()))-90;
        return ((wanted-yaw+180)%360+360)%360-180;
    }
    public static boolean edge(FigSourceGeometry.World world,BlockPos p) {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            if(x==0&&z==0)continue;
            boolean empty=true;
            for(int y=-1;y<3;y++)if(!world.passable(p.add(x,-y,z))){empty=false;break;}
            if(empty)return true;
        }
        return false;
    }
    public static boolean levelNeighbors(FigSourceGeometry.World world,BlockPos p) {
        double support=world.standingEye(new Vec3(p.x()+.5,p.y(),p.z()+.5)).y()-1.62;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            if(x==0&&z==0)continue;
            boolean supported=false;
            for(int y=-1;y<=1;y++) {
                BlockPos next=p.add(x,y,z);
                double height=world.standingEye(new Vec3(next.x()+.5,next.y(),next.z()+.5)).y()-1.62;
                if(Math.abs(height-support)<=.5&&world.standing(next)){supported=true;break;}
            }
            if(!supported)return false;
        }
        return true;
    }
}
