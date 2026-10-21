package dev.veltraph.client.navigation.mc;

/** Actual native method observations in the real LocalPlayer's private mixin state.
 * No method publishes input or changes a native flag/pose/tick. */
public interface GroundProfileMotionCarrier {
    boolean expectProfileFrame(LegacyTravelPublicationLease.Leg.ProfileInputFrame actualFrame);
    default LegacyTravelPublicationLease.Leg.ProfileInputFrame expectedProfileFrame(){return null;}
    Object profileNativeBaseline();
    ObservedStep profileNativeAfter(Object exactBaseline);
    boolean ownsProfileStep(ObservedStep exact);
    record NativePose(dev.veltraph.client.world.Vec3 position,dev.veltraph.client.world.Vec3 velocity,
            float yaw,boolean onGround,boolean sprinting,int nativeTick,float travelSpeed) {}
    final class ObservedStep {
        private final Object actualIdentity;private final LegacyTravelPublicationLease.Leg.ProfileInputFrame inputs;
        private final NativePose before,after;private final dev.veltraph.client.world.Vec3 travelInput;
        private final int actualJumpCalls;
        public ObservedStep(Object identity,LegacyTravelPublicationLease.Leg.ProfileInputFrame inputs,
                NativePose before,NativePose after,dev.veltraph.client.world.Vec3 travelInput,int jumps){
            actualIdentity=identity;this.inputs=inputs;this.before=before;this.after=after;this.travelInput=travelInput;actualJumpCalls=jumps;
        }
        public Object nativeIdentity(){return actualIdentity;}
        public LegacyTravelPublicationLease.Leg.ProfileInputFrame issuedInputs(){return inputs;}
        public NativePose before(){return before;}public NativePose after(){return after;}
        public dev.veltraph.client.world.Vec3 travelInput(){return travelInput;}
        public int actualJumpCalls(){return actualJumpCalls;}
    }
}
