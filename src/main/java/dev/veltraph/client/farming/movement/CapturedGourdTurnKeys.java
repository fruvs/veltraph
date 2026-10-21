package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;

/** Ordinary eight-direction keys during a gradual turn; target geometry stays world-relative. */
public final class CapturedGourdTurnKeys {
    public record Keys(boolean forward, boolean back, boolean left, boolean right,
            double dx, double dz) {}
    private Keys previous;

    public Keys toward(Vec3 from, Vec3 goal, float actualYaw) {
        double tx=goal.x()-from.x(),tz=goal.z()-from.z(),distance=Math.hypot(tx,tz);
        if (!Double.isFinite(distance)||distance<=.10||!Float.isFinite(actualYaw))return null;
        double yaw=Math.toRadians(actualYaw),best=-2;
        Keys result=null;
        for(int f=-1;f<=1;f++)for(int side=-1;side<=1;side++) {
            if(f==0&&side==0)continue;
            double dx=side*Math.cos(yaw)-f*Math.sin(yaw);
            double dz=f*Math.cos(yaw)+side*Math.sin(yaw);
            double norm=Math.hypot(dx,dz);dx/=norm;dz/=norm;
            double score=(dx*tx+dz*tz)/distance;
            boolean same=previous!=null&&previous.forward()==(f>0)&&previous.back()==(f<0)
                    &&previous.left()==(side>0)&&previous.right()==(side<0);
            if(same)score+=.04; // Hysteresis keeps neighboring key sectors from fluttering.
            if(score>best){best=score;result=new Keys(f>0,f<0,side>0,side<0,dx,dz);}
        }
        previous=result;
        return result;
    }

    public void reset(){previous=null;}
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"previous",previous);
    }
}
