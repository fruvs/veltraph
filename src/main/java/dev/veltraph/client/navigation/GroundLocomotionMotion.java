package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
/** Explicit conservative supported-motion envelope, not a claim to simulate all native physics. */
public final class GroundLocomotionMotion {
    private GroundLocomotionMotion(){}
    private static boolean finite(double x){return Double.isFinite(x);}
    public static boolean finite(Vec3 p){return p!=null&&finite(p.x())&&finite(p.y())&&finite(p.z());}
    public static boolean speedAttribute(double value,boolean sprint){
        return finite(value)&&value>=.099999&&value<=(sprint?.130001:.100001);
    }
    public static boolean takeoff(Vec3 velocity,double speed,boolean grounded,boolean sprinting){
        return grounded&&!sprinting&&speedAttribute(speed,false)&&finite(velocity)
                &&Math.hypot(velocity.x(),velocity.z())<=.16&&velocity.y()>=-.081&&velocity.y()<=.01;
    }
    public static boolean sprint(Vec3 velocity,double speed){
        return speedAttribute(speed,true)&&finite(velocity)&&Math.hypot(velocity.x(),velocity.z())<=.22
                &&velocity.y()>=-.081&&velocity.y()<=.01;
    }
    public static boolean jumpEndpoints(Vec3 origin,Vec3 target){
        return finite(origin)&&finite(target)&&target.y()-origin.y()>.6&&target.y()-origin.y()<=1.001
                &&origin.horizontalDistanceTo(target)<=1.65;
    }
    public static boolean landingMotion(Vec3 position,Vec3 velocity,Vec3 landing,double speed){
        return finite(position)&&finite(velocity)&&finite(landing)&&speedAttribute(speed,false)
                &&position.distanceTo(landing)<=.65&&Math.abs(position.y()-landing.y())<.08
                &&Math.hypot(velocity.x(),velocity.z())<=.16&&velocity.y()>=-.081&&velocity.y()<=.01;
    }
    public static boolean drop(Vec3 position,Vec3 velocity,Vec3 origin,Vec3 landing,double speed){return drop(position,velocity,origin,landing,speed,true);}
    public static boolean dropCurrent(Vec3 position,Vec3 velocity,Vec3 origin,Vec3 landing,double speed){return drop(position,velocity,origin,landing,speed,false);}
    private static boolean drop(Vec3 position,Vec3 velocity,Vec3 origin,Vec3 landing,double speed,boolean twoSteps){
        if(!finite(position)||!finite(velocity)||!finite(origin)||!finite(landing)||!speedAttribute(speed,false)
                ||Math.hypot(velocity.x(),velocity.z())>.16||velocity.y()<-.65||velocity.y()>.01
                ||origin.y()-landing.y()<=.6||origin.y()-landing.y()>1.001||position.y()>origin.y()+.081||position.y()<landing.y()-.081)return false;
        double dx=landing.x()-origin.x(),dz=landing.z()-origin.z(),length=Math.hypot(dx,dz);
        var source=GroundPathfinder.node(origin);var target=GroundPathfinder.node(landing);
        if(length<.35||length>1.65||Math.abs(source.x()-target.x())+Math.abs(source.z()-target.z())!=1)return false;dx/=length;dz/=length;
        for(int tick:twoSteps?new int[]{0,2}:new int[]{0}){double x=position.x()+velocity.x()*tick-origin.x(),z=position.z()+velocity.z()*tick-origin.z();
            if(x*dx+z*dz<-.35||x*dx+z*dz>length+.35||Math.abs(x*dz-z*dx)>.18)return false;}
        return true;
    }
    public static boolean airborne(Vec3 position,Vec3 velocity,Vec3 takeoff,Vec3 landing,double speed){return airborne(position,velocity,takeoff,landing,speed,true);}
    /** Same exact caps for actual current body; does not grant the original two-step movement input. */
    public static boolean airborneCurrent(Vec3 position,Vec3 velocity,Vec3 takeoff,Vec3 landing,double speed){return airborne(position,velocity,takeoff,landing,speed,false);}
    private static boolean airborne(Vec3 position,Vec3 velocity,Vec3 takeoff,Vec3 landing,double speed,boolean twoSteps){
        if(!finite(position)||!finite(velocity)||!finite(takeoff)||!finite(landing)||!speedAttribute(speed,false)
                ||Math.hypot(velocity.x(),velocity.z())>.16||velocity.y()<-.62||velocity.y()>.421
                ||position.y()<takeoff.y()-.081||position.y()>takeoff.y()+1.26)return false;
        double dx=landing.x()-takeoff.x(),dz=landing.z()-takeoff.z(),length=Math.hypot(dx,dz);
        if(length<=.01||length>1.65)return false;dx/=length;dz/=length;
        // Include two observed velocity steps of momentum. Fresh native geometry must
        // prove this same widened rectangle; no cached prediction grants movement.
        for(int tick:twoSteps?new int[]{0,2}:new int[]{0}){
            double x=position.x()+velocity.x()*tick-takeoff.x(),z=position.z()+velocity.z()*tick-takeoff.z();
            double along=x*dx+z*dz,lateral=Math.abs(x*dz-z*dx);
            if(along<-.10||along>length+.35||lateral>.18)return false;
        }
        return true;
    }
}
