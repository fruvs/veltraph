package dev.veltraph.client.combat.grind;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.function.Predicate;

/** Scores swept-clear vanilla input directions, preserving a turn direction until it is obstructed. */
public final class CombatMovement {
    public record Keys(boolean forward,boolean back,boolean left,boolean right) {}
    private int direction=1;private Keys previous=new Keys(false,false,false,false);
    public void reset() { direction=java.util.concurrent.ThreadLocalRandom.current().nextBoolean()?1:-1;previous=new Keys(false,false,false,false); }
    public Keys choose(Vec3 position,Vec3 boss,float yaw,double desiredRadius,boolean retreat,boolean hold,
                       Predicate<Vec3> safe,List<Vec3> otherMobs) {
        return choose(position,boss,yaw,desiredRadius,retreat,hold,safe,otherMobs,new Vec3(0,0,0));
    }
    public Keys choose(Vec3 position,Vec3 boss,float yaw,double desiredRadius,boolean retreat,boolean hold,
                       Predicate<Vec3> safe,List<Vec3> otherMobs,Vec3 velocity) {
        if(hold) return previous=new Keys(false,false,false,false);
        double dx=position.x()-boss.x(),dz=position.z()-boss.z(),distance=Math.max(.01,Math.hypot(dx,dz));
        double radial=Math.max(-1,Math.min(1,(desiredRadius-distance)*1.6));
        double tangent=retreat?0:distance>desiredRadius+1? .12:.65;
        double wantX=dx/distance*radial-dz/distance*tangent*direction;
        double wantZ=dz/distance*radial+dx/distance*tangent*direction;
        boolean precise=retreat&&desiredRadius<.5;
        if(precise) {wantX-=velocity.x()*4;wantZ-=velocity.z()*4;}
        double lookahead=precise?.35:.8;
        double rad=Math.toRadians(yaw),fx=-Math.sin(rad),fz=Math.cos(rad),lx=Math.cos(rad),lz=Math.sin(rad);
        Keys best=new Keys(false,false,false,false);double score=-Double.MAX_VALUE;
        for(int forward=-1;forward<=1;forward++) for(int left=-1;left<=1;left++) {
            if(forward==0&&left==0) continue;
            double length=Math.hypot(forward,left),x=(forward*fx+left*lx)/length,z=(forward*fz+left*lz)/length;
            Vec3 next=new Vec3(position.x()+x*lookahead,position.y(),position.z()+z*lookahead);
            if(!safe.test(next))continue;
            double value=x*wantX+z*wantZ;
            for(Vec3 mob:otherMobs) {
                value-=Math.max(0,1.6-next.horizontalDistanceTo(mob))*.8;
                if(!retreat)value-=occlusion(next,boss,mob);
            }
            Keys keys=new Keys(forward>0,forward<0,left>0,left<0);
            if(keys.equals(previous)) value+=.18; // suppress left/right thrashing on small target updates
            if(value>score) { score=value;best=keys; }
        }
        previous=best;return best;
    }
    /** Prefer a side that exposes the selected hit volume instead of staring through a closer mob. */
    private static double occlusion(Vec3 eye,Vec3 target,Vec3 mob) {
        double dx=target.x()-eye.x(),dz=target.z()-eye.z(),squared=dx*dx+dz*dz;
        if(squared<.01)return 0;
        double t=((mob.x()-eye.x())*dx+(mob.z()-eye.z())*dz)/squared;
        if(t<=.05||t>=.95)return 0;
        double distance=Math.hypot(mob.x()-(eye.x()+dx*t),mob.z()-(eye.z()+dz*t));
        return Math.max(0,.8-distance)*2.5;
    }
}
