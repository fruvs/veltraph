package dev.veltraph.client.combat.grind;

import dev.veltraph.client.world.Vec3;

/** Bounded moving-target lead and low-arc gravity compensation for ordinary full-charge arrows. */
public final class RangedBallistics {
    private RangedBallistics() {}
    public static Vec3 aim(Vec3 eye,Vec3 center,Vec3 velocity,boolean shortbow) {
        double speed=shortbow?3.2:3.0;
        double flight=Math.min(14,eye.horizontalDistanceTo(center)*(shortbow?.3:1/speed));
        double vx=Math.max(-.4,Math.min(.4,velocity.x())),vz=Math.max(-.4,Math.min(.4,velocity.z()));
        double x=center.x()+vx*flight,z=center.z()+vz*flight,horizontal=Math.hypot(x-eye.x(),z-eye.z());
        double height=center.y()-eye.y(),gravity=shortbow?.006:.05,squared=speed*speed;
        double discriminant=squared*squared-gravity*(gravity*horizontal*horizontal+2*height*squared);
        if(horizontal<.01||discriminant<0)return new Vec3(x,center.y(),z);
        double slope=(squared-Math.sqrt(discriminant))/(gravity*horizontal);
        return new Vec3(x,eye.y()+slope*horizontal,z);
    }
    /** Continue beyond the intended target: a miss remains a live projectile until terrain stops it. */
    public static java.util.List<Vec3> flight(Vec3 eye,float yaw,float pitch,Vec3 inheritedVelocity,boolean shortbow){
        var path=new java.util.ArrayList<Vec3>();path.add(eye);double speed=shortbow?3.2:3;
        double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch),vx=-Math.sin(yr)*Math.cos(pr)*speed+inheritedVelocity.x(),
                vy=-Math.sin(pr)*speed+inheritedVelocity.y(),vz=Math.cos(yr)*Math.cos(pr)*speed+inheritedVelocity.z();
        Vec3 position=eye;for(int tick=0;tick<26;tick++){position=new Vec3(position.x()+vx,position.y()+vy,position.z()+vz);path.add(position);vx*=.99;vz*=.99;vy=vy*.99-(shortbow?.006:.05);}return java.util.List.copyOf(path);
    }
}
