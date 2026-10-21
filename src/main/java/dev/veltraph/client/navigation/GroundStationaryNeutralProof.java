package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure whole-horizon stationary classification. No descriptor grants support, collision,
 * native identity, profile expiry, input ownership, publication, time budget or movement authority.
 * The native producer must prove all unique intended moves against fresh supported geometry. */
public final class GroundStationaryNeutralProof {
    private GroundStationaryNeutralProof() {}
    public static final class Descriptor {
        private final CapturedProfileTrajectory.Trace trace;
        private final GroundProfileForecast.Brake brake;
        private final List<Vec3> moves;
        private Descriptor(CapturedProfileTrajectory.Trace trace, GroundProfileForecast.Brake brake, List<Vec3> moves) {
            this.trace=trace;this.brake=brake;this.moves=List.copyOf(moves);
        }
        public Vec3 origin(){return trace.origin();}
        public Vec3 observedVelocity(){return trace.observedVelocity();}
        public GroundNativePhysicsProfile.State profileState(){return trace.profileState();}
        public float friction(){return trace.friction();}
        public float yaw(){return trace.yaw();}
        public int traceFrames(){return trace.samples().size();}
        public int brakeFrames(){return brake.samples().size();}
        public int maxHorizon(){return Math.max(traceFrames(),brakeFrames());}
        /** Exact intended collision displacements, preserving distinct first/steady Y values. */
        public List<Vec3> collisionMoves(){return moves;}
    }
    /** Original S33-compatible classifier entry. No captured native authority is inferred. */
    public static Optional<Descriptor> describe(CapturedProfileTrajectory.Trace trace, GroundProfileForecast.Brake brake) {
        if(trace==null||trace.profileState()==null)return Optional.empty();
        return describe(GroundNativePhysicsProfile.classify(trace.profileState()).orElse(null),trace,brake);
    }
    /** Uses an already constructed math profile without selecting or widening a profile family.
     * A native caller must supply its genuine original Context.profile and separately retain
     * Journey custody. This public arithmetic value does not establish native authority. */
    public static Optional<Descriptor> describe(GroundNativePhysicsProfile originalMathProfile,
            CapturedProfileTrajectory.Trace trace, GroundProfileForecast.Brake brake) {
        if(trace==null||brake==null||trace.samples().isEmpty()||trace.samples().size()>64
                ||brake.samples().isEmpty()||brake.samples().size()>64
                ||!GroundLocomotionMotion.finite(trace.origin())||!nonpositiveVertical(trace.observedVelocity())
                ||!trace.origin().equals(brake.origin())||!trace.origin().equals(brake.asymptoticCoast())
                ||!trace.observedVelocity().equals(brake.observedVelocity())
                ||trace.profileState()==null||!trace.profileState().equals(brake.profileState())
                ||!Float.isFinite(trace.yaw())||Float.floatToIntBits(trace.friction())!=Float.floatToIntBits(brake.blockFriction()))
            return Optional.empty();
        var profile=originalMathProfile;
        if(profile==null||!profile.state().equals(trace.profileState())||!profile.state().equals(brake.profileState())
                ||profile.sprinting()||!Float.isFinite(profile.groundAcceleration(trace.friction())))return Optional.empty();
        var moves=new ArrayList<Vec3>(2);var velocity=trace.observedVelocity();
        for(var sample:trace.samples()){
            if(sample==null||!trace.origin().equals(sample.position())||!nonpositiveVertical(sample.velocity())
                    ||!nonpositiveVertical(sample.intendedMove())||!zero(sample.acceleration())
                    ||sample.input()!=CapturedProfileTrajectory.Input.NEUTRAL
                    ||sample.phase()!=CapturedProfileTrajectory.Phase.SUPPORTED_GROUND
                    ||sample.condition()!=CapturedProfileTrajectory.Condition.FRESH_FLAT_SUPPORT_CLAMP
                    ||sample.controls()==null||sample.controls().input()!=CapturedProfileTrajectory.Input.NEUTRAL
                    ||sample.controls().jump()!=CapturedProfileTrajectory.JumpIntent.RELEASE
                    ||sample.controls().sprint()!=CapturedProfileTrajectory.SprintIntent.RELEASE)return Optional.empty();
            var expected=CapturedProfileTrajectory.frame(profile,trace.origin(),velocity,trace.friction(),trace.yaw(),
                    CapturedProfileTrajectory.Input.NEUTRAL,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,
                    CapturedProfileTrajectory.JumpIntent.RELEASE,CapturedProfileTrajectory.SprintIntent.RELEASE).orElse(null);
            if(expected==null||!sample.position().equals(expected.position())||!sample.velocity().equals(expected.velocity())
                    ||!sample.intendedMove().equals(expected.intendedMove())||!sample.acceleration().equals(expected.acceleration()))
                return Optional.empty();
            if(!moves.contains(sample.intendedMove()))moves.add(sample.intendedMove());
            velocity=sample.velocity();
        }
        // Brake is independently constructed from the SAME original observation, not trace end velocity.
        // Its private neutral constructor has no yaw/control fields; verify every actual brake field.
        var expectedBrake=GroundProfileForecast.groundBrake(profile,trace.origin(),trace.observedVelocity(),
                trace.friction(),brake.samples().size()).orElse(null);
        if(expectedBrake==null||!brake.asymptoticCoast().equals(expectedBrake.asymptoticCoast()))return Optional.empty();
        for(int i=0;i<brake.samples().size();i++){
            var sample=brake.samples().get(i);var expected=expectedBrake.samples().get(i);
            if(sample==null||sample.nativeStep()!=i+1||!trace.origin().equals(sample.position())
                    ||!zero(sample.velocity())||!sample.equals(expected))return Optional.empty();
        }
        return Optional.of(new Descriptor(trace,brake,moves));
    }
    private static boolean positiveZero(double v){return Double.doubleToLongBits(v)==Double.doubleToLongBits(0D);}
    private static boolean nonpositiveVertical(Vec3 v){return GroundLocomotionMotion.finite(v)
            &&positiveZero(v.x())&&positiveZero(v.z())&&v.y()<=0D;}
    private static boolean zero(Vec3 v){return GroundLocomotionMotion.finite(v)
            &&positiveZero(v.x())&&positiveZero(v.y())&&positiveZero(v.z());}
}
