package dev.veltraph.client.combat.ghost;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Additional geometric veto, never a native owner/reach/attack permission. */
public final class GhostReceivedHitEnvelope {
    private GhostReceivedHitEnvelope(){}
    public record Frame(Object player,Object world,Object connection,Object target,java.util.UUID targetUuid,Object job,long cameraEpoch,int tick,int slot,
                        Vec3 eye,Vec3 look,Vec3 currentPosition,Vec3 receivedPosition,Vec3 interpolationPosition,AABB nativeBox,float pickRadius) {}
    public static boolean same(Frame first,Frame current){return first!=null&&current!=null&&first.player==current.player&&first.world==current.world
            &&first.connection==current.connection&&first.target==current.target&&first.job==current.job&&first.equals(current);}
    public static boolean allows(Frame frame){
        if(frame==null||frame.player==null||frame.world==null||frame.connection==null||frame.target==null||frame.targetUuid==null||frame.job==null
                ||!finite(frame.eye)||!finite(frame.look)||!finite(frame.currentPosition)||!finite(frame.receivedPosition)||!finite(frame.interpolationPosition)
                ||!Float.isFinite(frame.pickRadius)||frame.pickRadius<0||!finiteBox(frame.nativeBox)||Math.abs(frame.look.lengthSqr()-1)>.001)return false;
        // A zero codec base with a nonzero native actor pose is an uninitialized/unsupported receipt.
        if(frame.receivedPosition.equals(Vec3.ZERO)&&!frame.currentPosition.equals(Vec3.ZERO))return false;
        var current=frame.nativeBox.inflate(frame.pickRadius);
        var received=current.move(frame.receivedPosition.subtract(frame.currentPosition));
        var endpoint=current.move(frame.interpolationPosition.subtract(frame.currentPosition));
        var common=common(current,received,endpoint);if(common==null)return false;
        return common.contains(frame.eye)||common.clip(frame.eye,frame.eye.add(frame.look.scale(3))).isPresent();
    }
    /** Actual observed motion is an extra rejection operand, never an owner, reach or future server receipt. */
    public record Motion(boolean grounded,Vec3 velocity) {}
    public static boolean sameMotion(Motion first,Motion current){return first!=null&&current!=null&&first.equals(current);}
    /**
     * Agreeing current/received/interpolation poses can all describe the same pre-landing airborne body.
     * Defer that unsupported transition until the native actor actually reports grounded again.
     * For grounded motion, intersect the existing envelope with one observed horizontal displacement;
     * this can only veto an attack. No gravity, latency horizon, landing height or padding is invented.
     */
    public static boolean motionAllows(Frame frame,Motion motion){
        if(!allows(frame)||motion==null||!finite(motion.velocity)||!motion.grounded||motion.velocity.y>0)return false;
        var current=frame.nativeBox.inflate(frame.pickRadius);
        var received=current.move(frame.receivedPosition.subtract(frame.currentPosition));
        var endpoint=current.move(frame.interpolationPosition.subtract(frame.currentPosition));
        var common=common(current,received,endpoint);if(common==null)return false;
        // The observed grounded flag supports suppressing the ordinary negative gravity component.
        // This translated box is a conservative geometric veto, never a predicted actual server pose.
        var observedStep=common.move(motion.velocity.x,0,motion.velocity.z);
        var shared=common(common,observedStep,observedStep);
        return shared!=null&&(shared.contains(frame.eye)||shared.clip(frame.eye,frame.eye.add(frame.look.scale(3))).isPresent());
    }
    private static AABB common(AABB a,AABB b,AABB c){
        double minX=Math.max(a.minX,Math.max(b.minX,c.minX)),minY=Math.max(a.minY,Math.max(b.minY,c.minY)),minZ=Math.max(a.minZ,Math.max(b.minZ,c.minZ));
        double maxX=Math.min(a.maxX,Math.min(b.maxX,c.maxX)),maxY=Math.min(a.maxY,Math.min(b.maxY,c.maxY)),maxZ=Math.min(a.maxZ,Math.min(b.maxZ,c.maxZ));
        return minX<maxX&&minY<maxY&&minZ<maxZ?new AABB(minX,minY,minZ,maxX,maxY,maxZ):null;
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static boolean finiteBox(AABB b){return b!=null&&Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)
            &&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ)&&b.minX<b.maxX&&b.minY<b.maxY&&b.minZ<b.maxZ;}
}
