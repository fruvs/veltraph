package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;

/** Shared per-hop travel dispatch for routes and automation controllers. */
public final class McTravelNavigator {
    public enum State { ACTIVE, ARRIVED, FAILED }
    private final McSharedMovementNavigator shared;private boolean sharedMode;
    private McNavigator walk;private McFlightNavigator fly;private McEtherwarpNavigator warp;
    private final Minecraft client;private final ConfigService config;
    private final FarmServices services;
    private final dev.veltraph.client.service.InputService input;
    private McChunkGoalNavigator chunkGoal;
    private boolean chunkMode,loadedReturnMode;
    private McLoadedGroundReturnNavigator loadedReturn;
    private boolean automaticMode;
    private McAutomaticTravelNavigator automatic;
    private LocalRoute.Kind kind=LocalRoute.Kind.WALK;
    private final LegacyTravelPublicationLease legacyAuthority;
    private LegacyTravelPublicationLease.Leg legacyLeg;
    private long legacyJourney;
    private boolean legacyMode,legacyActuatorReady;
    private LegacyTravelRetirement.RetirementOutcome lastLegacyRetirement;
    private boolean lastLegacyHelperCleanupFailed;
    private LegacyTravelPublicationLease.Leg lastRetiredLegacy;
    private boolean legacyHandoffPending;
    private boolean automaticRetirementRefused;
    public McTravelNavigator(Minecraft client,FarmServices services,ConfigService config) {
        this.client=client;this.config=config;this.services=services;input=services.input();
        legacyAuthority=new LegacyTravelPublicationLease(client,services,config,()->config.revision(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));
        shared=new McSharedMovementNavigator(client,services,config);walk=new McNavigator(client,services.input(),config);fly=new McFlightNavigator(client,services.input(),config);warp=new McEtherwarpNavigator(client,services,config);
    }
    private dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest issuedReturnRequest;
    /** Actual final shared child affiliation; a copied request cannot affiliate another wrapper. */
    public boolean issuedReturnSharedChild(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,McSharedMovementNavigator child){
        return request!=null&&issuedReturnRequest==request&&request.travelIdentity()==this&&child==shared;
    }
    /** Final child identity only; this does not establish a publication lease or Root authority. */
    public boolean issuedReturnInitialChild(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,Object child,boolean sharedWalk){
        return request!=null&&request.travelIdentity()==this&&child==(sharedWalk?shared:this);
    }
    public boolean issuedReturnPublicationLease(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,LegacyTravelPublicationLease actual,Object child){
        if(request==null||issuedReturnRequest!=request||request.travelIdentity()!=this)return false;
        return child==this&&actual==legacyAuthority||child==shared&&shared.issuedReturnPublicationLease(request,actual);
    }
    public boolean issuedReturnChild(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,Object child){
        return request!=null&&issuedReturnRequest==request&&request.travelIdentity()==this
            &&(child==shared||child==walk||child==fly||child==warp||child==loadedReturn||child==chunkGoal);
    }
    /** Only the original Journey action can select this explicit issued-return child dispatcher. */
    public boolean startIssuedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            LocalRoute.Node node,double terminalRadius,SharedMovementPolicy.Purpose purpose){
        if(request==null||request.travelIdentity()!=this||!request.startDispatchCurrent(this)||node==null
            ||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||!node.position().equals(request.targetPosition())||!request.sourceKindCurrent(node.kind())
            ||Float.floatToIntBits(node.yaw())!=Float.floatToIntBits(request.canonicalTargetPoint().yaw())||Float.floatToIntBits(node.pitch())!=Float.floatToIntBits(request.canonicalTargetPoint().pitch()))return false;
        if(issuedReturnRequest!=null&&issuedReturnRequest!=request)return false;
        if(!request.claimInitialChildAdmission(this,node.kind()==LocalRoute.Kind.WALK?shared:this))return false;
        if(!request.retireBeforeInitialChildStart(this))return false;
        if(!request.current()||!request.startDispatchCurrent(this)||!previousRetirementClean())return false;
        if(!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35||node.kind()!=LocalRoute.Kind.WALK&&terminalRadius!=.35)return false;
        issuedReturnRequest=request;kind=node.kind();
        return switch(kind){
            case WALK->{sharedMode=true;yield shared.startIssuedReturn(request,node.position(),2500,32,terminalRadius,purpose);}
            case FLY->{if(!beginIssuedLegacy(request))yield false;legacyActuatorReady=true;yield startedLegacy(fly.start(node.position(),legacyLeg,purpose));}
            case ETHERWARP->{if(!beginIssuedLegacy(request))yield false;legacyActuatorReady=true;yield startedLegacy(warp.start(node.target(),node.face(),node.position(),legacyLeg));}
        };
    }
    private boolean beginIssuedLegacy(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request){
        if(!previousRetirementClean()||!request.startDispatchCurrent(this))return false;
        legacyLeg=legacyAuthority.beginIssuedReturn(request,this,++legacyJourney,request.driverIdentity(),SharedMovementPolicy.Purpose.EXACT_RETURN,request.targetPosition());
        legacyMode=legacyLeg!=null;legacyActuatorReady=false;
        if(legacyMode){lastLegacyRetirement=null;lastLegacyHelperCleanupFailed=false;lastRetiredLegacy=null;legacyHandoffPending=true;}
        return legacyMode;
    }
    public boolean startLoadedIssuedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,LocalRoute.Node node,double radius){
        if(request==null||request.travelIdentity()!=this||!request.startDispatchCurrent(this)||node==null||node.kind()!=LocalRoute.Kind.WALK
            ||!node.position().equals(request.targetPosition())||!request.sourceKindCurrent(node.kind())||!Double.isFinite(radius)||radius<=0||radius>.35)return false;
        if(issuedReturnRequest!=null&&issuedReturnRequest!=request)return false;
        if(!request.claimInitialChildAdmission(this,this))return false;
        if(!request.retireBeforeInitialChildStart(this))return false;if(!request.current()||!request.startDispatchCurrent(this))return false;issuedReturnRequest=request;
        if(!beginIssuedLegacy(request))return false;
        if(!bindOriginalLoadedDispatchAfterPredecessorRetirement())return startedLegacy(false);
        loadedReturn=new McLoadedGroundReturnNavigator(client,services,config,groundAdapter(),loadedDispatch);
        if(loadedDispatch!=null)loadedDispatch.wrapper=loadedReturn;kind=LocalRoute.Kind.WALK;loadedReturnMode=true;legacyActuatorReady=true;
        return startedLegacy(loadedReturn.start(node.position(),radius));
    }
    private dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall loadedReturnCall;
    private LoadedReturnDispatch loadedDispatch;
    private boolean stoppingLoadedReturn;
    private dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall pendingLoadedReturnCall;
    /** Private native dispatcher for one unchanged canonical loaded-return controller. */
    public final class LoadedReturnDispatch {
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall call;
        private McLoadedGroundReturnNavigator wrapper;
        private McLoadedGroundReturnNavigator.SelectedLoadedSegment activeSegment,retiredSegment;
        private LegacyTravelPublicationLease.Leg predecessor;
        private LegacyTravelRetirement.RetirementOutcome retirement;
        private boolean selecting,stopping,failed;
        private LoadedReturnDispatch(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall call,LegacyTravelPublicationLease.Leg predecessor){this.call=call;this.predecessor=predecessor;}
        public McTravelNavigator travelIdentity(){return McTravelNavigator.this;}
        public McSharedMovementNavigator sharedChildIdentity(){return shared;}
        public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall returnCallIdentity(){return call;}
        public LegacyTravelPublicationLease.Leg originalLegIdentity(){return predecessor;}
        public LegacyTravelRetirement.RetirementOutcome actualRetirementIdentity(){return retirement;}
        public boolean nativeRealmCurrent(){return !failed&&loadedDispatch==this&&loadedReturnCall==call&&loadedReturnMode&&loadedReturn==wrapper
            &&call.travelIdentity()==McTravelNavigator.this&&call.configIdentity()==config&&call.servicesIdentity()==services&&call.nativeSceneCurrent();}
        public boolean cleanupRealmCurrent(){return loadedDispatch==this&&loadedReturnCall==call&&loadedReturn==wrapper
            &&call.travelIdentity()==McTravelNavigator.this&&call.configIdentity()==config&&call.servicesIdentity()==services&&call.cleanupSceneCurrent()&&call.originalDispatchScopeCurrent(McTravelNavigator.this);}
        public boolean selectingCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){return selecting&&activeSegment==exact
            &&nativeRealmCurrent()&&call.selectingSourceCurrent(McTravelNavigator.this);}
        public boolean selectedSourceCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){return activeSegment==exact&&nativeRealmCurrent();}
        public boolean cleanupSourceCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){return activeSegment==exact&&stopping&&cleanupRealmCurrent();}
        public boolean retiredLineageCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){return retiredSegment==exact&&cleanupRealmCurrent()
            &&predecessor!=null&&retirement!=null&&predecessor.retirementOutcome()==retirement&&retirement.cleanSuccess();}
        public boolean startSelected(McLoadedGroundReturnNavigator actual,McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            if(actual!=wrapper||source==null||selecting||stopping||!nativeRealmCurrent()||!call.selectingSourceCurrent(McTravelNavigator.this)
                ||!source.selectingOriginalStart())return false;
            activeSegment=source;selecting=true;
            try{
                if(predecessor==null)return false;
                if(predecessor.retirementOutcome()==null){
                    if(legacyLeg!=predecessor||!predecessor.current())return false;
                    var outcome=predecessor.retireOwned();
                    if(predecessor.retirementOutcome()!=outcome||!outcome.cleanSuccess())return false;
                    retirement=outcome;lastLegacyRetirement=outcome;lastRetiredLegacy=predecessor;
                    legacyLeg=null;legacyMode=legacyActuatorReady=false; // Keep actual loaded controller/canonical clock alive.
                }else{retirement=predecessor.retirementOutcome();if(!retirement.cleanSuccess())return false;}
                if(!source.nativeRealmCurrent()||!shared.startOriginalLoadedSegment(source))return false;
                return shared.ownsLoadedSegmentSource(source)&&source.nativeRealmCurrent();
            }finally{selecting=false;}
        }
        public LoadedGroundReturnController.Progress tickSelected(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){
            if(!selectedSourceCurrent(exact)||!call.nativeDispatchCurrent(McTravelNavigator.this)||!shared.ownsLoadedSegmentSource(exact))return LoadedGroundReturnController.Progress.FAILED;
            var result=shared.tick();
            return !selectedSourceCurrent(exact)?LoadedGroundReturnController.Progress.FAILED:switch(result){
                case ACTIVE->LoadedGroundReturnController.Progress.ACTIVE;case ARRIVED->LoadedGroundReturnController.Progress.ARRIVED;default->LoadedGroundReturnController.Progress.FAILED;};
        }
        private void recordScopedSharedRetirement(LegacyTravelPublicationLease.Leg real){
            var exact=activeSegment;var outcome=real==null?null:real.retirementOutcome();
            if(exact==null||real==null||outcome==null||!outcome.cleanSuccess()||!cleanupRealmCurrent())
                throw new IllegalStateException("No real scoped loaded Shared cleanup receipt");
            stopping=true;
            try{exact.recordActualRetirement(real,outcome);predecessor=real;retirement=outcome;retiredSegment=exact;
                lastRetiredLegacy=real;lastLegacyRetirement=outcome;}finally{stopping=false;}
        }
        public void stopSelected(McLoadedGroundReturnNavigator.SelectedLoadedSegment exact){
            if(exact==null||activeSegment!=exact)return;
            if(stopping){failed=true;throw new IllegalStateException("Original loaded segment cleanup reentered");}
            stopping=true;boolean cleanupReturned=false;
            try{
                if(retiredSegment==exact&&predecessor!=null&&retirement!=null&&predecessor.retirementOutcome()==retirement
                    &&retirement.cleanSuccess()&&!shared.hasLeg()){cleanupReturned=true;return;} // Actual scoped cleanup already returned; controller only retires its metadata.
                var real=shared.actualLoadedLegIdentity(exact);boolean hadShared=shared.hasLeg();
                if(hadShared){
                    // A stale selected source never retires an ordinary or newer Shared journey.
                    if(real==null||!shared.ownsLoadedSegmentSource(exact)
                        ||shared.actualLoadedLegIdentity(exact)!=real||activeSegment!=exact||!cleanupRealmCurrent()){
                        failed=true;throw new IllegalStateException("Original loaded Shared cleanup source superseded");
                    }
                    if(!shared.ownsLoadedSegmentSource(exact)||shared.actualLoadedLegIdentity(exact)!=real
                        ||activeSegment!=exact||!cleanupRealmCurrent()){
                        failed=true;throw new IllegalStateException("Original loaded Shared cleanup identity changed");
                    }
                    if(!shared.stopOriginalLoadedSegment(exact,real)){
                        failed=true;throw new IllegalStateException("Original loaded Shared scoped cleanup refused");
                    }
                    if(shared.hasLeg()||shared.loadedSegmentSourceIdentity()!=null||activeSegment!=exact||!cleanupRealmCurrent()){
                        failed=true;throw new IllegalStateException("Original loaded Shared cleanup did not retire exact scope");
                    }
                }
                var outcome=real==null?null:real.retirementOutcome();
                if(real==null&&!hadShared&&predecessor!=null&&predecessor.retirementOutcome()!=null
                    &&predecessor.retirementOutcome().cleanSuccess()){cleanupReturned=true;return;} // No Shared native leg was ever admitted; original max-two candidate retry remains bounded.
                if(real==null||outcome==null||!outcome.cleanSuccess()||shared.retirementRefused()){
                    failed=true;throw new IllegalStateException("Actual loaded Shared segment cleanup refused");
                }
                exact.recordActualRetirement(real,outcome);
                predecessor=real;retirement=outcome;retiredSegment=exact;lastRetiredLegacy=real;lastLegacyRetirement=outcome;
                cleanupReturned=true;
            }catch(RuntimeException|Error failure){failed=true;throw failure;}
            finally{if(cleanupReturned)activeSegment=null;stopping=false;}
        }
    }
    /** Actual private Journey selects this typed source; a public node or geometry flag cannot manufacture it. */
    public boolean startOriginalLoadedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall call,LocalRoute.Node node,double radius){
        if(call==null||call.travelIdentity()!=this||call.configIdentity()!=config||call.servicesIdentity()!=services||node==null
            ||!node.position().equals(call.canonicalTarget())||loadedReturnCall!=null)return false;
        return call.withOriginalTravelDispatch(this,"START",()->{
            if(pendingLoadedReturnCall!=null)throw new IllegalStateException("Loaded original start reentered");
            pendingLoadedReturnCall=call;
            try{return call.issuedRequestIdentity()==null?startLoadedReturn(node,radius):startLoadedIssuedReturn(call.issuedRequestIdentity(),node,radius);}
            finally{pendingLoadedReturnCall=null;}
        });
    }
    /** Loaded source starts from the original canonical-purpose lease, including actual maintenance Binding capture. */
    private boolean beginOriginalLoadedLegacy(){
        var source=pendingLoadedReturnCall;if(source==null)return beginLegacy();
        if(!previousRetirementClean()||!source.nativeDispatchCurrent(this))return false;
        var registry=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        var parent=registry==null?null:registry.inputOwner().orElse(null);if(parent==null)return false;
        legacyLeg=legacyAuthority.beginExpected(++legacyJourney,parent,SharedMovementPolicy.Purpose.EXACT_RETURN,source.canonicalTarget());
        legacyMode=legacyLeg!=null;legacyActuatorReady=false;
        if(legacyMode){lastLegacyRetirement=null;lastLegacyHelperCleanupFailed=false;lastRetiredLegacy=null;legacyHandoffPending=true;}
        return legacyMode;
    }
    private boolean bindOriginalLoadedDispatchAfterPredecessorRetirement(){
        var actual=pendingLoadedReturnCall;if(actual==null)return true;
        if(loadedReturnCall!=null||loadedDispatch!=null||legacyLeg==null||!legacyLeg.current()
            ||!actual.nativeDispatchCurrent(this)||actual.issuedRequestIdentity()!=issuedReturnRequest)return false;
        loadedReturnCall=actual;loadedDispatch=new LoadedReturnDispatch(actual,legacyLeg);return true;
    }
    public State tickOriginalLoadedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall exact){
        if(exact==null||loadedReturnCall!=exact||loadedDispatch==null||!loadedReturnMode)return State.FAILED;
        return exact.withOriginalTravelDispatch(this,"TICK",()->exact.issuedRequestIdentity()==null?tick():tickIssuedReturn(exact.issuedRequestIdentity()));
    }

    public State tickIssuedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request){
        if(request==null||issuedReturnRequest!=request||request.travelIdentity()!=this||!request.tickDispatchCurrent(this)||!request.current())return State.FAILED;
        var state=tick();return request.tickDispatchCurrent(this)&&request.current()?state:State.FAILED;
    }
    /** Bound dispatcher continuation; actual old leg identities remain unchanged. */
    public PassiveLifecycleContinuation capturePassiveLifecycleContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual){
        if(actual==null||!actual.sourceBeforeCurrent(client))return null;
        var leg=legacyLeg;var closed=lastRetiredLegacy;
        var child=leg==null?null:leg.capturePassiveLifecycleContinuation(actual);
        if(leg!=null&&child==null)return null;
        var retired=closed==null||closed==leg||sharedMode||leg!=null?null:closed.capturePassiveLifecycleContinuation(actual);
        if(closed!=null&&closed!=leg&&!sharedMode&&leg==null&&retired==null)return null;
        var sharedChild=shared.capturePassiveLifecycleContinuation(actual);if(sharedChild==null)return null;
        var result=new PassiveLifecycleContinuation(actual,leg,closed,child,retired,sharedChild);
        return result.rawCurrent()&&actual.sourceBeforeCurrent(client)?result:null;
    }
    public final class PassiveLifecycleContinuation {
        private final dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual;
        private final LegacyTravelPublicationLease.Leg leg,closed;
        private final LegacyTravelPublicationLease.Leg.PassiveLifecycleContinuation child,retired;
        private final McSharedMovementNavigator.PassiveLifecycleContinuation sharedChild;
        private final boolean sharedActive=sharedMode,legacyActive=legacyMode,automaticActive=automaticMode,loadedActive=loadedReturnMode,chunkActive=chunkMode,ready=legacyActuatorReady,handoff=legacyHandoffPending;
        private final long journey=legacyJourney;private final LocalRoute.Kind originalKind=kind;
        private final McNavigator originalWalk=walk;private final McFlightNavigator originalFly=fly;private final McEtherwarpNavigator originalWarp=warp;
        private final McLoadedGroundReturnNavigator originalLoaded=loadedReturn;private final McAutomaticTravelNavigator originalAutomatic=automatic;private final McChunkGoalNavigator originalChunk=chunkGoal;
        private final LegacyTravelRetirement.RetirementOutcome originalRetirement=lastLegacyRetirement;
        private final boolean originalHelperFailure=lastLegacyHelperCleanupFailed,originalAutomaticFailure=automaticRetirementRefused;
        private boolean prepared,committed;
        private PassiveLifecycleContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual,LegacyTravelPublicationLease.Leg leg,LegacyTravelPublicationLease.Leg closed,
                LegacyTravelPublicationLease.Leg.PassiveLifecycleContinuation child,LegacyTravelPublicationLease.Leg.PassiveLifecycleContinuation retired,McSharedMovementNavigator.PassiveLifecycleContinuation sharedChild){
            this.actual=actual;this.leg=leg;this.closed=closed;this.child=child;this.retired=retired;this.sharedChild=sharedChild;}
        public boolean rawCurrent(){return !committed&&client.isSameThread()&&legacyLeg==leg&&lastRetiredLegacy==closed&&legacyJourney==journey&&kind==originalKind
            &&sharedMode==sharedActive&&legacyMode==legacyActive&&automaticMode==automaticActive&&loadedReturnMode==loadedActive&&chunkMode==chunkActive
            &&legacyActuatorReady==ready&&legacyHandoffPending==handoff&&walk==originalWalk&&fly==originalFly&&warp==originalWarp
            &&loadedReturn==originalLoaded&&automatic==originalAutomatic&&chunkGoal==originalChunk&&lastLegacyRetirement==originalRetirement
            &&lastLegacyHelperCleanupFailed==originalHelperFailure&&automaticRetirementRefused==originalAutomaticFailure
            &&(child==null||child.rawCurrent())&&(retired==null||retired.rawCurrent())&&sharedChild.rawCurrent();}
        public boolean prepare(){if(prepared||!rawCurrent()||!actual.normalReturnedCurrent(client)
            ||child!=null&&!child.prepare()||retired!=null&&!retired.prepare()||!sharedChild.prepare())return false;prepared=true;return rawCurrent();}
        public boolean commit(){if(!prepared||!actual.passiveDeclarationCommitWindowCurrent()||!rawCurrent())return false;
            if(child!=null&&!child.commit()||retired!=null&&!retired.commit()||!sharedChild.commit())return false;committed=true;return true;}
    }
    /** Logical child transaction, including failed/arrived cleanup until stop; grants no input permission. */
    public boolean hasSharedMovementLeg(){return shared.hasLeg();}
    public boolean hasOwnedLegacyLeg(){return legacyLeg!=null;}
    public boolean currentOwnedLegacyLeg(){return legacyLeg!=null&&legacyLeg.current();}
    /** Actual cleanup accounting; a retired logical reference is not a native release receipt. */
    public LegacyTravelRetirement.RetirementOutcome legacyRetirementOutcome(){return lastLegacyRetirement;}
    public boolean legacyHelperCleanupFailed(){return lastLegacyHelperCleanupFailed;}
    /** Source-prescribed automatic hop keeps its original ground budget and owns a concrete leg. */
    public boolean startLegacyHop(LocalRoute.Node node){
        if(node==null){stop();return false;}
        return node.kind()==LocalRoute.Kind.WALK?startWalking(node.position(),2500):start(node);
    }
    /** Automatic handoff binds its exact closed predecessor before this child can publish. */
    public boolean startLegacyHop(LocalRoute.Node node,LegacyTravelPublicationLease.Leg exactPredecessor){
        stop();if(node==null||exactPredecessor==null||!previousRetirementClean())return false;
        kind=node.kind();legacyLeg=legacyAuthority.beginAfter(++legacyJourney,exactPredecessor);
        legacyMode=legacyLeg!=null;legacyActuatorReady=false;if(!legacyMode)return false;
        lastLegacyRetirement=null;lastLegacyHelperCleanupFailed=false;lastRetiredLegacy=null;legacyHandoffPending=true;
        if(kind==LocalRoute.Kind.WALK){
            lastWalkingSearchBudget=2500;var actor=groundAdapter();walk=new McNavigator(client,actor,config,actor);
            legacyActuatorReady=true;return startedLegacy(walk.start(node.position(),2500,32,.35));
        }
        legacyActuatorReady=true;
        return startedLegacy(kind==LocalRoute.Kind.FLY
            ?fly.start(node.position(),java.util.concurrent.ThreadLocalRandom.current().nextLong(),legacyLeg)
            :warp.start(node.target(),node.face(),node.position(),legacyLeg));
    }
    /** Exact closed predecessor context, read-only and still invalidated by any newer admission. */
    public LegacyTravelPublicationIdentity.State retiredContinuationContext(){
        return lastRetiredLegacy==null?null:lastRetiredLegacy.retirementContinuationContext();
    }
    /** Opaque actual closed leg for beginAfter; no permission is granted by exposing this handle. */
    public LegacyTravelPublicationLease.Leg retiredContinuationLeg(){return retiredContinuationContext()==null?null:lastRetiredLegacy;}
    public LegacyTravelPublicationIdentity.State postLegacyRetirementOwnParentContext(){return lastRetiredLegacy==null?null:lastRetiredLegacy.postRetirementOwnParentContext();}
    /** One-shot handoff needs both actual completed cleanup and fresh closed exact predecessor. */
    public boolean retireForHandoff(){
        if(sharedMode||automaticMode||!legacyHandoffPending)return false;
        if(legacyLeg!=null)stop();
        boolean clean=lastLegacyRetirement!=null&&lastLegacyRetirement.cleanSuccess()
            &&!lastLegacyHelperCleanupFailed&&retiredContinuationContext()!=null;
        legacyHandoffPending=false;return clean;
    }
    private boolean previousRetirementClean(){return !automaticRetirementRefused&&!lastLegacyHelperCleanupFailed&&(lastLegacyRetirement==null||lastLegacyRetirement.cleanSuccess());}
    private boolean beginLegacy(){if(!previousRetirementClean())return false;legacyLeg=legacyAuthority.begin(++legacyJourney);legacyMode=legacyLeg!=null;legacyActuatorReady=false;if(legacyMode){lastLegacyRetirement=null;lastLegacyHelperCleanupFailed=false;lastRetiredLegacy=null;legacyHandoffPending=true;}return legacyMode;}
    private LeaseGroundAdapter groundAdapter(){return new LeaseGroundAdapter(legacyLeg);}
    private boolean startedLegacy(boolean started){if(!started)stop();return started;}
    public boolean hasEtherwarpTool() { return warp.findTool()>=0; }
    public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now) { return sharedMode?shared.consumeExpectedTeleport(position,now):automaticMode?automatic.consumeExpectedTeleport(position,now):warp.consumeExpectedTeleport(position,now); }
    public boolean etherwarpUseDispatched() { return warp.useDispatched(); }
    public dev.veltraph.client.movement.device.DeviceUseReceipt.Applied etherwarpAppliedReceipt(){return legacyLeg!=null&&legacyLeg.current()?warp.appliedReceipt():null;}
    public dev.veltraph.client.movement.device.DeviceUseReceipt.Issue etherwarpIssue(){return legacyLeg!=null&&legacyLeg.current()?warp.issue():null;}
    public boolean hasAutomaticLineage(){return automaticMode&&automatic!=null&&automatic.hasLineage();}
    /** Opt-in policy; it never changes the meaning of explicit route kinds or approach settings. */
    public boolean startAutomatic(AutomaticTravelPolicy.Request request,TravelAtlas atlas,McAutomaticTravelAccess access) {
        stop();if(!previousRetirementClean())return false;if(automatic==null)automatic=new McAutomaticTravelNavigator(client,services,config);
        automaticMode=true;return automatic.start(request,atlas,access);
    }
    public void onAutomaticServerMessage(String message) {if(automaticMode)automatic.onServerMessage(message);}
    public void onAutomaticActionBar(String message) {if(automaticMode)automatic.onActionBar(message);}
    public AutomaticTravelPolicy.Plan automaticPlan() {return automaticMode?automatic.selectedPlan():null;}
    /** Recovered walk/flight dispatch for building and tree approaches; no implicit etherwarp use. */
    public boolean startApproach(dev.veltraph.client.world.BlockPos target) {return startApproach(target,SharedMovementPolicy.Purpose.GENERAL_TRAVEL);}
    /** Reserved tool/block approaches can declare ground-only walking without changing flight settings. */
    public boolean startApproach(dev.veltraph.client.world.BlockPos target,SharedMovementPolicy.Purpose purpose) {
        if(target==null||purpose==null) { stop();return false; }
        var mode=config.get(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE);
        var point=walk.position(target);
        if(mode!=ApproachMode.FLY && start(new LocalRoute.Node(point,0,0),.35,purpose)) return true;
        if(mode==ApproachMode.WALK_ONLY) return false;
        if(mode==ApproachMode.AUTO && (client.player==null||!client.player.getAbilities().mayfly)) return false;
        return start(new LocalRoute.Node(LocalRoute.Kind.FLY,point,0,0),.35,purpose);
    }
    public boolean start(LocalRoute.Node node) {
        return start(node,.35);
    }
    /** Passive exact private Shared return-child affiliation; it cannot create a Leg or permission. */
    public McSharedMovementNavigator.NativeNormalReturnAffiliation captureNativeNormalReturnAffiliation(LegacyTravelPublicationLease.Leg exact,Object expectedParent,dev.veltraph.client.world.Vec3 expectedGoal){
        return sharedMode?shared.captureNativeNormalReturnAffiliation(exact,expectedParent,config,expectedGoal):null;
    }
    /** Passive proof of the immediately completed original normal journey; never movement/slot permission. */
    public boolean normalReturnRetirementClean(){return shared.normalReturnRetirementClean();}
    /** Strict return poses currently require supported walking; other travel kinds keep default proof. */
    public boolean start(LocalRoute.Node node,double terminalRadius) {
        return start(node,terminalRadius,terminalRadius<.35?SharedMovementPolicy.Purpose.EXACT_RETURN:SharedMovementPolicy.Purpose.GENERAL_TRAVEL);
    }
    /** Explicit source/crop/return policy is retained even when its terminal tolerance is the default. */
    public boolean start(LocalRoute.Node node,double terminalRadius,SharedMovementPolicy.Purpose purpose) {
        stop();
        if(!previousRetirementClean())return false;
        if(!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35
                ||node!=null&&node.kind()!=LocalRoute.Kind.WALK&&terminalRadius!=.35)return false;if(node==null||purpose==null)return false;kind=node.kind();
        return switch(kind) {
            case WALK -> {sharedMode=true;yield shared.start(node.position(),terminalRadius,purpose);}
            case FLY -> {if(!beginLegacy())yield false;legacyActuatorReady=true;yield startedLegacy(fly.start(node.position(),legacyLeg,purpose));}
            case ETHERWARP -> {if(!beginLegacy())yield false;legacyActuatorReady=true;yield startedLegacy(warp.start(node.target(),node.face(),node.position(),legacyLeg));}
        };
    }
    /** Explicit saved-return ground caller: loaded horizons, unchanged exact final tolerance. */
    public boolean startLoadedReturn(LocalRoute.Node node,double terminalRadius){
        stop();if(node==null||node.kind()!=LocalRoute.Kind.WALK||!Double.isFinite(terminalRadius)||terminalRadius<=0||terminalRadius>.35)return false;
        if(!beginOriginalLoadedLegacy())return false;
        if(!bindOriginalLoadedDispatchAfterPredecessorRetirement())return startedLegacy(false);
        loadedReturn=new McLoadedGroundReturnNavigator(client,services,config,groundAdapter(),loadedDispatch);
        if(loadedDispatch!=null)loadedDispatch.wrapper=loadedReturn;
        kind=LocalRoute.Kind.WALK;loadedReturnMode=true;legacyActuatorReady=true;return startedLegacy(loadedReturn.start(node.position(),terminalRadius));
    }
    public boolean loadedReturnGeometry(){return loadedReturnMode&&loadedReturn!=null&&loadedReturn.geometryLoaded();}
    public boolean loadedReturnMode(){return loadedReturnMode;}
    private int lastWalkingSearchBudget;
    /** Additive bounded walk request for source callers; existing starts retain2500nodes/radius32. */
    public boolean startWalking(dev.veltraph.client.world.Vec3 target,int maxNodes) {
        if(target==null)return false;
        int checked=checkedWalkingBudget(maxNodes);
        stop();
        lastWalkingSearchBudget=checked;
        kind=LocalRoute.Kind.WALK;
        if(!beginLegacy())return false;var actor=groundAdapter();walk=new McNavigator(client,actor,config,actor);
        legacyActuatorReady=true;return startedLegacy(walk.start(target,lastWalkingSearchBudget,32,.35));
    }
    public static int checkedWalkingBudget(int maxNodes) {
        if(maxNodes<1||maxNodes>2500)throw new IllegalArgumentException("Walking search budget must be1..2500");
        return maxNodes;
    }
    /** Actual value passed to the planner by startWalking; readonly diagnostic for private Root proof. */
    public int lastWalkingSearchBudget(){return lastWalkingSearchBudget;}
    public boolean startChunkGoal(int chunkX,int chunkZ,java.util.List<dev.veltraph.client.world.Vec3> standingPositions) {
        stop();if(!beginLegacy())return false;var actor=groundAdapter();chunkGoal=new McChunkGoalNavigator(client,actor,config,legacyLeg);
        legacyActuatorReady=true;chunkMode=chunkGoal.start(chunkX,chunkZ,standingPositions);return startedLegacy(chunkMode);
    }
    public State tick() {
        if(loadedDispatch!=null&&loadedDispatch.failed)return State.FAILED;
        if(sharedMode)return shared.tick();
        if(legacyMode&&(legacyLeg==null||!legacyLeg.current())){stop();return State.FAILED;}
        if(loadedReturnMode)return loadedReturn.tick();
        if(automaticMode)return automatic.tick();
        if(chunkMode)return chunkGoal.tick();
        return switch(kind) {
            case WALK -> switch(walk.tick()) { case ARRIVED -> State.ARRIVED;case WALKING -> State.ACTIVE;default -> State.FAILED; };
            case FLY -> switch(fly.tick()) { case ARRIVED -> State.ARRIVED;case FLYING -> State.ACTIVE;default -> State.FAILED; };
            case ETHERWARP -> switch(warp.tick()) { case ARRIVED -> State.ARRIVED;case AIMING,WAITING_TELEPORT -> State.ACTIVE;default -> State.FAILED; };
        };
    }
    public String status() { if(loadedReturnCleanupRefused())return "Loaded return FAILED (original cleanup custody retained)";if(automaticRetirementRefused)return "Automatic movement cleanup unconfirmed";if(lastLegacyHelperCleanupFailed||lastLegacyRetirement!=null&&!lastLegacyRetirement.cleanSuccess())return "Movement cleanup "+(lastLegacyRetirement==null?"unconfirmed":lastLegacyRetirement.status())+(lastLegacyHelperCleanupFailed?" (child cleanup refused)":"");return sharedMode?shared.status():loadedReturnMode?loadedReturn.status():automaticMode?automatic.status():chunkMode?chunkGoal.status():switch(kind) { case WALK -> walk.status();case FLY -> fly.status();case ETHERWARP -> warp.status(); }; }
    public String sharedFirstFailure(){return shared.firstFailure();}
    /** Diagnostic only; selected enum never admits movement. */
    public SharedMovementPolicy.Mode sharedSelectedMode(){return sharedMode?shared.selectedMode():null;}
    /** Cached operands only: no fresh permission or native state query. */
    public String sharedRetirementDiagnostic(){return "refused="+shared.retirementRefused()+" outcome="+shared.groundRetirementOutcome();}
    public String diagnostics() { return sharedMode?shared.status():loadedReturnMode?loadedReturn.diagnostics():kind==LocalRoute.Kind.WALK&&!automaticMode&&!chunkMode?walk.diagnostics():status(); }
    /**
     * Abandon unqualified legacy state without touching native keys/camera/slots/abilities.
     * A retained shared transaction must be retired through its exact-session, fenced stop instead.
     */
    public void retireLogical(){
        if(shared.hasLeg()||legacyLeg!=null||loadedDispatch!=null)throw new IllegalStateException("Retire owned movement through fenced stop");
        walk=new McNavigator(client,services.input(),config);fly=new McFlightNavigator(client,services.input(),config);
        warp=new McEtherwarpNavigator(client,services,config);
        chunkMode=loadedReturnMode=automaticMode=sharedMode=false;
        chunkGoal=null;loadedReturn=null;automatic=null;kind=LocalRoute.Kind.WALK;
    }
    public final class IssuedReturnStopSnapshot {
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source;
        private final Object originalRequest=issuedReturnRequest;
        private final LegacyTravelPublicationLease.Leg originalLeg=legacyLeg;
        private final Object originalWalk=walk,originalFly=fly,originalWarp=warp,originalLoaded=loadedReturn,originalChunk=chunkGoal,originalAutomatic=automatic;
        private final boolean wasShared=sharedMode,wasAutomatic=automaticMode,wasLoaded=loadedReturnMode,wasChunk=chunkMode;
        private final long originalJourney=legacyJourney;
        private final LocalRoute.Kind originalKind=kind;
        private final McSharedMovementNavigator.IssuedReturnStopSnapshot sharedSnapshot;
        private final long originalLegTicket,originalLegEpoch;
        private boolean stopping,retired;
        private McNavigator retiredWalk;private McFlightNavigator retiredFly;private McEtherwarpNavigator retiredWarp;
        private IssuedReturnStopSnapshot(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){
            this.source=source;sharedSnapshot=wasShared||wasLoaded&&loadedDispatch!=null&&shared.hasLeg()?shared.captureIssuedReturnStop(source):null;
            var actual=originalLeg!=null?originalLeg:sharedSnapshot==null?null:sharedSnapshot.originalLeg();
            originalLegTicket=actual==null?0:actual.actualTicket();originalLegEpoch=actual==null?-1:actual.cameraEpoch();
        }
        public boolean ownsOriginalActuator(Object actual){return actual!=null&&(actual==originalWalk||actual==originalFly||actual==originalWarp||actual==originalLoaded||actual==originalChunk||sharedSnapshot!=null&&sharedSnapshot.ownsOriginalActuator(actual));}
        public boolean ownsOriginalWrapper(Object actual){return actual==McTravelNavigator.this||sharedSnapshot!=null&&actual==shared;}
        public boolean ownsOriginalCamera(dev.veltraph.client.service.mc.McOwnedRotation.IssuedReturnCameraWindow camera){
            var actual=originalLeg!=null?originalLeg:sharedSnapshot==null?null:sharedSnapshot.originalLeg();
            return actual!=null&&camera.hadOriginalPositive()&&originalLegTicket==camera.originalPositiveTicket()&&originalLegEpoch==camera.originalEpoch();
        }
        public boolean ownsOriginalLeg(LegacyTravelPublicationLease.Leg actual){return actual!=null&&(actual==originalLeg||sharedSnapshot!=null&&actual==sharedSnapshot.originalLeg());}
        public boolean current(){
            if(retired)return legacyJourney==originalJourney&&issuedReturnRequest==null&&walk==retiredWalk&&fly==retiredFly&&warp==retiredWarp&&!sharedMode&&!automaticMode&&!loadedReturnMode&&!chunkMode
                &&legacyLeg==null&&!legacyMode&&!legacyActuatorReady&&loadedReturn==null&&chunkGoal==null&&automatic==null
                &&(sharedSnapshot==null||sharedSnapshot.current());
            return issuedReturnRequest==originalRequest&&legacyJourney==originalJourney&&kind==originalKind&&sharedMode==wasShared&&automaticMode==wasAutomatic
                &&loadedReturnMode==wasLoaded&&chunkMode==wasChunk&&legacyLeg==originalLeg&&walk==originalWalk&&fly==originalFly&&warp==originalWarp
                &&loadedReturn==originalLoaded&&chunkGoal==originalChunk&&automatic==originalAutomatic&&(sharedSnapshot==null||sharedSnapshot.current());
        }
        public boolean stopOwned(java.util.function.BooleanSupplier finalSource,java.util.function.BooleanSupplier nativeSource){
            if(retired)return current();if(stopping||!current())return false;stopping=true;
            try {
                if(!finalSource.getAsBoolean()||!current())return false;
                if(wasShared||wasLoaded&&sharedSnapshot!=null){
                    if(sharedSnapshot==null||!sharedSnapshot.stopOwned(finalSource))return false;
                    if(wasLoaded&&loadedDispatch!=null){
                        if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.logicalOnly()){
                            loadedDispatch.failed=true;loadedDispatch.activeSegment=null; // Metadata disposal cannot certify a real native retirement.
                        }else loadedDispatch.recordScopedSharedRetirement(sharedSnapshot.originalLeg());
                        if(!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(loadedReturn,loadedReturn::stop))return false;
                    }
                    sharedMode=false;
                }
                else if(wasAutomatic)return false; // No source automatic controller belongs to this exact saved-anchor request.
                else if(originalLeg!=null){
                    try(var original=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.beginOriginalLegRetirement(originalLeg,McTravelNavigator.this)){
                    if(original==null||!original.current())return false;
                    if(!current()||!finalSource.getAsBoolean()||!current())return false;
                    var outcome=originalLeg.retireOwned();
                    if(!current()||!finalSource.getAsBoolean()||!current())return false;
                    if(legacyActuatorReady){
                        var actor=wasLoaded&&loadedReturn!=null?loadedReturn:wasChunk&&chunkGoal!=null?chunkGoal:
                            switch(originalKind){case WALK->walk;case FLY->fly;case ETHERWARP->warp;};
                        if(!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.resetOriginalActuator(actor,()->{
                            if(wasLoaded&&loadedReturn!=null)loadedReturn.stop();else if(wasChunk&&chunkGoal!=null)chunkGoal.stop();
                            else switch(originalKind){case WALK->walk.stop();case FLY->fly.stop();case ETHERWARP->warp.stop();}
                        }))return false;
                    }
                    lastLegacyRetirement=outcome;lastRetiredLegacy=originalLeg;
                    }
                }
                else if(shared.hasLeg())return false;
                // Only the captured old wrapper's references are retired. No provider follows these writes.
                legacyLeg=null;legacyMode=legacyActuatorReady=false;loadedReturnMode=chunkMode=automaticMode=false;loadedReturn=null;chunkGoal=null;automatic=null;
                loadedDispatch=null;loadedReturnCall=null;
                retiredWalk=new McNavigator(client,services.input(),config);retiredFly=new McFlightNavigator(client,services.input(),config);retiredWarp=new McEtherwarpNavigator(client,services,config);
                walk=retiredWalk;fly=retiredFly;warp=retiredWarp;issuedReturnRequest=null;
                retired=true;return current();
            } finally {stopping=false;}
        }
    }
    public IssuedReturnStopSnapshot captureIssuedReturnStop(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnCleanup source){
        if(source==null||source.travelIdentity()!=this||source.requestIdentity()==null
            ||issuedReturnRequest!=null&&source.requestIdentity()!=issuedReturnRequest)return null;
        if(issuedReturnRequest==null&&(legacyLeg!=null||shared.hasLeg()||automaticMode||sharedMode))return null;
        var original=new IssuedReturnStopSnapshot(source);return original.current()?original:null;
    }
    /** Negative refusal only; retains the original loaded cleanup source without granting any native action. */
    public boolean loadedReturnCleanupRefused(){return loadedDispatch!=null&&loadedDispatch.failed;}
    public void stop() {
        if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.stopTravel(this))return;
        if(loadedReturnMode&&loadedDispatch!=null){
            var original=loadedDispatch;var originalCall=loadedReturnCall;var actual=loadedReturn;
            if(stoppingLoadedReturn){original.failed=true;throw new IllegalStateException("Original loaded wrapper cleanup reentered");}
            stoppingLoadedReturn=true;
            try{
                if(actual==null)throw new IllegalStateException("Original loaded wrapper cleanup unavailable");
                actual.stop();
                if(original.failed||loadedDispatch!=original||loadedReturnCall!=originalCall||loadedReturn!=actual
                    ||shared.hasLeg()||shared.loadedSegmentSourceIdentity()!=null)
                    throw new IllegalStateException("Original loaded wrapper cleanup custody unretired");
                loadedDispatch=null;loadedReturnCall=null;loadedReturn=null;loadedReturnMode=false;legacyMode=legacyActuatorReady=false;legacyLeg=null;
            }catch(RuntimeException|Error failure){original.failed=true;throw failure;}
            finally{stoppingLoadedReturn=false;}
            return;
        }
        if(sharedMode){shared.stop();sharedMode=false;return;}
        if(automaticMode){
            var retiring=automatic;
            try{if(retiring!=null)retiring.stop();}
            finally{automaticRetirementRefused|=retiring!=null&&retiring.retirementRefused();automaticMode=false;automatic=null;}
            if(automaticRetirementRefused&&retiring!=null&&retiring.postRetirementOwnParentContext()!=null)
                throw new IllegalStateException("Owned automatic movement cleanup incomplete");
            return;
        }
        if(legacyLeg!=null){
            var retiring=legacyLeg;
            try{
                if(!legacyActuatorReady){}
                else if(loadedReturnMode&&loadedReturn!=null)loadedReturn.stop();
                else if(chunkMode&&chunkGoal!=null)chunkGoal.stop();
                else switch(kind){case WALK->walk.stop();case FLY->fly.stop();case ETHERWARP->warp.stop();}
            }catch(RuntimeException childCleanupRefused){
                // The exact leg still attempts its own cleanup and exposes the refusal to the parent.
                lastLegacyHelperCleanupFailed=true;
            }finally{
                try{lastLegacyRetirement=retiring.retireOwned();lastRetiredLegacy=retiring;}
                finally{
                    // Clear only this wrapper's retired references, even if native cleanup refuses.
                    legacyLeg=null;legacyMode=legacyActuatorReady=false;
                    loadedReturnMode=chunkMode=false;loadedReturn=null;chunkGoal=null;
                    walk=new McNavigator(client,services.input(),config);
                    fly=new McFlightNavigator(client,services.input(),config);
                    warp=new McEtherwarpNavigator(client,services,config);
                }
            }
            // Existing ModuleManager exception handling stops only an exactly proved current parent.
            // Superseded/foreign cleanup remains a diagnostic and never disables its newer owner.
            if(lastLegacyRetirement!=null&&!lastLegacyRetirement.cleanSuccess()
                    &&retiring.postRetirementOwnParentContext()!=null)
                throw new IllegalStateException("Owned movement cleanup incomplete: "+lastLegacyRetirement.status());
            return;
        }
        // Unbound legacy primitives have no permission to undo a newer native controller.
        retireLogical();
    }
}
