package dev.veltraph.client.rewarp;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.rotation.SourceRotationStepper;
/** Separate pause-settle pose; the immutable original final-return pose is never replaced. */
public final class FarmPreparationPose {
    public record Observation(Object player,Object world,Object connection,long teleportSerial,Vec3 position,
            float yaw,float pitch,Object posture,boolean grounded,boolean supported,double horizontalMotion,boolean controlsUnchanged){}
    private final Object token,player,world,connection,posture;private final long serial;private final Vec3 originalReturn;
    private final float yaw,pitch;private Vec3 settled;
    public FarmPreparationPose(Object token,Observation original){this(token,original.position(),original);}
    public FarmPreparationPose(Object token,Vec3 originalReturn,Observation original){
        this.token=java.util.Objects.requireNonNull(token);player=java.util.Objects.requireNonNull(original.player());
        world=java.util.Objects.requireNonNull(original.world());connection=java.util.Objects.requireNonNull(original.connection());
        posture=java.util.Objects.requireNonNull(original.posture());serial=original.teleportSerial();this.originalReturn=java.util.Objects.requireNonNull(originalReturn);yaw=original.yaw();pitch=original.pitch();
    }
    public boolean valid(Object token,Observation o){return this.token==token&&o!=null&&player==o.player()&&world==o.world()
            &&connection==o.connection()&&serial==o.teleportSerial()&&posture==o.posture()&&o.controlsUnchanged()
            &&finite(o.position())&&Math.abs(SourceRotationStepper.wrap(o.yaw()-yaw))<=.001&&Math.abs(o.pitch()-pitch)<=.001
            &&(settled==null||settled.distanceTo(o.position())<=.001&&o.grounded()&&o.horizontalMotion()<=.02);}
    public boolean captureSettled(Object token,Observation o,boolean acknowledgmentsDrained){
        if(settled!=null)return valid(token,o);
        if(!valid(token,o)||!acknowledgmentsDrained||!o.grounded()||!o.supported()||!(o.horizontalMotion()<=.02))return false;
        settled=o.position();return true;
    }
    public Vec3 originalReturn(){return originalReturn;}
    public Vec3 settled(){return settled;}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
}
