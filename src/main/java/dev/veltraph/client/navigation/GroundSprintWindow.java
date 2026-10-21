package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Optional;
/** Select an admitted braking-distance hint; never extend a route or relax native sprint gates. */
public final class GroundSprintWindow {
    private GroundSprintWindow(){}
    public static Optional<Vec3> select(Vec3 actual,float yaw,List<Vec3> candidates){
        if(!GroundLocomotionMotion.finite(actual)||!Float.isFinite(yaw)||candidates==null||candidates.size()>33)return Optional.empty();
        for(Vec3 point:candidates){
            if(!GroundLocomotionMotion.finite(point)||Math.abs(point.y()-actual.y())>=.05)continue;
            double distance=actual.horizontalDistanceTo(point);if(distance<3)continue;
            float desired=(float)Math.toDegrees(Math.atan2(-(point.x()-actual.x()),point.z()-actual.z()));
            if(Math.abs(WalkController.wrap(desired-yaw))<8)return Optional.of(point);
        }
        return Optional.empty();
    }
}
