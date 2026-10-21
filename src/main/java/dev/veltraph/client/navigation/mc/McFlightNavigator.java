package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;

/** Authorized ability flight, using ordinary movement keys. Never grants mayfly or spoofs position. */
public final class McFlightNavigator {
    private final Minecraft client;private final InputService input;private final ConfigService config;
    private final FlightController controller=new FlightController(dev.veltraph.client.service.mc.McRotationService.sessionMultiplier());
    private final McNavigator terrain;
    private Vec3 goal;private int regenerations;private String status="Idle";
    private boolean supportedLanding;private boolean requireSupportedLanding;private McSupportedFlightPreview.Observation supportedObservation;private SupportedLeaf supportedLeaf;private double supportedTerminalRadius=.35;private McSharedMovementNavigator.FlightDeadline sharedDeadline;
    private SharedMovementPolicy.Purpose coursePurpose;
    private LegacyTravelPublicationLease.Leg publication;
    private boolean standalone;
    private boolean fresh() { return (publication!=null?publication.current():standalone&&!dev.veltraph.client.service.mc.McOwnedRotation.installed(client))
        &&(!requireSupportedLanding||McSupportedFlightPreview.current(client,supportedObservation)); }
    private net.minecraft.client.player.LocalPlayer owner;
    private net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    public McFlightNavigator(Minecraft client,InputService input,ConfigService config) {
        this.client=client;this.input=input;this.config=config;terrain=new McNavigator(client,input);controller.clearance(this::segmentClear);controller.landingClearance(this::safeLandingColumn);
    }
    private boolean bodyClear(BlockPos feet) { return McNativeFlightGeometry.body(client,terrain,new Vec3(feet.x()+.5,feet.y(),feet.z()+.5),0); }
    private Vec3 position() { return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()); }
    private boolean nativeStanding(Vec3 point){return McNativeFlightGeometry.standing(client,terrain,point);}
    private boolean nativeStandingSweep(Vec3 from,Vec3 to){int n=Math.max(1,(int)Math.ceil(from.distanceTo(to)*10));for(int i=0;i<=n;i++)if(!nativeStanding(TravelPath.lerp(from,to,(double)i/n)))return false;return true;}
    public boolean start(Vec3 destination) { return start(destination,java.util.concurrent.ThreadLocalRandom.current().nextLong()); }
    private long routeSeed;
    public boolean start(Vec3 destination,long seed) { return start(destination,seed,null); }
    /** Root-bound callers must pass the concrete captured publication leg. */
    public boolean start(Vec3 destination,long seed,LegacyTravelPublicationLease.Leg supplied) {
        return start(destination,seed,supplied,null);
    }
    /** Ordinary purpose-bearing journey samples its seed inside the production service. */
    public boolean start(Vec3 destination,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose) {
        return start(destination,java.util.concurrent.ThreadLocalRandom.current().nextLong(),supplied,purpose);
    }
    /** Shared mode must finish on loaded native support, never on a floating endpoint. */
    public boolean startSupported(Vec3 destination,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose){
        return startSupported(destination,supplied,purpose,.35);
    }
    /** Native shared request retains its actual precision; old authored flight APIs are unchanged. */
    public boolean startSupported(Vec3 destination,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose,double terminalRadius){
        if(!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35)return false;
        return startInternal(destination,java.util.concurrent.ThreadLocalRandom.current().nextLong(),supplied,purpose,true,terminalRadius);
    }
    /** Only the actual shared native port can supply its private original clock window. */
    boolean startSupported(Vec3 destination,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose,double terminalRadius,McSharedMovementNavigator.FlightDeadline deadline){
        if(deadline==null||!deadline.current()||!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35)return false;
        boolean started=startInternal(destination,java.util.concurrent.ThreadLocalRandom.current().nextLong(),supplied,purpose,true,terminalRadius,deadline);
        return started&&deadline.current();
    }
    /** Purpose changes only advisory course shaping; the same actual native Leg remains mandatory. */
    public boolean start(Vec3 destination,long seed,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose) {
        return startInternal(destination,seed,supplied,purpose,false);
    }
    private boolean startInternal(Vec3 destination,long seed,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose,boolean requireSupport){
        return startInternal(destination,seed,supplied,purpose,requireSupport,.35);
    }
    private boolean startInternal(Vec3 destination,long seed,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose,boolean requireSupport,double terminalRadius){
        return startInternal(destination,seed,supplied,purpose,requireSupport,terminalRadius,null);
    }
    private boolean startInternal(Vec3 destination,long seed,LegacyTravelPublicationLease.Leg supplied,SharedMovementPolicy.Purpose purpose,boolean requireSupport,double terminalRadius,McSharedMovementNavigator.FlightDeadline deadline){
        stop();sharedDeadline=deadline;supportedTerminalRadius=terminalRadius;requireSupportedLanding=requireSupport;publication=supplied;standalone=supplied==null&&!dev.veltraph.client.service.mc.McOwnedRotation.installed(client);
        supportedObservation=requireSupportedLanding?McSupportedFlightPreview.capture(client,destination):null;
        supportedLeaf=requireSupportedLanding&&supportedObservation!=null&&supplied!=null?new SupportedLeaf(supplied,supportedObservation):null;
        routeSeed=seed;goal=destination;regenerations=0;coursePurpose=purpose;
        if(!fresh()) { status="Captured flight publication lease required";return false; }
        if(client.player==null||client.level==null||client.player.shouldDiscardFriction()||!client.player.isAlive()||client.gui.screen()!=null
                ||destination==null||!Double.isFinite(destination.x())||!Double.isFinite(destination.y())||!Double.isFinite(destination.z())
                ||!client.player.getAbilities().mayfly) { status="Available world and server flight permission required";return false; }
        if(requireSupportedLanding&&!nativeStanding(destination)){stop();status="Shared flight requires loaded destination support";return false;}
        if(!plan()||!fresh()) { stop();return false; }
        owner=client.player;ownerLevel=client.level;
        if(!fresh()||requireSupportedLanding&&(supportedLeaf==null||!supportedLeaf.current())) { stop();return false; }
        if(!client.player.getAbilities().flying) {
            if(requireSupportedLanding&&(supportedLeaf==null||!supportedLeaf.current())){stop();status="Supported takeoff proof changed";return false;}
            client.player.getAbilities().flying=true;
            if(!fresh()) { stop();return false; }
            client.player.onUpdateAbilities();
        }
        return true;
    }
    private boolean plan() {
        if(!fresh())return false;
        Vec3 origin=position();
        if((supportedLanding||requireSupportedLanding)&&!nativeStanding(goal)) { status="Owned landing support changed or unloaded";return false; }
        boolean takeoff=client.player.onGround();
        Vec3 searchStart=takeoff?new Vec3(origin.x(),origin.y()+(requireSupportedLanding?3:1),origin.z()):origin;
        if(takeoff && !McNativeFlightGeometry.body(client,terrain,searchStart,0)) { status="No headroom to take off";return false; }
        // A supported destination needs a vertical landing column. A descending shortcut can
        // touch adjacent higher support early and cause vanilla to end flight before arrival.
        boolean landing=supportedLanding||nativeStanding(goal)&&origin.distanceTo(goal)>.3;
        supportedLanding=landing;
        Vec3 searchGoal=landing?new Vec3(goal.x(),goal.y()+(requireSupportedLanding?3:1.25),goal.z()):goal;
        var air=new FlightPathfinder.Airspace() {
            public boolean clear(BlockPos feet) { return bodyClear(feet); }
            public boolean poseClear(Vec3 feet) { return McNativeFlightGeometry.body(client,terrain,feet,0); }
        };
        var path=FlightPathfinder.find(air,searchStart,searchGoal,
                Math.max(4,Math.min(128,config.get(TravelConfig.FLIGHT_SEARCH_RADIUS))),
                Math.max(100,Math.min(50000,config.get(TravelConfig.FLIGHT_NODE_BUDGET))),this::segmentClear);
        if(!fresh()) { status="Flight lease changed during geometry read";return false; }
        status=path.found()?"Flying":path.reason();
        if(path.found()) {
            var nodes=new java.util.ArrayList<Vec3>();if(takeoff) nodes.add(searchStart);nodes.addAll(path.nodes());
            // Shared supported journeys retain their actual takeoff before shaping the open cruise.
            // This keeps the varied vertical envelope above support instead of flattening invalid curves.
            Vec3 shapingOrigin=requireSupportedLanding&&takeoff?searchStart:origin;
            var shapingNodes=requireSupportedLanding&&takeoff?path.nodes():nodes;
            var shaped=coursePurpose==SharedMovementPolicy.Purpose.GENERAL_TRAVEL
                ?OpenFlightCourse.shape(shapingOrigin,shapingNodes,this::segmentClear,(a,b)->segmentClear(a,b,.45),routeSeed+regenerations)
                :TravelPath.shapeFlight(origin,nodes,this::segmentClear,(a,b)->segmentClear(a,b,.45),routeSeed+regenerations);
            if(requireSupportedLanding&&takeoff&&!shaped.isEmpty()){
                var takingOff=new java.util.ArrayList<Vec3>();takingOff.add(searchStart);takingOff.addAll(shaped);shaped=java.util.List.copyOf(takingOff);
            }
            if(shaped.isEmpty()) { status="No swept connection from actual flight position";return false; }
            if(landing) {
                if(!segmentClear(searchGoal,goal)) { status="Landing column obstructed or unloaded";return false; }
                var landingPath=new java.util.ArrayList<Vec3>(shaped);landingPath.add(goal);shaped=java.util.List.copyOf(landingPath);
            }
            if(!fresh())return false;
            if(requireSupportedLanding)controller.startSupported(shaped,System.currentTimeMillis(),supportedTerminalRadius,FlightMotionEnvelope.horizontalDecay(supportedObservation.traits().drag()));
            else controller.start(shaped,System.currentTimeMillis(),supportedLanding);
        }
        return path.found();
    }
    /** Recheck the swept player body, including loaded chunks, when a compressed segment is executed. */
    private boolean segmentClear(Vec3 target) {
        if(!fresh()||target==null||client.player==null||client.level==null) return false;
        return segmentClear(position(),target);
    }
    private boolean segmentClear(Vec3 from,Vec3 target) {
        return segmentClear(from,target,.3);
    }
    private boolean segmentClear(Vec3 from,Vec3 target,double margin) {
        if(!fresh())return false;
        int samples=Math.max(1,(int)Math.ceil(from.distanceTo(target)*4));
        for(int i=0;i<=samples;i++) {
            double t=(double)i/samples,x=from.x()+(target.x()-from.x())*t,y=from.y()+(target.y()-from.y())*t,z=from.z()+(target.z()-from.z())*t;
            if(!McNativeFlightGeometry.body(client,terrain,new Vec3(x,y,z),margin-.3))return false;
        }
        return fresh();
    }
    /** Read-only exact landing handshake: supported actual footprint plus an entirely loaded column. */
    public boolean safeLandingColumn(Vec3 from,Vec3 target) {
        if(!fresh()||from==null||target==null||client.player==null||client.level==null||!client.player.isAlive()
                ||from.horizontalDistanceTo(target)>=.25||from.y()-target.y()<=.6||from.y()-target.y()>1.5
                ||!nativeStanding(target))return false;
        Vec3 projected=new Vec3(from.x(),target.y(),from.z());
        return nativeStanding(projected)&&nativeStandingSweep(projected,target)&&segmentClear(from,projected)&&segmentClear(from,target)&&fresh();
    }
    public FlightController.State tick() {
        if(!fresh()||owner==null||client.player!=owner||client.level!=ownerLevel||owner.shouldDiscardFriction()||!owner.isAlive()||client.gui.screen()!=null) {
            stop();status="Travel ownership or world unavailable";return FlightController.State.BLOCKED;
        }
        var player=client.player;var velocity=player.getDeltaMovement();
        var inputFrame=new FlightController.Input(position(),new Vec3(velocity.x,velocity.y,velocity.z),player.getYRot(),true,
                player.getAbilities().mayfly&&(player.getAbilities().flying||(player.onGround()&&goal!=null&&position().distanceTo(goal)<.3)),
                (!supportedLanding||nativeStanding(goal))&&segmentClear(controller.target()),
                player.onGround()&&nativeStanding(position())&&goal!=null&&nativeStanding(goal),System.currentTimeMillis());
        var out=controller.tick(inputFrame,
                config.get(TravelConfig.FLIGHT_MAX_SPEED),config.get(TravelConfig.FLIGHT_STALL_MS),client.options.sensitivity().get(),config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER));
        if(requireSupportedLanding&&supportedLeaf!=null)supportedLeaf.bind(inputFrame,out,controller.selectedLead());
        if(!fresh()) { stop();status="Flight lease changed during geometry read";return FlightController.State.BLOCKED; }
        if(publication!=null)publication.releaseAllNineOwned();else if(fresh())input.releaseAllMovement();
        status=controller.status();
        if(out.state()==FlightController.State.BLOCKED && player.getAbilities().mayfly && player.getAbilities().flying && regenerations++<1) {
            if(plan()) { status="Regenerated flight path";return FlightController.State.FLYING; }
        }
        if(out.state()==FlightController.State.FLYING) {
            if(requireSupportedLanding&&(!nativeStanding(goal)||!segmentClear(controller.target()))){stop();status="Supported flight native geometry changed before publication";return FlightController.State.BLOCKED;}
            if(!fresh()) { stop();return FlightController.State.BLOCKED; }
            ((dev.veltraph.client.mixin.FlightToggleAccessor)player).veltraph$clearFlightToggleWindow(0);
            if(publication!=null) {
                long ticket=requireSupportedLanding?publication.publishFlightYaw(out.yaw(),supportedLeaf):publication.publishYaw(out.yaw());
                if(ticket<=0||!publication.current()) { stop();return FlightController.State.BLOCKED; }
                boolean keys=requireSupportedLanding
                    ?publication.setFlightKey(LegacyTravelRetirement.Key.FORWARD,out.forward(),supportedLeaf)
                        &&publication.setFlightKey(LegacyTravelRetirement.Key.BACK,out.back(),supportedLeaf)
                        &&publication.setFlightKey(LegacyTravelRetirement.Key.LEFT,out.left(),supportedLeaf)
                        &&publication.setFlightKey(LegacyTravelRetirement.Key.RIGHT,out.right(),supportedLeaf)
                        &&publication.setFlightKey(LegacyTravelRetirement.Key.JUMP,out.up(),supportedLeaf)
                        &&publication.setFlightKey(LegacyTravelRetirement.Key.SNEAK,out.down(),supportedLeaf)
                    :publication.setForwardHeld(out.forward())&&publication.setBackHeld(out.back())&&publication.setLeftHeld(out.left())
                        &&publication.setRightHeld(out.right())&&publication.setJumpHeld(out.up())&&publication.setSneakHeld(out.down());
                if(!keys){stop();return FlightController.State.BLOCKED;}
            } else if(fresh()) {
                dev.veltraph.client.service.mc.McOwnedRotation.plan(client,out.yaw(),dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client));
                input.setForwardHeld(out.forward());input.setBackHeld(out.back());input.setLeftHeld(out.left());input.setRightHeld(out.right());input.setJumpHeld(out.up());input.setSneakHeld(out.down());
            }
        }
        return out.state();
    }
    /** Constructed only by this actuator with its actual Leg and observation. No caller boolean is authority. */
    final class SupportedLeaf {
        private final LegacyTravelPublicationLease.Leg leg;private final McSupportedFlightPreview.Observation observation;
        private FlightController.Input inputFrame;private FlightController.Output proposed;private Vec3 lead;private McNativeFlightGeometry.Budget proofBudget;private String lastPublicationDecision="NOT_CHECKED",lastPublicationStage="NOT_CHECKED";
        private final java.util.List<Object> configValues;private final java.util.List<Long> configRevisions;
        private final java.util.List<dev.veltraph.client.config.ConfigKey<?>> keys=java.util.List.of(TravelConfig.TRAVEL_APPROACH_MODE,
            TravelConfig.FLIGHT_MAX_SPEED,TravelConfig.FLIGHT_STALL_MS,TravelConfig.FLIGHT_SEARCH_RADIUS,TravelConfig.FLIGHT_NODE_BUDGET,RotationConfig.ROTATION_SPEED_MULTIPLIER);
        private Object nativeValue(dev.veltraph.client.config.ConfigKey<?> key){
            if(config instanceof dev.veltraph.client.config.MapConfigService map)return map.get(key);
            if(config instanceof dev.veltraph.client.config.JsonConfigService json)return json.get(key);
            return null;
        }
        private long nativeRevision(dev.veltraph.client.config.ConfigKey<?> key){
            if(config instanceof dev.veltraph.client.config.MapConfigService map)return map.revision(key).orElse(-1);
            if(config instanceof dev.veltraph.client.config.JsonConfigService json)return json.revision(key).orElse(-1);
            return -1;
        }
        private boolean nativePolicyCurrent(){
            if(dev.veltraph.client.VeltraphBootstrap.config()!=config||configValues.size()!=keys.size()||configRevisions.size()!=keys.size())return false;
            for(int n=0;n<keys.size();n++)if(!java.util.Objects.equals(configValues.get(n),nativeValue(keys.get(n)))||configRevisions.get(n)!=nativeRevision(keys.get(n)))return false;
            return nativeValue(TravelConfig.TRAVEL_APPROACH_MODE)!=ApproachMode.WALK_ONLY;
        }
        private SupportedLeaf(LegacyTravelPublicationLease.Leg leg,McSupportedFlightPreview.Observation observation){this.leg=leg;this.observation=observation;
            var values=new java.util.ArrayList<Object>();var revisions=new java.util.ArrayList<Long>();
            for(var key:keys){var value=nativeValue(key);long revision=nativeRevision(key);if(value==null||revision<0||revision==Long.MAX_VALUE){values.clear();revisions.clear();break;}values.add(value);revisions.add(revision);}
            configValues=java.util.List.copyOf(values);configRevisions=java.util.List.copyOf(revisions);
        }
        private void bind(FlightController.Input input,FlightController.Output output,Vec3 lead){inputFrame=input;proposed=output;this.lead=lead;proofBudget=new McNativeFlightGeometry.Budget();}
        /** Actual output and native inertia, including all headings, must fit fresh loaded geometry. */
        boolean publicationCurrent(){
            lastPublicationStage="OUTPUT_OR_INITIAL_NATIVE_SCOPE";lastPublicationDecision="OUTPUT_OR_INITIAL_NATIVE_SCOPE";
            var view=new McNativeFlightGeometry.ReadView(client,proofBudget);
            if(inputFrame==null||proposed==null||proposed.state()!=FlightController.State.FLYING||lead==null)return false;
            if(!current(view)){if(!view.refusalReason().isEmpty())lastPublicationDecision=view.refusalReason();return false;}
            var nativeMotion=client.player.getDeltaMovement();var motion=new Vec3(nativeMotion.x,nativeMotion.y,nativeMotion.z);
            lastPublicationStage="NATIVE_FRAME_CHANGED";lastPublicationDecision="NATIVE_FRAME_CHANGED";
            if(!position().equals(inputFrame.position())||!motion.equals(inputFrame.velocity())||client.player.getYRot()!=inputFrame.yaw())return false;
            lastPublicationStage="SELECTED_LEAD_OBSTRUCTED";lastPublicationDecision="SELECTED_LEAD_OBSTRUCTED";
            if(!McNativeFlightGeometry.sweptIn(client,position(),lead,0,view)){if(!view.refusalReason().isEmpty())lastPublicationDecision=view.refusalReason();return false;}
            // Existing production read-only native invoker includes actual block jump factor,
            // current effect and native float rounding; no modeled jump magnitude is granted.
            double jump=client.player instanceof dev.veltraph.client.mixin.FigJumpPowerAccessor nativeJump
                ?nativeJump.veltraph$sourceJumpPower(1f):Double.NaN;
            var bounds=FlightMotionEnvelope.bounds(position(),motion,proposed,observation.traits().flyingSpeed(),client.player.getSpeed(),observation.traits().movement(),observation.traits().drag(),client.player.onGround(),client.player.isSprinting(),client.player.shouldDiscardFriction(),jump);
            lastPublicationStage="MOTION_ENVELOPE";lastPublicationDecision="MOTION_ENVELOPE";if(!McNativeFlightGeometry.envelopeIn(client,bounds,goal,view)){if(!view.refusalReason().isEmpty())lastPublicationDecision=view.refusalReason();return false;}
            lastPublicationStage="FINAL_NATIVE_SCOPE";lastPublicationDecision="FINAL_NATIVE_SCOPE";
            // There are no public role/config/provider callbacks in the native geometry pass.
            // Recheck its original native fields and clock without repeating every geometry cell.
            if(!fieldsCurrent()||proofBudget==null||!proofBudget.spend(0)||!position().equals(inputFrame.position())
                ||!new Vec3(client.player.getDeltaMovement().x,client.player.getDeltaMovement().y,client.player.getDeltaMovement().z).equals(inputFrame.velocity())
                ||client.player.getYRot()!=inputFrame.yaw())return false;
            lastPublicationStage="ACCEPTED";lastPublicationDecision="ACCEPTED";return true;
        }
        /** Callback-free refusal only: all public provider/config callbacks precede this native leaf. */
        private boolean fieldsCurrent(){return requireSupportedLanding&&publication==leg&&supportedObservation==observation&&supportedLeaf==this
            &&(sharedDeadline==null||sharedDeadline.current())&&nativePolicyCurrent()&&McSupportedFlightPreview.current(client,observation)
            &&goal!=null&&goal.equals(observation.goal());}
        boolean current(){var budget=proofBudget==null?new McNativeFlightGeometry.Budget():proofBudget;
            return current(new McNativeFlightGeometry.ReadView(client,budget));}
        private boolean current(McNativeFlightGeometry.ReadView view){return fieldsCurrent()
            &&McNativeFlightGeometry.standingIn(client,goal,view)&&McNativeFlightGeometry.bodyIn(client,position(),0,view)
            &&fieldsCurrent()&&view.current();}
    }
    public void stop() {
        var own=publication;boolean ownStandalone=fresh()&&standalone;
        controller.reset();owner=null;ownerLevel=null;goal=null;supportedLanding=false;requireSupportedLanding=false;supportedObservation=null;supportedLeaf=null;supportedTerminalRadius=.35;sharedDeadline=null;coursePurpose=null;publication=null;standalone=false;
        if(own!=null) { own.releaseAllNineOwned();own.cancelCameraOwned(); }
        else if(ownStandalone)input.releaseAllMovement();
    }
    public String status() { return status+" | seed="+routeSeed+" replans="+regenerations+" target="+controller.target(); }
}
