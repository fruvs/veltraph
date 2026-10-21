package dev.veltraph.client.hunting;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;

/** The recovered hunting caller replaces the shared rotating target, with no completion callback. */
public final class HuntingAim {
    private HuntingAim() {}
    public static void request(RotationService rotation,Vec3 eye,Vec3 sourceAim){
        double dx=sourceAim.x()-eye.x(),dy=sourceAim.y()-eye.y(),dz=sourceAim.z()-eye.z();
        rotation.rotateTo((float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),null);
    }
}
