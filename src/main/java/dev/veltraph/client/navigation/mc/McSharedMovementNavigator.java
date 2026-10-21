package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.movement.device.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Actual registered-parent binding for the common travel dispatcher. No tool is inferred available. */
public final class McSharedMovementNavigator implements SharedMovementCoordinator.Port {
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private McNavigator ground;private McNavigator candidateGround;private final SharedMovementCoordinator coordinator;
    private final LegacyTravelPublicationLease publicationLease;
    private LegacyTravelPublicationLease.Leg publicationLeg;
    private LegacyTravelRetirement.RetirementOutcome groundRetirement;
    private boolean groundResetFailed;private SharedMovementPolicy.Options groundOptions;private Vec3 originalGoal;
    private McInstantTransmission.CameraCustodyAdapter deviceAdapter;
    private OwnedDeviceAdmission ownedDeviceAdmission;
    private McLoadedGroundReturnNavigator.SelectedLoadedSegment loadedSegmentSource;
    private long loadedNativeClockFloor=-1;
    private LoadedPreKernelAdmission loadedPreKernelAdmission;
    private boolean loadedKernelEntered;
    private LoadedStopSnapshot loadedStopSnapshot,loadedPortStop;
    private LoadedAdmissionMarker loadedAdmissionMarker;
    /** Private actual factory-entry identity. It is a final refusal fence, never movement permission. */
    private final class LoadedAdmissionMarker {
        final McLoadedGroundReturnNavigator.SelectedLoadedSegment source;
        final boolean initial;
        final SharedMovementCoordinator.Lease originalLease=lease;
        final Object originalGeneration=generation,originalParent=parent,originalCamera=cameraOwner;
        final Object originalConfigStamp=configStamp,originalPolicy=nativePolicy;
        final Vec3 selectedGoal=originalGoal;
        final long originalJourney=journey;
        final LegacyTravelPublicationLease.Leg originalLeg=publicationLeg;
        boolean canceled;
        private LoadedAdmissionMarker(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,boolean initial){
            this.source=source;this.initial=initial;
        }
        boolean fieldsCurrent(LegacyTravelPublicationLease.Leg expectedLeg){return loadedAdmissionMarker==this&&!canceled
            &&source!=null&&loadedSegmentSource==source&&loadedIssuedAffiliation(source)&&lease==originalLease
            &&generation==originalGeneration&&parent==originalParent&&cameraOwner==originalCamera
            &&configStamp==originalConfigStamp&&nativePolicy==originalPolicy&&journey==originalJourney
            &&originalGoal==selectedGoal&&publicationLeg==expectedLeg&&loadedPortStop==null
            &&source.sharedChildIdentity()==McSharedMovementNavigator.this&&source.configIdentity()==config
            &&source.returnCallIdentity().servicesIdentity()==services&&source.segmentPosition().equals(selectedGoal)
            &&source.parentIdentity()==originalParent&&source.cameraOwnerIdentity()==originalCamera;}
        boolean beforePublicationCurrent(){return fieldsCurrent(originalLeg);}
    }
    public Object loadedInitialAdmissionIdentity(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
        var actual=loadedAdmissionMarker;
        return actual!=null&&actual.initial&&actual.source==source&&actual.beforePublicationCurrent()?actual:null;
    }
    public boolean loadedInitialAdmissionFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,Object opaque){
        return opaque instanceof McSharedMovementNavigator.LoadedAdmissionMarker actual&&actual==loadedAdmissionMarker
            &&actual.initial&&actual.source==source&&actual.beforePublicationCurrent();
    }
    public Object loadedContinuationAdmissionIdentity(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,LegacyTravelPublicationLease.Leg predecessor){
        var actual=loadedAdmissionMarker;
        return actual!=null&&!actual.initial&&actual.source==source&&actual.originalLeg==predecessor
            &&actual.beforePublicationCurrent()?actual:null;
    }
    public boolean loadedContinuationAdmissionFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,
            LegacyTravelPublicationLease.Leg predecessor,Object opaque){
        return opaque instanceof McSharedMovementNavigator.LoadedAdmissionMarker actual&&actual==loadedAdmissionMarker
            &&!actual.initial&&actual.source==source&&actual.originalLeg==predecessor&&actual.beforePublicationCurrent();
    }
    /** Captured original cleanup operands and receipts only; native authority remains with each actual Leg/actuator. */
    private final class LoadedStopSnapshot {
        final McLoadedGroundReturnNavigator.SelectedLoadedSegment source=loadedSegmentSource;
        final SharedMovementCoordinator.Lease originalLease=lease;
        final LegacyTravelPublicationLease.Leg originalLeg=publicationLeg;
        final Object originalGeneration=generation,originalParent=parent,originalCamera=cameraOwner;
        final Object originalConfigStamp=configStamp,originalPolicy=nativePolicy,originalOptions=groundOptions,originalGoalPoint=originalGoal;
        final long originalJourney=journey;
        final McNavigator originalGround=ground;
        final McFlightNavigator originalFlight=flight;
        final McInstantTransmission originalDevice=device;
        final boolean flightMode=usingFlight,deviceMode=usingDevice;
        final Object originalPlayer=client.player,originalWorld=client.level,originalConnection=client.getConnection();
        final Object originalSdkOptions=client.options,originalGameMode=client.gameMode;
        boolean inFlight,nativeAttempted,portReturned,failed;
        LegacyTravelRetirement.RetirementOutcome originalOutcome;
        private LoadedStopSnapshot(){}
        final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest originalIssuedRequest=issuedReturnRequest;
        final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment originalIssuedSegment=issuedLoadedSegment;
        boolean scopeCurrent(){return source!=null&&issuedReturnRequest==originalIssuedRequest&&issuedLoadedSegment==originalIssuedSegment
            &&loadedIssuedAffiliation(source)&&loadedSegmentSource==source
            &&lease==originalLease&&publicationLeg==originalLeg&&generation==originalGeneration
            &&parent==originalParent&&cameraOwner==originalCamera&&journey==originalJourney
            &&configStamp==originalConfigStamp&&nativePolicy==originalPolicy&&groundOptions==originalOptions&&originalGoal==originalGoalPoint
            &&ground==originalGround&&flight==originalFlight&&device==originalDevice
            &&client.player==originalPlayer&&client.level==originalWorld&&client.getConnection()==originalConnection
            &&client.options==originalSdkOptions&&client.gameMode==originalGameMode&&VeltraphBootstrap.config()==config
            &&originalLeg!=null&&originalLease!=null&&source.sharedChildIdentity()==McSharedMovementNavigator.this
            &&source.configIdentity()==config&&source.returnCallIdentity().servicesIdentity()==services
            &&originalLeg.parent()==originalParent&&originalLeg.cameraAuthority()==originalCamera;}
        boolean observedCurrent(){return scopeCurrent()&&(source.cleanupSourceCurrent()||source.nativeSourceCurrent())&&scopeCurrent();}
        void refuse(){failed=true;if(scopeCurrent())groundResetFailed=true;}
        boolean cleanReturned(){return portReturned&&!failed&&scopeCurrent()&&originalOutcome!=null
            &&originalLeg.retirementOutcome()==originalOutcome&&groundRetirement==originalOutcome
            &&originalOutcome.cleanSuccess()&&originalOutcome.removal()==LegacyTravelRetirement.Removal.REMOVED_OWN
            &&!retirementRefused();}
        void nativeStop(){
            if(nativeAttempted)return;
            if(!observedCurrent()){refuse();return;}
            nativeAttempted=true;
            try{
                if(!finishDeviceConsumed()||!observedCurrent()){refuse();return;}
                if(flightMode&&originalFlight!=null){
                    try{originalFlight.stop();}catch(RuntimeException cleanupRefused){refuse();}
                    if(!observedCurrent()){refuse();return;}
                }
                if(deviceMode&&originalDevice!=null){
                    try{originalDevice.cancel();}catch(RuntimeException cleanupRefused){refuse();}
                    if(!observedCurrent()){refuse();return;}
                }
                try{originalGround.stop();}catch(RuntimeException cleanupRefused){refuse();}
                if(!observedCurrent()){refuse();return;}
                var outcome=originalLeg.retireOwned();
                if(!scopeCurrent()){failed=true;return;}
                originalOutcome=outcome;groundRetirement=outcome;portReturned=true;
                if(!outcome.cleanSuccess()){refuse();return;}
                usingDevice=false;usingFlight=false;deviceAdapter=null;ownedDeviceAdmission=null;
                var continuation=originalLeg.retirementContinuationContext();
                if(!scopeCurrent()){failed=true;return;}
                if(continuation!=null){epoch=originalLeg.cameraEpoch();rotationGeneration=originalLeg.requestGeneration();}
            }catch(RuntimeException|Error failure){refuse();throw failure;}
        }
    }
    /** Exact real dispatcher cleanup endpoint. A copied source/Leg or generic vacancy cannot authorize this call. */
    public boolean stopOriginalLoadedSegment(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,LegacyTravelPublicationLease.Leg expectedLeg){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var first=frames.filter(f->f.getDeclaringClass()!=McSharedMovementNavigator.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==McTravelNavigator.LoadedReturnDispatch.class&&first.getMethodName().equals("stopSelected");});
        if(!caller||source==null||expectedLeg==null||source!=loadedSegmentSource||expectedLeg!=publicationLeg
            ||!ownsLoadedSegmentSource(source))return false;
        if(loadedAdmissionMarker!=null){loadedAdmissionMarker.canceled=true;return false;}
        return stopLoadedOriginal();
    }
    private boolean stopLoadedOriginal(){
        if(loadedPortStop!=null)return false;
        var original=loadedStopSnapshot;
        if(original==null){original=new LoadedStopSnapshot();loadedStopSnapshot=original;}
        if(original.inFlight)return false;
        final var exact=original;
        original.inFlight=true;
        try{
            if(!original.observedCurrent()){original.refuse();return false;}
            var completed=coordinator.state()==SharedMovementCoordinator.State.ARRIVED?nativeNormalReturnAffiliation:null;
            boolean originalCompleted=completed!=null&&completed.rawCurrent();
            boolean retired;
            if(!loadedKernelEntered){exact.nativeStop();retired=exact.cleanReturned();}
            else{
                final boolean[] preNativeVeto={true};
                retired=coordinator.stopScoped(()->{
                    if(!exact.observedCurrent())return false;
                    if(preNativeVeto[0]){
                        preNativeVeto[0]=false;
                        return !exact.nativeAttempted||exact.cleanReturned();
                    }
                    return exact.cleanReturned();
                });
            }
            if(!retired||!exact.cleanReturned()){exact.refuse();return false;}
            normalReturnStopCompleted=originalCompleted&&completed.cleanupRawCurrent()&&!retirementRefused();
            if(!exact.scopeCurrent())return false;
            loadedStopSnapshot=null;
            loadedPreKernelAdmission=null;loadedKernelEntered=false;loadedSegmentSource=null;loadedNativeClockFloor=-1;
            issuedLoadedSegment=null;issuedReturnRequest=null;suspendedReturnBridge=null;
            issuedDeviceEntry=null;retiredReturnDeviceAdmission=null;returnApproach=null;originalReturnSelectionHistory=null;
            normalReturnInitialHistory=null;normalReturnRestorationChain=null;normalReturnCleanupRestoration=null;nativeNormalReturnAffiliation=null;
            expectedAcks.clear();cursorPrepared=false;prepared=null;geometry=null;lease=null;parent=null;cameraOwner=null;maintenanceAuthority=null;
            held=null;configStamp=null;nativePolicy=null;generation=null;deviceCandidate=null;deviceProbe=null;device=null;usingDevice=false;actualDeviceIssue=null;
            publicationLeg=null;candidateGround=null;groundOptions=null;originalGoal=null;deviceAdapter=null;ownedDeviceAdmission=null;
            flight=null;flightCandidate=null;flightPreview=null;usingFlight=false;
            return true;
        }catch(RuntimeException|Error failure){exact.refuse();throw failure;}
        finally{original.inFlight=false;}
    }
    private void stopOwnedLoaded(SharedMovementCoordinator.Lease expected){
        if(expected==null||expected!=lease||loadedSegmentSource==null||publicationLeg==null||loadedPortStop!=null)return;
        var original=loadedStopSnapshot;
        if(original==null)original=new LoadedStopSnapshot();
        if(!original.scopeCurrent())return;
        loadedPortStop=original;
        try{original.nativeStop();if(original.scopeCurrent())loadedStopSnapshot=original;}
        finally{if(loadedPortStop==original)loadedPortStop=null;}
    }
    /** Genuine factory result retained for fail-stop cleanup only, never native action permission. */
    private final class LoadedPreKernelAdmission {
        private final McLoadedGroundReturnNavigator.SelectedLoadedSegment source;
        private final LegacyTravelPublicationLease.Leg actual;
        private final SharedMovementCoordinator.Lease originalLease=lease;
        private final Object originalGeneration=generation,originalParent=parent,originalCamera=cameraOwner;
        private final long originalJourney=journey;
        private final Vec3 selectedGoal=originalGoal;
        private boolean attempted;
        private LoadedPreKernelAdmission(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,LegacyTravelPublicationLease.Leg actual){
            this.source=source;this.actual=actual;
        }
        private boolean same(){return loadedPreKernelAdmission==this&&!loadedKernelEntered&&loadedSegmentSource==source
            &&publicationLeg==actual&&lease==originalLease&&generation==originalGeneration&&parent==originalParent
            &&cameraOwner==originalCamera&&journey==originalJourney&&originalGoal==selectedGoal&&loadedIssuedAffiliation(source)
            &&source.sharedChildIdentity()==McSharedMovementNavigator.this&&source.configIdentity()==config
            &&source.returnCallIdentity().servicesIdentity()==services&&source.segmentPosition().equals(selectedGoal)
            &&actual.parent()==originalParent&&actual.cameraAuthority()==originalCamera;}
        private boolean retire(){
            if(!same()||attempted){groundResetFailed=true;return false;}
            attempted=true;
            try{stopOwned(originalLease,null);}
            catch(RuntimeException|Error failure){groundResetFailed=true;throw failure;}
            var outcome=actual.retirementOutcome();
            if(!same()||groundRetirement!=outcome||outcome==null||!outcome.cleanSuccess()
                ||outcome.removal()!=LegacyTravelRetirement.Removal.REMOVED_OWN||retirementRefused()){
                groundResetFailed=true;return false;
            }
            return true;
        }
    }
    /** Actual selected loaded segment only; neither an arbitrary goal nor advisory options can mint it. */
    public boolean startOriginalLoadedSegment(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var first=frames.filter(f->f.getDeclaringClass()!=McSharedMovementNavigator.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==McTravelNavigator.LoadedReturnDispatch.class
                &&first.getMethodName().equals("startSelected");});
        if(!caller||source==null||source.initialAdmittedLegIdentity()!=null||source.sharedChildIdentity()!=this||source.configIdentity()!=config
            ||source.returnCallIdentity().servicesIdentity()!=services||source.searchNodes()!=2500||source.searchRadius()!=32
            ||!source.initialSharedStartCurrent(this))return false;
        var request=source.issuedRequestIdentity();
        if(request!=null){var cap=request.captureOriginalLoadedSegment(source);
            if(cap==null||cap.sourceIdentity()!=source||cap.requestIdentity()!=request||cap.sharedChildIdentity()!=this||!cap.nativeCurrent())return false;}
        return start(source.segmentPosition(),source.searchNodes(),source.searchRadius(),source.terminalRadius(),
            SharedMovementPolicy.Purpose.EXACT_RETURN,false,request,source);
    }
    /** Passive typed affiliation; full current native permission remains with the actual source/Leg. */
    public boolean ownsLoadedSegmentSource(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){
        return exact!=null&&loadedSegmentSource==exact&&exact.sharedChildIdentity()==this
            &&originalGoal!=null&&originalGoal.equals(exact.segmentPosition())&&parent==exact.parentIdentity()
            &&cameraOwner==exact.cameraOwnerIdentity()&&config==exact.configIdentity();
    }
    /** Exact producer affiliation only; no source currentness or movement permission is inferred from this leaf. */
    private boolean loadedIssuedAffiliation(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
        return source!=null&&(source.issuedRequestIdentity()==null?issuedReturnRequest==null&&issuedLoadedSegment==null
            :issuedReturnRequest==source.issuedRequestIdentity()&&issuedLoadedSegment!=null
                &&issuedLoadedSegment.requestIdentity()==issuedReturnRequest&&issuedLoadedSegment.sourceIdentity()==source
                &&issuedLoadedSegment.sharedChildIdentity()==this);
    }
    public LegacyTravelPublicationLease.Leg actualLoadedLegIdentity(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){
        return ownsLoadedSegmentSource(exact)?publicationLeg:null;
    }
    public LegacyTravelRetirement.RetirementOutcome loadedSegmentRetirementIdentity(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){
        var leg=actualLoadedLegIdentity(exact);return leg==null?null:leg.retirementOutcome();
    }
    public McLoadedGroundReturnNavigator.SelectedLoadedSegment loadedSegmentSourceIdentity(){return loadedSegmentSource;}
    /** Original absolute loaded horizon is a refusal bound; no callback may renew its clock. */
    private boolean loadedNativeWindowCurrent(){
        var source=loadedSegmentSource;if(source==null)return true;
        long actual=System.currentTimeMillis();
        if(!ReturnDeviceDeadlines.permitsOriginalWindow(actual,source.originalStartedAtMillis(),loadedNativeClockFloor,
            coordinator.observedClockFloor(),source.originalDeadlineMillis()))return false;
        loadedNativeClockFloor=actual;return true;
    }
    private String firstFailure="";
    private NativeSharedTravelPolicy nativePolicy;
    private Module parent;private Object cameraOwner;private McFarmMaintenanceAuthorityBridge.Binding maintenanceAuthority;private DynamicRouteConfigStamp configStamp;private long epoch;
    private dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest issuedReturnRequest;
    private McSuspendedFarmTravelBridge.Binding suspendedReturnBridge;
    private dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment issuedLoadedSegment;
    private int slot;private ItemStack held;private Object generation,geometry;
    private SharedMovementCoordinator.Lease lease;
    private SharedMovementPolicy.Candidate prepared,deviceCandidate;private boolean cursorPrepared,usingDevice,deviceAdmission;
    private McFlightNavigator flight;private SharedMovementPolicy.Candidate flightCandidate;private McSupportedFlightPreview.Observation flightPreview;private boolean usingFlight;
    private McInstantTransmission device;private McInstantTransmission.Probe deviceProbe;
    private long journey;private boolean expectedConsumed;
    private int maxNodes=2500,searchRadius=32;private boolean preferLevel;
    private long rotationGeneration;
    private IssuedReturnStopSnapshot issuedStopSnapshot;
    private final SharedMovementExpectedAckWindow expectedAcks=new SharedMovementExpectedAckWindow();private SharedMovementCoordinator.Issue actualDeviceIssue;
    public McSharedMovementNavigator(Minecraft client,FarmServices services,ConfigService config){
        this.client=client;this.services=services;this.config=config;
        publicationLease=new LegacyTravelPublicationLease(client,services,config,
            ()->config.revision(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));
        var dormant=new DormantGroundPublication(()->owned(lease));
        ground=new McNavigator(client,dormant,config,dormant);
        coordinator=new SharedMovementCoordinator(this);
    }
    public boolean start(Vec3 goal,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,2500,32,radius,purpose);}
    public boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,maxNodes,searchRadius,radius,purpose,false);}
    public boolean startGround(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){
        return start(goal,maxNodes,searchRadius,radius,purpose==SharedMovementPolicy.Purpose.GENERAL_TRAVEL?SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL:purpose,false);
    }
    public boolean startPreferLevel(Vec3 goal,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,2500,32,radius,purpose,true);}
    public boolean startPreferLevel(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){return start(goal,maxNodes,searchRadius,radius,purpose,true);}
    /** Only the real private saved-anchor instruction supplies this request. */
    public boolean startIssuedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose){
        if(request==null||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||request.purpose()!=purpose
                ||!SharedMovementPolicy.finite(goal)||!goal.equals(request.targetPosition())
                ||!request.ownsSharedChild(this)||!request.sharedStartDispatchCurrent(this))return false;
        return start(goal,maxNodes,searchRadius,radius,purpose,false,request);
    }
    /** Callback-free affiliation; this grants no current/publish/cleanup permission. */
    public boolean issuedReturnPublicationLease(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,LegacyTravelPublicationLease exact){
        return request!=null&&request==issuedReturnRequest&&exact==publicationLease&&request.ownsSharedChild(this);
    }
    private boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose,boolean preferLevel){
        return start(goal,maxNodes,searchRadius,radius,purpose,preferLevel,null);
    }
    private boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose,boolean preferLevel,dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request){
        return start(goal,maxNodes,searchRadius,radius,purpose,preferLevel,request,null);
    }
    private boolean start(Vec3 goal,int maxNodes,int searchRadius,double radius,SharedMovementPolicy.Purpose purpose,boolean preferLevel,
            dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,McLoadedGroundReturnNavigator.SelectedLoadedSegment loaded){
        if(loadedAdmissionMarker!=null){loadedAdmissionMarker.canceled=true;return false;}
        stop();if(loadedStopSnapshot!=null||loadedPortStop!=null||retirementRefused()||!SharedMovementPolicy.finite(goal)||maxNodes<1||maxNodes>5000||searchRadius<1||searchRadius>50||purpose==null||!Double.isFinite(radius)||radius<=0||radius>.35)return false;
        firstFailure="";this.maxNodes=maxNodes;this.searchRadius=searchRadius;this.preferLevel=preferLevel;
        issuedReturnRequest=request;suspendedReturnBridge=null;loadedSegmentSource=loaded;loadedNativeClockFloor=-1;
        issuedLoadedSegment=loaded==null||request==null?null:request.currentLoadedSegmentIdentity();
        loadedPreKernelAdmission=null;loadedKernelEntered=false;
        if(loaded!=null&&(!loadedIssuedAffiliation(loaded)||request!=null&&!request.currentLoadedSegmentFieldsCurrent(loaded)||!loaded.segmentPosition().equals(goal)
            ||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||loaded.searchNodes()!=maxNodes||loaded.searchRadius()!=searchRadius
            ||Double.doubleToLongBits(loaded.terminalRadius())!=Double.doubleToLongBits(radius)
            ||!loaded.initialSharedStartCurrent(this)))return false;
        var modules=VeltraphBootstrap.moduleManager();
        parent=modules==null?null:modules.inputOwner().orElse(null);
        configStamp=DynamicRouteConfigStamp.capture(config).orElse(null);nativePolicy=NativeSharedTravelPolicy.capture(config,configStamp);
        if(parent==null||configStamp==null||nativePolicy==null||client.player==null||client.level==null||client.getConnection()==null)return false;
        cameraOwner=parent;maintenanceAuthority=null;
        var maintenance=VeltraphBootstrap.farmMaintenance();
        if(request!=null){
            if(parent!=request.driverIdentity())return false;
            if(loaded==null){suspendedReturnBridge=McSuspendedFarmTravelBridge.capture(client,request,this,config,services,configStamp,purpose,goal);
                if(suspendedReturnBridge==null)return false;}
            else if(issuedLoadedSegment==null||!issuedLoadedSegment.nativeCurrent())return false;
        }else if(parent instanceof dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant&&maintenance!=null&&maintenance.active()){
            maintenanceAuthority=loaded==null?McFarmMaintenanceAuthorityBridge.capture(client,parent,config,configStamp,purpose,goal).orElse(null)
                :loaded.originalLegIdentity().loadedMaintenanceBinding(loaded);
            if(maintenanceAuthority==null)return false;
            cameraOwner=maintenanceAuthority.cameraOwner();
        }
        epoch=McOwnedRotation.epoch(client);
        if(!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService))return false;
        rotationGeneration=((dev.veltraph.client.service.mc.McRotationService)services.rotation()).requestGeneration();
        slot=services.inventory().selectedSlot();
        if(slot<0||slot>8)return false;
        originalReturnSelectionHistory=purpose==SharedMovementPolicy.Purpose.EXACT_RETURN
            ?dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory()):null;
        normalReturnInitialHistory=originalReturnSelectionHistory;
        held=client.player.getInventory().getItem(slot).copy();generation=new Object();journey++;expectedConsumed=false;expectedAcks.clear();
        lease=new SharedMovementCoordinator.Lease(parent,client.player,client.level,client.getConnection(),parent,cameraOwner,config,configStamp.approachRevision(),epoch);
        // Reserve the exact registered input/camera role pair before any candidate geometry.
        if(loaded!=null){
            originalGoal=goal; // Effect-free selected affiliation keeps an actual failed admission observable for cleanup.
            if(!admitOriginalLoadedMovementLeg(loaded,goal,purpose))return false;
        }
        else publicationLeg=request==null?publicationLease.beginExpected(journey,parent,purpose,goal)
                :publicationLease.beginIssuedReturn(request,this,journey,parent,purpose,goal);
        if(publicationLeg==null)return false;
        if(publicationLeg.parent()!=parent||publicationLeg.cameraAuthority()!=cameraOwner){
            groundRetirement=publicationLeg.retireOwned();return false;
        }
        originalGoal=goal;epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
        var allowed=SharedMovementPolicy.allowedFor(purpose,configStamp.approach());
        long timeout=120000;
        if(loaded!=null){long actual=now();long remaining=loaded.originalDeadlineMillis()-actual;
            if(actual<loaded.originalStartedAtMillis()||remaining<100||!loaded.initialSharedStartCurrent(this)||!loadedNativeWindowCurrent())return false;
            timeout=Math.min(timeout,remaining);
        }
        var options=new SharedMovementPolicy.Options(allowed,purpose,allowed.stream().anyMatch(SharedMovementPolicy::device)?2:0,timeout,radius,1.25);
        groundOptions=options;groundRetirement=null;groundResetFailed=false;
        if(loaded!=null)loadedKernelEntered=true; // Actual valid kernel call takes over its existing normal stop path.
        return coordinator.start(lease,goal,options);
    }
    /** Actual typed admission retains even a failed new Leg for genuine cleanup; no empty-slot rebind. */
    private boolean admitOriginalLoadedMovementLeg(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,Vec3 goal,
            SharedMovementPolicy.Purpose purpose){
        if(source!=loadedSegmentSource||loadedAdmissionMarker!=null||loadedStopSnapshot!=null
            ||!source.initialSharedStartCurrent(this))return false;
        var marker=new LoadedAdmissionMarker(source,true);loadedAdmissionMarker=marker;
        try{
            var actual=issuedLoadedSegment==null?publicationLease.beginAfterLoadedSegment(source,this,journey,parent,purpose,goal)
                :publicationLease.beginAfterIssuedLoadedSegment(issuedLoadedSegment,source,this,journey,parent,purpose,goal);
            if(actual==null)return false;
            publicationLeg=actual; // Retain genuine published output before any source-binding visitor.
            loadedPreKernelAdmission=new LoadedPreKernelAdmission(source,actual);
            if(issuedLoadedSegment!=null){suspendedReturnBridge=actual.loadedIssuedBinding(issuedLoadedSegment);
                if(suspendedReturnBridge==null)return false;}
            return source.bindActualSharedLeg(this,actual)&&marker.fieldsCurrent(actual);
        }finally{if(loadedAdmissionMarker==marker)loadedAdmissionMarker=null;}
    }
    public McTravelNavigator.State tick(){
        var result=coordinator.tick();return switch(result){case ARRIVED->McTravelNavigator.State.ARRIVED;
            case MOVING,SELECTING,WAITING_RECEIPT->McTravelNavigator.State.ACTIVE;default->McTravelNavigator.State.FAILED;};
    }
    public boolean consumeExpectedTeleport(Vec3 position,long now){
        if(!owned(lease))return false;
        if(usingDevice&&device!=null&&device.issue()!=null){
            var applied=NativeDeviceTeleportReceipt.applied(device.issue());
            if(applied!=null)retainExpectedAck(device.issue(),applied);
        }
        var latest=NativeDeviceTeleportReceipt.current(client); // Freshness veto only, not authorization.
        boolean consumed=latest!=null&&owned(lease)&&expectedAcks.consume(lease,generation,position,now,latest.serial());
        if(consumed)expectedConsumed=true;return consumed;
    }
    private void retainExpectedAck(DeviceUseReceipt.Issue issue,DeviceUseReceipt.Applied applied){
        if(issue==null||applied==null||lease==null)return;
        var submitted=new SharedMovementCoordinator.Issue(issue.nonce(),issue.domain(),issue.baseline(),issue.issuedAt(),true);
        var accepted=new SharedMovementCoordinator.AppliedReceipt(applied.nonce(),applied.domain(),applied.serial(),applied.receivedAt(),applied.position(),applied.context().player(),applied.context().world(),applied.context().connection());
        expectedAcks.retain(lease,generation,submitted,accepted,issue.goal(),issue.tolerance(),issue.deadline());
    }
    /** Passive lifecycle data continuation for this original shared transaction, including an exact empty state. */
    PassiveLifecycleContinuation capturePassiveLifecycleContinuation(VeltraphBootstrap.RootPassiveTransition actual){
        if(actual==null||!actual.sourceBeforeCurrent(client))return null;
        var leg=publicationLeg;var child=leg==null?null:leg.capturePassiveLifecycleContinuation(actual);
        if(leg!=null&&child==null)return null;
        var result=new PassiveLifecycleContinuation(actual,leg,child);
        return result.rawCurrent()&&actual.sourceBeforeCurrent(client)?result:null;
    }
    final class PassiveLifecycleContinuation {
        private final VeltraphBootstrap.RootPassiveTransition actual;
        private final LegacyTravelPublicationLease.Leg leg;
        private final LegacyTravelPublicationLease.Leg.PassiveLifecycleContinuation child;
        private final SharedMovementCoordinator.Lease originalLease=lease;private final Object originalGeneration=generation;
        private final Object originalGeometry=geometry;private final Module originalParent=parent;private final Object originalCamera=cameraOwner;
        private final McFarmMaintenanceAuthorityBridge.Binding originalMaintenance=maintenanceAuthority;
        private final McInstantTransmission originalDevice=device;private final OwnedDeviceAdmission originalAdmission=ownedDeviceAdmission;
        private final SharedMovementCoordinator.Issue originalIssue=actualDeviceIssue;private final McInstantTransmission.Probe originalProbe=deviceProbe;
        private final McInstantTransmission.CameraCustodyAdapter originalAdapter=deviceAdapter;
        private final McFlightNavigator originalFlight=flight;private final McSupportedFlightPreview.Observation originalPreview=flightPreview;
        private final boolean deviceMode=usingDevice,flightMode=usingFlight;private final long originalJourney=journey,originalEpoch=epoch,originalRotation=rotationGeneration;
        private boolean prepared,committed;
        private PassiveLifecycleContinuation(VeltraphBootstrap.RootPassiveTransition actual,LegacyTravelPublicationLease.Leg leg,LegacyTravelPublicationLease.Leg.PassiveLifecycleContinuation child){this.actual=actual;this.leg=leg;this.child=child;}
        boolean rawCurrent(){return !committed&&client.isSameThread()&&publicationLeg==leg&&lease==originalLease&&generation==originalGeneration
            &&geometry==originalGeometry&&parent==originalParent&&cameraOwner==originalCamera&&maintenanceAuthority==originalMaintenance
            &&device==originalDevice&&ownedDeviceAdmission==originalAdmission&&actualDeviceIssue==originalIssue&&deviceProbe==originalProbe&&deviceAdapter==originalAdapter
            &&flight==originalFlight&&flightPreview==originalPreview&&usingDevice==deviceMode&&usingFlight==flightMode
            &&journey==originalJourney&&epoch==originalEpoch&&rotationGeneration==originalRotation&&(child==null||child.rawCurrent());}
        boolean prepare(){if(prepared||!rawCurrent()||!actual.normalReturnedCurrent(client)||child!=null&&!child.prepare())return false;prepared=true;return rawCurrent();}
        boolean commit(){if(!prepared||!actual.passiveDeclarationCommitWindowCurrent()||!rawCurrent()||child!=null&&!child.commit())return false;committed=true;return true;}
    }
    public boolean hasLeg(){return lease!=null;}
    public boolean hasActiveLeg(){return switch(coordinator.state()){case SELECTING,MOVING,WAITING_RECEIPT->true;default->false;};}
    public boolean ownsCurrentGeneration(){return owned(lease);}
    /** Permission for the exact temporary selected tool only; original full inventory remains unchanged. */
    public boolean permitsSelectedSlot(int expectedOriginal){return expectedOriginal==slot&&usingDevice&&device!=null
        &&(device.state()==McInstantTransmission.State.AIMING||device.state()==McInstantTransmission.State.WAITING)
        &&owned(lease)&&deviceProbe!=null&&services.inventory().selectedSlot()==deviceProbe.tool().slot()
        &&McMovementDeviceInventory.unchanged(client,deviceProbe.inventory())&&owned(lease);}
    public String status(){return "Shared movement: "+coordinator.status()+" | "+ground.diagnostics();}
    public String firstFailure(){return firstFailure;}
    /** Read-only selected policy, never a native permission or proof handle. */
    public SharedMovementPolicy.Mode selectedMode(){var selected=coordinator.selected();return selected==null?null:selected.mode();}
    /** Private original shared wrapper identity; native cleanup still needs the real original Leg. */
    public final class IssuedReturnStopSnapshot {
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source;
        private final Object originalGeneration=generation,originalLease=lease,originalRequest=issuedReturnRequest,originalBridge=suspendedReturnBridge;
        private final LegacyTravelPublicationLease.Leg originalLeg=publicationLeg;
        private final Object originalFlight=flight,originalDevice=device,originalGround=ground;
        private final Object originalParent=parent,originalCameraOwner=cameraOwner,originalMaintenance=maintenanceAuthority,originalConfig=configStamp,originalPolicy=nativePolicy;
        private final Object originalHeld=held,originalPrepared=prepared,originalGeometry=geometry,originalCandidateGround=candidateGround,originalOptions=groundOptions,originalGoalPoint=originalGoal;
        private final Object originalDeviceCandidate=deviceCandidate,originalDeviceProbe=deviceProbe,originalDeviceIssue=actualDeviceIssue,originalDeviceAdapter=deviceAdapter,originalDeviceAdmission=ownedDeviceAdmission;
        private final Object originalFlightCandidate=flightCandidate,originalFlightPreview=flightPreview;
        private final boolean originalUsingDevice=usingDevice,originalUsingFlight=usingFlight,originalCursorPrepared=cursorPrepared;
        private final long originalJourney=journey;private final int originalMaxNodes=maxNodes,originalSearchRadius=searchRadius;private final boolean originalPreferLevel=preferLevel;
        private boolean stopping,retired,portReturned;
        private IssuedReturnStopSnapshot(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){this.source=source;}
        public LegacyTravelPublicationLease.Leg originalLeg(){return originalLeg;}
        public boolean ownsOriginalActuator(Object actual){return actual!=null&&(actual==originalFlight||actual==originalDevice||actual==originalGround);}
        public boolean current(){
            if(retired)return generation==null&&lease==null&&issuedReturnRequest==null&&suspendedReturnBridge==null&&publicationLeg==null
                &&parent==null&&cameraOwner==null&&prepared==null&&geometry==null&&held==null&&configStamp==null&&nativePolicy==null
                &&device==null&&flight==null&&!usingDevice&&!usingFlight&&candidateGround==null&&groundOptions==null&&originalGoal==null;
            return generation==originalGeneration&&lease==originalLease&&issuedReturnRequest==originalRequest&&suspendedReturnBridge==originalBridge
                &&publicationLeg==originalLeg&&flight==originalFlight&&device==originalDevice&&ground==originalGround
                &&parent==originalParent&&cameraOwner==originalCameraOwner&&maintenanceAuthority==originalMaintenance&&configStamp==originalConfig&&nativePolicy==originalPolicy
                &&held==originalHeld&&prepared==originalPrepared&&geometry==originalGeometry&&candidateGround==originalCandidateGround&&groundOptions==originalOptions&&originalGoal==originalGoalPoint
                &&deviceCandidate==originalDeviceCandidate&&deviceProbe==originalDeviceProbe&&actualDeviceIssue==originalDeviceIssue
                &&(portReturned?deviceAdapter==null&&ownedDeviceAdmission==null:deviceAdapter==originalDeviceAdapter&&ownedDeviceAdmission==originalDeviceAdmission)
                &&flightCandidate==originalFlightCandidate&&flightPreview==originalFlightPreview
                &&(portReturned?!usingDevice&&!usingFlight:usingDevice==originalUsingDevice&&usingFlight==originalUsingFlight)&&cursorPrepared==originalCursorPrepared
                &&journey==originalJourney&&maxNodes==originalMaxNodes&&searchRadius==originalSearchRadius&&preferLevel==originalPreferLevel;
        }
        public boolean stopOwned(java.util.function.BooleanSupplier finalSource){
            if(retired)return current();if(stopping||!current())return false;stopping=true;
            try{
                if(!finalSource.getAsBoolean()||!current())return false;
                issuedStopSnapshot=this;boolean stopped;
                try{stopped=coordinator.stopScoped(()->current()&&finalSource.getAsBoolean()&&current());}
                finally{if(issuedStopSnapshot==this)issuedStopSnapshot=null;}
                if(!stopped)stopped=coordinator.retireEmptyScoped(()->current()&&finalSource.getAsBoolean()&&current());
                if(!stopped||!current())return false;
                // These exact original fields retire only after all port callbacks returned and passed the final leaf.
                expectedAcks.clear();cursorPrepared=false;prepared=null;geometry=null;lease=null;parent=null;cameraOwner=null;maintenanceAuthority=null;
                held=null;configStamp=null;nativePolicy=null;generation=null;deviceCandidate=null;deviceProbe=null;device=null;usingDevice=false;actualDeviceIssue=null;
                publicationLeg=null;candidateGround=null;groundOptions=null;originalGoal=null;deviceAdapter=null;ownedDeviceAdmission=null;flight=null;flightCandidate=null;flightPreview=null;usingFlight=false;
                issuedReturnRequest=null;suspendedReturnBridge=null;issuedDeviceEntry=null;retiredReturnDeviceAdmission=null;returnApproach=null;originalReturnSelectionHistory=null;normalReturnInitialHistory=null;normalReturnRestorationChain=null;normalReturnCleanupRestoration=null;nativeNormalReturnAffiliation=null;retired=true;return current();
            }finally{stopping=false;}
        }
    }
    public IssuedReturnStopSnapshot captureIssuedReturnStop(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){
        if(source==null||source.requestIdentity()==null||!source.requestIdentity().ownsSharedChild(this))return null;
        var original=new IssuedReturnStopSnapshot(source);return original.current()?original:null;
    }
    /** Passive reset evidence from one actual completed return and normally returned clean stop. */
    public boolean normalReturnRetirementClean(){return !hasLeg()&&normalReturnStopCompleted&&!retirementRefused();}
    public void stop(){
        if(loadedAdmissionMarker!=null){loadedAdmissionMarker.canceled=true;return;}
        if(loadedSegmentSource!=null&&loadedIssuedAffiliation(loadedSegmentSource)){stopLoadedOriginal();return;}
        var preKernel=loadedPreKernelAdmission;
        if(preKernel!=null&&!loadedKernelEntered){
            if(!preKernel.retire())return; // Refusal retains exact fail-stop evidence; never clears ACTIVE silently.
            loadedPreKernelAdmission=null;
        }
        normalReturnStopCompleted=false;
        var completed=coordinator.state()==SharedMovementCoordinator.State.ARRIVED?nativeNormalReturnAffiliation:null;
        boolean originalCompleted=completed!=null&&completed.rawCurrent();
        try{
            coordinator.stop();
            normalReturnStopCompleted=originalCompleted&&completed.cleanupRawCurrent()&&!retirementRefused();
        }finally{loadedPreKernelAdmission=null;loadedKernelEntered=false;loadedSegmentSource=null;loadedNativeClockFloor=-1;issuedDeviceEntry=null;retiredReturnDeviceAdmission=null;returnApproach=null;originalReturnSelectionHistory=null;normalReturnInitialHistory=null;normalReturnRestorationChain=null;normalReturnCleanupRestoration=null;nativeNormalReturnAffiliation=null;expectedAcks.clear();cursorPrepared=false;prepared=null;geometry=null;lease=null;parent=null;cameraOwner=null;maintenanceAuthority=null;held=null;configStamp=null;nativePolicy=null;generation=null;deviceCandidate=null;deviceProbe=null;device=null;usingDevice=false;actualDeviceIssue=null;publicationLeg=null;candidateGround=null;groundOptions=null;originalGoal=null;deviceAdapter=null;ownedDeviceAdmission=null;flight=null;flightCandidate=null;flightPreview=null;usingFlight=false;}
    }
    @Override public long now(){return System.currentTimeMillis();}
    @Override public boolean owned(SharedMovementCoordinator.Lease expected){
        if(expected==null||expected!=lease||!groundCustodyCurrent())return false;
        if(usingDevice&&device!=null&&device.state()==McInstantTransmission.State.ARRIVED){
            if(!deviceTerminalCurrent())return false;
        }
        return nativeOwned(expected,epoch);
    }
    private boolean nativeOwned(SharedMovementCoordinator.Lease expected,long expectedEpoch){
        final var loadedAtEntry=loadedSegmentSource;
        final var loadedGenerationAtEntry=generation;
        final var loadedLegAtEntry=publicationLeg;
        final var loadedCameraAtEntry=cameraOwner;
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
            &&slotCurrent()&&groundCustodyCurrent()&&nativePolicy!=null&&nativePolicy.current()
            &&dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,parent)
            &&(issuedReturnRequest==null||suspendedReturnBridge!=null&&suspendedReturnBridge.current()&&suspendedReturnBridge.nativeCurrent())
            &&(loadedAtEntry==null||loadedSegmentSource==loadedAtEntry&&ownsLoadedSegmentSource(loadedAtEntry)&&loadedAtEntry.nativeSourceCurrent())
            &&(loadedAtEntry==null||loadedNativeWindowCurrent())
            &&(loadedAtEntry==null||McOwnedRotation.authority(client)==loadedCameraAtEntry)
            &&(loadedAtEntry==null||loadedSegmentSource==loadedAtEntry&&lease==expected&&generation==loadedGenerationAtEntry
                &&publicationLeg==loadedLegAtEntry&&cameraOwner==loadedCameraAtEntry&&loadedLegAtEntry!=null
                &&(loadedLegAtEntry.retirementOutcome()==null?(issuedLoadedSegment==null?loadedLegAtEntry.loadedMaintenanceSourceFieldsCurrent(loadedAtEntry)
                        :loadedLegAtEntry.loadedIssuedSourceFieldsCurrent(loadedAtEntry))
                    :(issuedLoadedSegment==null?loadedLegAtEntry.loadedMaintenanceRetiredFieldsCurrent(loadedAtEntry)
                        :loadedLegAtEntry.loadedIssuedRetiredFieldsCurrent(loadedAtEntry))));
    }
    private boolean slotCurrent(){
        if(deviceAdmission&&deviceProbe!=null)return (services.inventory().selectedSlot()==slot||services.inventory().selectedSlot()==deviceProbe.tool().slot())
            &&McMovementDeviceInventory.unchanged(client,deviceProbe.inventory());
        if(usingDevice&&device!=null&&(device.state()==McInstantTransmission.State.AIMING||device.state()==McInstantTransmission.State.WAITING))
            return deviceProbe!=null&&services.inventory().selectedSlot()==deviceProbe.tool().slot()
                &&McMovementDeviceInventory.unchanged(client,deviceProbe.inventory());
        return services.inventory().selectedSlot()==slot&&ItemStack.matches(held,client.player.getInventory().getItem(slot));
    }
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
        flightCandidate=null;flightPreview=null;
        if(options.purpose()==SharedMovementPolicy.Purpose.GENERAL_TRAVEL&&options.allowed().contains(SharedMovementPolicy.Mode.FLIGHT)
                &&config.get(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE)!=ApproachMode.WALK_ONLY){
            flightPreview=McSupportedFlightPreview.read(client,candidateGround,goal,
                Math.max(4,Math.min(128,config.get(dev.veltraph.client.config.TravelConfig.FLIGHT_SEARCH_RADIUS))),
                Math.max(100,Math.min(50000,config.get(dev.veltraph.client.config.TravelConfig.FLIGHT_NODE_BUDGET))));
            if(!owned(lease)){stopOwned(lease,null);return List.of();}
            if(flightPreview!=null){flightCandidate=new SharedMovementPolicy.Candidate("supported-flight",SharedMovementPolicy.Mode.FLIGHT,
                goal,flightPreview.cost(),0,flightPreview,generation,null);accepted.add(flightCandidate);}
        }
        deviceCandidate=null;deviceProbe=null;returnApproach=null;
        if(uses>0&&nativePolicy!=null&&nativePolicy.devicesAllowed()&&options.allowed().stream().anyMatch(SharedMovementPolicy::device)
                &&(options.purpose()==SharedMovementPolicy.Purpose.GENERAL_TRAVEL&&options.terminalRadius()>=.30
                    ||options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&issuedDeviceEntry==null)){
            var inventory=McMovementDeviceInventory.read(client).orElse(null);
            if(inventory!=null)for(var tool:inventory.tools())for(var ability:tool.capabilities()){
                if(ability.kind()!=MovementDeviceEvidence.Kind.INSTANT_TRANSMISSION||ability.sneak()||ability.causesOtherActions()
                    ||ability.range()<=0||origin.distanceTo(goal)<ability.range()+.5)continue;
                var length=origin.distanceTo(goal);Vec3 landing;
                if(options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN){
                    if(!tool.serverId().equals("ASPECT_OF_THE_END")&&!tool.serverId().equals("ASPECT_OF_THE_VOID"))continue;
                    landing=ReturnApproachGeometry.landing(origin,goal,ability.range()).orElse(null);if(landing==null)continue;
                    var nativeApproach=new ReturnApproachProof(origin,goal,landing,ability.range());returnApproach=nativeApproach;
                    if(!nativeApproach.geometryCurrent()){returnApproach=null;continue;}
                }else landing=new Vec3(origin.x()+(goal.x()-origin.x())*ability.range()/length,
                    origin.y()+(goal.y()-origin.y())*ability.range()/length,origin.z()+(goal.z()-origin.z())*ability.range()/length);
                var probe=McInstantTransmission.probe(client,services,config,landing,now()).orElse(null);
                if(probe==null||!owned(lease))continue;
                var mode=probe.tool().serverId().equals("ASPECT_OF_THE_VOID")?SharedMovementPolicy.Mode.AOTV:SharedMovementPolicy.Mode.AOTE;
                deviceProbe=probe;deviceCandidate=new SharedMovementPolicy.Candidate("instant-"+probe.tool().serverId(),mode,landing,
                    5+landing.distanceTo(goal),1,probe,probe.capability(),probe.tool().identity());
                accepted.add(deviceCandidate);return owned(lease)?List.copyOf(accepted):List.of();
            }
        }
        return owned(lease)?List.copyOf(accepted):List.of();
    }
    @Override public boolean permitted(SharedMovementCoordinator.Lease l,SharedMovementPolicy.Candidate c){
        if(!owned(l))return false;
        if(c==flightCandidate){
            return groundOptions!=null
                &&config.get(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE)!=ApproachMode.WALK_ONLY
                &&c.geometryProof()==flightPreview&&c.capabilityProof()==generation
                &&McSupportedFlightPreview.current(client,flightPreview)&&candidateGround!=null
                &&McNativeFlightGeometry.standing(client,candidateGround,c.destination())&&McNativeFlightGeometry.body(client,candidateGround,new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()),0)
                &&owned(l);
        }
        if(c==deviceCandidate){
            if(nativePolicy==null||!nativePolicy.devicesAllowed())return false;
            if(c.geometryProof()!=deviceProbe||c.capabilityProof()!=deviceProbe.capability()||c.toolIdentity()!=deviceProbe.tool().identity())return false;
            if(groundOptions.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN){
                if(returnApproach==null||!returnApproach.structural()||!returnApproach.landing.equals(c.destination()))return false;
                if(!usingDevice&&!returnApproach.geometryCurrent())return false;
            }
            if(usingDevice)return device!=null&&device.state()!=McInstantTransmission.State.FAILED&&device.state()!=McInstantTransmission.State.CANCELLED;
            var fresh=McInstantTransmission.probe(client,services,config,c.destination(),now()).orElse(null);
            return fresh!=null&&fresh.inventory().inventory().equals(deviceProbe.inventory().inventory())
                &&fresh.tool().identity().equals(deviceProbe.tool().identity())&&fresh.capability().equals(deviceProbe.capability())&&owned(l);
        }
        return cursorPrepared&&c==prepared&&c.geometryProof()==geometry&&c.capabilityProof()==generation
            &&candidateGround!=null&&candidateGround.bodyClear(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()),false)
            &&candidateGround.standingClear(c.destination())&&owned(l);
    }
    @Override public SharedMovementCoordinator.Issue begin(SharedMovementCoordinator.Lease l,SharedMovementPolicy.Candidate c){
        if(!permitted(l,c)||!admitMovementLeg())return null;
        if(c==flightCandidate){
            ownedDeviceAdmission=null;usingDevice=false;actualDeviceIssue=null;usingFlight=true;
            flight=new McFlightNavigator(client,services.input(),config);
            if(!McSupportedFlightPreview.current(client,flightPreview)||!publicationLeg.current()
                ||!flight.startSupported(c.destination(),publicationLeg,groundOptions.purpose(),groundOptions.terminalRadius(),new FlightDeadline()))return null;
            if(!groundCustodyCurrent()||!McSupportedFlightPreview.current(client,flightPreview))return null;
            epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
            return new SharedMovementCoordinator.Issue(new Object(),generation,0,now(),false);
        }
        usingFlight=false;
        if(c==deviceCandidate){
            actualDeviceIssue=null;expectedConsumed=false;
            usingDevice=true;device=new McInstantTransmission(client,services,config);
            deviceAdapter=newDeviceAdapter(l,generation,publicationLeg);
            final var deviceLease=l;final var deviceGeneration=generation;final var deviceAuthority=maintenanceAuthority;
            final var deviceCamera=cameraOwner;final var deviceConfigStamp=configStamp;final long deviceJourney=journey;
            final var deviceCameraAdapter=deviceAdapter;
            var fence=new McInstantTransmission.LeaseFence(){
                public boolean current(Object owner,Object player,Object world,Object connection,long nativeEpoch){
                    return lease==deviceLease&&generation==deviceGeneration&&maintenanceAuthority==deviceAuthority
                        &&cameraOwner==deviceCamera&&owner==deviceLease.inputOwner()&&player==deviceLease.player()
                        &&world==deviceLease.world()&&connection==deviceLease.connection()&&nativeOwned(deviceLease,nativeEpoch);}
                public Object policy(){return config;}public long revision(){return deviceConfigStamp.approachRevision();}public long journey(){return deviceJourney;}
                public McInstantTransmission.CameraCustodyAdapter cameraAdapter(){return deviceCameraAdapter;}
            };
            deviceAdmission=true;boolean started;
            final long startNow=now();
            final var admitted=new OwnedDeviceAdmission(device,fence,c,deviceAdapter,publicationLeg,startNow);
            ownedDeviceAdmission=admitted;
            if(groundOptions.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&issuedReturnRequest==null){
                if(returnApproach==null||!returnApproach.geometryCurrent()){deviceAdmission=false;return null;}
                admitted.normalReturnAdmission=new NormalReturnDeviceAdmission(admitted,returnApproach);
                if(loadedSegmentSource!=null&&maintenanceAuthority!=null){
                    var actualAuthority=captureRestoredMaintenanceDeviceAuthority(admitted.normalReturnAdmission);
                    if(actualAuthority==null||admitted.normalReturnAdmission.loadedMaintenanceAuthorityIdentity()!=actualAuthority){deviceAdmission=false;return null;}
                }
            }
            if(groundOptions.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&issuedReturnRequest!=null){
                admitted.returnAdmission=new ReturnDeviceAdmission(admitted);
                admitted.returnTransit=issuedReturnRequest.captureDeviceTransit(admitted.returnAdmission);
                if(admitted.returnTransit==null){deviceAdmission=false;return null;}
            }
            try{started=admitted.deviceDeadline>startNow&&device.start(parent,fence,c.destination(),startNow,admitted.deviceDeadline-startNow);admitted.started=started;}
            finally{deviceAdmission=false;}
            if(!started||ownedDeviceAdmission!=admitted||!admitted.sourceCurrent())return null;
            if(!groundCustodyCurrent())return null;
            return new SharedMovementCoordinator.Issue(new Object(),generation,0,now(),false);
        }
        ownedDeviceAdmission=null;usingDevice=false;actualDeviceIssue=null;
        if(!publicationLeg.current())return null;
        var actor=new LeaseGroundAdapter(publicationLeg);
        ground=new McNavigator(client,actor,config,actor);
        if(groundOptions==null||!prepareGround(ground,c.destination(),groundOptions)||!publicationLeg.current())return null;
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
        return new SharedMovementCoordinator.Issue(new Object(),generation,0,now(),false);
    }
    @Override public SharedMovementCoordinator.Progress tick(SharedMovementCoordinator.Lease l,SharedMovementPolicy.Candidate c,SharedMovementCoordinator.Issue i){
        if(!permitted(l,c))return SharedMovementCoordinator.Progress.REJECTED;
        if(c==flightCandidate){
            if(!usingFlight||flight==null||i.receiptDomain()!=generation)return SharedMovementCoordinator.Progress.REJECTED;
            var result=flight.tick();
            if(!groundCustodyCurrent()||!McSupportedFlightPreview.current(client,flightPreview))return SharedMovementCoordinator.Progress.REJECTED;
            epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
            return switch(result){case FLYING->SharedMovementCoordinator.Progress.ACTIVE;case ARRIVED->SharedMovementCoordinator.Progress.ARRIVED;default->SharedMovementCoordinator.Progress.REJECTED;};
        }
        if(c==deviceCandidate){
            var result=device.tick(now());
            if(device.appliedReceipt()!=null)retainExpectedAck(device.issue(),device.appliedReceipt());
            if(result==McInstantTransmission.State.AIMING||result==McInstantTransmission.State.WAITING||result==McInstantTransmission.State.ARRIVED){
                if(!groundCustodyCurrent())return SharedMovementCoordinator.Progress.REJECTED;
                if(result==McInstantTransmission.State.ARRIVED&&!deviceTerminalCurrent())return SharedMovementCoordinator.Progress.REJECTED;
            }
            return switch(result){case AIMING,WAITING->SharedMovementCoordinator.Progress.ACTIVE;case ARRIVED->SharedMovementCoordinator.Progress.ARRIVED;default->SharedMovementCoordinator.Progress.REJECTED;};
        }
        if(i.receiptDomain()!=generation)return SharedMovementCoordinator.Progress.REJECTED;
        var result=ground.tick();
        if(!groundCustodyCurrent())return SharedMovementCoordinator.Progress.REJECTED;
        // This Leg consumed the exact positive own camera ticket before any guarded key publication.
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();
        return switch(result){case WALKING->SharedMovementCoordinator.Progress.ACTIVE;case ARRIVED->SharedMovementCoordinator.Progress.ARRIVED;default->SharedMovementCoordinator.Progress.REJECTED;};
    }
    @Override public SharedMovementCoordinator.Issue dispatchedIssue(SharedMovementCoordinator.Lease l,SharedMovementCoordinator.Issue armed){
        if(!owned(l)||device==null||!device.dispatched()||device.issue()==null)return null;
        var nativeIssue=device.issue();
        if(actualDeviceIssue==null)actualDeviceIssue=new SharedMovementCoordinator.Issue(nativeIssue.nonce(),nativeIssue.domain(),nativeIssue.baseline(),nativeIssue.issuedAt(),true);
        return actualDeviceIssue;
    }
    @Override public SharedMovementCoordinator.AppliedReceipt receipt(SharedMovementCoordinator.Lease l,SharedMovementCoordinator.Issue i){
        if(!owned(l)||device==null||device.appliedReceipt()==null)return null;
        var r=device.appliedReceipt();return new SharedMovementCoordinator.AppliedReceipt(r.nonce(),r.domain(),r.serial(),r.receivedAt(),r.position(),r.context().player(),r.context().world(),r.context().connection());
    }
    @Override public boolean at(Vec3 destination,double radius){
        if(!owned(lease))return false;var p=client.player;var at=new Vec3(p.getX(),p.getY(),p.getZ());
        return at.horizontalDistanceTo(destination)<=radius&&Math.abs(at.y()-destination.y())<.08
            &&p.onGround()&&Math.hypot(p.getDeltaMovement().x,p.getDeltaMovement().z)<.08
            &&(coordinator.selected()==flightCandidate?McNativeFlightGeometry.standing(client,ground,at):ground.standingClear(at))&&owned(lease);
    }
    /** Privately created only during this actual coordinator's begin; a clock value is not authority. */
    final class FlightDeadline {
        private final SharedMovementCoordinator.Lease expected=lease;private final Object expectedGeneration=generation;
        private final long began=coordinator.originalBeganAt(),deadline=coordinator.originalDeadlineAt();private long lastNative=coordinator.observedClockFloor();
        private FlightDeadline(){}
        boolean current(){long now=System.currentTimeMillis();boolean valid=client.isSameThread()&&expected==lease&&expectedGeneration==generation
            &&began>=0&&deadline>began&&coordinator.originalBeganAt()==began&&coordinator.originalDeadlineAt()==deadline
            &&now>=began&&now>=lastNative&&now>=coordinator.observedClockFloor()&&now<=deadline;
            if(valid)lastNative=now;return valid;}
    }
    private boolean prepareGround(McNavigator navigator,Vec3 goal,SharedMovementPolicy.Options options){
        if(options.purpose()==SharedMovementPolicy.Purpose.CROP_WORK)
            return preferLevel?maxNodes==2500&&searchRadius==32&&options.terminalRadius()==.35&&navigator.startPreferLevel(goal):navigator.start(goal,maxNodes,searchRadius,options.terminalRadius());
        if(options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN)
            return preferLevel?maxNodes==2500&&searchRadius==32&&options.terminalRadius()==.35&&navigator.startDynamicExactReturnPreferLevel(goal,maxNodes,searchRadius,options.terminalRadius())
                :navigator.startDynamicExactReturn(goal,maxNodes,searchRadius,options.terminalRadius());
        return preferLevel?navigator.startDynamicPreferLevel(goal,maxNodes,searchRadius,options.terminalRadius()):navigator.startDynamic(goal,maxNodes,searchRadius,options.terminalRadius());
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
        if(!publicationLeg.current()||publicationLeg.parent()!=parent||publicationLeg.cameraAuthority()!=cameraOwner)return false;
        epoch=publicationLeg.cameraEpoch();rotationGeneration=publicationLeg.requestGeneration();return true;
    }
    public LegacyTravelRetirement.RetirementOutcome groundRetirementOutcome(){return groundRetirement;}
    public boolean retirementRefused(){return groundResetFailed||groundRetirement!=null&&!groundRetirement.cleanSuccess();}
    private boolean deviceTerminalCurrent(){
        if(device==null||deviceAdapter==null)return false;var terminal=device.terminalCleanup();
        return terminal!=null&&terminal.adapter()==deviceAdapter&&terminal.issue()==device.issue()
            &&terminal.applied()==device.appliedReceipt()&&terminal.current();
    }
    private boolean admitMovementLeg(){
        if(publicationLeg==null||groundOptions==null||originalGoal==null||retirementRefused())return false;
        if(loadedSegmentSource!=null&&issuedReturnRequest==null&&publicationLeg.retirementOutcome()!=null){
            var source=loadedSegmentSource;var predecessor=publicationLeg;var completed=loadedStopSnapshot;
            if(loadedAdmissionMarker!=null||completed==null||completed.source!=source||completed.originalLeg!=predecessor
                ||!completed.cleanReturned())return false;
            var marker=new LoadedAdmissionMarker(source,false);loadedAdmissionMarker=marker;
            try{
                var continued=predecessor.retirementContinuationContext();
                if(continued==null||!predecessor.retirementOutcome().cleanSuccess()||!marker.beforePublicationCurrent()
                    ||!source.nativeSourceCurrent()||!source.originalSelectionHistoryCurrent()||!loadedNativeWindowCurrent()
                    ||!marker.beforePublicationCurrent())return false;
                var affiliation=nativeNormalReturnAffiliation;
                if(affiliation!=null&&(!affiliation.matchesActualLeg(predecessor)||!affiliation.nativeCurrent()
                    ||continued.parent()!=parent||continued.inputOwner()!=parent||continued.cameraAuthority()!=cameraOwner
                    ||continued.player()!=client.player||continued.world()!=client.level||continued.connection()!=client.getConnection()
                    ||continued.config()!=config||continued.journey()!=journey)||!marker.beforePublicationCurrent())return false;
                var next=publicationLease.beginAfterLoadedContinuation(source,this,journey,groundOptions.purpose(),originalGoal,predecessor);
                if(next==null)return false;
                publicationLeg=next; // Genuine output retained before every post-publication visitor and refusal.
                loadedPreKernelAdmission=new LoadedPreKernelAdmission(source,next);
                loadedStopSnapshot=null; // Completed old receipt stays with old Leg data; it never cleans the successor.
                if(!marker.fieldsCurrent(next)||next.originalPredecessorIdentity()!=predecessor)return false;
                if(affiliation!=null&&(next.parent()!=parent||next.journeyGeneration()!=journey
                    ||!affiliation.advanceAfterActualAdmission(predecessor,next))){if(marker.fieldsCurrent(next))groundResetFailed=true;return false;}
                return groundCustodyCurrent()&&marker.fieldsCurrent(next);
            }finally{if(loadedAdmissionMarker==marker)loadedAdmissionMarker=null;}
        }
        if(loadedSegmentSource!=null&&issuedLoadedSegment!=null&&publicationLeg.retirementOutcome()!=null&&issuedDeviceEntry==null){
            var source=loadedSegmentSource;var cap=issuedLoadedSegment;var predecessor=publicationLeg;var completed=loadedStopSnapshot;
            if(loadedAdmissionMarker!=null||completed==null||completed.source!=source||completed.originalLeg!=predecessor
                ||!completed.cleanReturned()||!loadedIssuedAffiliation(source)||!cap.nativeCurrent()||!loadedNativeWindowCurrent())return false;
            var marker=new LoadedAdmissionMarker(source,false);loadedAdmissionMarker=marker;
            try{var next=publicationLease.beginAfterIssuedLoadedContinuation(cap,source,this,journey,groundOptions.purpose(),originalGoal,predecessor);
                if(next==null)return false;
                publicationLeg=next;loadedPreKernelAdmission=new LoadedPreKernelAdmission(source,next);loadedStopSnapshot=null;
                if(next.originalPredecessorIdentity()!=predecessor||next.loadedIssuedBinding(cap)!=suspendedReturnBridge
                    ||!marker.fieldsCurrent(next))return false;
                return groundCustodyCurrent()&&marker.fieldsCurrent(next);
            }finally{if(loadedAdmissionMarker==marker)loadedAdmissionMarker=null;}
        }
        if(publicationLeg.retirementOutcome()!=null){
            var predecessor=publicationLeg;
            final var completedLoadedStop=loadedSegmentSource==null?null:loadedStopSnapshot;
            if(loadedSegmentSource!=null&&(issuedLoadedSegment==null?
                completedLoadedStop==null||completedLoadedStop.source!=loadedSegmentSource
                    ||completedLoadedStop.originalLeg!=predecessor||!completedLoadedStop.cleanReturned()
                :!loadedIssuedAffiliation(loadedSegmentSource)||issuedDeviceEntry==null
                    ||issuedDeviceEntry.loadedSegmentIdentity()!=issuedLoadedSegment||issuedDeviceEntry.predecessorIdentity()!=predecessor
                    ||groundRetirement!=predecessor.retirementOutcome()||groundRetirement==null||!groundRetirement.cleanSuccess()
                    ||!issuedLoadedSegment.nativeCurrent()||!loadedNativeWindowCurrent()))return false;
            var continued=predecessor.retirementContinuationContext();
            if(continued==null||!predecessor.retirementOutcome().cleanSuccess())return false;
            // Check the original transaction before beginAfter performs any actual successor admission.
            var affiliation=nativeNormalReturnAffiliation;
            if(issuedReturnRequest==null&&affiliation!=null&&(!affiliation.matchesActualLeg(predecessor)
                ||!affiliation.nativeCurrent()||continued.parent()!=parent||continued.inputOwner()!=parent
                ||continued.cameraAuthority()!=cameraOwner||continued.player()!=client.player||continued.world()!=client.level
                ||continued.connection()!=client.getConnection()||continued.config()!=config||continued.journey()!=journey))return false;
            LegacyTravelPublicationLease.Leg next;
            if(issuedReturnRequest!=null){
                var entry=issuedDeviceEntry;if(entry==null||entry.predecessorIdentity()!=predecessor)return false;
                try{next=publicationLease.beginAfterIssuedReturnEntry(entry,issuedReturnRequest,this,journey,originalGoal,groundOptions.terminalRadius(),predecessor);}
                catch(RuntimeException|Error failure){
                    var failed=publicationLease.consumeFailedIssuedReturnEntry(entry,issuedReturnRequest,this);
                    if(failed!=null)publicationLeg=failed;throw failure;
                }
            }else if(loadedSegmentSource!=null){
                var source=loadedSegmentSource;
                if(source.issuedRequestIdentity()!=null||!ownsLoadedSegmentSource(source)||!source.nativeSourceCurrent()
                    ||!source.originalSelectionHistoryCurrent()||!loadedNativeWindowCurrent())return false;
                next=publicationLease.beginAfterLoadedContinuation(source,this,journey,groundOptions.purpose(),originalGoal,predecessor);
            }else next=publicationLease.beginAfter(journey,groundOptions.purpose(),originalGoal,predecessor);
            if(next==null)return false;
            if(loadedSegmentSource!=null&&issuedLoadedSegment==null&&(completedLoadedStop==null||loadedStopSnapshot!=completedLoadedStop
                ||!completedLoadedStop.cleanReturned()||next.originalPredecessorIdentity()!=predecessor))return false;
            publicationLeg=next;
            if(loadedSegmentSource!=null&&issuedLoadedSegment!=null){
                loadedPreKernelAdmission=new LoadedPreKernelAdmission(loadedSegmentSource,next);
                if(next.originalPredecessorIdentity()!=predecessor||next.loadedIssuedBinding(issuedLoadedSegment)!=suspendedReturnBridge
                    ||!next.loadedIssuedSourceFieldsCurrent(loadedSegmentSource))return false;
            }
            if(completedLoadedStop!=null)loadedStopSnapshot=null; // Real private successor replaces old cleanup data, never old permission.
            // Only this actual Shared beginAfter result can advance the passive exact-Leg affiliation.
            if(issuedReturnRequest==null&&affiliation!=null){
                if(next.originalPredecessorIdentity()!=predecessor||next.parent()!=parent||next.journeyGeneration()!=journey
                    ||!affiliation.advanceAfterActualAdmission(predecessor,next)){groundResetFailed=true;return false;}
            }
        }
        return groundCustodyCurrent();
    }
    private McInstantTransmission.CameraCustodyAdapter newDeviceAdapter(SharedMovementCoordinator.Lease expected,Object expectedGeneration,
            LegacyTravelPublicationLease.Leg actualLeg){
        return new McInstantTransmission.CameraCustodyAdapter(){
            private final Object slotCleanupContext=new Object();private Object selectedSlotLoan;
            private dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.Snapshot.DeviceSlotCustody issuedSlotCustody;
            private ReturnDeviceAdmission issuedAdmission(){var a=ownedDeviceAdmission;return a!=null&&a.returnAdmission!=null
                &&a.adapter==this&&a.leg==actualLeg?a.returnAdmission:null;}
            private NormalReturnDeviceAdmission normalAdmission(){var a=ownedDeviceAdmission;return a!=null&&a.normalReturnAdmission!=null
                &&a.adapter==this&&a.leg==actualLeg?a.normalReturnAdmission:null;}
            private boolean same(){return client.isSameThread()&&lease==expected&&generation==expectedGeneration&&publicationLeg==actualLeg
                &&actualLeg.parent()==parent&&actualLeg.cameraAuthority()==cameraOwner&&cameraOwner==expected.cameraOwner()
                &&client.player==expected.player()&&client.level==expected.world()&&client.getConnection()==expected.connection()
                &&config==expected.configStore()&&VeltraphBootstrap.config()==config;}
            public dev.veltraph.client.service.mc.McRotationService.CleanupReceipt queueOwnedSource(float yaw,float pitch,Runnable completed){
                if(!same())return null;var admitted=issuedAdmission();
                if(admitted!=null){var receipt=actualLeg.queueOwnedIssuedDeviceSource(admitted,yaw,pitch,completed);return same()?receipt:null;}
                if(issuedReturnRequest!=null)return null;
                var receipt=actualLeg.queueOwnedSource(yaw,pitch,completed);return same()?receipt:null;}
            public dev.veltraph.client.service.mc.McRotationService.CleanupReceipt refreshOwnedSource(dev.veltraph.client.service.mc.McRotationService.CleanupReceipt expectedReceipt){
                if(!same())return null;var receipt=actualLeg.refreshOwnedSource(expectedReceipt);return same()?receipt:null;}
            public void deliverQueuedSourceCompletion(dev.veltraph.client.service.mc.McRotationService.CleanupReceipt exactReceipt){
                if(!same())return;var admitted=issuedAdmission();
                if(admitted!=null)actualLeg.deliverIssuedDeviceQueueCompletion(admitted,exactReceipt);
            }
            public boolean cancelOwnedSource(dev.veltraph.client.service.mc.McRotationService.CleanupReceipt expectedReceipt){
                return same()&&actualLeg.cancelOwnedSource(expectedReceipt)&&same();}
            public boolean selectSlotOwned(int expectedCurrent,int next,java.util.function.BooleanSupplier beforeDeviceFence,
                    java.util.function.BooleanSupplier afterDeviceFence){
                if(!same())return false;
                var returned=issuedAdmission();
                if(returned!=null){
                    var transit=returned.admitted.returnTransit;
                    if(transit==null||!(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService nativeInventory))return false;
                    if(expectedCurrent==next)return next==returned.deviceSlot()&&transit.originalSlot()==next
                        &&transit.nativeCurrent()&&same()&&beforeDeviceFence.getAsBoolean()&&same()&&afterDeviceFence.getAsBoolean()
                        &&same()&&transit.nativeCurrent();
                    return Boolean.TRUE.equals(returned.request.withDeviceSelection(returned,nativeInventory,expectedCurrent,next,false,
                        call->applyIssuedDeviceSlotSelection(returned,call,expectedCurrent,next,beforeDeviceFence,afterDeviceFence)));
                }
                if(issuedReturnRequest!=null)return false;
                var normal=normalAdmission();
                if(normal!=null){
                    if(expectedCurrent==next)return next==normal.deviceSlot()&&normal.originalSlot()==next
                        &&same()&&beforeDeviceFence.getAsBoolean()&&same()&&afterDeviceFence.getAsBoolean()&&same()
                        &&actualLeg.normalReturnDeviceSlotCurrent(normal);
                    return applyNormalReturnDeviceSlotSelection(normal,expectedCurrent,next,beforeDeviceFence,afterDeviceFence,false);
                }
                if(actualLeg.parent() instanceof dev.veltraph.client.mining.commission.CommissionModule&&maintenanceAuthority==null){
                    if(selectedSlotLoan!=null)return false;
                    selectedSlotLoan=actualLeg.selectDeviceSlotLoanOwned(slotCleanupContext,expectedCurrent,next,
                        ()->same()&&beforeDeviceFence.getAsBoolean()&&same(),
                        ()->same()&&afterDeviceFence.getAsBoolean()&&same());
                    return selectedSlotLoan!=null&&actualLeg.deviceSlotLoanReady(slotCleanupContext,selectedSlotLoan)&&same();
                }
                return actualLeg.selectSlotOwned(expectedCurrent,next,
                    ()->same()&&beforeDeviceFence.getAsBoolean()&&same(),
                    ()->same()&&afterDeviceFence.getAsBoolean()&&same())&&same();
            }
            /** The canonical actual adapter alone invokes this named native selection bridge. */
            private boolean applyIssuedDeviceSlotSelection(ReturnDeviceAdmission admitted,
                    dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall call,
                    int from,int to,java.util.function.BooleanSupplier before,java.util.function.BooleanSupplier after){
                if(!same()||admitted!=issuedAdmission()||call==null||!call.belongsTo(admitted.sourceHeader()))return false;
                var custody=call.cleanupRequested()?call.sourceHeader().retainedDeviceSlotCustody(call.transitIdentity()):call.sourceHeader().captureDeviceSlotCustody(call);
                if(custody==null||call.cleanupRequested()&&custody!=issuedSlotCustody)return false;
                if(!call.cleanupRequested())issuedSlotCustody=custody;
                var actual=custody.beginSelection(call);if(actual==null)return false;
                boolean selected=actualLeg.selectIssuedDeviceSlotOwned(admitted,call,actual,from,to,
                    ()->same()&&before.getAsBoolean()&&same(),()->same()&&after.getAsBoolean()&&same());
                if(selected&&call.cleanupRequested()&&loadedSegmentSource!=null){
                    var witness=custody.captureRestorationWitness();
                    if(witness==null||issuedLoadedSegment==null||witness.transitIdentity().loadedSegmentIdentity()!=issuedLoadedSegment
                        ||!loadedSegmentSource.returnCallIdentity().advanceOriginalIssuedSelection(witness,loadedSegmentSource))return false;
                    originalReturnSelectionHistory=witness.restoredHistory();
                }
                return selected&&(call.cleanupRequested()?custody.restoredCurrent():custody.readyCurrent())&&same();
            }
            /** Named real normal-return selector/restore; the private Leg owns every native history fact. */
            private boolean applyNormalReturnDeviceSlotSelection(NormalReturnDeviceAdmission admission,int from,int to,
                    java.util.function.BooleanSupplier before,java.util.function.BooleanSupplier after,boolean restore){
                if(!same()||admission!=normalAdmission())return false;
                if(restore){
                    if(admission.normalSlotLoan==null)return false;
                    boolean restored=actualLeg.restoreNormalReturnDeviceSlotLoanOwned(admission,admission.normalSlotLoan);
                    if(restored){
                        var exactHistory=actualLeg.normalReturnRestoredSelectionHistory(admission,admission.normalSlotLoan);
                        if(exactHistory==null||!exactHistory.current())return false;
                        // The witness is minted only here, after this exact real native restore.
                        var witness=actualLeg.captureNormalReturnRestorationWitness(admission,admission.normalSlotLoan);
                        var previous=provenNormalReturnHistory();
                        if(witness==null||previous==null||witness.originalLegIdentity()!=actualLeg||witness.originalSlot()!=slot
                            ||!witness.originalHistory().sameStoredObservation(previous)
                            ||!witness.restoredHistory().sameStoredObservation(exactHistory)||!witness.rawCurrent())return false;
                        if(witness.retainedLineageCurrent()){
                            if(loadedSegmentSource!=null){
                                if(!loadedSegmentSource.returnCallIdentity().advanceOriginalSelection(witness,loadedSegmentSource))return false;
                                var authority=admission.loadedMaintenanceAuthorityIdentity();
                                if(authority!=null&&!authority.originalNormalSlotRestorationReturned(witness))return false;
                            }
                            if(normalReturnCleanupRestoration!=null)return false;
                            normalReturnRestorationChain=new NormalReturnRestorationLink(normalReturnRestorationChain,witness);
                        }else{
                            // Local/no-packet selection may be strictly restored but cannot seed positive continuation.
                            if(normalReturnCleanupRestoration!=null)return false;
                            normalReturnCleanupRestoration=witness;
                        }
                        originalReturnSelectionHistory=exactHistory;
                    }
                    return restored&&same();
                }
                if(admission.normalSlotLoan!=null)return false;
                var loan=actualLeg.selectNormalReturnDeviceSlotLoanOwned(admission,from,to,
                    ()->same()&&before.getAsBoolean()&&same(),()->same()&&after.getAsBoolean()&&same());
                admission.normalSlotLoan=loan;
                return loan!=null&&actualLeg.normalReturnDeviceSlotLoanReady(admission,loan)&&same();
            }
            /** Actual private device-start token adds refusal only; the existing typed restore owns every native effect. */
            public McInstantTransmission.SelectedSlotCleanup restoreStartingSlotLoan(McInstantTransmission.StartCustody exact){
                var admitted=ownedDeviceAdmission;var normal=normalAdmission();
                if(normal==null)return McInstantTransmission.SelectedSlotCleanup.UNAVAILABLE;
                var loan=normal.normalSlotLoan;
                if(exact==null||admitted==null||loan==null||issuedReturnRequest!=null||!same()
                    ||admitted!=normal.admitted||admitted.normalReturnAdmission!=normal||admitted.adapter!=this||admitted.leg!=actualLeg
                    ||admitted.executor!=device||admitted.fence==null
                    ||!exact.matchesOriginal(admitted.executor,this,admitted.fence,parent,journey,normal.originalSlot(),normal.deviceSlot())
                    ||!normal.cleanupSourceCurrent())return McInstantTransmission.SelectedSlotCleanup.REFUSED;
                // Native/source callbacks have returned. All original aliases and genuine N2 history are checked last.
                if(!same()||ownedDeviceAdmission!=admitted||normalAdmission()!=normal||normal.normalSlotLoan!=loan
                    ||!exact.matchesOriginal(admitted.executor,this,admitted.fence,parent,journey,normal.originalSlot(),normal.deviceSlot())
                    ||!exact.nativeStructuralCurrent()
                    ||!actualLeg.normalReturnSelectionCustodyRawCurrent(normal,normal.originalSlot(),normal.originalSelectionHistory()))
                    return McInstantTransmission.SelectedSlotCleanup.REFUSED;
                var result=restoreSelectedSlotLoan();
                if(result!=McInstantTransmission.SelectedSlotCleanup.RESTORED)return result;
                // RESTORED is the original canonical receipt/witness result, never a fresh slot/history inference.
                return same()&&ownedDeviceAdmission==admitted&&normalAdmission()==normal&&normal.normalSlotLoan==loan
                    &&exact.matchesOriginal(admitted.executor,this,admitted.fence,parent,journey,normal.originalSlot(),normal.deviceSlot())
                    &&exact.nativeStructuralCurrent()
                    &&actualLeg.normalReturnSelectionCustodyRawCurrent(normal,normal.originalSlot(),normal.originalSelectionHistory())
                    ?McInstantTransmission.SelectedSlotCleanup.RESTORED:McInstantTransmission.SelectedSlotCleanup.REFUSED;
            }
            public McInstantTransmission.SelectedSlotCleanup restoreSelectedSlotLoan(){
                var returned=issuedAdmission();
                if(returned!=null){
                    var transit=returned.admitted.returnTransit;
                    if(transit==null||!same())return McInstantTransmission.SelectedSlotCleanup.REFUSED;
                    if(issuedSlotCustody==null)return transit.originalSlot()==returned.deviceSlot()&&transit.cleanupCurrent()
                        ?McInstantTransmission.SelectedSlotCleanup.RESTORED:McInstantTransmission.SelectedSlotCleanup.REFUSED;
                    if(!(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService nativeInventory))return McInstantTransmission.SelectedSlotCleanup.REFUSED;
                    boolean restored=Boolean.TRUE.equals(returned.request.withDeviceSelection(returned,nativeInventory,returned.deviceSlot(),transit.originalSlot(),true,
                        call->applyIssuedDeviceSlotSelection(returned,call,returned.deviceSlot(),transit.originalSlot(),
                            ()->same()&&transit.cleanupCurrent(),()->same()&&call.custodySourceCurrent())));
                    return restored?McInstantTransmission.SelectedSlotCleanup.RESTORED:McInstantTransmission.SelectedSlotCleanup.REFUSED;
                }
                if(issuedReturnRequest!=null)return McInstantTransmission.SelectedSlotCleanup.REFUSED;
                var normal=normalAdmission();
                if(normal!=null){
                    if(normal.normalSlotLoan==null)return normal.originalSlot()==normal.deviceSlot()&&actualLeg.normalReturnDeviceSlotCurrent(normal)
                        ?McInstantTransmission.SelectedSlotCleanup.RESTORED:McInstantTransmission.SelectedSlotCleanup.REFUSED;
                    boolean restored=applyNormalReturnDeviceSlotSelection(normal,normal.deviceSlot(),normal.originalSlot(),()->same(),()->same(),true);
                    return restored?McInstantTransmission.SelectedSlotCleanup.RESTORED:McInstantTransmission.SelectedSlotCleanup.REFUSED;
                }
                if(selectedSlotLoan==null)return McInstantTransmission.SelectedSlotCleanup.UNAVAILABLE;
                return same()&&actualLeg.restoreDeviceSlotLoanOwned(slotCleanupContext,selectedSlotLoan)&&same()
                    ?McInstantTransmission.SelectedSlotCleanup.RESTORED:McInstantTransmission.SelectedSlotCleanup.REFUSED;
            }
            public Object ownedUseSnapshot(){
                if(!same())return null;
                var admitted=ownedDeviceAdmission;
                if(admitted==null||admitted.adapter!=this||admitted.leg!=actualLeg||!admitted.sourceCurrent())return null;
                var issued=admitted.executor.issue();
                if(issued==null)return null;
                if(admitted.handed!=null)return admitted.handed.issue==issued&&ownedUseSnapshotCurrent(admitted.handed)?admitted.handed:null;
                var actualGameplay=admitted.returnAdmission!=null?actualLeg.ownedIssuedUseSnapshot(admitted.returnAdmission)
                    :admitted.normalReturnAdmission!=null?actualLeg.ownedNormalReturnUseSnapshot(admitted.normalReturnAdmission):actualLeg.ownedUseSnapshot();
                if(actualGameplay==null||!same()||!admitted.sourceCurrent()||admitted.executor.issue()!=issued)return null;
                var handed=new OwnedDeviceUseSource(admitted,issued,actualGameplay);
                admitted.handed=handed;
                if(admitted.returnAdmission!=null&&(admitted.returnTransit==null
                    ||!admitted.returnAdmission.request.bindDeviceIssue(admitted.returnAdmission,issued,handed)))return null;
                return ownedUseSnapshotCurrent(handed)?handed:null;
            }
            public boolean ownedUseSnapshotCurrent(Object exactSnapshot){
                if(!(exactSnapshot instanceof OwnedDeviceUseSource actual))return false;
                return same()&&actual.admission.adapter==this&&McSharedMovementNavigator.ownedDeviceUseSnapshotCurrent(
                    actual.admission.executor,actual,actual.admission.fence,parent,cameraOwner,config,
                    actual.cameraEpoch,actual.cameraGeneration,journey,actual.admission.candidate.destination());
            }
            public Object publicationSnapshot(){
                if(!same())return null;var snapshot=actualLeg.publicationSnapshot();return same()?snapshot:null;
            }
            public boolean publicationCurrent(Object exactSnapshot){return same()&&actualLeg.publicationCurrent(exactSnapshot)&&same();}
            public dev.veltraph.client.service.mc.McRotationService.GuiRequestRetirement retireGuiOwnedSource(dev.veltraph.client.service.mc.McRotationService.CleanupReceipt exactHanded){
                if(!same())return null;var retirement=actualLeg.retireOwnedSourceForGui(exactHanded);return same()?retirement:null;
            }
            public dev.veltraph.client.service.mc.McRotationService.GuiRequestRetirement observedGuiRequestRetirement(){
                return same()?actualLeg.observedGuiRequestRetirement():null;
            }
            public boolean guiCleanupCurrent(dev.veltraph.client.service.mc.McRotationService.GuiRequestRetirement exact){
                return exact!=null&&same()&&actualLeg.observedGuiRequestRetirement()==exact&&actualLeg.cleanupCurrent()
                    &&actualLeg.observedGuiRequestRetirement()==exact&&same();
            }
            public Object guiCleanupSnapshot(dev.veltraph.client.service.mc.McRotationService.GuiRequestRetirement exact){
                if(!same())return null;var snapshot=actualLeg.guiCleanupSnapshot(exact);return same()?snapshot:null;
            }
            public boolean guiCleanupSnapshotCurrent(dev.veltraph.client.service.mc.McRotationService.GuiRequestRetirement exact,Object snapshot){
                return same()&&actualLeg.guiCleanupSnapshotCurrent(exact,snapshot)&&same();
            }
            public boolean current(){return same()&&actualLeg.current()&&same();}
            public boolean cleanupCurrent(){return same()&&actualLeg.cleanupCurrent()&&same();}
            public boolean inventoryCleanupCurrent(){
                if(!same())return false;var admitted=issuedAdmission();
                return admitted!=null?actualLeg.inventoryCleanupCurrentIssued(admitted)&&same()
                    :issuedReturnRequest==null&&actualLeg.inventoryCleanupCurrent()&&same();
            }
            public long cameraEpoch(){return actualLeg.cameraEpoch();}
            public long requestGeneration(){return actualLeg.requestGeneration();}
            public Object cameraAuthority(){return actualLeg.cameraAuthority();}
        };
    }

    /** Actual private normal-device admission is captured before the genuine SDK native selection. */
    private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority captureRestoredMaintenanceDeviceAuthority(
            NormalReturnDeviceAdmission actual){
        var source=loadedSegmentSource;
        if(actual==null||actual.admitted!=ownedDeviceAdmission||actual.admitted.normalReturnAdmission!=actual
            ||actual.legIdentity()!=publicationLeg||maintenanceAuthority==null||source==null||!ownsLoadedSegmentSource(source)
            ||!source.nativeSourceCurrent())return null;
        return source.returnCallIdentity().issueRestoredMaintenanceDeviceAuthority(source,actual);
    }
    private ReturnApproachProof returnApproach;
    private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalReturnSelectionHistory;
    private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory normalReturnInitialHistory;
    private NativeNormalReturnAffiliation nativeNormalReturnAffiliation;
    private boolean normalReturnStopCompleted;
    private NormalReturnRestorationLink normalReturnRestorationChain;
    private LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness normalReturnCleanupRestoration;
    /** Immutable own-setter links, appended only inside the actual native restore branch. */
    private static final class NormalReturnRestorationLink {
        final NormalReturnRestorationLink previous;
        final LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness witness;
        private NormalReturnRestorationLink(NormalReturnRestorationLink previous,
                LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness witness){this.previous=previous;this.witness=witness;}
    }
    /** Stored lineage only: earlier genuine histories need not remain the current native version. */
    private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory provenNormalReturnHistory(){
        if(normalReturnInitialHistory==null)return null;
        var latest=normalReturnRestorationChain;
        for(var link=latest;link!=null;link=link.previous){
            var witness=link.witness;
            var before=link.previous==null?normalReturnInitialHistory:link.previous.witness.restoredHistory();
            if(witness==null||!witness.retainedLineageCurrent()||witness.originalSlot()!=slot||before==null
                ||!witness.originalHistory().sameStoredObservation(before))return null;
        }
        return latest==null?normalReturnInitialHistory:latest.witness.restoredHistory();
    }
    /** Private native geometry/source association. This is not a copied candidate or permission Boolean. */
    private final class ReturnApproachProof {
        final SharedMovementCoordinator.Lease originalLease=lease;final Object originalGeneration=generation;
        final Vec3 origin,canonical,landing;final double range;
        final Object player=client.player,world=client.level,connection=client.getConnection();
        private ReturnApproachProof(Vec3 origin,Vec3 canonical,Vec3 landing,double range){this.origin=origin;this.canonical=canonical;this.landing=landing;this.range=range;}
        boolean boundSource(){return lease==originalLease&&generation==originalGeneration&&originalGoal==canonical
            &&client.player==player&&client.level==world&&client.getConnection()==connection
            &&ReturnApproachGeometry.exact(origin,canonical,landing,range);}
        boolean structural(){return returnApproach==this&&boundSource();}
        boolean geometryCurrent(){return structural()&&owned(originalLease)
            &&new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).distanceTo(origin)<=1e-6
            &&ground.standingClear(origin)&&ground.standingClear(landing)&&TravelPath.wideClear(origin,landing,ground::walkClear)
            &&structural()&&owned(originalLease);}
    }
    /** Ordinary active-owner return retains all original Leg role/Root/native capsule gates. */
    /** Passive exact private Shared journey/Leg observation, never native action authority. */
    public final class NativeNormalReturnAffiliation {
        private LegacyTravelPublicationLease.Leg currentLeg;
        private final Object originalParent=parent,originalGeneration=generation,originalCamera=cameraOwner;
        private final SharedMovementCoordinator.Lease originalLease=lease;
        private final long originalJourney=journey;
        private final Vec3 goal=originalGoal;
        private final ConfigService store=config;
        private final Object player=client.player,world=client.level,connection=client.getConnection();
        private final Object inventory=client.player.getInventory(),options=client.options,gameMode=client.gameMode,menu=client.player.inventoryMenu;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory initialHistory=normalReturnInitialHistory;
        private NativeNormalReturnAffiliation(LegacyTravelPublicationLease.Leg exact){currentLeg=exact;}
        /** Structural membership only; exact native source and Root remain separate permission consumers. */
        private boolean structuralCurrent(){return client.isSameThread()&&nativeNormalReturnAffiliation==this&&issuedReturnRequest==null
            &&parent==originalParent&&cameraOwner==originalCamera&&generation==originalGeneration&&lease==originalLease&&journey==originalJourney&&originalGoal==goal
            &&publicationLeg==currentLeg&&currentLeg!=null&&config==store&&VeltraphBootstrap.config()==store
            &&groundOptions!=null&&groundOptions.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN
            &&currentLeg.parent()==originalParent&&currentLeg.cameraAuthority()==originalCamera&&currentLeg.journeyGeneration()==originalJourney
            &&initialHistory!=null&&normalReturnInitialHistory==initialHistory
            &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.level!=null
            &&client.getConnection()!=null&&client.options==options&&client.gameMode==gameMode&&client.player.getInventory()==inventory
            &&client.player.inventoryMenu==menu&&client.player.containerMenu==menu&&client.player.inventoryMenu.getCarried().isEmpty()
            &&client.player.level()==client.level&&client.player.isAlive()&&!client.player.isPassenger()&&!client.player.isScoping()
            &&!client.player.isInLava()&&!client.isPaused()&&client.gui.screen()==null&&client.getCameraEntity()==client.player
            &&!client.level.tickRateManager().isEntityFrozen(client.player);}
        public boolean matchesActualLeg(LegacyTravelPublicationLease.Leg exact){return exact!=null&&exact==currentLeg&&structuralCurrent();}
        public LegacyTravelPublicationLease.Leg predecessorIdentity(){return structuralCurrent()?currentLeg.originalPredecessorIdentity():null;}
        /** Called only by actual Shared admission, after real clean predecessor retirement and beginAfter. */
        private boolean advanceAfterActualAdmission(LegacyTravelPublicationLease.Leg predecessor,LegacyTravelPublicationLease.Leg next){
            if(nativeNormalReturnAffiliation!=this||currentLeg!=predecessor||publicationLeg!=next||next==null
                ||next.originalPredecessorIdentity()!=predecessor||next.parent()!=originalParent||next.journeyGeneration()!=originalJourney
                ||parent!=originalParent||cameraOwner!=originalCamera||generation!=originalGeneration||lease!=originalLease||journey!=originalJourney||originalGoal!=goal
                ||normalReturnCleanupRestoration!=null||provenNormalReturnHistory()==null)return false;
            currentLeg=next;return structuralCurrent();
        }
        public boolean nativeCurrent(){
            var state=coordinator.state();
            return structuralCurrent()&&!retirementRefused()&&normalReturnCleanupRestoration==null
                &&(state==SharedMovementCoordinator.State.SELECTING||state==SharedMovementCoordinator.State.MOVING
                    ||state==SharedMovementCoordinator.State.WAITING_RECEIPT||state==SharedMovementCoordinator.State.ARRIVED)
                &&slotProjectionCurrent(false)&&rawCurrent();
        }
        /** Cleanup-only affiliation, including the original FAILED/STOPPED/own-disable cleanup window. */
        public boolean cleanupCurrent(){return structuralCurrent()&&slotProjectionCurrent(true)&&cleanupRawCurrent();}
        /** Inert final projection after all semantic/source callbacks; never an owner/admission permission. */
        public boolean rawCurrent(){
            var state=coordinator.state();
            return structuralCurrent()&&!retirementRefused()&&normalReturnCleanupRestoration==null
                &&(state==SharedMovementCoordinator.State.SELECTING||state==SharedMovementCoordinator.State.MOVING
                    ||state==SharedMovementCoordinator.State.WAITING_RECEIPT||state==SharedMovementCoordinator.State.ARRIVED)
                &&slotProjectionRawCurrent(false)&&structuralCurrent()&&!retirementRefused();
        }
        public boolean cleanupRawCurrent(){return structuralCurrent()&&slotProjectionRawCurrent(true)&&structuralCurrent();}
        /** Only the latest stored own history is compared to native current; pending calls use actual N2 custody. */
        private boolean slotProjectionCurrent(boolean cleanup){
            var proven=provenNormalReturnHistory();if(proven==null)return false;
            var partial=normalReturnCleanupRestoration;
            if(partial!=null){return cleanup&&partial.originalLegIdentity()==currentLeg&&partial.originalSlot()==slot
                &&partial.originalHistory().sameStoredObservation(proven)&&partial.rawCurrent()
                &&partial.restoredHistory().sameStoredObservation(originalReturnSelectionHistory);}
            if(!proven.sameStoredObservation(originalReturnSelectionHistory))return false;
            var admitted=ownedDeviceAdmission;var normal=admitted==null?null:admitted.normalReturnAdmission;
            if(normal!=null){
                // A completed restore is already in our private chain; its admission still holds the earlier history.
                var latest=normalReturnRestorationChain;
                if(latest==null||latest.witness.originalLegIdentity()!=currentLeg
                    ||!latest.witness.originalHistory().sameStoredObservation(normal.originalSelectionHistory())){
                    return normal.legIdentity()==currentLeg&&normal.originalSelectionHistory()!=null
                        &&normal.originalSelectionHistory().sameStoredObservation(proven)
                        &&currentLeg.normalReturnSelectionCustodyCurrent(normal,slot,proven);
                }
            }
            return proven.current()&&client.player.getInventory().getSelectedSlot()==slot;
        }
        /** Raw stored history/association only; component equality belongs to the earlier semantic phase. */
        private boolean slotProjectionRawCurrent(boolean cleanup){
            var proven=provenNormalReturnHistory();if(proven==null)return false;
            var partial=normalReturnCleanupRestoration;
            if(partial!=null){return cleanup&&partial.originalLegIdentity()==currentLeg&&partial.originalSlot()==slot
                &&partial.originalHistory().sameStoredObservation(proven)&&partial.rawProjectionCurrent()
                &&partial.restoredHistory().sameStoredObservation(originalReturnSelectionHistory);}
            if(!proven.sameStoredObservation(originalReturnSelectionHistory))return false;
            var admitted=ownedDeviceAdmission;var normal=admitted==null?null:admitted.normalReturnAdmission;
            if(normal!=null){
                // A completed restore is already in our private chain; its admission still holds the earlier history.
                var latest=normalReturnRestorationChain;
                if(latest==null||latest.witness.originalLegIdentity()!=currentLeg
                    ||!latest.witness.originalHistory().sameStoredObservation(normal.originalSelectionHistory())){
                    return normal.legIdentity()==currentLeg&&normal.originalSelectionHistory()!=null
                        &&normal.originalSelectionHistory().sameStoredObservation(proven)
                        &&currentLeg.normalReturnSelectionCustodyRawCurrent(normal,slot,proven);
                }
            }
            return proven.current()&&client.player.getInventory().getSelectedSlot()==slot;
        }
        /** The original helper history must link to Shared's immutable initial observation, never a fresh capture. */
        public boolean selectedSlotCurrent(int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory helperOriginalHistory){
            return helperOriginalHistory!=null&&originalSlot==slot&&initialHistory.sameStoredObservation(helperOriginalHistory)&&nativeCurrent();
        }
        public boolean cleanupSelectedSlotCurrent(int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory helperOriginalHistory){
            return helperOriginalHistory!=null&&originalSlot==slot&&initialHistory.sameStoredObservation(helperOriginalHistory)&&cleanupCurrent();
        }
        public boolean rawSelectedSlotCurrent(int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory helperOriginalHistory){
            return helperOriginalHistory!=null&&originalSlot==slot&&initialHistory.sameStoredObservation(helperOriginalHistory)&&rawCurrent();
        }
        public boolean cleanupRawSelectedSlotCurrent(int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory helperOriginalHistory){
            return helperOriginalHistory!=null&&originalSlot==slot&&initialHistory.sameStoredObservation(helperOriginalHistory)&&cleanupRawCurrent();
        }
    }
    /** Only raw affiliation is returned; private actual macro producer and original factory supply permission. */
    public NativeNormalReturnAffiliation captureNativeNormalReturnAffiliation(LegacyTravelPublicationLease.Leg exact,
            Object expectedParent,ConfigService expectedStore,Vec3 expectedGoal){
        if(!client.isSameThread()||issuedReturnRequest!=null||exact==null||exact!=publicationLeg||expectedParent!=parent
            ||expectedStore!=config||expectedGoal==null||originalGoal==null||!originalGoal.equals(expectedGoal)
            ||generation==null||lease==null||client.player==null||normalReturnInitialHistory==null||groundOptions==null
            ||groundOptions.purpose()!=SharedMovementPolicy.Purpose.EXACT_RETURN)return null;
        var actual=nativeNormalReturnAffiliation;
        if(actual==null){actual=new NativeNormalReturnAffiliation(exact);nativeNormalReturnAffiliation=actual;}
        return actual.matchesActualLeg(exact)&&actual.nativeCurrent()?actual:null;
    }
    public final class NormalReturnDeviceAdmission {
        private final OwnedDeviceAdmission admitted;
        private final ReturnApproachProof approach;
        private final int originalSlot=slot;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory=originalReturnSelectionHistory;
        private Object normalSlotLoan;
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority loadedMaintenanceAuthority;
        public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority loadedMaintenanceAuthorityIdentity(){return loadedMaintenanceAuthority;}
        /** Only the actual SavedReturnAdapter private producer can bind its genuine capsule before final handoff checks. */
        public boolean bindLoadedMaintenanceDeviceAuthority(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.filter(f->f.getDeclaringClass()!=NormalReturnDeviceAdmission.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedReturnAdapter.class
                    &&first.getMethodName().equals("issueLoadedReturnDeviceAuthority");
            });
            if(!caller||exact==null||loadedMaintenanceAuthority!=null||exact.admissionIdentity()!=this
                ||loadedSegmentSource==null||exact.sourceIdentity()!=loadedSegmentSource
                ||exact.callIdentity()!=loadedSegmentSource.returnCallIdentity()||maintenanceAuthority==null
                ||exact.inputOwner()!=admitted.originalParent||exact.cameraOwner()!=admitted.originalCamera
                ||!sourceCurrentStructural()||admitted.normalReturnAdmission!=this)return false;
            loadedMaintenanceAuthority=exact;return loadedMaintenanceDeviceFieldsCurrent(exact);
        }
        /** Stored exact private association only, never an independent Root/source/gameplay permission. */
        public boolean loadedMaintenanceDeviceFieldsCurrent(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){
            return exact!=null&&loadedMaintenanceAuthority==exact&&exact.admissionIdentity()==this
                &&loadedSegmentSource!=null&&exact.sourceIdentity()==loadedSegmentSource
                &&exact.callIdentity()==loadedSegmentSource.returnCallIdentity()
                &&maintenanceAuthority!=null&&exact.inputOwner()==admitted.originalParent&&exact.cameraOwner()==admitted.originalCamera
                &&sourceCurrentStructural()&&admitted.returnApproachProof!=null&&admitted.returnApproachProof.boundSource();
        }
        public McInstantTransmission.TerminalCleanupReceipt terminalCleanupIdentity(){return admitted.executor.terminalCleanup();}
        /** Same private association/native context only. Actual cleanup source and pending close remain with the authority issuer. */
        public boolean loadedMaintenanceDeviceCleanupFieldsCurrent(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){
            var expected=admitted.originalLease;
            return exact!=null&&loadedMaintenanceAuthority==exact&&exact.admissionIdentity()==this
                &&loadedSegmentSource!=null&&exact.sourceIdentity()==loadedSegmentSource
                &&exact.callIdentity()==loadedSegmentSource.returnCallIdentity()&&maintenanceAuthority!=null
                &&exact.inputOwner()==admitted.originalParent&&exact.cameraOwner()==admitted.originalCamera
                &&sourceCurrentStructural()&&client.isSameThread()&&client.player==expected.player()
                &&client.level==expected.world()&&client.getConnection()==expected.connection()
                &&client.player!=null&&client.level!=null&&client.player.level()==client.level
                &&client.player.isAlive()&&!client.player.isPassenger()&&!client.player.isScoping()&&!client.player.isInLava()
                &&!client.isPaused()&&client.gui.screen()==null&&client.getCameraEntity()==client.player
                &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty();
        }
        public boolean loadedMaintenanceDeviceCleanupSlotFieldsCurrent(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){
            return loadedMaintenanceDeviceCleanupFieldsCurrent(exact)&&admitted.leg!=null&&originalHistory!=null
                &&admitted.leg.normalReturnSelectionCustodyRawCurrent(this,originalSlot,originalHistory);
        }
        /** Native custody projection after the issuer's semantic/Root checks; it invokes no full source or role guard. */
        public boolean loadedMaintenanceDeviceSlotFieldsCurrent(dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority exact){
            return loadedMaintenanceDeviceFieldsCurrent(exact)&&admitted.leg!=null&&originalHistory!=null
                &&admitted.leg.normalReturnSelectionCustodyRawCurrent(this,originalSlot,originalHistory);
        }
        private NormalReturnDeviceAdmission(OwnedDeviceAdmission admitted,ReturnApproachProof approach){this.admitted=admitted;this.approach=approach;}
        public LegacyTravelPublicationLease.Leg legIdentity(){return admitted.leg;}
        public McSharedMovementNavigator issuerIdentity(){return McSharedMovementNavigator.this;}
        public McLoadedGroundReturnNavigator.SelectedLoadedSegment loadedSegmentSourceIdentity(){return loadedSegmentSource;}
        public Object parentIdentity(){return admitted.originalParent;}
        public Vec3 targetPosition(){return admitted.goal;}
        public int deviceSlot(){return admitted.probe.tool().slot();}public int originalSlot(){return originalSlot;}
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalSelectionHistory(){return originalHistory;}
        public Object normalSlotLoanIdentity(){return normalSlotLoan;}
        public DeviceUseReceipt.Issue issueIdentity(){return admitted.executor.issue();}
        public Object actualOwnedUseSourceIdentity(){return admitted.handed;}
        private boolean budgetSourceCurrent(){
            var state=coordinator.state();var real=admitted.executor.issue();int remaining=coordinator.remainingToolUses();
            boolean dispatched=actualDeviceIssue!=null&&actualDeviceIssue.dispatched()&&admitted.executor.dispatched()&&real!=null
                &&actualDeviceIssue.token()==real.nonce()&&actualDeviceIssue.receiptDomain()==real.domain()
                &&actualDeviceIssue.baseline()==real.baseline()&&actualDeviceIssue.issuedAt()==real.issuedAt();
            boolean charged=state==SharedMovementCoordinator.State.WAITING_RECEIPT||state==SharedMovementCoordinator.State.ARRIVED;
            boolean ending=state==SharedMovementCoordinator.State.FAILED||state==SharedMovementCoordinator.State.STOPPED;
            boolean budget=charged?dispatched&&remaining==admitted.availableUses-admitted.candidate.toolUses()
                :ending?remaining==admitted.availableUses||dispatched&&remaining==admitted.availableUses-admitted.candidate.toolUses()
                :remaining==admitted.availableUses;
            return budget&&SharedMovementPolicy.device(admitted.candidate.mode())&&admitted.options.allowed().contains(admitted.candidate.mode())
                &&admitted.candidate.toolUses()==1&&admitted.availableUses>=admitted.candidate.toolUses()&&admitted.availableUses<=admitted.options.toolUses()
                &&coordinator.originalBeganAt()==admitted.began&&coordinator.originalDeadlineAt()==admitted.journeyDeadline
                &&admitted.began>=0&&admitted.startNow>=admitted.began&&admitted.startNow<admitted.journeyDeadline&&admitted.deviceDeadline>admitted.startNow;
        }
        public boolean sourceCurrentStructural(){return budgetSourceCurrent()&&admitted.normalReturnAdmission==this&&issuedReturnRequest==null
            &&lease==admitted.originalLease&&generation==admitted.originalGeneration&&publicationLeg==admitted.leg
            &&ownedDeviceAdmission==admitted&&device==admitted.executor&&deviceAdapter==admitted.adapter&&usingDevice
            &&(deviceAdmission||admitted.started)&&parent==admitted.originalParent&&cameraOwner==admitted.originalCamera
            &&journey==admitted.originalJourney&&nativePolicy==admitted.policy&&groundOptions==admitted.options&&originalGoal==admitted.goal
            &&admitted.options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&coordinator.selected()==admitted.candidate
            &&deviceCandidate==admitted.candidate&&deviceProbe==admitted.probe&&admitted.candidate.geometryProof()==admitted.probe
            &&admitted.candidate.capabilityProof()==admitted.probe.capability()&&admitted.candidate.toolIdentity()==admitted.probe.tool().identity()
            &&approach!=null&&approach.structural()&&originalSlot>=0&&originalSlot<9&&originalHistory!=null;}
        public boolean nativeSourceCurrent(){return sourceCurrentStructural()&&client.isSameThread()&&client.player==admitted.originalLease.player()
            &&client.level==admitted.originalLease.world()&&client.getConnection()==admitted.originalLease.connection()
            &&client.player!=null&&client.level!=null&&client.player.level()==client.level&&client.player.isAlive()
            &&!client.player.isPassenger()&&!client.player.isScoping()&&!client.player.isInLava()&&!client.isPaused()
            &&client.gui.screen()==null&&client.getCameraEntity()==client.player&&client.player.containerMenu==client.player.inventoryMenu
            &&client.player.inventoryMenu.getCarried().isEmpty()&&!client.level.tickRateManager().isEntityFrozen(client.player)
            &&VeltraphBootstrap.config()==config&&admitted.policy.current();}
        public boolean cleanupSourceCurrent(){return nativeSourceCurrent();}
        public boolean qualifiedApproachCurrent(Vec3 originalOrigin,Vec3 actualDeviceGoal){return sourceCurrentStructural()
            &&approach.origin.equals(originalOrigin)&&approach.landing.equals(actualDeviceGoal)&&approach.geometryCurrent();}
    }
    private ReturnDeviceAdmission retiredReturnDeviceAdmission;
    private dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.FinalEntryContinuation issuedDeviceEntry;
    /** Real private Shared producer affiliation. Structural leaves alone never authorize a native effect. */
    public final class ReturnDeviceAdmission {
        private final OwnedDeviceAdmission admitted;
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request;
        private final Object expectedOptions=client.options,expectedGameMode=client.gameMode;
        private McInstantTransmission.TerminalCleanupReceipt terminalBeforeRetirement;
        private ReturnDeviceAdmission(OwnedDeviceAdmission admitted){this.admitted=admitted;request=issuedReturnRequest;}
        public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest requestIdentity(){return request;}
        public McSharedMovementNavigator issuerIdentity(){return McSharedMovementNavigator.this;}
        public dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.SourceHeader sourceHeader(){return request==null?null:request.sourceHeader();}
        public dev.veltraph.client.rewarp.FarmReturnPoint canonicalTargetPoint(){return request==null?null:request.canonicalTargetPoint();}
        public Vec3 targetPosition(){return admitted.goal;}
        public int deviceSlot(){return admitted.probe.tool().slot();}
        public long deviceDeadlineMillis(){return admitted.deviceDeadline;}
        public long journeyDeadlineMillis(){return admitted.journeyDeadline;}
        public long deviceStartedAtMillis(){return admitted.startNow;}
        public McInstantTransmission.State deviceState(){return admitted.executor.state();}
        public LegacyTravelPublicationLease.Leg legIdentity(){return admitted.leg;}
        public LegacyTravelPublicationLease.Leg originalLegIdentity(){return admitted.leg;}
        public DeviceUseReceipt.Issue issueIdentity(){return admitted.executor.issue();}
        public Object actualOwnedUseSourceIdentity(){return admitted.handed;}
        public Object ownedUseSourceIdentity(){return admitted.handed;}
        public boolean qualifiedApproachCurrent(Vec3 origin,Vec3 endpoint){return custodySourceCurrent()&&admitted.returnApproachProof!=null
            &&admitted.returnApproachProof.origin.equals(origin)&&admitted.returnApproachProof.landing.equals(endpoint)
            &&admitted.returnApproachProof.geometryCurrent();}
        public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transitIdentity(){return admitted.returnTransit;}
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment selectedSegment=issuedLoadedSegment;
        public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedSegmentIdentity(){return selectedSegment;}
        private boolean permanentAffiliation(){return admitted.returnAdmission==this&&request!=null&&issuedReturnRequest==request
            &&lease==admitted.originalLease&&generation==admitted.originalGeneration&&parent==admitted.originalParent
            &&cameraOwner==admitted.originalCamera&&journey==admitted.originalJourney&&nativePolicy==admitted.policy
            &&groundOptions==admitted.options&&originalGoal==admitted.goal
            &&admitted.candidate.geometryProof()==admitted.probe&&admitted.candidate.capabilityProof()==admitted.probe.capability()
            &&admitted.candidate.toolIdentity()==admitted.probe.tool().identity()
            &&admitted.options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN
            &&admitted.returnApproachProof!=null&&admitted.returnApproachProof.boundSource()
            &&admitted.returnApproachProof.landing.equals(admitted.candidate.destination())
            &&request.ownsSharedChild(McSharedMovementNavigator.this)
            &&(selectedSegment==null?loadedSegmentSource==null&&request.targetPosition().equals(admitted.goal)
                :issuedLoadedSegment==selectedSegment&&loadedSegmentSource==selectedSegment.sourceIdentity()
                    &&selectedSegment.requestIdentity()==request&&selectedSegment.sharedChildIdentity()==McSharedMovementNavigator.this
                    &&selectedSegment.segmentPosition().equals(admitted.goal)&&loadedIssuedAffiliation(loadedSegmentSource));}
        private boolean liveBudgetFieldsCurrent(){
            int actualRemaining=coordinator.remainingToolUses();
            var actualState=coordinator.state();var nativeIssue=admitted.executor.issue();
            boolean dispatched=actualDeviceIssue!=null&&actualDeviceIssue.dispatched()&&admitted.executor.dispatched()
                &&nativeIssue!=null&&actualDeviceIssue.token()==nativeIssue.nonce()&&actualDeviceIssue.receiptDomain()==nativeIssue.domain()
                &&actualDeviceIssue.baseline()==nativeIssue.baseline()&&actualDeviceIssue.issuedAt()==nativeIssue.issuedAt();
            boolean charged=actualState==SharedMovementCoordinator.State.WAITING_RECEIPT||actualState==SharedMovementCoordinator.State.ARRIVED;
            boolean cleanupState=actualState==SharedMovementCoordinator.State.FAILED||actualState==SharedMovementCoordinator.State.STOPPED;
            boolean originalBudget=charged?dispatched&&actualRemaining==admitted.availableUses-admitted.candidate.toolUses()
                :cleanupState?actualRemaining==admitted.availableUses||dispatched&&actualRemaining==admitted.availableUses-admitted.candidate.toolUses()
                :actualRemaining==admitted.availableUses;
            return coordinator.selected()==admitted.candidate&&deviceCandidate==admitted.candidate&&deviceProbe==admitted.probe
                &&SharedMovementPolicy.device(admitted.candidate.mode())&&admitted.options.allowed().contains(admitted.candidate.mode())
                &&admitted.candidate.toolUses()==1&&admitted.availableUses>=admitted.candidate.toolUses()
                &&admitted.availableUses<=admitted.options.toolUses()&&originalBudget
                &&coordinator.originalBeganAt()==admitted.began&&coordinator.originalDeadlineAt()==admitted.journeyDeadline
                &&admitted.began>=0&&admitted.startNow>=admitted.began&&admitted.startNow<admitted.journeyDeadline
                &&admitted.deviceDeadline>admitted.startNow;
        }
        public boolean sourceCurrentStructural(){return custodySourceCurrent();}
        public boolean custodySourceCurrent(){return permanentAffiliation()&&liveBudgetFieldsCurrent()&&ownedDeviceAdmission==admitted
            &&device==admitted.executor&&deviceAdapter==admitted.adapter&&publicationLeg==admitted.leg
            &&usingDevice&&(deviceAdmission||admitted.started)
            &&(coordinator.state()==SharedMovementCoordinator.State.SELECTING||coordinator.state()==SharedMovementCoordinator.State.MOVING
                ||coordinator.state()==SharedMovementCoordinator.State.WAITING_RECEIPT||coordinator.state()==SharedMovementCoordinator.State.ARRIVED
                ||coordinator.state()==SharedMovementCoordinator.State.FAILED||coordinator.state()==SharedMovementCoordinator.State.STOPPED);}
        /** Native data only: no request/view/teleport marker, history provider or publication callback is invoked. */
        private boolean nativeFieldsCurrent(){
            var expected=admitted.originalLease;
            return client.isSameThread()&&client.options==expectedOptions&&client.gameMode==expectedGameMode
                &&client.player==expected.player()&&client.level==expected.world()&&client.getConnection()==expected.connection()
                &&client.player!=null&&client.level!=null&&client.player.level()==client.level&&client.player.isAlive()
                &&!client.player.isPassenger()&&!client.player.isScoping()&&!client.player.isInLava()&&!client.isPaused()
                &&client.gui.screen()==null&&client.getCameraEntity()==client.player&&client.player.containerMenu==client.player.inventoryMenu
                &&client.player.inventoryMenu.getCarried().isEmpty()&&!client.level.tickRateManager().isEntityFrozen(client.player)
                &&VeltraphBootstrap.config()==config&&expected.configStore()==config&&admitted.policy.current();
        }
        private long lastNativeWindow=-1;
        /** Final refusal-only wall-clock fence; cleanup retains its separate expiry-independent source. */
        private boolean originalNativeWindowCurrent(){
            long actual=System.currentTimeMillis();
            long deadline=Math.min(admitted.deviceDeadline,Math.min(admitted.journeyDeadline,admitted.originalReturnDeadline));
            if(admitted.originalReturnDeadline<=0||!ReturnDeviceDeadlines.permitsOriginalWindow(actual,admitted.startNow,
                lastNativeWindow,coordinator.observedClockFloor(),deadline))return false;
            lastNativeWindow=actual;return true;
        }
        public boolean nativeSourceCurrent(){
            var actual=admitted.executor.state();
            return custodySourceCurrent()&&nativeFieldsCurrent()&&(deviceAdmission&&actual==McInstantTransmission.State.IDLE
                ||actual==McInstantTransmission.State.AIMING||actual==McInstantTransmission.State.WAITING||actual==McInstantTransmission.State.ARRIVED)
                &&originalNativeWindowCurrent();
        }
        public boolean cleanupSourceCurrent(){return custodySourceCurrent()&&nativeFieldsCurrent();}
        private boolean captureTerminalBeforeRetirement(McInstantTransmission.TerminalCleanupReceipt actual){
            if(terminalBeforeRetirement!=null)return terminalBeforeRetirement==actual;
            if(actual==null||actual!=admitted.executor.terminalCleanup()||actual.adapter()!=admitted.adapter
                ||actual.issue()!=admitted.executor.issue()||actual.applied()!=admitted.executor.appliedReceipt()
                ||!nativeSourceCurrent()||!actual.current())return false;
            terminalBeforeRetirement=actual;return nativeSourceCurrent()&&actual.current();
        }
        /** Cached genuine terminal fact plus current source context; never revives an expired device source. */
        public boolean terminalSourceFieldsCurrent(McInstantTransmission.TerminalCleanupReceipt exact){
            return exact!=null&&terminalBeforeRetirement==exact&&permanentAffiliation()&&nativeFieldsCurrent()
                &&(ownedDeviceAdmission==admitted||retiredReturnDeviceAdmission==this)
                &&exact.adapter()==admitted.adapter&&exact.issue()==admitted.executor.issue()
                &&exact.applied()==admitted.executor.appliedReceipt();
        }
    }
    /** Private admission is minted only around this coordinator's real, one-shot device start. */
    private final class OwnedDeviceAdmission {
        final McSharedMovementNavigator issuer=McSharedMovementNavigator.this;
        final McInstantTransmission executor;final McInstantTransmission.LeaseFence fence;
        final SharedMovementPolicy.Candidate candidate;final McInstantTransmission.CameraCustodyAdapter adapter;
        final LegacyTravelPublicationLease.Leg leg;final SharedMovementCoordinator.Lease originalLease=lease;
        final Object originalGeneration=generation;final Module originalParent=parent;final Object originalCamera=cameraOwner;
        final NativeSharedTravelPolicy policy=nativePolicy;final SharedMovementPolicy.Options options=groundOptions;
        final McInstantTransmission.Probe probe=deviceProbe;final Vec3 goal=originalGoal;
        final ReturnApproachProof returnApproachProof=returnApproach;
        final long originalJourney=journey,began=coordinator.originalBeganAt(),journeyDeadline=coordinator.originalDeadlineAt();
        final int availableUses=coordinator.remainingToolUses();final long startNow,deviceDeadline,originalReturnDeadline;
        boolean started;OwnedDeviceUseSource handed;ReturnDeviceAdmission returnAdmission;NormalReturnDeviceAdmission normalReturnAdmission;
        dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit returnTransit;
        private OwnedDeviceAdmission(McInstantTransmission executor,McInstantTransmission.LeaseFence fence,
                SharedMovementPolicy.Candidate candidate,McInstantTransmission.CameraCustodyAdapter adapter,
                LegacyTravelPublicationLease.Leg leg,long startNow){
            this.executor=executor;this.fence=fence;this.candidate=candidate;this.adapter=adapter;this.leg=leg;
            this.startNow=startNow;
            long returnDeadline=issuedReturnRequest==null?journeyDeadline:issuedReturnRequest.originalReturnDeadlineMillis();
            originalReturnDeadline=loadedSegmentSource==null?returnDeadline:Math.min(returnDeadline,loadedSegmentSource.originalDeadlineMillis());
            deviceDeadline=ReturnDeviceDeadlines.originalBound(startNow,journeyDeadline,originalReturnDeadline);
        }
        /** Cached source operands only; all native/source authority comes from the genuine Leg capsule. */
        private boolean sourceCurrent(){
            return started&&ownedDeviceAdmission==this&&device==executor&&deviceAdapter==adapter&&usingDevice
                &&lease==originalLease&&generation==originalGeneration&&publicationLeg==leg&&parent==originalParent
                &&cameraOwner==originalCamera&&journey==originalJourney&&nativePolicy==policy&&groundOptions==options
                &&originalGoal==goal&&deviceCandidate==candidate&&deviceProbe==probe&&coordinator.selected()==candidate
                &&candidate.geometryProof()==probe&&candidate.capabilityProof()==probe.capability()
                &&candidate.toolIdentity()==probe.tool().identity()&&SharedMovementPolicy.device(candidate.mode())
                &&(options.purpose()==SharedMovementPolicy.Purpose.GENERAL_TRAVEL
                    ||options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&returnAdmission!=null&&returnTransit!=null
                        &&returnTransit.nativeCurrent()&&returnAdmission.nativeSourceCurrent()
                    ||options.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&normalReturnAdmission!=null
                        &&normalReturnAdmission.nativeSourceCurrent())&&options.allowed().contains(candidate.mode())
                &&candidate.toolUses()==1&&availableUses>=candidate.toolUses()&&availableUses<=options.toolUses()
                &&coordinator.remainingToolUses()==availableUses
                &&(coordinator.state()==SharedMovementCoordinator.State.SELECTING||coordinator.state()==SharedMovementCoordinator.State.MOVING)
                &&coordinator.originalBeganAt()==began&&coordinator.originalDeadlineAt()==journeyDeadline
                &&began>=0&&startNow>=began&&startNow<journeyDeadline&&deviceDeadline>startNow;
        }
    }
    /** Opaque source handoff, never constructible from a public adapter, receipt record or Boolean. */
    private static final class OwnedDeviceUseSource {
        final OwnedDeviceAdmission admission;final DeviceUseReceipt.Issue issue;final Object gameplay;
        final long cameraEpoch,cameraGeneration;
        private OwnedDeviceUseSource(OwnedDeviceAdmission admission,DeviceUseReceipt.Issue issue,Object gameplay){
            this.admission=admission;this.issue=issue;this.gameplay=gameplay;
            cameraEpoch=admission.leg.cameraEpoch();cameraGeneration=admission.leg.requestGeneration();
        }
    }
    /** Read-only extraction from the actual private handoff; copied records/adapters cannot qualify. */
    public static boolean issuedOwnedUseSource(Object opaque){return opaque instanceof OwnedDeviceUseSource actual
        &&actual.admission.returnAdmission!=null&&actual.admission.returnTransit!=null;}
    public static dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit issuedDeviceTransitForOwnedUse(
            Object opaque,McInstantTransmission exact,DeviceUseReceipt.Issue issue){
        if(!(opaque instanceof OwnedDeviceUseSource actual)||actual.admission.executor!=exact||actual.issue!=issue
            ||actual.admission.handed!=actual||actual.admission.returnAdmission==null||actual.admission.returnTransit==null)return null;
        var transit=actual.admission.returnTransit;
        return transit.admissionIdentity()==actual.admission.returnAdmission&&transit.issueIdentity()==issue
            &&transit.ownedUseSourceIdentity()==actual&&transit.nativeCurrent()?transit:null;
    }
    /** A refusal bound only: the scalar grants no source permission and adds no clock/provider read. */
    public static long ownedDeviceUseJourneyDeadline(Object opaque){
        if(!(opaque instanceof OwnedDeviceUseSource actual))return -1;
        var admission=actual.admission;
        return admission.handed==actual&&admission.sourceCurrent()?Math.min(admission.journeyDeadline,admission.originalReturnDeadline):-1;
    }
    /** Callback-free final source veto. It cannot qualify an arbitrary executor or copied Leg capsule. */
    public static boolean ownedDeviceUseSnapshotCurrent(McInstantTransmission executor,Object opaque,
            McInstantTransmission.LeaseFence exactFence,Object expectedParent,Object expectedCamera,Object expectedStore,
            long epoch,long requestGeneration,long journey,Vec3 deviceGoal){
        if(!(opaque instanceof OwnedDeviceUseSource actual))return false;
        var admission=actual.admission;var issuer=admission.issuer;var issue=actual.issue;
        if(admission.handed!=actual||executor!=admission.executor||exactFence!=admission.fence
                ||expectedParent!=admission.originalParent||expectedCamera!=admission.originalCamera||expectedStore!=issuer.config
                ||journey!=admission.originalJourney||deviceGoal!=admission.candidate.destination()
                ||epoch!=actual.cameraEpoch||requestGeneration!=actual.cameraGeneration||!admission.sourceCurrent()
                ||issuer.coordinator.state()!=SharedMovementCoordinator.State.MOVING
                ||executor.state()!=McInstantTransmission.State.WAITING||executor.issue()!=issue
                ||issue.goal()!=deviceGoal||issue.context().parent()!=expectedParent||issue.context().policy()!=expectedStore
                ||issue.context().cameraEpoch()!=epoch||issue.context().journey()!=journey
                ||issue.context().player()!=admission.originalLease.player()||issue.context().world()!=admission.originalLease.world()
                ||issue.context().connection()!=admission.originalLease.connection()
                ||issue.deadline()!=admission.deviceDeadline||issue.issuedAt()<admission.startNow||issue.issuedAt()>=issue.deadline()
                ||issue.issuedAt()>=admission.journeyDeadline||issue.issuedAt()>=admission.originalReturnDeadline)return false;
        return LegacyTravelPublicationLease.ownedUseSnapshotCurrent(issuer.client,actual.gameplay,expectedParent,expectedCamera,
            expectedStore,epoch,requestGeneration,journey,deviceGoal);
    }

    /** Called only after the coordinator has decided failure; no guard or control action is repeated. */
    @Override public void observeFirstFailure(String why){
        if(!firstFailure.isEmpty())return;
        try{var p=client.player;var root=VeltraphBootstrap.moduleManager();var snapshot=root==null?null:root.lifecycleSnapshot();
            firstFailure="cause="+why+" coordinator="+coordinator.status()+" ground="+ground.diagnostics()+" beforeGroundStop="+ground.lastStopOperands()
                +" epochHeld="+epoch+" epochNative="+McOwnedRotation.epoch(client)+" serialNative="+McOwnedRotation.publicationSerial(client)
                +" serviceGenHeld="+rotationGeneration+" serviceGenNative="+(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService r?r.requestGeneration():-1)
                +" parentEnabled="+(parent!=null&&parent.isEnabled())+" parentRegistered="+(root!=null&&parent!=null&&root.get(parent.id()).orElse(null)==parent)
                +" lifecycleKnown="+(snapshot!=null&&snapshot.known())+" lock="+dev.veltraph.client.input.ScriptingInputLock.isActive()
                +" lockOwner="+dev.veltraph.client.input.ScriptingInputLock.ownerModuleId()+" device="+usingDevice
                +" legEpoch="+(publicationLeg==null?-1:publicationLeg.cameraEpoch())+" legGen="+(publicationLeg==null?-1:publicationLeg.requestGeneration())
                +" legTicket="+(publicationLeg==null?-1:publicationLeg.actualTicket())+" legRetirement="+(publicationLeg==null?null:publicationLeg.retirementOutcome())
                +" playerSame="+(lease!=null&&p==lease.player())+" worldSame="+(lease!=null&&client.level==lease.world())
                +" connectionSame="+(lease!=null&&client.getConnection()==lease.connection())+" configSame="+(VeltraphBootstrap.config()==config)
                +" slotHeld="+slot+" slotNative="+(p==null?-1:p.getInventory().getSelectedSlot())
                +" heldStackSame="+(p!=null&&slot>=0&&slot<9&&ItemStack.matches(held,p.getInventory().getItem(slot)))
                +" heldStackDiff="+heldStackDiff()
                +" pose="+(p==null?"none":p.position())+" velocity="+(p==null?"none":p.getDeltaMovement())
                +" keys="+(client.options==null?"none":java.util.List.of(client.options.keyUp.isDown(),client.options.keyDown.isDown(),client.options.keyLeft.isDown(),client.options.keyRight.isDown(),client.options.keyJump.isDown(),client.options.keyShift.isDown(),client.options.keySprint.isDown(),client.options.keyAttack.isDown(),client.options.keyUse.isDown()));
        }catch(Throwable unavailable){firstFailure="cause="+why+" captureUnavailable="+unavailable.getClass().getName();}
        try{System.out.println("SHARED_MOVEMENT_FIRST_FAILURE "+firstFailure);}catch(Throwable unavailable){}
    }
    /** First-failure diagnostics only: no codec, inventory action, guard refresh, or control supplier. */
    private String heldStackDiff(){
        try{var p=client.player;if(p==null||slot<0||slot>8)return "unavailable";
            var actual=p.getInventory().getItem(slot);var expected=held;
            var nearby=new java.util.ArrayList<String>();int inspected=0;boolean truncated=false;
            if(client.level!=null){var entities=client.level.entitiesForRendering().iterator();
                while(entities.hasNext()){
                    if(inspected>=256){truncated=true;break;}
                    var entity=entities.next();inspected++;
                    if(entity instanceof net.minecraft.world.entity.item.ItemEntity item&&item.distanceToSqr(p)<36){
                        nearby.add("id="+item.getId()+" pos="+item.position()+" stack="+stackDescription(item.getItem()));
                        if(nearby.size()>=16){truncated=true;break;}
                    }
                }
            }
            return "expected={"+stackDescription(expected)+"} actual={"+stackDescription(actual)+"}"
                +" sameItem="+(expected!=null&&expected.getItem()==actual.getItem())+" sameCount="+(expected!=null&&expected.getCount()==actual.getCount())
                +" sameComponents="+(expected!=null&&expected.getComponents().equals(actual.getComponents()))+" nearbyItems="+nearby+" inspected="+inspected+" truncated="+truncated;
        }catch(Throwable unavailable){return "unavailable="+unavailable.getClass().getName();}
    }
    private static String stackDescription(ItemStack stack){
        if(stack==null)return "null";
        return "empty="+stack.isEmpty()+" item="+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
            +" count="+stack.getCount()+" damage="+stack.getDamageValue()+" components="+stack.getComponents();
    }
    /** Passive cached/native scalar trace at the original retirement boundary; no guard is repeated. */
    private void traceRetirement(String phase,LegacyTravelPublicationLease.Leg leg){
        try{var p=client.player;String row="phase="+phase+" device="+usingDevice+" deviceState="+(device==null?"none":device.state())
            +" deviceReason="+(device==null?"none":device.reason())+" deviceCleanup="+(device==null?"none":device.cleanupDiagnostic())
            +" coordinator="+coordinator.status()+" heldEpoch="+epoch+" nativeEpoch="+McOwnedRotation.epoch(client)
            +" heldGen="+rotationGeneration+" nativeGen="+(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService r?r.requestGeneration():-1)
            +" nativeSerial="+McOwnedRotation.publicationSerial(client)+" legEpoch="+leg.cameraEpoch()+" legGen="+leg.requestGeneration()+" legTicket="+leg.actualTicket()
            +" outcome="+leg.retirementOutcome()+" gui="+(client.gui.screen()==null?"null":client.gui.screen().getClass().getName())+" tick="+(p==null?-1:p.tickCount)
            +" nativeCancels="+McOwnedRotation.cancellationDiagnostics(client);
            try{System.out.println("SHARED_RETIRE_PHASE "+row);}catch(Throwable reportUnavailable){}
        }catch(Throwable unavailable){}
    }
    private void stopOwnedIssuedReturn(SharedMovementCoordinator.Lease l,SharedMovementCoordinator.Issue i){
        if(l==null||l!=lease||publicationLeg==null)return;
        var retiring=publicationLeg;var originalGeneration=generation;var originalRequest=issuedReturnRequest;var originalBridge=suspendedReturnBridge;
        java.util.function.BooleanSupplier original=()->generation==originalGeneration&&lease==l&&publicationLeg==retiring&&issuedReturnRequest==originalRequest&&suspendedReturnBridge==originalBridge;
        var originalScope=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()
            ?dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.beginOriginalLegRetirement(retiring,this):null;
        if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()&&(originalScope==null||!originalScope.current()))return;
        try {
        traceRetirement("before-device-cancel",retiring);
        // The genuine original Leg owns all native releases. Subsequent child reset cannot issue native cleanup twice.
        traceRetirement("before-leg-retire",retiring);var outcome=retiring.retireOwned();
        if(!original.getAsBoolean())return;groundRetirement=outcome;traceRetirement("after-leg-retire",retiring);
        if(usingFlight&&flight!=null&&!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(flight,flight::stop)){groundResetFailed=true;return;}
        if(usingDevice&&device!=null&&!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(device,device::cancel)){groundResetFailed=true;return;}
        if(!original.getAsBoolean())return;
        if(!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(ground,ground::stop)){groundResetFailed=true;return;}
        if(!original.getAsBoolean())return;
        usingDevice=false;usingFlight=false;deviceAdapter=null;ownedDeviceAdmission=null;
        if(issuedStopSnapshot!=null&&issuedStopSnapshot.originalLeg==retiring&&issuedStopSnapshot.originalGeneration==originalGeneration)issuedStopSnapshot.portReturned=true;
        if(groundRetirement.cleanSuccess()&&retiring.retirementContinuationContext()!=null){
            epoch=retiring.cameraEpoch();rotationGeneration=retiring.requestGeneration();
        }
        // Root exception handling can stop only the exact proved original parent. Foreign work stays intact.
        if(!groundRetirement.cleanSuccess()&&retiring.postRetirementOwnParentContext()!=null)
            throw new IllegalStateException("Owned shared ground cleanup incomplete: "+groundRetirement.status());
        }finally{if(originalScope!=null)originalScope.close();}
    }
    /** Actual ARRIVED/no-loan cleanup receipt only; no raw slot equality can stand in for terminal ownership. */
    private boolean finishDeviceConsumed(){
        var admitted=ownedDeviceAdmission;var normal=admitted==null?null:admitted.normalReturnAdmission;
        if(normal==null||normal.loadedMaintenanceAuthorityIdentity()==null||!usingDevice||device==null
            ||device.state()!=McInstantTransmission.State.ARRIVED||normal.originalSlot()!=normal.deviceSlot())return true;
        var exact=device.terminalCleanup();
        return exact!=null&&exact==normal.terminalCleanupIdentity()&&exact.current()
            &&normal.loadedMaintenanceAuthorityIdentity().originalNoLoanTerminalReturned(exact);
    }
    @Override public void stopOwned(SharedMovementCoordinator.Lease l,SharedMovementCoordinator.Issue i){
        if(l!=null&&l==lease&&issuedReturnRequest!=null&&publicationLeg!=null&&usingDevice&&device!=null
                &&device.state()==McInstantTransmission.State.ARRIVED&&ownedDeviceAdmission!=null
                &&ownedDeviceAdmission.returnAdmission!=null){
            var retiring=publicationLeg;var admitted=ownedDeviceAdmission;var returned=admitted.returnAdmission;
            var transit=admitted.returnTransit;var terminal=device.terminalCleanup();var originalGeneration=generation;
            if(transit==null||terminal==null||!returned.captureTerminalBeforeRetirement(terminal))return;
            var ack=transit.ackTransitIdentity();
            var entry=ack==null?null:ack.captureFinalEntryContinuation(transit,terminal,retiring);
            if(entry==null){groundResetFailed=true;return;}
            var outcome=retiring.retireOwned();
            if(generation!=originalGeneration||lease!=l||publicationLeg!=retiring||ownedDeviceAdmission!=admitted)return;
            groundRetirement=outcome;
            if(!outcome.cleanSuccess()){groundResetFailed=true;return;}
            retiredReturnDeviceAdmission=returned;
            if(!entry.recordOriginalRetirement(outcome)){groundResetFailed=true;return;}
            issuedDeviceEntry=entry;
            // Device already completed its real camera/slot cleanup before its private terminal receipt.
            if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()){
                if(!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(device,device::cancel)
                    ||!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(ground,ground::stop)){groundResetFailed=true;return;}
            }else{device.cancel();ground.stop();}
            if(generation!=originalGeneration||lease!=l||publicationLeg!=retiring)return;
            usingDevice=false;deviceAdapter=null;ownedDeviceAdmission=null;
            epoch=retiring.cameraEpoch();rotationGeneration=retiring.requestGeneration();return;
        }
        if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()){stopOwnedIssuedReturn(l,i);return;}
        if(loadedSegmentSource!=null&&loadedIssuedAffiliation(loadedSegmentSource)){stopOwnedLoaded(l);return;}
        if(l==null||l!=lease||publicationLeg==null)return;
        var retiring=publicationLeg;traceRetirement("before-device-cancel",retiring);
        if(!finishDeviceConsumed()){groundResetFailed=true;return;}
        if(usingFlight&&flight!=null){try{flight.stop();}catch(RuntimeException helperCleanupRefused){groundResetFailed=true;}}
        if(usingDevice&&device!=null){try{device.cancel();}catch(RuntimeException helperCleanupRefused){groundResetFailed=true;}}
        // The actual Leg accounts for cleanup before a controller can reset private state.
        traceRetirement("before-leg-retire",retiring);groundRetirement=retiring.retireOwned();traceRetirement("after-leg-retire",retiring);usingDevice=false;usingFlight=false;deviceAdapter=null;ownedDeviceAdmission=null;
        try{ground.stop();}catch(RuntimeException helperResetFailure){groundResetFailed=true;}
        if(groundRetirement.cleanSuccess()&&retiring.retirementContinuationContext()!=null){
            epoch=retiring.cameraEpoch();rotationGeneration=retiring.requestGeneration();
        }
        // Root exception handling can stop only the exact proved original parent. Foreign work stays intact.
        if(!groundRetirement.cleanSuccess()&&retiring.postRetirementOwnParentContext()!=null)
            throw new IllegalStateException("Owned shared ground cleanup incomplete: "+groundRetirement.status());
    }
}
