package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;

/** Active captured-profile controller. Only the existing guarded publisher applies Decisions.
 * Native movement is observed through the real player's private mixin carriers. This class never
 * writes a player pose, velocity, sprint flag, ground flag or tick, and never pumps a native tick.
 */
public final class McCapturedProfileLocomotion {
    public enum Phase { GROUND, TAKEOFF, AIRBORNE, DROP_EXIT, DROPPING, LANDING_BRAKE, RETIRED }
    public static final class Decision {
        private final Controls controls;private final float yaw;private final WalkController.State state;
        private final String reason;private final boolean blocked;
        private final CapturedProfileTrajectory.Phase nativePhase;
        private final McCapturedProfileGeometry.Context before;private final Trace trace;
        private CapturedProfileGroundYawEnvelope.Envelope groundYawEnvelope;
        private Decision(Controls controls,float yaw,WalkController.State state,String reason,boolean blocked,
                CapturedProfileTrajectory.Phase nativePhase,McCapturedProfileGeometry.Context before,Trace trace) {
            this.controls=controls;this.yaw=yaw;this.state=state;this.reason=reason;this.blocked=blocked;
            this.nativePhase=nativePhase;this.before=before;this.trace=trace;
        }
        public Controls controls(){return controls;}public float yaw(){return yaw;}
        public WalkController.State state(){return state;}public String reason(){return reason;}
        public boolean blocked(){return blocked;}
    }
    private record Pending(Decision decision,LegacyTravelPublicationLease.Leg.ProfileInputFrame frame,
            Object baseline,McCapturedProfileGeometry.Context before,Sample expected) {}
    private static final List<LegacyTravelRetirement.Key> KEYS=List.of(LegacyTravelRetirement.Key.FORWARD,
            LegacyTravelRetirement.Key.BACK,LegacyTravelRetirement.Key.LEFT,LegacyTravelRetirement.Key.RIGHT,
            LegacyTravelRetirement.Key.JUMP,LegacyTravelRetirement.Key.SNEAK,LegacyTravelRetirement.Key.SPRINT);
    private final Minecraft client;private final LeaseGroundAdapter actor;
    private final McGroundNativePhysicsProfile.Journey journey;private final LocalPlayer owner;
    private final boolean directional;private final boolean directionalVanilla;
    private boolean initialPlaneAttempted,initialPlaneReady;
    private McCapturedProfileGeometry.InitialDirectionalPlaneKind initialPlaneKind;
    private java.util.List<Vec3> initialSteeringCourse=java.util.List.of();private Vec3 directionalSteering;private Input directionalPrevious=Input.NEUTRAL;
    private Phase phase=Phase.GROUND;private Decision staged;private Pending pending;
    private CapturedProfilePulseFlightPlan.Plan jumpPlan;private int jumpStep;
    private Vec3 flightOrigin,flightLanding,dropOriginal;
    private GroundProfileMotionCarrier.ObservedStep dropLatch;private Vec3 latchOriginal,latchLanding;private float flightYaw;
    private boolean neutralReplan,replanAir;private long lastFrameSequence;
    private int acceptedPhysics,acceptedJumps,acceptedContacts;
    private WalkController originalWalker;
    private double terminalRadius=.35D;private boolean terminalConfigured;
    private long episodeAt,terminalBrakeAt;
    private GroundPublicationFailureSnapshot originalAfterPublicationFailure;
    public GroundPublicationFailureSnapshot cachedOriginalAfterPublicationFailure(){return originalAfterPublicationFailure;}
    private String retirement="";private CapturedProfileDiagnostics diagnostics=CapturedProfileDiagnostics.inactive();
    public McCapturedProfileLocomotion(Minecraft client,LeaseGroundAdapter actor,McGroundNativePhysicsProfile.Journey journey) {
        this.client=java.util.Objects.requireNonNull(client);this.actor=java.util.Objects.requireNonNull(actor);
        this.journey=java.util.Objects.requireNonNull(journey);this.owner=client.player;this.directional=false;this.directionalVanilla=false;
        if(!canonicalPlayer()||context().isEmpty())stopWith("Captured native profile/carriers unavailable");
        cache();
    }
    /** Only the explicit directional factory sets this immutable mode; ordinary captures remain exact. */
    private McCapturedProfileLocomotion(Minecraft client,LeaseGroundAdapter actor,McGroundNativePhysicsProfile.Journey journey,boolean directional){
        this.client=java.util.Objects.requireNonNull(client);this.actor=java.util.Objects.requireNonNull(actor);
        this.journey=java.util.Objects.requireNonNull(journey);this.owner=client.player;this.directional=directional;
        var captured=context();this.directionalVanilla=captured.isPresent()
                &&GroundOwnedSprintTransition.directionalVanillaProfileSupported(captured.orElseThrow().profile().state());
        if(!canonicalPlayer()||captured.isEmpty())stopWith("Directional native profile/carriers unavailable");
        cache();
    }
    /** GENERAL dynamic transit only: the navigator calls this distinct factory after its authentic
     * route/config/source proof. Exact/crop callers continue to use the original captureQualified.
     * Unknown initial profiles return the original unsupported result. A supported profile whose
     * real Journey capture fails is CUSTODY_REFUSED and may never fall through to walking.
     */
    public static Admission captureDirectionalQualified(Minecraft client,LeaseGroundAdapter actor){
        var observed=McGroundNativePhysicsProfile.captureDirectionalQualified(client,actor);
        if(observed.kind()!=McGroundNativePhysicsProfile.AdmissionKind.CAPTURED)return new Admission(observed.kind(),null);
        var controller=new McCapturedProfileLocomotion(client,actor,observed.journey().orElseThrow(),true);
        return controller.phase==Phase.RETIRED?new Admission(McGroundNativePhysicsProfile.AdmissionKind.CUSTODY_REFUSED,null)
                :new Admission(McGroundNativePhysicsProfile.AdmissionKind.CAPTURED,controller);
    }
    public static Optional<McCapturedProfileLocomotion> capture(Minecraft client,LeaseGroundAdapter actor) {
        if(client==null||actor==null)return Optional.empty();
        return McGroundNativePhysicsProfile.capture(client,actor).flatMap(j->{
            var controller=new McCapturedProfileLocomotion(client,actor,j);
            return controller.phase==Phase.RETIRED?Optional.empty():Optional.of(controller);
        });
    }
    public static final class Admission {
        private final McGroundNativePhysicsProfile.AdmissionKind kind;private final McCapturedProfileLocomotion controller;
        private Admission(McGroundNativePhysicsProfile.AdmissionKind kind,McCapturedProfileLocomotion controller){this.kind=kind;this.controller=controller;}
        public McGroundNativePhysicsProfile.AdmissionKind kind(){return kind;}
        public Optional<McCapturedProfileLocomotion> controller(){return Optional.ofNullable(controller);}
    }
    /** Supported capture failure is never converted into a walking exemption or renewed Leg. */
    public static Admission captureQualified(Minecraft client,LeaseGroundAdapter actor){
        var observed=McGroundNativePhysicsProfile.captureCaptured(client,actor);
        if(observed.kind()!=McGroundNativePhysicsProfile.AdmissionKind.CAPTURED)return new Admission(observed.kind(),null);
        var controller=new McCapturedProfileLocomotion(client,actor,observed.journey().orElseThrow());
        return controller.phase==Phase.RETIRED?new Admission(McGroundNativePhysicsProfile.AdmissionKind.CUSTODY_REFUSED,null)
                :new Admission(McGroundNativePhysicsProfile.AdmissionKind.CAPTURED,controller);
    }
    private boolean canonicalPlayer() {
        return client==Minecraft.getInstance()&&client.isSameThread()&&!client.isPaused()&&owner!=null&&client.player==owner
                &&client.getCameraEntity()==owner
                &&owner.getClass()==LocalPlayer.class&&(Object)owner instanceof GroundProfileMotionCarrier
                &&(Object)owner instanceof GroundOwnedSprintCarrier
                &&(Object)owner instanceof dev.veltraph.client.mixin.GroundSprintTimingAccessor
                &&(Object)owner instanceof dev.veltraph.client.mixin.GroundSprintCapabilityAccessor;
    }
    private static boolean captured(GroundNativePhysicsProfile profile) {
        if(profile==null||profile.state().effects().size()!=7||profile.state().jumpAmplifier()!=5)return false;
        long base=Double.doubleToLongBits(profile.state().baseSpeed());
        return base==Double.doubleToLongBits(GroundNativePhysicsProfile.CAPTURED_360)
                ||base==Double.doubleToLongBits(GroundNativePhysicsProfile.CAPTURED_400);
    }
    /** Pure initial-profile forwarding only; context/custody/native validation remains unchanged. */
    private static boolean directionalProfileSupported(GroundNativePhysicsProfile profile){
        return profile!=null&&GroundOwnedSprintTransition.directionalProfileSupported(profile.state());
    }
    private Optional<McCapturedProfileGeometry.Context> context() {
        if(phase==Phase.RETIRED||!canonicalPlayer())return Optional.empty();
        return McCapturedProfileGeometry.current(client,journey).filter(c->directional
                ?directionalProfileSupported(c.profile()):captured(c.profile()));
    }
    private void cache(){diagnostics=new CapturedProfileDiagnostics(phase.name(),acceptedPhysics,acceptedJumps,acceptedContacts,retirement);}
    public CapturedProfileDiagnostics cachedDiagnostics(){return diagnostics;}
    public Phase phase(){return phase;}
    /** Passive checkpoint; no custody callback, native field, world, timer or RNG is read. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"phase",phase,"staged",staged,"pending",pending,
                "jumpPlan",jumpPlan,"jumpStep",jumpStep,"flightOrigin",flightOrigin,"flightLanding",flightLanding,"dropOriginal",dropOriginal,
                "dropLatch",dropLatch,"latchOriginal",latchOriginal,"latchLanding",latchLanding,"flightYaw",flightYaw,"neutralReplan",neutralReplan,"replanAir",replanAir,"lastFrameSequence",lastFrameSequence,
                "directional",directional,"directionalVanilla",directionalVanilla,"initialPlaneAttempted",initialPlaneAttempted,"initialPlaneReady",initialPlaneReady,"initialPlaneKind",initialPlaneKind,"initialSteeringCourse",initialSteeringCourse,
                "directionalSteering",directionalSteering,"directionalPrevious",directionalPrevious,
                "originalWalker",originalWalker,"terminalRadius",terminalRadius,"terminalConfigured",terminalConfigured,"episodeAt",episodeAt,"terminalBrakeAt",terminalBrakeAt,
                "diagnostics",diagnostics,"retirement",retirement);
    }
    /** Bound once by the owning navigator; final reads use this original model, not a Boolean callback. */
    public void bindOriginalWalker(WalkController exact){
        if(originalWalker!=null||exact==null)throw new IllegalArgumentException("Original walker binding");
        originalWalker=exact;
    }
    /** Exact original start tolerance, installed once by the owning navigator; no wider than .35. */
    public void originalTerminalRadius(double radius){
        if(terminalConfigured||!Double.isFinite(radius)||radius<=0D||radius>.35D)throw new IllegalArgumentException("Captured original terminal tolerance");
        terminalRadius=radius;terminalConfigured=true;
    }
    /** Additional live refusal for the exact staged command before the original publisher presses sprint. */
    public boolean sprintPressCurrent(Decision exact){
        return exact!=null&&exact==staged&&!exact.blocked&&exact.controls!=null
                &&exact.controls.sprint()==SprintIntent.PRESS&&CapturedProfileSprintEligibility.mayPress(sprintTraits(),exact.controls.input());
    }
    public boolean requiresNeutralReplan(){return neutralReplan&&phase!=Phase.RETIRED;}
    /** Freezes the original walker until actual native contact and ground braking are reconciled. */
    public WalkController.Output airborneOutput() {
        return phase==Phase.TAKEOFF||phase==Phase.AIRBORNE||phase==Phase.DROP_EXIT||phase==Phase.DROPPING||phase==Phase.LANDING_BRAKE
                ?new WalkController.Output(false,false,flightYaw,WalkController.State.WALKING):null;
    }
    public void stop(){stopWith("Captured controller stopped");}
    private void stopWith(String reason){phase=Phase.RETIRED;retirement=reason;staged=null;pending=null;neutralReplan=false;
        jumpPlan=null;dropLatch=null;latchOriginal=latchLanding=null;journey.retire();cache();}
    private Decision refuse(String reason,float yaw){stopWith(reason);return new Decision(null,yaw,WalkController.State.BLOCKED,reason,true,null,null,null);}
    /** Read-only native traits. This additional refusal never replaces profile/scope/geometry proof. */
    private CapturedProfileSprintEligibility.Traits sprintTraits(){
        if(!canonicalPlayer())return null;
        return new CapturedProfileSprintEligibility.Traits(owner.isAlive(),owner.isSpectator(),owner.isPassenger(),owner.isUsingItem(),
                owner.isScoping(),owner.isInWater(),owner.isInLava(),owner.getAbilities().flying,
                owner.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),owner.getFoodData().getFoodLevel(),
                ((dev.veltraph.client.mixin.GroundSprintCapabilityAccessor)(Object)owner).veltraph$nativeSprintPossible(owner.getAbilities().flying),
                owner.isMovingSlowly(),owner.isFallFlying(),owner.horizontalCollision,owner.minorHorizontalCollision,owner.onGround());
    }
    /** Native-only final clock fence; original begin times and clock floor are never reset here. */
    private boolean finalDeadlineCurrent(){
        GroundPublicationFailureCache.deadlineBegin();
        if(originalWalker==null)return false;
        long wall=System.currentTimeMillis();
        if(!originalWalker.locomotionClockCurrent(wall))return false;
        var state=originalWalker.state();
        if(state==WalkController.State.WALKING&&!originalWalker.locomotionProgressCurrent(wall,4000))return false;
        if(state!=WalkController.State.WALKING&&state!=WalkController.State.ARRIVED)return false;
        long originalDeadlineNanoTime=System.nanoTime();GroundPublicationFailureCache.deadlineNano(originalDeadlineNanoTime);
        return CapturedProfileFinalDeadline.check(originalDeadlineNanoTime,episodeAt,terminalBrakeAt,airborneOutput()!=null)
                ==CapturedProfileFinalDeadline.Result.CURRENT;
    }
    private int sprintTimer(){return ((dev.veltraph.client.mixin.GroundSprintTimingAccessor)(Object)owner).veltraph$actualSprintTriggerTime();}
    private int forwardDelay(){return owner.input.hasForwardImpulse()?0:Math.max(0,sprintTimer()-1);}
    private static double speed(Vec3 v){return Math.hypot(v.x(),v.z());}
    private static float toward(Vec3 from,Vec3 to){return (float)Math.toDegrees(Math.atan2(-(to.x()-from.x()),to.z()-from.z()));}
    private static CapturedProfileTrajectory.Phase air(){return CapturedProfileTrajectory.Phase.OBSERVED_AIRBORNE;}
    private Decision issue(McCapturedProfileGeometry.Context c,Input input,SprintIntent sprint,
            CapturedProfileTrajectory.Phase nativePhase,Trace trace,float cameraYaw,WalkController.State state,String reason) {
        if(sprint==SprintIntent.PRESS&&!CapturedProfileSprintEligibility.mayPress(sprintTraits(),input))sprint=SprintIntent.RELEASE;
        var pose=c.pose();var sample=frame(c.profile(),pose.position(),pose.velocity(),c.friction(),pose.yaw(),input,nativePhase,
                nativePhase==CapturedProfileTrajectory.Phase.GROUNDED_TAKEOFF?JumpIntent.PRESS:JumpIntent.RELEASE,sprint);
        if(sample.isEmpty())return refuse("Captured mathematical phase/control mismatch",cameraYaw);
        staged=new Decision(sample.orElseThrow().controls(),cameraYaw,state,reason,false,nativePhase,c,trace);
        neutralReplan=false;cache();return staged;
    }
    private Optional<Trace> trace(McCapturedProfileGeometry.Context c,Input first,CapturedProfileTrajectory.Phase start,int neutralSteps) {
        var inputs=new ArrayList<Input>();inputs.add(first);for(int i=0;i<neutralSteps;i++)inputs.add(Input.NEUTRAL);
        return forecast(c.profile(),c.pose().position(),c.pose().velocity(),c.friction(),c.pose().yaw(),start,inputs,c.remainingEffectTicks());
    }
    private Decision groundNeutral(McCapturedProfileGeometry.Context c,float cameraYaw,String reason,WalkController.State state) {
        var t=trace(c,Input.NEUTRAL,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,32);
        var brake=GroundProfileForecast.groundBrake(c.profile(),c.pose().position(),c.pose().velocity(),c.friction(),32);
        if(t.isEmpty()||brake.isEmpty())return refuse("Captured neutral stopping geometry refused",cameraYaw);
        var stationary=GroundStationaryNeutralProof.describe(c.profile(),t.orElseThrow(),brake.orElseThrow());
        if(stationary.isPresent()){
            // Eligible native failures are terminal: an expired or dirty proof cannot retry the old path.
            if(!McCapturedProfileGeometry.stationaryNeutral(client,journey,t.orElseThrow(),brake.orElseThrow()))
                return refuse("Captured stationary neutral native proof refused",cameraYaw);
        }else if(!McCapturedProfileGeometry.sweptTrace(client,journey,t.orElseThrow(),null,false)
                ||!McCapturedProfileGeometry.groundCoast(client,journey,brake.orElseThrow()))
            return refuse("Captured neutral stopping geometry refused",cameraYaw);
        return issue(c,Input.NEUTRAL,SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,t.orElseThrow(),cameraYaw,state,reason);
    }
    /** No world or permission reads; the publisher invokes this only for the cached yaw replan branch. */
    public Decision neutralReplanDecision() {
        if(!requiresNeutralReplan())return refuse("No captured neutral replan pending",owner==null?0:owner.getYRot());
        var current=context();if(current.isEmpty())return refuse("Captured neutral replan custody lost",0);
        var c=current.orElseThrow();
        if(!finalDeadlineCurrent())return refuse("Captured original deadline expired before neutral replan",c.pose().yaw());
        if(c.pose().onGround()){
            if(phase==Phase.DROP_EXIT)return refuse("Queued turn during owned drop latch requires a new supported episode",c.pose().yaw());
            var neutral=groundNeutral(c,c.pose().yaw(),"Queued owned camera: neutral ground replan",WalkController.State.WALKING);
            if(!neutral.blocked()&&!finalDeadlineCurrent())return refuse("Captured original deadline expired during neutral geometry",c.pose().yaw());
            if(!neutral.blocked()){phase=Phase.GROUND;jumpPlan=null;dropOriginal=null;dropLatch=null;latchOriginal=latchLanding=null;episodeAt=0;}
            return neutral;
        }
        var t=trace(c,Input.NEUTRAL,air(),0);
        if(c.profile().sprinting()||t.isEmpty()||!McCapturedProfileGeometry.sweptTrace(client,journey,t.orElseThrow(),null,false))
            return refuse("Queued-camera neutral actual air body is unavailable",c.pose().yaw());
        if(!finalDeadlineCurrent())return refuse("Captured original deadline expired during air neutral geometry",c.pose().yaw());
        if(phase==Phase.AIRBORNE){jumpPlan=null;replanAir=true;}
        return issue(c,Input.NEUTRAL,SprintIntent.RELEASE,air(),t.orElseThrow(),c.pose().yaw(),WalkController.State.WALKING,"Queued owned camera: observed-air neutral replan");
    }
    /** Called once before installation/binding and before any real physics publication. Parent
     * supplies original initial + exact eager VARIED course and its ORIGINAL query budget.
     * For a lazily shaped ORIGINAL vanilla course, the parent leaves directional mode uninstalled.
     */
    public McCapturedProfileGeometry.InitialDirectionalPlaneKind initialDirectionalGroundEligibility(
            java.util.List<Vec3> exactEagerCourse,DynamicRouteQueryFence originalFence){
        if(GroundInitialDirectionalDiagnostics.copy(4,!directional)||GroundInitialDirectionalDiagnostics.copy(5,phase!=Phase.GROUND)
                ||GroundInitialDirectionalDiagnostics.copy(6,originalWalker!=null)||GroundInitialDirectionalDiagnostics.copy(7,staged!=null)
                ||GroundInitialDirectionalDiagnostics.copy(8,pending!=null)||GroundInitialDirectionalDiagnostics.copy(9,acceptedPhysics!=0)
                ||GroundInitialDirectionalDiagnostics.copy(10,initialPlaneAttempted)){GroundInitialDirectionalDiagnostics.returned("McCapturedProfileLocomotion.initialEligibility.originalGuardRefusal");stopWith("Initial directional plane eligibility unavailable");initialPlaneKind=McCapturedProfileGeometry.InitialDirectionalPlaneKind.CUSTODY_REFUSED;return initialPlaneKind;}
        initialPlaneAttempted=true;
        if(!directionalVanilla){initialPlaneKind=McCapturedProfileGeometry.InitialDirectionalPlaneKind.INITIAL_SUPPORTED;return initialPlaneKind;}
        McCapturedProfileGeometry.InitialDirectionalPlaneKind result;
        try{initialSteeringCourse=java.util.List.copyOf(exactEagerCourse);result=McCapturedProfileGeometry.initialDirectionalPlane(client,journey,initialSteeringCourse,originalFence);}
        catch(RuntimeException unknown){GroundInitialDirectionalDiagnostics.returned("McCapturedProfileLocomotion.initialEligibility.originalRuntimeExceptionCatch");stopWith("Initial directional plane native query unavailable");initialPlaneKind=McCapturedProfileGeometry.InitialDirectionalPlaneKind.CUSTODY_REFUSED;return initialPlaneKind;}
        initialPlaneKind=result;
        initialPlaneReady=result==McCapturedProfileGeometry.InitialDirectionalPlaneKind.INITIAL_SUPPORTED;
        if(!initialPlaneReady)stopWith(result==McCapturedProfileGeometry.InitialDirectionalPlaneKind.UNSUPPORTED_INITIAL_GEOMETRY
                ?"Initial partial native support retains original walking":"Initial directional plane custody/budget refused");
        return result;
    }
    /** Passive source classification only; null means the initial geometry was not attempted. */
    public McCapturedProfileGeometry.InitialDirectionalPlaneKind cachedInitialDirectionalGroundEligibility(){return initialPlaneKind;}
    /** The admitted world steering target is projected using CURRENT native yaw. The caller must
     * opt in through the distinct GENERAL factory. No target or Boolean grants publication custody.
     */
    public Decision tickDirectional(WalkController.Output requested,Vec3 target,Vec3 sprintTarget,
            Vec3 originalDropOrigin,Vec3 admittedSteeringTarget){
        if(!directional||directionalVanilla&&!initialPlaneReady)return refuse("Directional factory/initial native plane required",requested==null?0:requested.yaw());
        directionalSteering=requested!=null&&requested.state()==WalkController.State.WALKING
                &&GroundLocomotionMotion.finite(admittedSteeringTarget)?admittedSteeringTarget:null;
        try{return tick(requested,target,sprintTarget,originalDropOrigin);}finally{directionalSteering=null;}
    }
    public Decision tick(WalkController.Output requested,Vec3 target,Vec3 sprintTarget,Vec3 originalDropOrigin) {
        if(directionalVanilla&&!initialPlaneReady)return refuse("Initial directional native plane was not qualified",requested==null?0:requested.yaw());
        try {
            if(requested==null)return refuse("Captured walker output unavailable",0);
            if(phase==Phase.RETIRED)return new Decision(null,requested.yaw(),WalkController.State.BLOCKED,retirement,true,null,null,null);
            if(episodeAt!=0&&airborneOutput()!=null&&System.nanoTime()-episodeAt>1_800_000_000L)
                return refuse("Captured original 1800ms episode deadline",requested.yaw());
            if(!reconcile())return refuse("Captured native issued frame reconciliation failed",requested.yaw());
            var current=context();if(current.isEmpty())return refuse("Captured profile custody lost",requested.yaw());
            var c=current.orElseThrow();var pose=c.pose();
            if(sprintTimer()<0)return refuse("Unknown native sprint timer",requested.yaw());
            if(phase==Phase.LANDING_BRAKE)return brakeLanding(c,requested.yaw());
            if(phase==Phase.TAKEOFF||phase==Phase.AIRBORNE)return jumpAir(c);
            if(phase==Phase.DROP_EXIT||phase==Phase.DROPPING)return drop(c,requested.yaw());
            if(!pose.onGround())return refuse("Captured airborne motion has no owned episode",requested.yaw());
            if(directionalVanilla&&!CapturedDirectionalPlaneCourse.inProjectionRegion(pose.position(),initialSteeringCourse))
                return refuse("Directional actual pose left original bounded course projection region",requested.yaw());
            if(requested.state()!=WalkController.State.WALKING) {
                if(requested.state()==WalkController.State.BLOCKED)return refuse("Original walker blocked",requested.yaw());
                if(terminalBrakeAt==0)terminalBrakeAt=System.nanoTime();
                if(System.nanoTime()-terminalBrakeAt>4_000_000_000L)return refuse("Captured original terminal progress timeout",requested.yaw());
                if(c.profile().sprinting())return groundNeutral(c,requested.yaw(),"Release native terminal sprint",WalkController.State.WALKING);
                if(target!=null&&(pose.position().horizontalDistanceTo(target)>terminalRadius||speed(pose.velocity())>=.003D))
                    return selectGround(c,requested.yaw(),target,false,false);
                return groundNeutral(c,requested.yaw(),"Captured actual endpoint settled",requested.state());
            }
            if(!GroundLocomotionMotion.finite(target))return refuse("Captured original target unavailable",requested.yaw());
            if(requested.jump()&&target.y()-pose.position().y()>.6D)return beginJump(c,requested,target);
            if(originalDropOrigin!=null) {
                if(!CapturedProfileMotionMath.originalDrop(originalDropOrigin,target))return refuse("Original drop bound/certificate invalid",requested.yaw());
                if(c.profile().sprinting())return groundNeutral(c,requested.yaw(),"Release native sprint before original drop",WalkController.State.WALKING);
                dropOriginal=originalDropOrigin;dropLatch=null;latchOriginal=latchLanding=null;flightOrigin=pose.position();flightLanding=target;flightYaw=pose.yaw();phase=Phase.DROP_EXIT;episodeAt=System.nanoTime();
                return drop(c,requested.yaw());
            }
            if(Math.abs(target.y()-pose.position().y())>.600001D)return refuse("Captured unsupported ground elevation",requested.yaw());
            if(directionalSteering==null&&(!requested.forward()||CapturedProfileMotionMath.yawDifference(pose.yaw(),toward(pose.position(),target))>8D))
                return groundNeutral(c,requested.yaw(),"Captured actual heading alignment braking",WalkController.State.WALKING);
            boolean wantSprint=CapturedProfileSprintEligibility.mayRequest(sprintTraits())&&GroundLocomotionMotion.finite(sprintTarget)&&Math.abs(sprintTarget.y()-pose.position().y())<.05D
                    &&pose.position().horizontalDistanceTo(sprintTarget)>=3D
                    &&CapturedProfileMotionMath.yawDifference(pose.yaw(),toward(pose.position(),sprintTarget))<3D;
            if(c.profile().sprinting()&&!wantSprint)return groundNeutral(c,requested.yaw(),"Release native sprint before walking",WalkController.State.WALKING);
            return selectGround(c,requested.yaw(),wantSprint?sprintTarget:target,wantSprint,false);
        } catch(RuntimeException unavailable){return refuse("Captured native controller unavailable: "+unavailable.getClass().getSimpleName(),requested==null?0:requested.yaw());}
    }
    private Decision selectGround(McCapturedProfileGeometry.Context c,float cameraYaw,Vec3 target,boolean wantSprint,boolean braking) {
        if(directionalSteering!=null&&!braking){
            var simultaneous=selectDirectionalGround(c,cameraYaw,directionalSteering,wantSprint);
            if(simultaneous!=null)return simultaneous;
            // No unsafe lateral exemption in a narrow corridor. Fixed-yaw original selection is
            // still available once its camera heading is aligned; a queued turn remains neutral.
            if(CapturedProfileMotionMath.yawDifference(c.pose().yaw(),cameraYaw)>.5D)
                return groundNeutral(c,cameraYaw,"Directional full-heading stopping corridor unavailable",WalkController.State.WALKING);
        }
        Trace chosen=null;Input key=null;double best=Double.POSITIVE_INFINITY;
        if(!wantSprint&&!braking&&!c.profile().sprinting()) {
            var pulse=CapturedProfileGroundPlan.plan(c.profile(),c.pose().position(),c.pose().velocity(),target,c.friction(),
                    c.pose().yaw(),Math.min(.075D,terminalRadius),c.remainingEffectTicks());
            if(pulse.isPresent()) {
                var first=pulse.orElseThrow().firstInput();var stopping=trace(c,first,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,32);
                if(stopping.isPresent()&&McCapturedProfileGeometry.sweptTrace(client,journey,stopping.orElseThrow(),null,false))
                    return issue(c,first,SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,stopping.orElseThrow(),
                            cameraYaw,WalkController.State.WALKING,"Captured actual endpoint control pulse");
            }
        }
        for(var input:List.of(Input.NEUTRAL,Input.FORWARD,Input.BACKWARD,Input.LEFT,Input.RIGHT,
                Input.FORWARD_LEFT,Input.FORWARD_RIGHT,Input.BACKWARD_LEFT,Input.BACKWARD_RIGHT)) {
            var t=trace(c,input,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,32);if(t.isEmpty())continue;
            var end=t.orElseThrow().samples().getLast();
            double score=braking?end.position().horizontalDistanceTo(c.pose().position())+2D*speed(t.orElseThrow().samples().getFirst().velocity())
                    :end.position().horizontalDistanceTo(target);
            // The original walker admits the edge; this fresh complete stopping path additionally proves every actual input candidate.
            if(score>=best||!McCapturedProfileGeometry.sweptTrace(client,journey,t.orElseThrow(),null,false))continue;
            chosen=t.orElseThrow();key=input;best=score;
        }
        if(chosen==null)return refuse("Captured physical stopping corridor unavailable",cameraYaw);
        if(!braking&&key!=Input.NEUTRAL&&chosen.samples().getLast().position().horizontalDistanceTo(target)
                >c.pose().position().horizontalDistanceTo(target)+.075D)
            return groundNeutral(c,cameraYaw,"Captured endpoint overshoot braking",WalkController.State.WALKING);
        SprintIntent sprint=wantSprint&&CapturedProfileSprintEligibility.mayPress(sprintTraits(),key)?SprintIntent.PRESS:SprintIntent.RELEASE;
        return issue(c,key,sprint,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,chosen,cameraYaw,WalkController.State.WALKING,
                braking?"Captured pre-jump observed momentum braking":wantSprint?"Captured native sprint control":"Captured native walking control");
    }
    private Decision selectDirectionalGround(McCapturedProfileGeometry.Context c,float cameraYaw,Vec3 target,boolean wantSprint){
        if(!GroundLocomotionMotion.finite(target)||Math.abs(target.y()-c.pose().position().y())>.600001D)return null;
        Input input=CameraRelativeGroundSteering.select(c.pose().position(),target,c.pose().yaw(),directionalPrevious);
        var envelope=CapturedProfileGroundYawEnvelope.describe(c.profile(),c.pose().position(),c.pose().velocity(),
                c.friction(),c.pose().yaw(),cameraYaw,input);
        if(envelope.isEmpty())return null;
        var stopping=trace(c,input,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,32);
        if(stopping.isEmpty()||!McCapturedProfileGeometry.sweptGroundYawEnvelope(client,journey,stopping.orElseThrow(),envelope.orElseThrow()))return null;
        var intent=wantSprint&&CapturedProfileSprintEligibility.mayPress(sprintTraits(),input)?SprintIntent.PRESS:SprintIntent.RELEASE;
        var result=issue(c,input,intent,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,stopping.orElseThrow(),cameraYaw,
                WalkController.State.WALKING,"Captured simultaneous camera-relative ground pulse");
        if(!result.blocked()){result.groundYawEnvelope=envelope.orElseThrow();directionalPrevious=input;}
        return result;
    }
    private Decision beginJump(McCapturedProfileGeometry.Context c,WalkController.Output requested,Vec3 target) {
        if(c.profile().sprinting())return groundNeutral(c,requested.yaw(),"Release native sprint before boost jump",WalkController.State.WALKING);
        if(CapturedProfileMotionMath.yawDifference(c.pose().yaw(),toward(c.pose().position(),target))>.1D)
            return groundNeutral(c,requested.yaw(),"Align actual native boost heading",WalkController.State.WALKING);
        if(sprintTimer()>0)return groundNeutral(c,requested.yaw(),"Wait for actual native sprint double-tap timer",WalkController.State.WALKING);
        if(speed(c.pose().velocity())>.05D)return selectGround(c,requested.yaw(),c.pose().position(),false,true);
        var plan=CapturedProfilePulseFlightPlan.jump(c.profile(),c.pose().position(),c.pose().velocity(),target,c.friction(),c.pose().yaw(),c.remainingEffectTicks());
        if(plan.isEmpty())return selectGround(c,requested.yaw(),c.pose().position(),false,true);
        if(!McCapturedProfileGeometry.sweptTrace(client,journey,plan.orElseThrow().trace(),target,true))
            return refuse("Captured complete boost body/landing trajectory refused",requested.yaw());
        jumpPlan=plan.orElseThrow();jumpStep=0;flightOrigin=c.pose().position();flightLanding=target;flightYaw=c.pose().yaw();phase=Phase.TAKEOFF;episodeAt=System.nanoTime();
        return issue(c,Input.NEUTRAL,SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.GROUNDED_TAKEOFF,
                jumpPlan.trace(),requested.yaw(),WalkController.State.WALKING,"Captured owned neutral boost takeoff");
    }
    private Decision jumpAir(McCapturedProfileGeometry.Context c) {
        if(replanAir||jumpPlan==null){
            var resumed=CapturedProfilePulseFlightPlan.resume(c.profile(),c.pose().position(),c.pose().velocity(),flightLanding,
                    c.friction(),c.pose().yaw(),c.remainingEffectTicks(),Math.max(0,sprintTimer()-1));
            if(resumed.isEmpty())return refuse("Captured actual boost resume plan unavailable",flightYaw);
            jumpPlan=resumed.orElseThrow();jumpStep=0;flightYaw=c.pose().yaw();replanAir=false;
        }
        if(c.pose().onGround()||c.profile().sprinting()||jumpStep<0||jumpStep>=jumpPlan.trace().samples().size())
            return refuse("Captured boost episode or expected control lost",flightYaw);
        var inputs=jumpPlan.expectedControls().subList(jumpStep,jumpPlan.expectedControls().size()).stream().map(Controls::input).toList();
        var remaining=forecast(c.profile(),c.pose().position(),c.pose().velocity(),c.friction(),c.pose().yaw(),air(),inputs,c.remainingEffectTicks());
        if(remaining.isEmpty()||remaining.orElseThrow().samples().getLast().position().horizontalDistanceTo(flightLanding)>.075D
                ||speed(remaining.orElseThrow().samples().getLast().intendedMove())>.05D
                ||!McCapturedProfileGeometry.sweptTrace(client,journey,remaining.orElseThrow(),flightLanding,true))
            return refuse("Captured boost remaining actual trajectory lost",flightYaw);
        phase=Phase.AIRBORNE;
        return issue(c,inputs.getFirst(),SprintIntent.RELEASE,air(),remaining.orElseThrow(),flightYaw,WalkController.State.WALKING,"Captured owned airborne boost pulse");
    }
    private Decision drop(McCapturedProfileGeometry.Context c,float cameraYaw) {
        if(!CapturedProfileMotionMath.originalDrop(dropOriginal,flightLanding)||c.profile().sprinting()
                ||c.pose().position().y()>dropOriginal.y()+.081D||c.pose().position().y()<flightLanding.y()-1E-6D)
            return refuse("Captured original adjacent drop episode lost",cameraYaw);
        var start=c.pose().onGround()?CapturedProfileTrajectory.Phase.GROUNDED_EXIT:air();
        var candidate=CapturedProfilePulseFlightPlan.descent(c.profile(),c.pose().position(),c.pose().velocity(),dropOriginal,flightLanding,
                c.friction(),c.pose().yaw(),start,c.remainingEffectTicks(),forwardDelay());
        if(candidate.isPresent()) {
            var t=candidate.orElseThrow().trace();
            GroundProfileMotionCarrier.ObservedStep latch=dropLatch!=null&&dropOriginal.equals(latchOriginal)&&flightLanding.equals(latchLanding)
                    &&dropLatch.after().position().equals(c.pose().position())&&dropLatch.after().velocity().equals(c.pose().velocity())
                    &&dropLatch.after().nativeTick()==c.pose().nativeTick()&&dropLatch.after().onGround()==c.pose().onGround()
                    &&dropLatch.after().sprinting()==c.pose().sprinting()
                    &&Float.floatToIntBits(dropLatch.after().yaw())==Float.floatToIntBits(c.pose().yaw())?dropLatch:null;
            boolean geometry=c.pose().onGround()?(latch==null?McCapturedProfileGeometry.groundExitTrace(client,journey,t,dropOriginal,flightLanding)
                    :McCapturedProfileGeometry.groundExitTrace(client,journey,t,dropOriginal,flightLanding,latch))
                    :McCapturedProfileGeometry.sweptTrace(client,journey,t,flightLanding,true);
            if(geometry) {
                phase=c.pose().onGround()?Phase.DROP_EXIT:Phase.DROPPING;
                return issue(c,candidate.orElseThrow().firstInput(),SprintIntent.RELEASE,start,t,cameraYaw,WalkController.State.WALKING,"Captured original drop native descent pulse");
            }
        }
        if(!c.pose().onGround())return refuse("Captured original drop remaining contact path unavailable",cameraYaw);
        // Native Y is clipped before horizontal movement. A partial old source footprint may still
        // produce a grounded frame; only groundExitTrace's actual collision query grants that case.
        double along=(c.pose().velocity().x()*(flightLanding.x()-dropOriginal.x())
                +c.pose().velocity().z()*(flightLanding.z()-dropOriginal.z()))/dropOriginal.horizontalDistanceTo(flightLanding);
        Input first=along>.25D?Input.BACKWARD:along>.003D||forwardDelay()>0?Input.NEUTRAL:Input.FORWARD;
        for(var input:List.of(first,Input.NEUTRAL,Input.BACKWARD)) {
            var t=trace(c,input,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,0);
            if(t.isPresent()&&McCapturedProfileGeometry.groundExitTrace(client,journey,t.orElseThrow(),dropOriginal,flightLanding)) {
                phase=Phase.DROP_EXIT;
                return issue(c,input,SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,t.orElseThrow(),cameraYaw,
                        WalkController.State.WALKING,"Captured actual old-support drop approach");
            }
        }
        return refuse("Captured certified drop source/support geometry unavailable",cameraYaw);
    }
    private Decision brakeLanding(McCapturedProfileGeometry.Context c,float yaw) {
        if(!c.pose().onGround()||flightLanding==null||!McCapturedProfileGeometry.standingContact(client,journey,c.pose(),flightLanding,c.friction()))
            return refuse("Captured actual landing support lost",yaw);
        if(speed(c.pose().velocity())<.003D) {
            phase=Phase.GROUND;jumpPlan=null;dropOriginal=null;dropLatch=null;latchOriginal=latchLanding=null;episodeAt=0;
            return groundNeutral(c,yaw,"Captured observed landing settled",WalkController.State.WALKING);
        }
        return groundNeutral(c,yaw,"Captured observed landing neutral brake",WalkController.State.WALKING);
    }
    /** Called only after the existing publisher wrote all seven actual scoped keys. */
    public boolean afterPublished() {
        GroundPublicationFailureCache.begin();originalAfterPublicationFailure=null;
        try {
            if(GroundPublicationFailureCache.copy(29,staged==null)||GroundPublicationFailureCache.copy(30,staged.blocked)||GroundPublicationFailureCache.copy(31,pending!=null)||GroundPublicationFailureCache.copy(32,!canonicalPlayer())){GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn0"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish(null); return false;}
            // Attempt metadata is held by the genuine private Decision; it is separate from original evaluated operands.
            GroundPublicationFailureCache.directionalContext(staged.groundYawEnvelope!=null);
            GroundPublicationFailureCache.attempt(staged.controls.input(),staged.controls.jump(),staged.controls.sprint(),staged.nativePhase);
            var proof=McCapturedProfileGeometry.selectedProof(client,journey,staged.trace);
            if(GroundPublicationFailureCache.copy(33,proof==null)){GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn1"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured exact selected geometry proof unavailable"); stopWith("Captured exact selected geometry proof unavailable");return false;}
            proof.copyOriginalClockPhasesToPublicationFailure();
            var current=context();if(GroundPublicationFailureCache.copy(34,current.isEmpty())){GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn2"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured publication profile lost"); stopWith("Captured publication profile lost");return false;}
            var c=current.orElseThrow();var before=staged.before;
            if(GroundPublicationFailureCache.copy(35,!GroundPublicationFailureCache.deadlineSite(0,finalDeadlineCurrent()))){GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn3"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured original deadline expired after publication context"); stopWith("Captured original deadline expired after publication context");return false;}
            if(GroundPublicationFailureCache.copy(36,!c.profile().state().equals(before.profile().state()))||GroundPublicationFailureCache.copy(37,c.pose().nativeTick()!=before.pose().nativeTick()
                    )||GroundPublicationFailureCache.copy(38,!c.pose().position().equals(before.pose().position()))||GroundPublicationFailureCache.copy(39,!c.pose().velocity().equals(before.pose().velocity()))) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn4"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured publication changed native before state"); 
                stopWith("Captured publication changed native before state");return false;
            }
            if(GroundPublicationFailureCache.copy(40,CapturedProfileMotionMath.yawDifference(GroundPublicationFailureCache.poseYaw(c.pose().yaw()),before.pose().yaw())>.5D
                    &&staged.groundYawEnvelope==null)) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn5"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish(null); 
                neutralReplan=true;staged=null;cache();return false;
            }
            var frame=actor.profileInputFrame();
            if(GroundPublicationFailureCache.copy(41,frame==null)||GroundPublicationFailureCache.copy(42,!actor.ownsProfileFrame(frame))||GroundPublicationFailureCache.copy(43,!frame.nativeCurrent())||GroundPublicationFailureCache.copy(44,frame.issuedTick()!=c.pose().nativeTick()
                    )||GroundPublicationFailureCache.copy(45,frame.sequence()<=lastFrameSequence)){GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn6"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured original seven-key frame unavailable"); stopWith("Captured original seven-key frame unavailable");return false;}
            for(var key:KEYS)if(GroundPublicationFailureCache.copy(46,frame.held(key)!=CapturedProfileMotionMath.held(staged.controls,key))) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn7"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured real published key differs from decision"); 
                stopWith("Captured real published key differs from decision");return false;
            }
            if(GroundPublicationFailureCache.copy(47,!GroundPublicationFailureCache.deadlineSite(1,finalDeadlineCurrent()))){GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn8"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured original deadline expired after publication frame"); stopWith("Captured original deadline expired after publication frame");return false;}
            var queued=frame.ownedQueuedPose();
            if(GroundPublicationFailureCache.copy(48,queued==null)&&GroundPublicationFailureCache.copy(49,frame.unqualifiedQueuedPose())||GroundPublicationFailureCache.copy(50,queued!=null)&&GroundPublicationFailureCache.copy(51,!queued.nativeCurrent())) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn9"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured actual queued camera proof unavailable"); 
                stopWith("Captured actual queued camera proof unavailable");return false;
            }
            if(staged.groundYawEnvelope!=null&&(queued==null||!queued.nativeCurrent()
                    ||!staged.groundYawEnvelope.endpointMatches(queued.endpointYaw())
                    ||!McCapturedProfileGeometry.hasGroundYawEnvelope(client,journey,staged.trace,staged.groundYawEnvelope))){
                GroundPublicationFailureCache.returned("afterPublished.directionalFalseReturnQueuedWitness");originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Directional publication lost exact queued/full-heading geometry witness");
                stopWith("Directional publication lost exact queued/full-heading geometry witness");return false;
            }
            if(GroundPublicationFailureCache.copy(52,queued!=null)&&GroundPublicationFailureCache.copy(53,GroundPublicationFailureCache.queuedPending(queued.pending()))&&GroundPublicationFailureCache.copy(54,CapturedProfileMotionMath.yawDifference(GroundPublicationFailureCache.queuedYaw(queued.endpointYaw()),GroundPublicationFailureCache.poseYaw(c.pose().yaw()))>.5D
                    )&&GroundPublicationFailureCache.copy(55,(GroundPublicationFailureCache.input(staged.controls.input())!=Input.NEUTRAL||GroundPublicationFailureCache.jump(staged.controls.jump())==JumpIntent.PRESS)
                    &&staged.groundYawEnvelope==null)) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn10"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish(null); 
                neutralReplan=true;staged=null;cache();return false;
            }
            if(staged.groundYawEnvelope!=null&&(!frame.bindDirectionalPose(queued)||!frame.directionalPoseCurrent())){
                GroundPublicationFailureCache.returned("afterPublished.directionalFalseReturnPoseBinding");originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Directional frame lacks original applied-pose lineage binding");
                stopWith("Directional frame lacks original applied-pose lineage binding");return false;
            }
            if(GroundPublicationFailureCache.copy(56,GroundPublicationFailureCache.jump(staged.controls.jump())==JumpIntent.PRESS)&&GroundPublicationFailureCache.copy(57,!frame.actualPress(LegacyTravelRetirement.Key.JUMP))) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn11"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured takeoff lacks original jump press"); 
                stopWith("Captured takeoff lacks original jump press");return false;
            }
            var expected=frame(c.profile(),c.pose().position(),c.pose().velocity(),c.friction(),GroundPublicationFailureCache.poseYaw(c.pose().yaw()),GroundPublicationFailureCache.input(staged.controls.input()),
                    GroundPublicationFailureCache.phase(staged.nativePhase),GroundPublicationFailureCache.jump(staged.controls.jump()),GroundPublicationFailureCache.sprint(staged.controls.sprint()));
            var motion=(GroundProfileMotionCarrier)(Object)owner;var sprint=(GroundOwnedSprintCarrier)(Object)owner;
            // Everything below is native-only. No context/current/role/provider callbacks may follow this final world journal check.
            if(GroundPublicationFailureCache.copy(58,expected.isEmpty())||GroundPublicationFailureCache.copy(59,!GroundPublicationFailureCache.deadlineSite(2,finalDeadlineCurrent()))||GroundPublicationFailureCache.copy(60,!proof.callbackFreeCurrent(c,frame)
                    )||GroundPublicationFailureCache.copy(61,!GroundPublicationFailureCache.deadlineSite(3,finalDeadlineCurrent()))||GroundPublicationFailureCache.copy(62,!frame.nativeCurrent()
                    )||GroundPublicationFailureCache.copy(63,GroundPublicationFailureCache.sprint(staged.controls.sprint())==SprintIntent.PRESS)&&GroundPublicationFailureCache.copy(64,!CapturedProfileSprintEligibility.mayPress(sprintTraits(),GroundPublicationFailureCache.input(staged.controls.input())))) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn12"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured final selected world/deadline/sprint fence refused"); 
                stopWith("Captured final selected world/deadline/sprint fence refused");return false;
            }
            if(GroundPublicationFailureCache.copy(65,!motion.expectProfileFrame(frame))||GroundPublicationFailureCache.copy(66,motion.profileNativeBaseline()==null)||GroundPublicationFailureCache.copy(67,!sprint.expectOwnedSprintFrame(frame)
                    )||GroundPublicationFailureCache.copy(68,!frame.nativeCurrent())||GroundPublicationFailureCache.copy(69,queued!=null)&&GroundPublicationFailureCache.copy(70,!queued.nativeCurrent())||GroundPublicationFailureCache.copy(71,!GroundPublicationFailureCache.deadlineSite(4,finalDeadlineCurrent()))) {GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn13"); originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured native motion/sprint observer arm refused"); 
                stopWith("Captured native motion/sprint observer arm refused");return false;
            }
            pending=new Pending(staged,frame,motion.profileNativeBaseline(),c,expected.orElseThrow());lastFrameSequence=frame.sequence();
            staged=null;neutralReplan=false;GroundPublicationFailureCache.success();originalAfterPublicationFailure=null;return true;
        } catch(RuntimeException unavailable){GroundPublicationFailureCache.returned("afterPublished.originalRuntimeExceptionCatch");originalAfterPublicationFailure=GroundPublicationFailureCache.finish("Captured observer publication unavailable",originalAfterPublicationFailure);stopWith("Captured observer publication unavailable");return false;}
    }
    /** Native-only final original attribution fence AFTER every context/standingContact callback.
     * It retrieves no new queue/scope/history and grants no publication authority. Only a privately
     * bound directional frame uses this extra fence; original default/air behavior remains intact.
     */
    private boolean finalDirectionalReconcileCurrent(Pending exactPending,GroundProfileMotionCarrier motion,
            GroundProfileMotionCarrier.ObservedStep exactStep,McCapturedProfileGeometry.Context observed){
        if(exactPending==null||exactPending!=pending||exactPending.decision().groundYawEnvelope==null
                ||!canonicalPlayer()||motion!=(GroundProfileMotionCarrier)(Object)owner||exactStep==null||observed==null)return false;
        var frame=exactPending.frame();var after=exactStep.after();
        if(after==null||!motion.ownsProfileStep(exactStep)||motion.profileNativeAfter(exactPending.baseline())!=exactStep
                ||exactStep.nativeIdentity()!=exactPending.baseline()||exactStep.issuedInputs()!=frame
                ||motion.expectedProfileFrame()!=frame||!frame.nativeCurrent()||!frame.directionalPoseCurrent()
                ||!journey.callbackFreeForecastCurrent(observed.profile().state(),1)||!finalDeadlineCurrent())return false;
        var velocity=owner.getDeltaMovement();
        if(!new Vec3(owner.getX(),owner.getY(),owner.getZ()).equals(after.position())
                ||!new Vec3(velocity.x,velocity.y,velocity.z).equals(after.velocity())||owner.tickCount!=after.nativeTick()
                ||owner.onGround()!=after.onGround()||owner.isSprinting()!=after.sprinting()
                ||Float.floatToIntBits(owner.getYRot())!=Float.floatToIntBits(after.yaw())
                ||Float.floatToIntBits(owner.getSpeed())!=Float.floatToIntBits(observed.profile().state().travelSpeed()))return false;
        // All preceding operations are native leaves. Recheck the SAME causal frame/step after
        // their complete reads, immediately before acceptance or phase/count state is published.
        return exactPending==pending&&frame.nativeCurrent()&&frame.directionalPoseCurrent()
                &&motion.expectedProfileFrame()==frame&&motion.profileNativeAfter(exactPending.baseline())==exactStep
                &&motion.ownsProfileStep(exactStep)&&finalDeadlineCurrent();
    }
    private boolean reconcile() {
        if(pending==null)return phase!=Phase.TAKEOFF&&phase!=Phase.AIRBORNE&&phase!=Phase.DROPPING;
        if(!canonicalPlayer()||!actor.ownsProfileFrame(pending.frame())||!pending.frame().nativeCurrent())return false;
        var motion=(GroundProfileMotionCarrier)(Object)owner;var exact=motion.profileNativeAfter(pending.baseline());
        if(exact==null||!motion.ownsProfileStep(exact)||exact.nativeIdentity()!=pending.baseline()
                ||exact.issuedInputs()!=pending.frame()||exact.before()==null||exact.after()==null
                ||(long)exact.before().nativeTick()-pending.frame().issuedTick()!=1||exact.after().nativeTick()!=exact.before().nativeTick())return false;
        var before=pending.before().pose();var actualBefore=exact.before();var d=pending.decision();
        if(d.groundYawEnvelope!=null&&(!pending.frame().directionalPoseCurrent()
                ||motion.expectedProfileFrame()!=pending.frame()))return false;
        if(!actualBefore.position().equals(before.position())||!actualBefore.velocity().equals(before.velocity())
                ||actualBefore.onGround()!=before.onGround()
                ||(CapturedProfileMotionMath.yawDifference(actualBefore.yaw(),before.yaw())>.5D
                    &&!(d.groundYawEnvelope!=null&&directional&&d.nativePhase==CapturedProfileTrajectory.Phase.SUPPORTED_GROUND
                        &&d.controls.jump()==JumpIntent.RELEASE&&Float.isFinite(actualBefore.yaw())
                        &&pending.frame().directionalPoseCurrent())
                    &&!CapturedProfileMotionMath.yawIndependent(d.controls.input(),exact.travelInput(),d.nativePhase,actualBefore.sprinting()))
                ||Float.floatToIntBits(actualBefore.travelSpeed())!=Float.floatToIntBits(pending.before().profile().state().travelSpeed())
                ||Float.floatToIntBits(exact.after().travelSpeed())!=Float.floatToIntBits(actualBefore.travelSpeed())
                ||CapturedProfileMotionMath.inputAndJump(d.controls.input(),d.nativePhase,exact.travelInput(),exact.actualJumpCalls())
                    !=CapturedProfileMotionMath.Match.MATCHED)return false;
        var sprint=(GroundOwnedSprintCarrier)(Object)owner;var owned=sprint.ownedSprintObservation();
        if(owned==null||!sprint.ownsSprintObservation(owned)||owned.frame()!=pending.frame()
                ||owned.nativeTick()!=exact.after().nativeTick()
                ||Float.floatToIntBits(owned.travelSpeed())!=Float.floatToIntBits(actualBefore.travelSpeed()))return false;
        var refreshed=context();if(refreshed.isEmpty())return false;var c=refreshed.orElseThrow();
        if(Float.floatToIntBits(owned.after().field())!=Float.floatToIntBits(c.profile().state().travelSpeed())
                ||owned.after().sprinting()!=c.pose().sprinting()||actualBefore.sprinting()!=exact.after().sprinting())return false;
        var actual=exact.after();var now=c.pose();
        if(!now.position().equals(actual.position())||!now.velocity().equals(actual.velocity())||now.nativeTick()!=actual.nativeTick()
                ||now.onGround()!=actual.onGround()||now.sprinting()!=actual.sprinting()
                ||Float.floatToIntBits(now.yaw())!=Float.floatToIntBits(actual.yaw()))return false;
        var mathematical=frame(pending.before().profile(),actualBefore.position(),actualBefore.velocity(),pending.before().friction(),
                actualBefore.yaw(),d.controls.input(),d.nativePhase,d.controls.jump(),d.controls.sprint());
        if(mathematical.isEmpty())return false;var expected=mathematical.orElseThrow();
        boolean contact=actual.onGround()&&(phase==Phase.AIRBORNE||phase==Phase.DROPPING||d.nativePhase==CapturedProfileTrajectory.Phase.GROUNDED_EXIT);
        if(contact) {
            if(CapturedProfileMotionMath.landingContact(actualBefore.position(),expected,actual,flightLanding,.075D)
                    !=CapturedProfileMotionMath.Match.MATCHED
                    ||!McCapturedProfileGeometry.standingContact(client,journey,now,flightLanding,c.friction()))return false;
            if(d.groundYawEnvelope!=null&&!finalDirectionalReconcileCurrent(pending,motion,exact,c))return false;
            acceptedContacts++;phase=Phase.LANDING_BRAKE;
        } else {
            if(CapturedProfileMotionMath.freeMotion(expected,actual)!=CapturedProfileMotionMath.Match.MATCHED)return false;
            if(d.groundYawEnvelope!=null&&!finalDirectionalReconcileCurrent(pending,motion,exact,c))return false;
            if(d.nativePhase==CapturedProfileTrajectory.Phase.GROUNDED_TAKEOFF) {
                if(actual.onGround()||actual.sprinting())return false;acceptedJumps++;jumpStep=1;phase=Phase.AIRBORNE;
            } else if(phase==Phase.AIRBORNE) {if(actual.onGround()||actual.sprinting())return false;jumpStep++;}
            else if(phase==Phase.DROP_EXIT) {
                if(d.nativePhase==CapturedProfileTrajectory.Phase.SUPPORTED_GROUND
                        &&CapturedProfileDropLatchMath.mayRetain(actualBefore,actual,dropOriginal,flightLanding,exact.actualJumpCalls())) {
                    dropLatch=exact;latchOriginal=dropOriginal;latchLanding=flightLanding;
                } else {dropLatch=null;latchOriginal=latchLanding=null;}
                if(!actual.onGround())phase=Phase.DROPPING;
            }
            else if(phase==Phase.DROPPING) {if(actual.onGround())return false;}
            else if(!actual.onGround())return false;
        }
        acceptedPhysics++;pending=null;cache();return true;
    }
}
