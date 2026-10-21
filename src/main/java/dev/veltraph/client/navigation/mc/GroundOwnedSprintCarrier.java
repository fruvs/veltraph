package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import java.util.List;
/** Observations confer no input authority. Only private actual LocalPlayer mixin identity is admissible. */
public interface GroundOwnedSprintCarrier {
    boolean expectOwnedSprintFrame(LegacyTravelPublicationLease.Leg.ProfileInputFrame frame);
    Observation ownedSprintObservation();
    boolean ownsSprintObservation(Observation exact);
    record SpeedState(double base,double effective,float field,List<GroundNativePhysicsProfile.Modifier> modifiers,boolean sprinting){
        public SpeedState{modifiers=List.copyOf(modifiers);}}
    record Observation(LegacyTravelPublicationLease.Leg.ProfileInputFrame frame,int nativeTick,
            GroundPhysicsNativeRevisions.Token beforePlayer,GroundPhysicsNativeRevisions.Token afterPlayer,
            GroundPhysicsNativeRevisions.Token beforeMovement,GroundPhysicsNativeRevisions.Token afterMovement,
            SpeedState before,SpeedState after,float travelSpeed,boolean nativeSprintArgument,int sprintCalls,
            Object exactNativeSprintModifier,Object exactNativeSprintIdentifier,
            byte beforeFlags,byte afterFlags,byte nativeSprintFlags,Object exactSharedFlagsAccessor,Object exactSharedFlagsDataItem) {}
}
