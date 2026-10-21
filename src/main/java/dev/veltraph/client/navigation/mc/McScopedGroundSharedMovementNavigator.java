package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Explicit dry-ground component. The existing McSharedMovementNavigator mode union is untouched.
 * Admission and ordinary native outputs belong to one concrete publication Leg. Maintenance camera
 * pairs require the separate Lease G composition and are refused in this generic-Root component. */
public final class McScopedGroundSharedMovementNavigator implements SharedMovementCoordinator.Port {
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private McNavigator ground;private McNavigator candidateGround;private final SharedMovementCoordinator coordinator;
    private final LegacyTravelPublicationLease publicationLease;
    private LegacyTravelPublicationLease.Leg publicationLeg,lastRetiredPublicationLeg;
    private boolean handoffIssued;
    private LegacyTravelRetirement.RetirementOutcome groundRetirement;
    private boolean groundResetFailed;private SharedMovementPolicy.Options groundOptions;
    private Module parent;private Object cameraOwner;private McFarmMaintenanceAuthorityBridge.Binding maintenanceAuthority;private DynamicRouteConfigStamp configStamp;private long epoch;
    private int slot;private ItemStack held;private Object generation,geometry;
    private SharedMovementCoordinator.Lease lease;
    private SharedMovementPolicy.Candidate prepared;private boolean cursorPrepared;
    private long journey;
    private int maxNodes=2500,searchRadius=32;private boolean preferLevel;
    private long rotationGeneration;
    public McScopedGroundSharedMovementNavigator(Minecraft client,FarmServices services,ConfigService config){
        this.client=client;this.services=services;this.config=config;
        publicationLease=new LegacyTravelPublicationLease(client,services,config,
            ()->config.revision(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));
        var dormant=new DormantGroundPublication(()->owned(lease));
        ground=new McNavigator(client,dormant,config,dormant);
        coordinator=new SharedMovementCoordinator(this);
    }
    public boolean consumeExpectedTeleport(Vec3 position,long now){return false;}
    public boolean permitsSelectedSlot(int expectedOriginal){return false;}
    public boolean start(Vec3 goal,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,2500,32,radius,purpose);}
    public boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,maxNodes,searchRadius,radius,purpose,false);}
    public boolean startGround(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){
        return start(goal,maxNodes,searchRadius,radius,purpose==SharedMovementPolicy.Purpose.GENERAL_TRAVEL?SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL:purpose,false);
    }
    public boolean startPreferLevel(Vec3 goal,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,2500,32,radius,purpose,true);}
    public boolean startPreferLevel(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,maxNodes,searchRadius,radius,purpose,true);}
    public boolean startExpected(Vec3 goal,int maxNodes,int searchRadius,double radius,
            SharedMovementPolicy.Purpose purpose,Module expectedParent){
        if(expectedParent==null)return false;
        return start(goal,maxNodes,searchRadius,radius,purpose,false,expectedParent);
    }
    private boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose,boolean preferLevel){
        return start(goal,maxNodes,searchRadius,radius,purpose,preferLevel,null);
    }
    private boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose,
            boolean preferLevel,Module expectedParent){
        stop();if(retirementRefused())return false;
        if(!SharedMovementPolicy.finite(goal)||maxNodes<1||maxNodes>5000||searchRadius<1||searchRadius>50||purpose==null||!Double.isFinite(radius)||radius<=0||radius>.35)return false;
        this.maxNodes=maxNodes;this.searchRadius=searchRadius;this.preferLevel=preferLevel;
        var modules=VeltraphBootstrap.moduleManager();
        parent=modules==null?null:modules.inputOwner().orElse(null);
        if(expectedParent!=null&&parent!=expectedParent)return false;
        configStamp=DynamicRouteConfigStamp.capture(config).orElse(null);
        if(parent==null||configStamp==null||client.player==null||client.level==null||client.getConnection()==null)return false;
        cameraOwner=parent;maintenanceAuthority=null;
        var maintenance=VeltraphBootstrap.farmMaintenance();
        if(parent instanceof dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant&&maintenance!=null&&maintenance.active()){
            maintenanceAuthority=McFarmMaintenanceAuthorityBridge.capture(client,parent,config,configStamp,purpose,goal).orElse(null);
            if(maintenanceAuthority==null)return false;
            cameraOwner=maintenanceAuthority.cameraOwner();
        }
        epoch=McOwnedRotation.epoch(client);
        if(!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService))return false;
        rotationGeneration=((dev.veltraph.client.service.mc.McRotationService)services.rotation()).requestGeneration();
        slot=services.inventory().selectedSlot();
        if(slot<0||slot>8)return false;held=client.player.getInventory().getItem(slot).copy();generation=new Object();journey++;
        lease=new SharedMovementCoordinator.Lease(parent,client.player,client.level,client.getConnection(),parent,cameraOwner,config,configStamp.approachRevision(),epoch);
        // This explicitly scoped component supplies dry ground only.
        if(cameraOwner!=parent)return false; // Lease G must qualify the issued maintenance role pair.
        publicationLeg=publicationLease.beginExpected(journey,parent);
        if(publicationLeg==null)return false;
        lastRetiredPublicationLeg=null;handoffIssued=false;
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
        var allowed=Set.of(SharedMovementPolicy.Mode.WALK);
        var options=new SharedMovementPolicy.Options(allowed,purpose,0,120000,radius,1.25);
        groundOptions=options;groundRetirement=null;groundResetFailed=false;
        return coordinator.start(lease,goal,options);
    }
    public McTravelNavigator.State tick(){
        var result=coordinator.tick();return switch(result){case ARRIVED->McTravelNavigator.State.ARRIVED;
            case MOVING,SELECTING,WAITING_RECEIPT->McTravelNavigator.State.ACTIVE;default->McTravelNavigator.State.FAILED;};
    }


    public boolean hasLeg(){return lease!=null;}
    public boolean hasActiveLeg(){return switch(coordinator.state()){case SELECTING,MOVING,WAITING_RECEIPT->true;default->false;};}
    public boolean ownsCurrentGeneration(){return owned(lease);}

    public String status(){return "Shared movement: "+coordinator.status()+" | "+ground.diagnostics();}
    public void stop(){try{coordinator.stop();}finally{cursorPrepared=false;prepared=null;geometry=null;lease=null;parent=null;cameraOwner=null;maintenanceAuthority=null;held=null;configStamp=null;generation=null;publicationLeg=null;candidateGround=null;groundOptions=null;}}
    @Override public long now(){return System.currentTimeMillis();}
    @Override public boolean owned(SharedMovementCoordinator.Lease expected){
        return expected!=null&&expected==lease&&groundCustodyCurrent()&&nativeOwned(expected,epoch);
    }
    private boolean nativeOwned(SharedMovementCoordinator.Lease expected,long expectedEpoch){
        if(expected==null||expected!=lease||parent==null||generation==null||!client.isSameThread()||!groundCustodyCurrent())return false;
        var modules=VeltraphBootstrap.moduleManager();
        return modules!=null&&modules.get(parent.id()).orElse(null)==parent&&parent.isEnabled()
            &&modules.inputOwner().orElse(null)==parent
            &&dev.veltraph.client.input.ScriptingInputLock.isActive()
            &&parent.id().equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId())&&McOwnedRotation.installed(client)
            &&McOwnedRotation.authority(client)==cameraOwner&&McOwnedRotation.epoch(client)==expectedEpoch
            &&(maintenanceAuthority==null||expected.inputOwner()==parent&&expected.cameraOwner()==cameraOwner
                &&McFarmMaintenanceAuthorityBridge.current(maintenanceAuthority,client,config))
            &&rotationGeneration==((dev.veltraph.client.service.mc.McRotationService)services.rotation()).requestGeneration()
            &&client.player==expected.player()&&client.level==expected.world()&&client.getConnection()==expected.connection()
            &&client.player.isAlive()&&!client.isPaused()&&client.gui.screen()==null&&client.getCameraEntity()==client.player
            &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()
            &&!client.player.isScoping()&&!client.level.tickRateManager().isEntityFrozen(client.player)
            &&VeltraphBootstrap.config()==config&&configStamp!=null&&configStamp.current(config)
            &&slotCurrent()&&groundCustodyCurrent();
    }
    private boolean slotCurrent(){return services.inventory().selectedSlot()==slot&&ItemStack.matches(held,client.player.getInventory().getItem(slot));}
    @Override public List<SharedMovementPolicy.Candidate> candidates(Vec3 goal,SharedMovementPolicy.Options options,int uses){
        if(!owned(lease)||!SharedMovementPolicy.finite(goal))return List.of();
        var accepted=new ArrayList<SharedMovementPolicy.Candidate>();
        var origin=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        // Geometry installs only a private dormant cursor. It has no native publication capability.
        var dormant=new DormantGroundPublication(()->owned(lease));
        candidateGround=new McNavigator(client,dormant,config,dormant);
        cursorPrepared=SharedMovementPolicy.withinGroundHorizon(origin,goal,searchRadius)&&prepareGround(candidateGround,goal,options);
        if(!owned(lease)){stopOwned(lease,null);return List.of();}
        if(cursorPrepared){geometry=new Object();prepared=new SharedMovementPolicy.Candidate("ground",SharedMovementPolicy.Mode.WALK,goal,origin.distanceTo(goal),0,geometry,generation,null);accepted.add(prepared);}
        return owned(lease)?List.copyOf(accepted):List.of();
    }
    @Override public boolean permitted(SharedMovementCoordinator.Lease l,SharedMovementPolicy.Candidate c){
        if(!owned(l))return false;
        return cursorPrepared&&c==prepared&&c.geometryProof()==geometry&&c.capabilityProof()==generation
            &&candidateGround!=null&&candidateGround.bodyClear(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()),false)
            &&candidateGround.standingClear(c.destination())&&owned(l);
    }
    @Override public SharedMovementCoordinator.Issue begin(SharedMovementCoordinator.Lease l,SharedMovementPolicy.Candidate c){
        if(!permitted(l,c))return null;
        if(publicationLeg==null)return null;
        if(publicationLeg.retirementOutcome()!=null){
            var predecessor=publicationLeg;
            if(predecessor.retirementContinuationContext()==null)return null;
            publicationLeg=publicationLease.beginAfter(journey,predecessor);
            if(publicationLeg==null)return null;
        }
        if(!publicationLeg.current())return null;
        var actor=new LeaseGroundAdapter(publicationLeg);
        ground=new McNavigator(client,actor,config,actor);
        if(groundOptions==null||!prepareGround(ground,c.destination(),groundOptions)||!publicationLeg.current())return null;
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
        return new SharedMovementCoordinator.Issue(new Object(),generation,0,now(),false);
    }
    @Override public SharedMovementCoordinator.Progress tick(SharedMovementCoordinator.Lease l,SharedMovementPolicy.Candidate c,SharedMovementCoordinator.Issue i){
        if(!permitted(l,c))return SharedMovementCoordinator.Progress.REJECTED;
        if(i.receiptDomain()!=generation)return SharedMovementCoordinator.Progress.REJECTED;
        var result=ground.tick();
        if(!groundCustodyCurrent())return SharedMovementCoordinator.Progress.REJECTED;
        // This Leg consumed the exact positive own camera ticket before any guarded key publication.
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
        return switch(result){case WALKING->SharedMovementCoordinator.Progress.ACTIVE;case ARRIVED->SharedMovementCoordinator.Progress.ARRIVED;default->SharedMovementCoordinator.Progress.REJECTED;};
    }

    @Override public SharedMovementCoordinator.AppliedReceipt receipt(SharedMovementCoordinator.Lease l,SharedMovementCoordinator.Issue issue){return null;}
    @Override public boolean at(Vec3 destination,double radius){
        if(!owned(lease))return false;var p=client.player;var at=new Vec3(p.getX(),p.getY(),p.getZ());
        return at.horizontalDistanceTo(destination)<=radius&&Math.abs(at.y()-destination.y())<.08
            &&p.onGround()&&Math.hypot(p.getDeltaMovement().x,p.getDeltaMovement().z)<.08
            &&ground.standingClear(at)&&owned(lease);
    }
    private boolean prepareGround(McNavigator navigator,Vec3 goal,SharedMovementPolicy.Options options){
        boolean strict=options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN||options.purpose()==SharedMovementPolicy.Purpose.CROP_WORK;
        return strict
            ?(preferLevel?maxNodes==2500&&searchRadius==32&&options.terminalRadius()==.35&&navigator.startPreferLevel(goal):navigator.start(goal,maxNodes,searchRadius,options.terminalRadius()))
            :(preferLevel?navigator.startDynamicPreferLevel(goal,maxNodes,searchRadius,options.terminalRadius()):navigator.startDynamic(goal,maxNodes,searchRadius,options.terminalRadius()));
    }
    /** A clean closed Leg proves only this read-only interval; a new admission still uses beginAfter. */
    private boolean groundCustodyCurrent(){
        if(publicationLeg==null||groundResetFailed)return false;
        if(publicationLeg.retirementOutcome()!=null){
            var continuation=publicationLeg.retirementContinuationContext();
            return continuation!=null&&continuation.parent()==parent&&continuation.inputOwner()==parent
                &&continuation.cameraAuthority()==cameraOwner&&continuation.player()==client.player
                &&continuation.world()==client.level&&continuation.connection()==client.getConnection()
                &&continuation.config()==config&&continuation.epoch()==epoch&&continuation.requestGeneration()==rotationGeneration;
        }
        if(!publicationLeg.current()||publicationLeg.parent()!=parent)return false;
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();return true;
    }
    public LegacyTravelRetirement.RetirementOutcome groundRetirementOutcome(){return groundRetirement;}
    /** Read-only geometry helper used by the original four-cardinal pest approach search. */
    public Vec3 position(dev.veltraph.client.world.BlockPos point){return ground.position(point);}
    /** Cleanup eligibility is sampled from the actual active or closed Leg on every call. */
    public boolean cleanupCurrent(){
        var active=publicationLeg;
        if(active!=null&&(active.retirementOutcome()==null||!active.retirementOutcome().logicalRetired()))return active.cleanupCurrent();
        return retiredCleanupParentContext()!=null;
    }
    public boolean retirementRefused(){return groundResetFailed||groundRetirement!=null&&!groundRetirement.cleanSuccess();}
    /** Once per actual Leg. The Boolean cannot grant permission without the fresh concrete Leg below. */
    public boolean retireForHandoff(){
        if(handoffIssued)return false;
        stop();
        if(retirementRefused()||groundRetirement==null||!groundRetirement.cleanSuccess()
                ||retiredContinuationContext()==null)return false;
        handoffIssued=true;return true;
    }
    /** Concrete producer handle only after actual CLEAN retirement; private admission freshness is rechecked. */
    public LegacyTravelPublicationLease.Leg retiredContinuationLeg(){
        return handoffIssued&&retiredContinuationContext()!=null?lastRetiredPublicationLeg:null;
    }
    /** Diagnostic read only. The interaction factory consumes the actual concrete producer Leg. */
    public LegacyTravelPublicationIdentity.State retiredContinuationContext(){
        var retired=lastRetiredPublicationLeg;
        return retired==null||groundResetFailed||groundRetirement==null||!groundRetirement.cleanSuccess()
            ?null:retired.retirementContinuationContext();
    }
    public LegacyTravelPublicationIdentity.State retiredCleanupParentContext(){
        var retired=lastRetiredPublicationLeg;
        return retired==null?null:retired.retiredCleanupParentContext();
    }
    public LegacyTravelPublicationIdentity.State postRetirementOwnParentContext(){
        var retired=lastRetiredPublicationLeg;
        return retired==null?null:retired.postRetirementOwnParentContext();
    }

    @Override public void stopOwned(SharedMovementCoordinator.Lease l,SharedMovementCoordinator.Issue i){
        if(l==null||l!=lease||publicationLeg==null)return;
        var retiring=publicationLeg;
        // The actual Leg accounts for cleanup before a controller can reset private state.
        groundRetirement=retiring.retireOwned();lastRetiredPublicationLeg=retiring;
        try{ground.stop();}catch(RuntimeException helperResetFailure){groundResetFailed=true;}
        if(groundRetirement.cleanSuccess()&&retiring.retirementContinuationContext()!=null){
            epoch=retiring.cameraEpoch();rotationGeneration=retiring.requestGeneration();
        }
        // Root exception handling can stop only the exact proved original parent. Foreign work stays intact.
        if(!groundRetirement.cleanSuccess()&&retiring.postRetirementOwnParentContext()!=null)
            throw new IllegalStateException("Owned shared ground cleanup incomplete: "+groundRetirement.status());
    }
}
