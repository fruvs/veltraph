package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;
import net.minecraft.world.phys.AABB;

/** Pure geometric rejection rules only. A true result cannot create a native stay receipt. */
public final class GhostMeleeForecastMath {
    private GhostMeleeForecastMath(){}
    public static boolean allows(Vec3 actor,Vec3 target,Vec3 actorVelocity,Vec3 targetIntent,double radius,
                                 net.minecraft.world.phys.Vec3 eye,net.minecraft.world.phys.Vec3 look,AABB targetBox){
        if(!finite(actor)||!finite(target)||!finite(actorVelocity)||!finite(targetIntent)||eye==null||look==null||targetBox==null
                ||!Double.isFinite(eye.x)||!Double.isFinite(eye.y)||!Double.isFinite(eye.z)||!Double.isFinite(look.x)||!Double.isFinite(look.y)||!Double.isFinite(look.z)
                ||!Double.isFinite(radius)||radius<1||radius>3||Math.abs(look.lengthSqr()-1)>.001)return false;
        if(!Double.isFinite(targetBox.minX)||!Double.isFinite(targetBox.minY)||!Double.isFinite(targetBox.minZ)||!Double.isFinite(targetBox.maxX)||!Double.isFinite(targetBox.maxY)||!Double.isFinite(targetBox.maxZ))return false;
        if(targetIntent.y()!=0||actorVelocity.y()!=0||Math.hypot(targetIntent.x(),targetIntent.z())>.65||Math.hypot(actorVelocity.x(),actorVelocity.z())>.35)return false;
        var actorNext=new Vec3(actor.x()+actorVelocity.x(),actor.y(),actor.z()+actorVelocity.z());var targetNext=new Vec3(target.x()+targetIntent.x(),target.y()+targetIntent.y(),target.z()+targetIntent.z());
        if(Math.abs(actorNext.horizontalDistanceTo(targetNext)-radius)>.60)return false;
        var current=targetBox.clip(eye,eye.add(look.scale(3)));if(current.isEmpty()||current.get().distanceTo(eye)>3)return false;
        var futureEye=eye.add(actorVelocity.x(),0,actorVelocity.z());var futureBox=targetBox.move(targetIntent.x(),targetIntent.y(),targetIntent.z());
        var future=futureBox.clip(futureEye,futureEye.add(look.scale(3)));return future.isPresent()&&future.get().distanceTo(futureEye)<=3;
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
}
