package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;

/** Current-view ray against the entire reachable pick volume, without requiring its center. */
public final class CombatHitEnvelope {
    private CombatHitEnvelope() {}
    public static boolean intersects(Vec3 eye,Vec3 direction,Vec3 minimum,Vec3 maximum,double reach){
        double[] origin={eye.x(),eye.y(),eye.z()},ray={direction.x(),direction.y(),direction.z()},
                low={minimum.x(),minimum.y(),minimum.z()},high={maximum.x(),maximum.y(),maximum.z()};
        if(!Double.isFinite(reach)||reach<=0)return false;
        double length=Math.sqrt(ray[0]*ray[0]+ray[1]*ray[1]+ray[2]*ray[2]);
        if(!Double.isFinite(length)||length<1e-12)return false;
        double near=0,far=reach;
        for(int axis=0;axis<3;axis++){
            if(!Double.isFinite(origin[axis])||!Double.isFinite(low[axis])||!Double.isFinite(high[axis])||low[axis]>high[axis])return false;
            double step=ray[axis]/length;
            if(Math.abs(step)<1e-12){if(origin[axis]<low[axis]||origin[axis]>high[axis])return false;continue;}
            double a=(low[axis]-origin[axis])/step,b=(high[axis]-origin[axis])/step;
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
            if(near>far+1e-9)return false;
        }
        return true;
    }
    /** Narrows authority across native update cadence, for stationary and continuing packet eyes. */
    public static boolean observedCadence(Vec3 eye,Vec3 direction,Vec3 minimum,Vec3 maximum,Vec3 targetMotion,Vec3 playerMotion,int ticks,double reach){
        if(ticks<1||ticks>20)return false;
        for(int tick=0;tick<=ticks;tick++){
            var low=new Vec3(minimum.x()+targetMotion.x()*tick,minimum.y()+targetMotion.y()*tick,minimum.z()+targetMotion.z()*tick);
            var high=new Vec3(maximum.x()+targetMotion.x()*tick,maximum.y()+targetMotion.y()*tick,maximum.z()+targetMotion.z()*tick);
            var movingEye=new Vec3(eye.x()+playerMotion.x()*tick,eye.y()+playerMotion.y()*tick,eye.z()+playerMotion.z()*tick);
            if(!intersects(eye,direction,low,high,reach)||!intersects(movingEye,direction,low,high,reach))return false;
        }
        return true;
    }
}
