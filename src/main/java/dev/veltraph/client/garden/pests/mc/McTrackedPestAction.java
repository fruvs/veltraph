package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.garden.pests.tracker.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.LocationText;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.mixin.GardenTrackerAttackInvoker;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McFlightNavigator;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.*;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.HitResult;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Source tracker click/particles -> identified live target -> ordinary movement/use -> native recount. */
final class McTrackedPestAction {
    enum State { IDLE, TRAVEL, ROOF_TRAVEL, RELOCATE, AIM_TRACKER, WAIT_PARTICLES, FOLLOW_PARTICLES, TARGET_PEST, WAIT_CAPTURE, WAIT_COUNTER, LAND_NEXT, COMPLETE, FAILED, CANCELLED }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final AssignedPlotTravelPort travel;private final BooleanSupplier leaseValid;
    private final PestInventoryLease tool;private final McFlightNavigator flight;private final McWorldService terrain;private final McGameContextService signals;
    private final McPestRoofTravel roof;private PestRoofBindings roofBindings;
    private final LegacyTravelPublicationLease publications;private LegacyTravelPublicationLease.Leg flightLeg;private long flightJourney;
    private LegacyTravelRetirement.RetirementOutcome flightRetirement;private boolean retirementBlocked,handoffRefused;private String retirementDiagnostic="";
    private LegacyTravelPublicationIdentity.State capturedParentContext;
    private java.util.function.Supplier<LegacyTravelPublicationIdentity.State> retiredCleanupProof=()->null;
    private State state=State.IDLE;private PestTrackedBindings bindings;private PestTrackerTrace trace;private PestPlotSignals.Census census;
    private Object owner,world,connection;private AssignedPlotTravelPort.Target destination;private PestBoxGeometry.Plot plot;private Entity target;private Vec3 flyingGoal;
    private int bag,removed,initialTotal;private long deadline,next,lastReceipt;private boolean captureAck,flying;private String reason="Idle";private final Map<Integer,Integer> requests=new HashMap<>();
    McTrackedPestAction(Minecraft client,FarmServices services,ConfigService config,AssignedPlotTravelPort travel,BooleanSupplier leaseValid){this.client=client;this.services=services;this.config=config;this.travel=travel;this.leaseValid=leaseValid;
        publications=new LegacyTravelPublicationLease(client,services,config,()->config.revision(PestTrackerConfig.PEST_TRACKED_BINDINGS).orElse(-1));
        tool=new PestInventoryLease(client);flight=new McFlightNavigator(client,services.input(),config);terrain=new McWorldService(client);signals=new McGameContextService(client);roof=new McPestRoofTravel(client,services,config,leaseValid);}
    private long tick(){return client.player==null?0:client.player.tickCount;}private long now(){return tick()*50L;}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private Optional<PestPlotSignals.Census> readCensus(){signals.resolution();var s=signals.signals();return PestPlotSignals.parse(bindings.signals(),s.tabLinesRaw(),s.sidebarRowsRaw(),bindings.maxTotalPests());}
    Optional<String> ready(PestTrackedBindings bindings){this.bindings=bindings;
        if(client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||!client.player.isAlive()||client.player.isSpectator()||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Healthy native owner, closed inventory and empty cursor required");
        int slot=PestItems.uniqueSlot(client,bindings.cleaner().vacuum());if(slot<0||client.player.getInventory().getItem(slot).getCount()!=1||PestItems.counter(client,bindings.cleaner().vacuum(),bindings.cleaner().bagPrefix()).isEmpty()
            ||!PestItems.lore(client.player.getInventory().getItem(slot)).contains(bindings.trackerLoreLine()))return Optional.of("Exactly one identified vacuum, bag count and observed tracker upgrade lore required");
        if(config.get(PestTrackerConfig.PESTS_USE_ROOF_ETHERWARPING)) {
            try{roofBindings=PestBindings.read(config.get(PestTrackerConfig.PEST_ROOF_BINDINGS),PestRoofBindings.class);}catch(RuntimeException bad){return Optional.of("Exact observed roof contract required: "+bad.getMessage());}
            if(!roof.toolReady())return Optional.of("Roof Etherwarp requires the native hotbar Ether Transmission ability item");
        } else roofBindings=null;
        var census=readCensus();return census.isEmpty()||census.get().total()<1?Optional.of("Received exact native tab plot IDs/global counter and permitted observed spawns required"):Optional.empty();
    }
    boolean start(PestTrackedBindings bindings){resetCaptureDiagnostics();nearbyAdmission=null;if(!freshJourneyAllowed()||ready(bindings).isPresent())return false;owner=client.player;world=client.level;connection=client.getConnection();census=readCensus().orElseThrow();initialTotal=census.total();removed=0;requests.clear();
        if(!stopFlight(false))return false;bag=PestItems.counter(client,bindings.cleaner().vacuum(),bindings.cleaner().bagPrefix()).orElseThrow();trace=new PestTrackerTrace(bindings.trace());target=null;flying=false;if(!roof.reset()){retirementBlocked=true;state=State.FAILED;reason="MISMATCH: "+roof.status();return false;}return selectPlot();}
    /** Same business proof, genuine already-nearby model: no speculative particle click or teleport. */
    boolean startNearby(PestTrackedBindings b,McNearbyPestAdmission.Admission admission){
        resetCaptureDiagnostics();
        if(admission==null||!freshJourneyAllowed()||ready(b).isPresent())return false;
        var selected=admission.target(b);if(selected==null)return false;
        owner=client.player;world=client.level;connection=client.getConnection();census=readCensus().orElse(null);
        if(census==null||census.total()<1)return false;
        var matches=census.plots().stream().filter(p->p.plotId()==admission.plot()).toList();if(matches.size()!=1)return false;
        plot=matches.getFirst();destination=new AssignedPlotTravelPort.Target(plot.plotId(),plot.spawn(),plot.yaw(),plot.pitch());
        initialTotal=census.total();removed=0;requests.clear();bag=PestItems.counter(client,b.cleaner().vacuum(),b.cleaner().bagPrefix()).orElseThrow();
        if(!stopFlight(false)||!roof.reset())return false;trace=new PestTrackerTrace(b.trace());target=selected;flying=false;
        state=State.RELOCATE;deadline=tick()+b.requestTimeoutTicks();nearbyAdmission=admission;
        try{if(!tool.begin(b.cleaner().vacuum(),config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT))){fail("Nearby original inventory relocation refused");return false;}}
        catch(RuntimeException unknown){fail("Nearby original inventory relocation did not return normally");return false;}
        reason="Exact nearby native pest entered existing capture flow";return true;
    }
    private McNearbyPestAdmission.Admission nearbyAdmission;
    /** Diagnostic-only caches from original query/assignment sites; never native permission or fresh observation. */
    private final Map<String,Object> captureRequest=new LinkedHashMap<>(),captureFrame=new LinkedHashMap<>();
    private int captureTargetId=-1;
    private void resetCaptureDiagnostics(){captureRequest.clear();captureFrame.clear();captureTargetId=-1;}
    private void cached(String key,Object value){captureFrame.put(key,value);}
    private int cacheTargetId(int value){captureTargetId=value;return value;}
    private void beginCaptureFrame(){captureFrame.clear();cached("phase",state.name());
        for(String key:List.of("toolActive","sameNativeEntity","alive","gone","targetValid","bodyDistance","lineOfSight","aimWantedYaw","aimWantedPitch","aimObservedYaw","aimObservedPitch","aimYawAligned","aimPitchAligned","aimAligned","bagBefore","bagCurrent","ackPredicate","holdSampleTick","holdDeadline","useSetterRequested","useSetterReturned","targetId","targetIdentity","aimQueryPhase"))cached(key,"UNKNOWN_NOT_QUERIED");}

    private boolean scope(){return client.player==owner&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&!client.player.isSpectator()
        &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean selectPlot(){var selected=census.next(position());if(selected.isEmpty()){fail("Positive pest count has no permitted source plot");return false;}plot=selected.get();destination=new AssignedPlotTravelPort.Target(plot.plotId(),plot.spawn(),plot.yaw(),plot.pitch());
        if(!travel.start(destination,GameArea.GARDEN)){fail("Authoritative source plot-spawn request refused");return false;}state=State.TRAVEL;deadline=tick()+bindings.requestTimeoutTicks();reason="Awaiting authoritative plot "+plot.plotId()+" arrival";return true;}
    void tickAction(){if(retirementBlocked||handoffRefused||!active())return;
        if(nearbyAdmission!=null){boolean source=nearbyAdmission.validSource();captureRequest.put("lastNearbySourceQuery",source);if(!source){fail("Original nearby source policy/native scene changed");return;}}
        boolean scoped=scope();captureRequest.put("lastScopeQuery",scoped);captureRequest.put("lastLeaseQuery","UNKNOWN_NOT_QUERIED");
        if(!scoped){fail("Tracker player/world/connection/menu or maintenance lease changed");return;}
        boolean leased=leaseValid.getAsBoolean();captureRequest.put("lastLeaseQuery",leased);if(!leased){fail("Tracker player/world/connection/menu or maintenance lease changed");return;}
        long stageTick=tick();captureRequest.put("lastStageQueryTick",stageTick);captureRequest.put("lastStageQueryPhase",state.name());captureRequest.put("lastStageDeadline",deadline);
        if(stageTick>deadline&&state!=State.TARGET_PEST&&state!=State.LAND_NEXT){fail("Tracker stage outcome timed out; no uncertain retry");return;}
        switch(state){
            case TRAVEL->{var arrival=travel.tick(destination,GameArea.GARDEN);if(arrival==AssignedPlotTravelRuntime.State.WAITING_ARRIVAL)return;
                if(arrival!=AssignedPlotTravelRuntime.State.ARRIVED){fail("Wrong/silent native plot arrival");return;}travel.cancel();services.rotation().cancelRotation();
                // Roof evacuation is relevant only after authoritative arrival in the selected infested plot.
                // A currently reachable, identified live pest can be captured without acquiring a roof.
                var visible=nearModel(position(),bindings.cleaner().reach());
                if(roofBindings!=null&&(visible==null||!client.player.hasLineOfSight(visible))) {
                    if(!stopFlight())return;if(!roof.start(roofBindings,plot.plotId(),true)){fail("Source roof acquisition refused: "+roof.status());return;}
                    if(roof.tick()!=McPestRoofTravel.State.UNNEEDED){state=State.ROOF_TRAVEL;deadline=tick()+bindings.requestTimeoutTicks();return;}
                }
                beginVacuum();}
            case ROOF_TRAVEL->{var progress=roof.tick();if(progress==McPestRoofTravel.State.ACTIVE)return;
                if(progress!=McPestRoofTravel.State.ARRIVED||!GardenPlotBounds.contains(plot.plotId(),client.player.getX(),client.player.getZ())){fail("Unconfirmed/wrong roof arrival: "+roof.status());return;}beginVacuum();}
            case RELOCATE->{if(nearbyAdmission!=null&&nearbyAdmission.target(bindings)!=target){fail("Nearby pest visibility/range/identity changed before capture");return;}if(!tool.ready())return;
                state=nearbyAdmission==null?State.AIM_TRACKER:State.TARGET_PEST;deadline=tick()+bindings.requestTimeoutTicks();}
            case AIM_TRACKER->{if(!tool.activeTool()||!GardenPlotBounds.contains(plot.plotId(),client.player.getX(),client.player.getZ())){fail("Tracker held tool or selected plot changed");return;}
                services.rotation().track(plot.yaw(),bindings.activationPitch());if(Math.abs(WalkController.wrap(client.player.getYRot()-plot.yaw()))>2||Math.abs(client.player.getXRot()-bindings.activationPitch())>2)return;
                if(client.hitResult==null||client.hitResult.getType()!=HitResult.Type.MISS)return;
                if(((GardenTrackerAttackInvoker)client).veltraph$gardenTrackerMissTime()>0)return;
                if(!beginTrackerClick()){fail("Immediate owned native air-click proof refused");return;}}
            case WAIT_PARTICLES->{if(!tool.activeTool()){fail("Tracker tool changed while awaiting source particles");return;}receiveParticles();
                if(associateLatest())return;var goal=trace.goal();if(goal.isEmpty())return;Vec3 ray=rayGoal(goal.get());if(ray==null){fail("Source particle ray has no loaded open point");return;}
                var candidate=nearModel(ray,7);if(candidate!=null){target=candidate;state=State.TARGET_PEST;deadline=tick()+bindings.requestTimeoutTicks();return;}
                if(!move(ray)){fail("No server-permitted loaded flight route along source particle direction");return;}state=State.FOLLOW_PARTICLES;}
            case FOLLOW_PARTICLES->{var candidate=nearModel(flyingGoal,7);if(candidate!=null){target=candidate;if(!stopFlight())return;state=State.TARGET_PEST;deadline=tick()+bindings.requestTimeoutTicks();return;}
                var s=flight.tick();if(s==FlightController.State.BLOCKED){fail("Source particle flight blocked");return;}if(s==FlightController.State.ARRIVED){if(!stopFlight())return;state=State.AIM_TRACKER;deadline=tick()+bindings.requestTimeoutTicks();}}
            case TARGET_PEST->targetPest();
            case WAIT_CAPTURE->verifyCapture();
            case WAIT_COUNTER->{var current=readCensus();if(current.isEmpty())return;if(current.get().total()==census.total())return;
                if(current.get().total()!=census.total()-1||removed+current.get().total()!=initialTotal){fail("Native global pest counter changed without the exact owned removal");return;}census=current.get();
                if(client.player.getAbilities().flying){if(!move(plot.spawn())){fail("Supported ordinary landing before next source plot refused");return;}state=State.LAND_NEXT;deadline=tick()+bindings.requestTimeoutTicks();}
                else afterLanding();}
            case LAND_NEXT->{var s=flight.tick();if(s==FlightController.State.BLOCKED||tick()>deadline){fail("Supported source plot landing failed");return;}
                if(s==FlightController.State.ARRIVED&&client.player.onGround()&&!client.player.getAbilities().flying){if(!stopFlight())return;afterLanding();}}
            default->{}
        }
    }
    private void beginVacuum(){if(!stopFlight())return;if(!retireRoof())return;if(!tool.begin(bindings.cleaner().vacuum(),config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT))){fail("Owned tracker inventory relocation refused");return;}state=State.RELOCATE;deadline=tick()+bindings.requestTimeoutTicks();}
    /** Same-tick concrete lease and native owner/tool/cursor/MISS proof precede vanilla startAttack. */
    boolean beginTrackerClick(){
        if(!scope()||!leaseValid.getAsBoolean()||!tool.activeTool()||client.hitResult==null||client.hitResult.getType()!=HitResult.Type.MISS||((GardenTrackerAttackInvoker)client).veltraph$gardenTrackerMissTime()>0)return false;
        int count=requests.getOrDefault(plot.plotId(),0);if(count>=bindings.maxRequestsPerPlot())return false;
        int slot=PestItems.uniqueSlot(client,bindings.cleaner().vacuum());if(slot<0||!PestItems.lore(client.player.getInventory().getItem(slot)).contains(bindings.trackerLoreLine()))return false;
        lastReceipt=GardenTrackerParticles.serial();trace.begin(owner,world,connection,position(),lastReceipt,tick());captureAck=false;
        // MISS normally returns false after its native swing. The received source particle/effect,
        // rather than this return value or client animation, is the request's outcome witness.
        var binding=new McPestTrackerAirClick.Binding(owner,world,connection,client.player.getInventory().getSelectedSlot(),plot.plotId(),bindings.cleaner().vacuum(),client.player.getMainHandItem());
        if(!McPestTrackerAirClick.click(client,binding,leaseValid)){trace.clear();return false;}
        requests.put(plot.plotId(),count+1);state=State.WAIT_PARTICLES;deadline=tick()+bindings.requestTimeoutTicks();reason="One native left-click; waiting exact scoped tracker particle trace";return true;
    }
    private void receiveParticles(){for(var receipt:GardenTrackerParticles.read(owner,world,connection,now()))if(receipt.serial()>lastReceipt){trace.receive(owner,world,connection,position(),receipt,tick());lastReceipt=receipt.serial();}trace.tick(tick());}
    private boolean associateLatest(){var point=trace.latest();if(point.isEmpty()||position().distanceTo(point.get())>=25)return false;var model=nearModel(point.get(),3);if(model==null)return false;
        target=model;state=State.TARGET_PEST;deadline=tick()+bindings.requestTimeoutTicks();reason="Fresh source tracker endpoint associated with identified live pest";return true;}
    private List<Entity> models(){var result=new ArrayList<Entity>();for(var entity:client.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.getHealth()>0&&entity.getY()>=62&&entity.getY()<92
        &&PestItems.entityMatches(entity,bindings.cleaner().pest())&&GardenPlotBounds.contains(plot.plotId(),entity.getX(),entity.getZ())&&terrain.isLoaded(new Vec3(entity.getX(),entity.getY(),entity.getZ()).toBlockPos()))result.add(entity);return result;}
    private Entity nearModel(Vec3 point,double distance){Entity best=null;double nearest=distance;for(var model:models()){var eye=model.getEyePosition();double d=new Vec3(eye.x,eye.y,eye.z).distanceTo(point);
        if(d<nearest){best=model;nearest=d;}}return best;}
    private Vec3 rayGoal(PestTrackerTrace.Goal goal){Vec3 safe=goal.origin();for(int i=1;i<bindings.raySteps();i++){var d=goal.direction();var candidate=new Vec3(goal.origin().x()+d.x()*i,goal.origin().y()+d.y()*i,goal.origin().z()+d.z()*i);
        if(candidate.y()<68||!GardenPlotBounds.contains(plot.plotId(),candidate.x(),candidate.z())||!terrain.isLoaded(candidate.toBlockPos())||!terrain.bodyClear(candidate,.3,1.8))break;
        var found=nearModel(candidate,7);if(found!=null){var eye=found.getEyePosition();return new Vec3(eye.x,eye.y,eye.z);}safe=candidate;}return safe;}
    private boolean targetValid(){return target instanceof ArmorStand stand&&stand.getHealth()>0&&target.getY()>=62&&target.getY()<92&&client.level.getEntity(cacheTargetId(target.getId()))==target&&target.isAlive()&&PestItems.entityMatches(target,bindings.cleaner().pest())
        &&GardenPlotBounds.contains(plot.plotId(),target.getX(),target.getZ())&&terrain.isLoaded(new Vec3(target.getX(),target.getY(),target.getZ()).toBlockPos());}
    /** Helper owns publication cleanup; this workflow retires its exact leg before each handoff. */
    private boolean stopFlight(){return stopFlight(true);}
    private boolean stopFlight(boolean handoff){var old=flightLeg;RuntimeException stopFailure=null;boolean retireFailure=false;
        try{flight.stop();}catch(RuntimeException failed){stopFailure=failed;}
        finally{try{if(old!=null){try{flightRetirement=old.retireOwned();}catch(RuntimeException failed){retireFailure=true;flightRetirement=old.retirementOutcome();
                    if(flightRetirement==null)flightRetirement=LegacyTravelRetirement.notRetired(true,true);retirementBlocked=true;}}}
            finally{if(old!=null)retiredCleanupProof=old::retiredCleanupParentContext;if(flightLeg==old){flightLeg=null;flying=false;flyingGoal=null;}}}
        if(stopFailure!=null||retireFailure||flightRetirement!=null&&!flightRetirement.cleanSuccess()){
            retirementBlocked=true;retirementDiagnostic="MISMATCH: flight retirement "+flightRetirement;
            state=State.FAILED;reason=retirementDiagnostic;
            if(old!=null&&old.postRetirementOwnParentContext()!=null)throw new IllegalStateException(retirementDiagnostic);
        }
        if(!retirementBlocked&&handoff&&old!=null&&old.retirementContinuationContext()==null){
            handoffRefused=true;retirementDiagnostic="REVOKED: CLEAN flight retirement cannot authorize this handoff";state=State.FAILED;reason=retirementDiagnostic;
            if(retiredCleanupProof.get()==null){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed flight Parent scope superseded";state=State.FAILED;reason=retirementDiagnostic;}
        }
        return !retirementBlocked&&(!handoff||!handoffRefused);}
    private boolean freshJourneyAllowed(){if(retirementBlocked)return false;
        if(!sameCapturedParentCurrent()){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed flight Parent changed before fresh journey";state=State.FAILED;reason=retirementDiagnostic;return false;}
        handoffRefused=false;return true;}
    /** Fresh workflow admission keeps the originally registered Parent and scene; a CLEAN receipt grants no new owner. */
    private boolean sameCapturedParentCurrent(){var captured=capturedParentContext;if(captured==null)return true;
        if(!client.isSameThread()||!(captured.parent() instanceof dev.veltraph.client.core.Module parent))return false;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return root==captured.root()&&root.get(parent.id()).orElse(null)==parent&&root.inputOwner().orElse(null)==parent
            &&parent.isEnabled()&&parent.ownsGameplayInput()&&client.player==captured.player()&&client.level==captured.world()
            &&client.getConnection()==captured.connection()&&dev.veltraph.client.VeltraphBootstrap.config()==captured.config()
            &&services.rotation()==captured.service()&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==parent
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.inputOwner().orElse(null)==parent;
    }
    private boolean move(Vec3 point){if(!stopFlight()||!retireRoof())return false;if(retirementBlocked||!scope()||!leaseValid.getAsBoolean())return false;
        flyingGoal=point;flightLeg=publications.begin(++flightJourney);if(flightLeg==null)return false;if(capturedParentContext==null)capturedParentContext=flightLeg.commandContext();
        flying=flight.start(point,java.util.concurrent.ThreadLocalRandom.current().nextLong(),flightLeg);
        if(!flying||!scope()||!leaseValid.getAsBoolean()||!flightLeg.current()){stopFlight();return false;}return true;}
    private void targetPest(){if(nearbyAdmission!=null&&nearbyAdmission.target(bindings)!=target){fail("Original nearby target/census/visibility changed before native capture");return;}if(!targetValid()||!tool.activeTool()){fail("Tracked UUID/model, loaded plot or tool changed before capture");return;}
        if(tick()>deadline){fail("Identified moving pest could not be reached within bounded chase");return;}
        if(client.player.distanceTo(target)>bindings.cleaner().reach()||!client.player.hasLineOfSight(target)){
            var p=target.position();var goal=new Vec3(p.x-1,p.y,p.z);if(!flying||flyingGoal.distanceTo(goal)>2){if(!move(goal)){fail("No authorized loaded route to tracked moving pest");return;}}
            if(flight.tick()==FlightController.State.BLOCKED){fail("Moving pest approach blocked");return;}return;}
        if(!stopFlight())return;if(!aim())return;
        boolean unrelatedEntity=false,interactiveBlock=false;captureRequest.put("requestRayEntity","UNKNOWN_NOT_QUERIED");captureRequest.put("requestRayBlock","UNKNOWN_NOT_QUERIED");
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit){var actual=hit.getEntity();unrelatedEntity=actual!=target;captureRequest.put("requestRayEntity",actual==target?"EXACT_TARGET":"OTHER_ENTITY");captureRequest.put("requestRayEntityIdentity",System.identityHashCode(actual));}
        if(!unrelatedEntity&&client.hitResult instanceof net.minecraft.world.phys.BlockHitResult block){var actual=client.level.getBlockEntity(block.getBlockPos());interactiveBlock=actual!=null;captureRequest.put("requestRayBlock",interactiveBlock?"INTERACTIVE":"NON_INTERACTIVE");}
        if(unrelatedEntity||interactiveBlock){fail("Capture ray intersects unrelated interactive target");return;}
        captureAck=false;long requestTick=tick();next=requestTick+Math.max(1,bindings.cleaner().holdMs()/50);
        captureRequest.put("captureRequestTick",requestTick);captureRequest.put("captureHoldDeadline",next);captureRequest.put("captureStageDeadline",deadline);captureRequest.put("captureTargetId",captureTargetId);captureRequest.put("captureTargetIdentity",System.identityHashCode(target));captureRequest.put("appliedAckLatch",false);captureRequest.put("bagAtRequest",bag);
        captureRequest.put("useSetterRequested",true);captureRequest.put("useSetterReturned",false);McHeldKeys.setHeld(client.options.keyUse,true);captureRequest.put("useSetterReturned",true);state=State.WAIT_CAPTURE;
    }
    private boolean aim(){cached("aimQueryPhase",state.name());var d=target.getBoundingBox().getCenter().subtract(client.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        cached("aimWantedYaw",yaw);cached("aimWantedPitch",pitch);cached("aimObservedYaw","UNKNOWN_NOT_QUERIED");cached("aimObservedPitch","UNKNOWN_NOT_QUERIED");cached("aimPitchAligned","UNKNOWN_NOT_QUERIED");
        services.rotation().track(yaw,pitch);float actualYaw=client.player.getYRot();boolean yawAligned=Math.abs(WalkController.wrap(actualYaw-yaw))<2;
        cached("aimObservedYaw",actualYaw);cached("aimYawAligned",yawAligned);if(!yawAligned){cached("aimAligned",false);return false;}
        float actualPitch=client.player.getXRot();boolean pitchAligned=Math.abs(actualPitch-pitch)<2;cached("aimObservedPitch",actualPitch);cached("aimPitchAligned",pitchAligned);cached("aimAligned",pitchAligned);return pitchAligned;}
    private void verifyCapture(){beginCaptureFrame();boolean active=tool.activeTool();cached("toolActive",active);if(!active){fail("Tracked vacuum changed during use hold");return;}
        int queriedId=target.getId();var nativeEntity=client.level.getEntity(queriedId);boolean same=nativeEntity==target;cached("targetId",queriedId);cached("targetIdentity",System.identityHashCode(target));cached("sameNativeEntity",same);
        boolean gone;if(!same)gone=true;else{boolean alive=target.isAlive();cached("alive",alive);gone=!alive;}cached("gone",gone);
        if(gone){cached("useSetterRequested",false);McHeldKeys.setHeld(client.options.keyUse,false);cached("useSetterReturned",true);}
        else{
            boolean valid=targetValid();cached("targetValid",valid);if(!valid){fail("Tracked pest left owned range/plot during use");return;}
            float distance=client.player.distanceTo(target);cached("bodyDistance",distance);if(distance>bindings.cleaner().reach()){fail("Tracked pest left owned range/plot during use");return;}
            boolean visible=client.player.hasLineOfSight(target);cached("lineOfSight",visible);if(!visible){fail("Tracked pest left owned range/plot during use");return;}
            boolean aligned=aim();cached("useSetterRequested",aligned);McHeldKeys.setHeld(client.options.keyUse,aligned);cached("useSetterReturned",true);
        }
        int current=PestItems.counter(client,bindings.cleaner().vacuum(),bindings.cleaner().bagPrefix()).orElse(-1);cached("bagBefore",bag);cached("bagCurrent",current);if(current<bag||current>bag+1){fail("Unexpected tracked vacuum bag delta");return;}
        boolean acknowledged=false;if(gone){acknowledged=captureAck;cached("ackPredicate",acknowledged);}
        if(gone&&acknowledged&&current==bag+1){nearbyAdmission=null;McHeldKeys.setHeld(client.options.keyUse,false);bag=current;removed++;tool.restore();target=null;trace.clear();state=State.WAIT_COUNTER;deadline=tick()+bindings.requestTimeoutTicks();return;}
        long holdTick=tick();cached("holdSampleTick",holdTick);cached("holdDeadline",next);if(holdTick>=next)fail("Same pest removal/bag/message outcome withheld; stopped native hold");
    }
    private void afterLanding(){if(census.total()==0){if(!cleanup()){state=State.FAILED;reason="MISMATCH: terminal pest cleanup incomplete | "+status();return;}
        state=State.COMPLETE;reason="All native listed pest plots cleared with exact bag/global-count outcomes";return;}selectPlot();}
    void message(String line){travel.onServerMessage(line);if(state==State.WAIT_CAPTURE){boolean matched=bindings.cleaner().captureAck().equals(LocationText.stripColorCodes(line));captureRequest.put("lastReceivedAckMatch",matched);if(matched){captureAck=true;captureRequest.put("appliedAckLatch",true);}}}
    boolean consumeExpectedTeleport(Vec3 position,long now){return active()&&(roof.consumeExpectedTeleport(position,now)||travel.consumeExpectedTeleport(position,now));}
    private boolean retireRoof(){return retireRoof(true);}
    private boolean retireRoof(boolean handoff){boolean clean=handoff?roof.cancelForHandoff():roof.cancel();if(!clean){handoffRefused=true;retirementBlocked|=roof.retirementMismatch();state=State.FAILED;reason=roof.status();}return clean;}
    private boolean cleanup(){try{boolean flightClean=false,roofClean=false;try{flightClean=stopFlight(false);}finally{roofClean=retireRoof(false);}
        if(!flightClean||!roofClean)return false;
        McHeldKeys.setHeld(client.options.keyUse,false);McHeldKeys.setHeld(client.options.keyAttack,false);travel.cancel();services.rotation().cancelRotation();tool.restore();return true;}
        finally{if(trace!=null)trace.clear();target=null;flyingGoal=null;nearbyAdmission=null;}}
    private void fail(String text){state=State.FAILED;reason=text;cleanup();}
    void cancel(){if(!active())return;state=State.CANCELLED;reason="Tracked pest maintenance stopped";cleanup();}
    boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED&&state!=State.CANCELLED;}
    boolean complete(){return state==State.COMPLETE;}State state(){return state;}int removed(){return removed;}int requests(){return requests.values().stream().mapToInt(Integer::intValue).sum();}
    String status(){return state+": "+reason+" | "+retirementDiagnostic+" | "+travel.status()+" | "+roof.status()+" | "+flight.status()+" | cachedOriginalCapture request="+captureRequest+" frame="+captureFrame;}
}
