package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.config.TravelConfig;
import dev.veltraph.client.config.RotationConfig;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.garden.pests.mc.GardenActionModule;
import dev.veltraph.client.garden.pests.mc.PestInventoryLease;
import dev.veltraph.client.movement.device.DeviceUseReceipt;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.navigation.LegacyTravelPublicationIdentity;
import dev.veltraph.client.navigation.LegacyPublicationSnapshotIdentity;
import dev.veltraph.client.navigation.LegacyKeyOnlyPublicationIdentity;
import dev.veltraph.client.navigation.LegacyCleanupTransactionIdentity;
import dev.veltraph.client.navigation.NeutralMovementPublication;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.navigation.LegacyRetiredParentInteractionIdentity;
import dev.veltraph.client.rotation.DirectRotationPublicationIdentity;
import dev.veltraph.client.navigation.TravelHeading;
import dev.veltraph.client.navigation.DynamicRouteConfigStamp;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.service.mc.McInventoryService;
import net.minecraft.client.KeyMapping;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.service.mc.McRotationService;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;

/** Exact legacy child-leg custody; neither queued requests nor captured epochs grant permission. */
public final class LegacyTravelPublicationLease {
    private static final IdentityHashMap<Minecraft,Leg> ACTIVE=new IdentityHashMap<>();
    private static final IdentityHashMap<Minecraft,Long> ADMISSIONS=new IdentityHashMap<>();
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final LongSupplier revision;
    private static final java.util.List<ConfigKey<?>> KEYS=java.util.List.of(TravelConfig.TRAVEL_APPROACH_MODE,TravelConfig.FLIGHT_MAX_SPEED,
        TravelConfig.FLIGHT_STALL_MS,TravelConfig.FLIGHT_SEARCH_RADIUS,TravelConfig.FLIGHT_NODE_BUDGET,
        TravelConfig.ETHERWARP_TIMEOUT_MS,TravelConfig.ETHERWARP_STABLE_TICKS,TravelConfig.ETHERWARP_ABILITY_TOKEN,RotationConfig.ROTATION_SPEED_MULTIPLIER);
    private record Configuration(java.util.List<Long> epochs,java.util.List<Object> values){}
    private record ConfigurationView(dev.veltraph.client.config.OrderedConfigurationData.Data data,Configuration configuration){}
    private ConfigurationView reusableConfigurationView;
    private final dev.veltraph.client.config.OrderedConfigurationData configurationData=new dev.veltraph.client.config.OrderedConfigurationData(KEYS);
    private Configuration configuration(){
        if(config instanceof dev.veltraph.client.config.MapConfigService||config instanceof dev.veltraph.client.config.JsonConfigService){
            var observed=configurationData.capture(config);if(observed==null)return null;
            var next=configurationView(observed,reusableConfigurationView);
            reusableConfigurationView=next;return next.configuration();
        }
        var epochs=new java.util.ArrayList<Long>();var values=new java.util.ArrayList<Object>();
        for(var key:KEYS){var epoch=config.revision(key);if(epoch.isEmpty()||epoch.getAsLong()<0)return null;epochs.add(epoch.getAsLong());values.add(config.get(key));}
        return new Configuration(java.util.List.copyOf(epochs),java.util.List.copyOf(values));
    }
    /** Data-only wrapper selection after the complete original fresh capture; no permission/query is cached. */
    private static ConfigurationView configurationView(dev.veltraph.client.config.OrderedConfigurationData.Data observed,ConfigurationView prior){
        if(prior!=null&&prior.data()==observed)return prior;
        return new ConfigurationView(observed,new Configuration(observed.epochs(),observed.values()));
    }
    public LegacyTravelPublicationLease(Minecraft client,FarmServices services,ConfigService config,LongSupplier revision){
        this.client=Objects.requireNonNull(client);this.services=Objects.requireNonNull(services);this.config=Objects.requireNonNull(config);this.revision=Objects.requireNonNull(revision);
    }
    /** Refusal-only final validator for actual native sender; adapter Objects/Booleans cannot authenticate a capsule. */
    public static boolean ownedUseSnapshotCurrent(Minecraft client,Object capsule,Object expectedParent,Object expectedCamera,Object expectedStore,
            long expectedEpoch,long expectedGeneration,long expectedJourney,Vec3 expectedDeviceGoal){
        if(capsule instanceof LegacyTravelPublicationLease.Leg.NormalReturnOwnedUseSnapshot normal){
            var leg=normal.owner;
            return client!=null&&leg.clientIdentity()==client&&leg.parent==expectedParent&&leg.cameraAuthority()==expectedCamera
                &&leg.configIdentity()==expectedStore&&leg.journey==expectedJourney&&leg.identity!=null
                &&leg.identity.epoch()==expectedEpoch&&leg.identity.generation()==expectedGeneration
                &&normal.issue.goal()==expectedDeviceGoal&&normal.issue.context().parent()==expectedParent
                &&normal.issue.context().policy()==expectedStore&&normal.issue.context().player()==client.player
                &&normal.issue.context().world()==client.level&&normal.issue.context().connection()==client.getConnection()
                &&normal.issue.context().cameraEpoch()==expectedEpoch&&normal.issue.context().journey()==expectedJourney
                &&leg.ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&normal.ordinary.goal==leg.ownedUseGoal
                &&normal.admission.qualifiedApproachCurrent(normal.ordinary.origin,expectedDeviceGoal)
                &&leg.ownedNormalReturnUseSnapshotCurrent(normal);
        }
        if(capsule instanceof LegacyTravelPublicationLease.Leg.OwnedIssuedUseSnapshot issued){
            var leg=issued.owner;
            return client!=null&&leg.clientIdentity()==client&&leg.parent==expectedParent&&leg.cameraAuthority()==expectedCamera
                &&leg.configIdentity()==expectedStore&&leg.journey==expectedJourney&&leg.identity!=null
                &&leg.identity.epoch()==expectedEpoch&&leg.identity.generation()==expectedGeneration
                &&issued.issue.goal()==expectedDeviceGoal&&issued.issue.context().parent()==expectedParent
                &&issued.issue.context().cameraEpoch()==expectedEpoch&&issued.issue.context().journey()==expectedJourney
                &&issued.goal==leg.ownedUseGoal&&issued.request.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN
                &&issued.admission.qualifiedApproachCurrent(issued.origin,expectedDeviceGoal)
                &&leg.ownedIssuedUseSnapshotCurrent(issued);
        }
        if(!(capsule instanceof LegacyTravelPublicationLease.Leg.OwnedUseSnapshot actual))return false;
        var leg=actual.owner;var saved=leg.captured;
        return client!=null&&leg.clientIdentity()==client&&saved!=null&&leg.parent==expectedParent&&saved.cameraAuthority()==expectedCamera
            &&leg.configIdentity()==expectedStore&&saved.config()==expectedStore&&leg.journey==expectedJourney
            &&leg.identity!=null&&leg.identity.epoch()==expectedEpoch&&leg.identity.generation()==expectedGeneration
            &&actual.purpose==SharedMovementPolicy.Purpose.GENERAL_TRAVEL&&actual.goal==leg.ownedUseGoal
            &&dev.veltraph.client.navigation.OwnedUseIntentDirection.forward(actual.origin,actual.goal,expectedDeviceGoal)
            &&leg.ownedUseSnapshotCurrent(capsule);
    }
    private static final Object NON_COLLECTION_CAMERA=new Object();
    private Leg failedIssuedEntry;
    /** Exact failed new admission remains cleanup custody; no successful entry is reported. */
    public Leg consumeFailedIssuedReturnEntry(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.FinalEntryContinuation exact,
            dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,McSharedMovementNavigator shared){
        var failed=failedIssuedEntry;
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=LegacyTravelPublicationLease.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==McSharedMovementNavigator.class&&first.getMethodName().equals("admitMovementLeg");
        });
        if(!caller||failed==null||!client.isSameThread()||failed.entrySource!=exact||failed.issuedReturnRequest!=request
            ||failed.entryShared!=shared||ACTIVE.get(client)!=failed||ADMISSIONS.getOrDefault(client,0L)!=failed.admittedVersion)return null;
        failedIssuedEntry=null;return failed;
    }
    private static final class CollectionCameraSnapshot {
        final Minecraft client;final Object owner,source;final Leg leg;final ModuleManager root;
        final ModuleManager.LifecycleSnapshot lifecycle;final Object player,world,connection,store;
        final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        final LegacyPublicationSnapshotIdentity.State reservation;
        final long ticket,epoch;
        private CollectionCameraSnapshot(Minecraft client,Object owner,Leg leg,Object source,long ticket,long epoch){this.client=client;this.owner=owner;this.leg=leg;this.source=source;this.ticket=ticket;this.epoch=epoch;
            root=VeltraphBootstrap.moduleManager();lifecycle=root==null?null:root.lifecycleSnapshot();player=client.player;world=client.level;connection=client.getConnection();store=VeltraphBootstrap.config();
            input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();reservation=leg==null?null:leg.nativeReservation();}
    }
    /** Read-only additional veto captured from original private camera control BEFORE authority/delta/observer callbacks. */
    public static Object collectionCameraSnapshot(Minecraft client,Object actualCameraOwner,long actualTicket,long actualEpoch){
        if(!(actualCameraOwner instanceof dev.veltraph.client.mining.vein.VeinMiningModule))return NON_COLLECTION_CAMERA;
        if(client==null||!client.isSameThread()||actualTicket<=0||actualEpoch<0)return null;
        var owned=ACTIVE.get(client);Leg leg=owned!=null&&owned.parent==actualCameraOwner&&owned.identity!=null
            &&owned.positivePublishedSerial==actualTicket&&owned.identity.ticket()==actualTicket&&owned.identity.serial()==actualTicket&&owned.identity.epoch()==actualEpoch?owned:null;
        Object source=dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(client,actualCameraOwner,leg);if(source==null)return null;
        var actual=new CollectionCameraSnapshot(client,actualCameraOwner,leg,source,actualTicket,actualEpoch);
        return collectionCameraSnapshotCurrent(client,actualCameraOwner,actualTicket,actualEpoch,actual,System.nanoTime()/1_000_000)?actual:null;
    }
    /** Private source/Root/Leg/native fields only, no camera/role/config/clock/provider/geometry callback. */
    public static boolean collectionCameraSnapshotCurrent(Minecraft client,Object actualCameraOwner,long actualTicket,long actualEpoch,Object exact,long actualNow){
        if(exact==NON_COLLECTION_CAMERA)return !(actualCameraOwner instanceof dev.veltraph.client.mining.vein.VeinMiningModule);
        if(!(exact instanceof CollectionCameraSnapshot s)||s.client!=client||s.owner!=actualCameraOwner||s.ticket!=actualTicket||s.epoch!=actualEpoch||client==null||!client.isSameThread())return false;
        if(!dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,actualCameraOwner,s.leg,s.source,actualNow))return false;
        if(s.root==null||s.lifecycle==null||!s.lifecycle.known()||VeltraphBootstrap.moduleManager()!=s.root||!s.root.lifecycleCurrent(s.lifecycle)
            ||client.player!=s.player||client.level!=s.world||client.getConnection()!=s.connection||VeltraphBootstrap.config()!=s.store||s.input==null||!s.input.current()
            ||McOwnedRotation.publicationSerial(client)!=actualTicket||McOwnedRotation.epoch(client)!=actualEpoch)return false;
        return s.leg==null||s.leg.identity!=null&&s.leg.positivePublishedSerial==actualTicket&&s.leg.identity.ticket()==actualTicket
            &&LegacyPublicationSnapshotIdentity.same(s.reservation,s.leg.nativeReservation())&&s.leg.admittedVersion==ADMISSIONS.getOrDefault(client,0L)&&ACTIVE.get(client)==s.leg;
    }
    /** Root's registered current input owner is captured, never supplied by a caller's Boolean. */
    public Leg begin(long journeyGeneration){
        if(!client.isSameThread()||journeyGeneration<1||!(services.rotation() instanceof McRotationService rotation))return null;
        // The wrapper must retire the previous leg through its own exact cleanup first.
        if(ACTIVE.get(client)!=null)return null;long observedVersion=ADMISSIONS.getOrDefault(client,0L);
        var root=VeltraphBootstrap.moduleManager();Module parent=root==null?null:root.inputOwner().orElse(null);
        if(parent==null)return null;
        var leg=new Leg(parent,rotation,journeyGeneration);var before=leg.sample();var after=leg.sample();
        leg.identity=LegacyTravelPublicationIdentity.Lease.admit(before,after,leg,observedVersion,ADMISSIONS.getOrDefault(client,0L));
        if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=observedVersion)return null;
        leg.captured=before;long nextVersion=Math.addExact(observedVersion,1);
        leg.admittedVersion=nextVersion;ACTIVE.put(client,leg);ADMISSIONS.put(client,nextVersion);return leg;
    }
    /** Source callers can require their exact registered parent before any ACTIVE publication. */
    public Leg beginExpected(long journeyGeneration,Module expectedParent){
        if(!client.isSameThread()||journeyGeneration<1||expectedParent==null||!(services.rotation() instanceof McRotationService rotation)
                ||ACTIVE.get(client)!=null)return null;
        long observedVersion=ADMISSIONS.getOrDefault(client,0L);
        try {var root=VeltraphBootstrap.moduleManager();
            var lifecycle=root==null?null:root.lifecycleSnapshot();if(lifecycle==null||!lifecycle.known())return null;
            if(root==null||root.get(expectedParent.id()).orElse(null)!=expectedParent||root.inputOwner().orElse(null)!=expectedParent
                    ||McOwnedRotation.authority(client)!=expectedParent)return null;
            var leg=new Leg(expectedParent,rotation,journeyGeneration);var before=leg.sample();var after=leg.sample();
            leg.identity=LegacyTravelPublicationIdentity.Lease.admitExpected(before,after,leg,expectedParent,observedVersion,ADMISSIONS.getOrDefault(client,0L));
            if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=observedVersion||!root.lifecycleCurrent(lifecycle))return null;
            leg.captured=before;long nextVersion=Math.addExact(observedVersion,1);
            leg.admittedVersion=nextVersion;ACTIVE.put(client,leg);ADMISSIONS.put(client,nextVersion);return leg;
        }catch(RuntimeException unavailable){return null;}
    }
    /** Actual predecessor handoff; no caller State/Boolean can stand in for this private closed-leg lineage. */
    public Leg beginAfter(long journeyGeneration,Leg predecessor){
        if(!client.isSameThread()||journeyGeneration<1||predecessor==null||predecessor.clientIdentity()!=client
                ||!(services.rotation() instanceof McRotationService rotation)||predecessor.rotation!=rotation||predecessor.configIdentity()!=config||predecessor.issuedReturn!=null)return null;
        try {var predecessorBefore=predecessor.retirementContinuationContext();
        long observedVersion=ADMISSIONS.getOrDefault(client,0L),retiredVersion=predecessor.retiredAdmissionVersion;
        if(predecessorBefore==null||ACTIVE.get(client)!=null||retiredVersion<0||retiredVersion==Long.MAX_VALUE||observedVersion!=retiredVersion)return null;
        var root=VeltraphBootstrap.moduleManager();Module parent=root==null?null:root.inputOwner().orElse(null);
        if(parent==null)return null;
        var leg=new Leg(parent,rotation,journeyGeneration);var before=leg.sample();var after=leg.sample();
        var predecessorAfter=predecessor.retirementContinuationContext();long versionAfter=ADMISSIONS.getOrDefault(client,0L);
        leg.identity=LegacyTravelPublicationIdentity.Lease.admitAfter(before,after,leg,predecessorBefore,predecessorAfter,retiredVersion,observedVersion,versionAfter);
        if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=retiredVersion)return null;
        leg.originalPredecessor=predecessor;
        leg.captured=before;long nextVersion=Math.addExact(retiredVersion,1);
        leg.admittedVersion=nextVersion;ACTIVE.put(client,leg);ADMISSIONS.put(client,nextVersion);return leg;
        }catch(RuntimeException unavailable){return null;}
    }
    /** Purpose and original goal are captured by the actual Root provider before any leg publication. */
    public Leg begin(long journeyGeneration,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        if(!client.isSameThread())return null;var root=VeltraphBootstrap.moduleManager();
        Module parent=root==null?null:root.inputOwner().orElse(null);
        return beginExpected(journeyGeneration,parent,purpose,goal);
    }
    public Leg beginExpected(long journeyGeneration,Module expectedParent,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        return beginIntent(journeyGeneration,expectedParent,purpose,goal,null);
    }
    public Leg beginAfter(long journeyGeneration,SharedMovementPolicy.Purpose purpose,Vec3 goal,Leg predecessor){
        if(predecessor==null||predecessor.issuedReturn!=null)return null;return beginIntent(journeyGeneration,predecessor.parent,purpose,goal,predecessor);
    }
    private Leg beginIntent(long journey,Module expectedParent,SharedMovementPolicy.Purpose purpose,Vec3 goal,Leg predecessor){
        if(!client.isSameThread()||journey<1||expectedParent==null||purpose==null||!SharedMovementPolicy.finite(goal)
                ||ACTIVE.get(client)!=null||!(services.rotation() instanceof McRotationService rotation))return null;
        try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(lifecycle==null||!lifecycle.known()||root.get(expectedParent.id()).orElse(null)!=expectedParent||root.inputOwner().orElse(null)!=expectedParent)return null;
            long observedVersion=ADMISSIONS.getOrDefault(client,0L);
            var old=predecessor==null?null:predecessor.retirementContinuationContext();
            if(predecessor!=null&&(predecessor.clientIdentity()!=client||predecessor.configIdentity()!=config||predecessor.rotation!=rotation
                ||old==null||predecessor.retiredAdmissionVersion!=observedVersion))return null;
            Object camera=McOwnedRotation.authority(client);McFarmMaintenanceAuthorityBridge.Binding binding=null;
            if(camera!=expectedParent){var stamp=DynamicRouteConfigStamp.capture(config);if(stamp.isEmpty())return null;
                binding=McFarmMaintenanceAuthorityBridge.capture(client,expectedParent,config,stamp.get(),purpose,goal).orElse(null);
                if(binding==null||binding.inputOwner()!=expectedParent||binding.cameraOwner()!=camera)return null;
            }
            if(predecessor!=null&&(predecessor.maintenance==null)!=(binding==null))return null;
            if(predecessor!=null&&binding!=null&&(predecessor.maintenance.frame().pair().capture()!=binding.frame().pair().capture()
                    ||predecessor.maintenance.cameraOwner()!=binding.cameraOwner()))return null;
            var leg=new Leg(expectedParent,rotation,journey,binding,purpose,goal);var before=leg.sample();var after=leg.sample();
            var oldAfter=predecessor==null?null:predecessor.retirementContinuationContext();long versionAfter=ADMISSIONS.getOrDefault(client,0L);
            leg.identity=binding==null?(predecessor==null?LegacyTravelPublicationIdentity.Lease.admitExpected(before,after,leg,expectedParent,observedVersion,versionAfter)
                :LegacyTravelPublicationIdentity.Lease.admitAfter(before,after,leg,old,oldAfter,predecessor.retiredAdmissionVersion,observedVersion,versionAfter))
                :(predecessor==null?LegacyTravelPublicationIdentity.Lease.admitMaintenance(before,after,leg,binding.frame(),observedVersion,versionAfter)
                :LegacyTravelPublicationIdentity.Lease.admitMaintenanceAfter(before,after,leg,binding.frame(),old,oldAfter,predecessor.retiredAdmissionVersion,observedVersion,versionAfter));
            if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=observedVersion||observedVersion==Long.MAX_VALUE
                ||!leg.roleCurrent(false)||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(lifecycle))return null;
            if(predecessor!=null)leg.originalPredecessor=predecessor;
            leg.captured=before;leg.admittedVersion=observedVersion+1;ACTIVE.put(client,leg);ADMISSIONS.put(client,observedVersion+1);return leg;
        }catch(RuntimeException unavailable){return null;}
    }
    /** One genuine normal loaded source and actual clean predecessor, retaining its canonical maintenance Binding. */
    public Leg beginAfterLoadedSegment(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,McSharedMovementNavigator shared,
            long journey,Module expectedParent,SharedMovementPolicy.Purpose purpose,Vec3 selectedGoal){
        if(!actualLoadedAdmissionCaller("admitOriginalLoadedMovementLeg")||source==null||shared==null
            ||source.issuedRequestIdentity()!=null||source.sharedChildIdentity()!=shared||!source.initialSharedStartCurrent(shared)
            ||source.initialAdmittedLegIdentity()!=null||source.parentIdentity()!=expectedParent)return null;
        var originalAdmission=shared.loadedInitialAdmissionIdentity(source);if(originalAdmission==null)return null;
        return beginLoadedNormal(source,shared,journey,expectedParent,purpose,selectedGoal,source.originalLegIdentity(),true,originalAdmission);
    }
    /** Internal same-segment continuation still consumes the real old Leg; canonical pose is never a new selected goal. */
    public Leg beginAfterLoadedContinuation(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,McSharedMovementNavigator shared,
            long journey,SharedMovementPolicy.Purpose purpose,Vec3 selectedGoal,Leg predecessor){
        if(!actualLoadedAdmissionCaller("admitMovementLeg")||source==null||shared==null||predecessor==null
            ||source.issuedRequestIdentity()!=null||source.sharedChildIdentity()!=shared||!shared.ownsLoadedSegmentSource(source)
            ||source.initialAdmittedLegIdentity()==null||predecessor.loadedSource!=source||!source.nativeSourceCurrent())return null;
        var originalAdmission=shared.loadedContinuationAdmissionIdentity(source,predecessor);if(originalAdmission==null)return null;
        return beginLoadedNormal(source,shared,journey,predecessor.parent,purpose,selectedGoal,predecessor,false,originalAdmission);
    }
    private boolean actualLoadedAdmissionCaller(String method){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
        var first=frames.filter(f->f.getDeclaringClass()!=LegacyTravelPublicationLease.class).findFirst().orElse(null);
        return first!=null&&first.getDeclaringClass()==McSharedMovementNavigator.class&&first.getMethodName().equals(method);});}
    private Leg beginLoadedNormal(McLoadedGroundReturnNavigator.SelectedLoadedSegment source,McSharedMovementNavigator shared,
            long journey,Module expectedParent,SharedMovementPolicy.Purpose purpose,Vec3 selectedGoal,Leg predecessor,boolean initial,Object originalAdmission){
        if(!client.isSameThread()||source.configIdentity()!=config||source.returnCallIdentity().servicesIdentity()!=services
            ||expectedParent==null||!(expectedParent instanceof FarmMaintenanceCoordinator.Participant)
            ||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||selectedGoal==null||!selectedGoal.equals(source.segmentPosition())
            ||journey<1||ACTIVE.get(client)!=null||!(services.rotation() instanceof McRotationService rotation)
            ||predecessor==null||predecessor.clientIdentity()!=client||predecessor.configIdentity()!=config||predecessor.rotation!=rotation
            ||predecessor.issuedReturn!=null||predecessor.issuedReturnRequest!=null||!source.nativeRealmCurrent()
            ||!source.originalSelectionHistoryCurrent()||originalAdmission==null
            ||!(initial?shared.loadedInitialAdmissionFieldsCurrent(source,originalAdmission)
                :shared.loadedContinuationAdmissionFieldsCurrent(source,predecessor,originalAdmission)))return null;
        var outcome=predecessor.retirementOutcome();var binding=predecessor.maintenance;
        if(outcome==null||!outcome.cleanSuccess()||outcome.removal()!=LegacyTravelRetirement.Removal.REMOVED_OWN
            ||binding==null||binding.inputOwner()!=expectedParent||binding.cameraOwner()!=source.cameraOwnerIdentity()
            ||binding.frame().pair().capturedPose()!=source.canonicalTargetPoint()
            ||!McFarmMaintenanceAuthorityBridge.current(binding,client,config))return null;
        if(initial&&source.originalLegIdentity()!=predecessor||!initial&&(predecessor.loadedSource!=source
            ||source.initialAdmittedLegIdentity()!=predecessor.loadedFirstLeg))return null;
        try{var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
            if(life==null||!life.known()||root.get(expectedParent.id()).orElse(null)!=expectedParent
                ||root.inputOwner().orElse(null)!=expectedParent)return null;
            long version=ADMISSIONS.getOrDefault(client,0L);var old=predecessor.retirementContinuationContext();
            if(old==null||predecessor.retiredAdmissionVersion!=version||version<0||version==Long.MAX_VALUE)return null;
            var leg=new Leg(expectedParent,rotation,journey,binding,purpose,selectedGoal);
            leg.loadedSource=source;leg.loadedCanonicalPredecessor=source.originalLegIdentity();
            leg.loadedFirstLeg=initial?leg:predecessor.loadedFirstLeg;
            leg.loadedSettings=config instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot()
                :config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
            leg.loadedInput=ScriptingInputLock.controlSnapshot();
            if(leg.loadedSettings==null||leg.loadedInput==null||!leg.loadedInput.active()
                ||!binding.frame().helperId().equals(leg.loadedInput.ownerModuleId()))return null;
            leg.loadedOptions=client.options;leg.loadedGameMode=client.gameMode;leg.loadedInventory=client.player.getInventory();leg.loadedMenu=client.player.inventoryMenu;
            var observed=leg.sample();var after=leg.sample();var oldAfter=predecessor.retirementContinuationContext();
            leg.identity=LegacyTravelPublicationIdentity.Lease.admitMaintenanceAfter(observed,after,leg,binding.frame(),old,oldAfter,
                predecessor.retiredAdmissionVersion,version,ADMISSIONS.getOrDefault(client,0L));
            leg.captured=observed; // Private original data only; ACTIVE/native permission remains unpublished.
            if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=version
                ||!leg.roleCurrent(false)||!source.nativeRealmCurrent()||!source.originalSelectionHistoryCurrent()
                ||initial&&!source.initialSharedStartCurrent(shared)||!initial&&!source.nativeSourceCurrent()
                ||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(life)||!leg.nativeCurrent(after)||!leg.loadedRawNativeFieldsCurrent()
                ||!(initial?shared.loadedInitialAdmissionFieldsCurrent(source,originalAdmission)
                    :shared.loadedContinuationAdmissionFieldsCurrent(source,predecessor,originalAdmission)))return null;
            leg.originalPredecessor=predecessor;leg.captured=observed;leg.admittedVersion=version+1;
            ACTIVE.put(client,leg);ADMISSIONS.put(client,version+1);return leg;
        }catch(RuntimeException unavailable){return null;}
    }
    /** Exact selected issued source: canonical Request custody is retained, selected goal is only this child movement. */
    public Leg beginAfterIssuedLoadedSegment(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment cap,
            McLoadedGroundReturnNavigator.SelectedLoadedSegment source,McSharedMovementNavigator shared,long journey,
            Module driver,SharedMovementPolicy.Purpose purpose,Vec3 selectedGoal){
        if(!actualLoadedAdmissionCaller("admitOriginalLoadedMovementLeg")||cap==null||source==null||shared==null
            ||source.initialAdmittedLegIdentity()!=null||!source.initialSharedStartCurrent(shared))return null;
        var marker=shared.loadedInitialAdmissionIdentity(source);
        return marker==null?null:beginIssuedLoadedSelected(cap,source,shared,journey,driver,purpose,selectedGoal,
            source.originalLegIdentity(),true,marker);
    }
    /** Same selected segment successor consumes the exact actual clean old Leg and retains its original suspended Binding. */
    public Leg beginAfterIssuedLoadedContinuation(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment cap,
            McLoadedGroundReturnNavigator.SelectedLoadedSegment source,McSharedMovementNavigator shared,long journey,
            SharedMovementPolicy.Purpose purpose,Vec3 selectedGoal,Leg predecessor){
        if(!actualLoadedAdmissionCaller("admitMovementLeg")||cap==null||source==null||shared==null||predecessor==null
            ||predecessor.loadedIssuedSegment!=cap||predecessor.loadedSource!=source||!shared.ownsLoadedSegmentSource(source)
            ||source.initialAdmittedLegIdentity()!=predecessor.loadedFirstLeg||!source.nativeSourceCurrent())return null;
        var marker=shared.loadedContinuationAdmissionIdentity(source,predecessor);
        return marker==null?null:beginIssuedLoadedSelected(cap,source,shared,journey,predecessor.parent,purpose,selectedGoal,
            predecessor,false,marker);
    }
    private Leg beginIssuedLoadedSelected(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment cap,
            McLoadedGroundReturnNavigator.SelectedLoadedSegment source,McSharedMovementNavigator shared,long journey,
            Module driver,SharedMovementPolicy.Purpose purpose,Vec3 selectedGoal,Leg predecessor,boolean initial,Object marker){
        var request=cap.requestIdentity();
        if(!client.isSameThread()||request==null||source.issuedRequestIdentity()!=request||cap.sourceIdentity()!=source
            ||cap.sharedChildIdentity()!=shared||driver==null||request.driverIdentity()!=driver||source.parentIdentity()!=driver
            ||source.configIdentity()!=config||source.returnCallIdentity().servicesIdentity()!=services
            ||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||request.purpose()!=purpose||journey<1
            ||selectedGoal==null||!selectedGoal.equals(cap.segmentPosition())||!selectedGoal.equals(source.segmentPosition())
            ||cap.canonicalTargetPoint()!=request.canonicalTargetPoint()||!cap.canonicalTarget().equals(request.targetPosition())
            ||!request.ownsPublicationLease(this,shared)||!cap.nativeCurrent()||!source.originalSelectionHistoryCurrent()
            ||ACTIVE.get(client)!=null||!(services.rotation() instanceof McRotationService rotation)
            ||predecessor==null||predecessor.clientIdentity()!=client||predecessor.rotation!=rotation||predecessor.configIdentity()!=config
            ||predecessor.issuedReturnRequest!=request||predecessor.issuedReturn==null||predecessor.parent!=driver
            ||initial&&(cap.predecessorIdentity()!=predecessor||source.originalLegIdentity()!=predecessor
                ||cap.predecessorRetirementIdentity()!=predecessor.retirementOutcome())
            ||!initial&&(predecessor.loadedIssuedSegment!=cap||predecessor.loadedSource!=source
                ||predecessor.loadedFirstLeg!=source.initialAdmittedLegIdentity())
            ||!(initial?shared.loadedInitialAdmissionFieldsCurrent(source,marker)
                :shared.loadedContinuationAdmissionFieldsCurrent(source,predecessor,marker)))return null;
        var outcome=predecessor.retirementOutcome();
        if(outcome==null||!outcome.cleanSuccess()||outcome.removal()!=LegacyTravelRetirement.Removal.REMOVED_OWN)return null;
        try{var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
            if(life==null||!life.known()||root.get(driver.id()).orElse(null)!=driver||root.inputOwner().orElse(null)!=driver
                ||McOwnedRotation.authority(client)!=source.cameraOwnerIdentity())return null;
            long version=ADMISSIONS.getOrDefault(client,0L);var old=predecessor.retirementContinuationContext();
            if(old==null||predecessor.retiredAdmissionVersion!=version||version<0||version==Long.MAX_VALUE)return null;
            var stamp=DynamicRouteConfigStamp.capture(config).orElse(null);if(stamp==null)return null;
            var binding=initial?McSuspendedFarmTravelBridge.captureLoadedChild(client,request,cap,shared,config,services,stamp,purpose,selectedGoal)
                :McSuspendedFarmTravelBridge.retainLoadedChild(predecessor.issuedReturn,cap,shared,predecessor);
            if(binding==null||binding.loadedSegmentIdentity()!=cap||!binding.current()||!binding.nativeCurrent())return null;
            var leg=new Leg(driver,rotation,journey,null,purpose,selectedGoal,binding,request);
            leg.loadedSource=source;leg.loadedIssuedSegment=cap;leg.loadedCanonicalPredecessor=source.originalLegIdentity();
            leg.loadedFirstLeg=initial?leg:predecessor.loadedFirstLeg;
            leg.loadedSettings=initial?(config instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot()
                :config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null):predecessor.loadedSettings;
            leg.loadedInput=initial?ScriptingInputLock.controlSnapshot():predecessor.loadedInput;
            leg.loadedOptions=initial?client.options:predecessor.loadedOptions;leg.loadedGameMode=initial?client.gameMode:predecessor.loadedGameMode;
            leg.loadedInventory=initial?client.player.getInventory():predecessor.loadedInventory;leg.loadedMenu=initial?client.player.inventoryMenu:predecessor.loadedMenu;
            if(leg.loadedSettings==null||leg.loadedInput==null||!leg.loadedInput.current()||!leg.loadedInput.active()
                ||!driver.id().equals(ScriptingInputLock.ownerModuleId()))return null;
            var before=leg.sample();var after=leg.sample();var oldAfter=predecessor.retirementContinuationContext();
            leg.identity=LegacyTravelPublicationIdentity.Lease.admitAfter(before,after,leg,old,oldAfter,
                predecessor.retiredAdmissionVersion,version,ADMISSIONS.getOrDefault(client,0L));
            leg.captured=before;
            if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=version
                ||predecessor.retirementOutcome()!=outcome||!outcome.cleanSuccess()
                ||initial&&(cap.predecessorIdentity()!=predecessor||cap.predecessorRetirementIdentity()!=outcome)
                ||!cap.nativeCurrent()||!source.originalSelectionHistoryCurrent()||!binding.current()||!binding.nativeCurrent()
                ||!leg.nativeCurrent(after)||!leg.loadedRawNativeFieldsCurrent()||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(life)
                ||!(initial?shared.loadedInitialAdmissionFieldsCurrent(source,marker)
                    :shared.loadedContinuationAdmissionFieldsCurrent(source,predecessor,marker)))return null;
            leg.originalPredecessor=predecessor;leg.admittedVersion=version+1;ACTIVE.put(client,leg);ADMISSIONS.put(client,version+1);return leg;
        }catch(RuntimeException unavailable){return null;}
    }
    /** Only the actual original saved-anchor request can affiliate this exact private child lease. */
    public Leg beginIssuedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            McSharedMovementNavigator exactChild,long journey,Module driver,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        return beginIssuedReturnChild(request,exactChild,journey,driver,purpose,goal);
    }
    public Leg beginIssuedReturn(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            McTravelNavigator exactChild,long journey,Module driver,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        return beginIssuedReturnChild(request,exactChild,journey,driver,purpose,goal);
    }
    private Leg beginIssuedReturnChild(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            Object child,long journey,Module driver,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        if(!client.isSameThread()||request==null||child==null||journey<1||driver==null||driver!=request.driverIdentity()
            ||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||request.purpose()!=purpose||!SharedMovementPolicy.finite(goal)
            ||!goal.equals(request.targetPosition())||!request.current()||!request.ownsPublicationLease(this,child)
            ||ACTIVE.get(client)!=null||!(services.rotation() instanceof McRotationService rotation))return null;
        try {var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
            if(life==null||!life.known()||root.get(driver.id()).orElse(null)!=driver)return null;
            var stamp=DynamicRouteConfigStamp.capture(config).orElse(null);if(stamp==null)return null;
            var binding=child instanceof McSharedMovementNavigator shared?McSuspendedFarmTravelBridge.capture(client,request,shared,config,services,stamp,purpose,goal)
                :child instanceof McTravelNavigator travel?McSuspendedFarmTravelBridge.capture(client,request,travel,config,services,stamp,purpose,goal):null;
            if(binding==null||binding.driverIdentity()!=driver||!binding.current())return null;
            long version=ADMISSIONS.getOrDefault(client,0L);if(version<0||version==Long.MAX_VALUE)return null;
            var leg=new Leg(driver,rotation,journey,null,purpose,goal,binding,request);var before=leg.sample();var after=leg.sample();
            leg.identity=LegacyTravelPublicationIdentity.Lease.admitExpected(before,after,leg,driver,version,ADMISSIONS.getOrDefault(client,0L));
            if(leg.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=version
                ||!request.ownsPublicationLease(this,child)||!request.current()||!binding.current()||!binding.nativeCurrent()
                ||VeltraphBootstrap.moduleManager()!=root||!root.lifecycleCurrent(life)||!leg.nativeCurrent(after))return null;
            leg.captured=before;leg.admittedVersion=version+1;ACTIVE.put(client,leg);ADMISSIONS.put(client,version+1);return leg;
        }catch(RuntimeException unavailable){return null;}
    }
    /** One actual terminal/retirement continuation admits one new canonical ground-entry Leg. */
    public Leg beginAfterIssuedReturnEntry(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.FinalEntryContinuation cap,
            dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,McSharedMovementNavigator shared,
            long journey,Vec3 goal,double radius,Leg predecessor){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=LegacyTravelPublicationLease.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==McSharedMovementNavigator.class&&first.getMethodName().equals("admitMovementLeg");
        });
        if(!caller||!client.isSameThread()||cap==null||request==null||shared==null||journey<1||predecessor==null
            ||cap.predecessorIdentity()!=predecessor||predecessor.issuedReturnRequest!=request||predecessor.issuedReturn==null
            ||predecessor.clientIdentity()!=client||predecessor.configIdentity()!=config||predecessor.journey!=journey
            ||!(services.rotation() instanceof McRotationService rotation)||rotation!=predecessor.rotation
            ||ACTIVE.get(client)!=null||failedIssuedEntry!=null)return null;
        var instruction=request.publicationSourceSnapshot();
        if(instruction==null||!request.claimFinalEntryInstruction(cap,instruction,shared,goal,radius)
            ||!cap.recordEntryInstruction(request,instruction,shared,goal,radius))return null;
        Leg result=null;
        try {
            var old=predecessor.retirementContinuationContext();long version=ADMISSIONS.getOrDefault(client,0L);
            if(old==null||version!=predecessor.retiredAdmissionVersion||version<0||version==Long.MAX_VALUE)return null;
            var binding=McSuspendedFarmTravelBridge.captureIssuedReturnEntry(predecessor.issuedReturn,cap,request,shared,instruction,goal,radius);
            var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
            if(binding==null||life==null||!life.known()||!request.nativeCurrent())return null;
            result=new Leg(request.driverIdentity(),rotation,journey,null,SharedMovementPolicy.Purpose.EXACT_RETURN,goal,binding,request);
            if(cap.loadedSegmentIdentity()!=null){
                if(cap.loadedSegmentIdentity()!=predecessor.loadedIssuedSegment||binding.loadedSegmentIdentity()!=predecessor.loadedIssuedSegment
                    ||predecessor.loadedSource==null||shared.loadedSegmentSourceIdentity()!=predecessor.loadedSource)return null;
                result.loadedSource=predecessor.loadedSource;result.loadedIssuedSegment=predecessor.loadedIssuedSegment;
                result.loadedCanonicalPredecessor=predecessor.loadedCanonicalPredecessor;result.loadedFirstLeg=predecessor.loadedFirstLeg;
                result.loadedSettings=predecessor.loadedSettings;result.loadedInput=predecessor.loadedInput;
                result.loadedOptions=predecessor.loadedOptions;result.loadedGameMode=predecessor.loadedGameMode;
                result.loadedInventory=predecessor.loadedInventory;result.loadedMenu=predecessor.loadedMenu;
            }
            result.originalPredecessor=predecessor;
            var before=result.sample();var after=result.sample();var oldAfter=predecessor.retirementContinuationContext();
            result.identity=LegacyTravelPublicationIdentity.Lease.admitAfter(before,after,result,old,oldAfter,
                predecessor.retiredAdmissionVersion,version,ADMISSIONS.getOrDefault(client,0L));
            if(result.identity==null||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=version
                ||!root.lifecycleCurrent(life)||!request.currentFinalEntryInstructionCurrent(cap,instruction,shared,goal,radius)
                ||!binding.current()||!binding.nativeCurrent()||!result.nativeCurrent(after))return null;
            result.captured=before;result.entrySource=cap;result.entryInstruction=instruction;result.entryShared=shared;
            result.entryGoal=goal;result.entryRadius=radius;result.admittedVersion=version+1;
            ACTIVE.put(client,result);ADMISSIONS.put(client,version+1);
            if(!cap.bindEntryLeg(result)||!result.current()){
                failedIssuedEntry=result;throw new IllegalStateException("Actual issued-return entry binding failed; original cleanup custody retained");
            }
            return result;
        }catch(RuntimeException|Error failure){
            if(result!=null&&ACTIVE.get(client)==result&&ADMISSIONS.getOrDefault(client,0L)==result.admittedVersion)failedIssuedEntry=result;
            throw failure;
        }
    }
    public final class Leg {
        private dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.FinalEntryContinuation entrySource;
        private Object entryInstruction;
        private McSharedMovementNavigator entryShared;
        private Vec3 entryGoal;
        private double entryRadius;
        private OwnedIssuedUseSnapshot issuedDeviceUse;
        private IssuedDeviceQueue issuedDeviceQueue;
        private Object nativeNormalOwnerRole;
        /** Actual final macro-private source only; no generic declaring-class or Boolean waiver. */
        private boolean normalOwnerRoleCurrent(Object exact){
            if(exact==null||exact!=nativeNormalOwnerRole||issuedReturn!=null||maintenance!=null||ownedUsePurpose!=SharedMovementPolicy.Purpose.EXACT_RETURN)return false;
            return parent instanceof dev.veltraph.client.fishing.module.FishingModule&&exact instanceof dev.veltraph.client.fishing.module.FishingModule.NativeNormalReturnSource fishing?fishing.nativeCurrent()
                :parent instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule&&exact instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule.NativeNormalReturnSource foraging&&foraging.nativeCurrent();
        }
        private NormalReturnOwnedUseSnapshot normalReturnUse;
        private NormalReturnSelectedSlotLoan normalReturnSlotLoan;
        private StandardNormalReturnCleanupSource standardNormalReturnCleanupSource;
        /** Stored only by successful actual predecessor handoff factories; never caller-provided permission. */
        private Leg originalPredecessor;
        private McLoadedGroundReturnNavigator.SelectedLoadedSegment loadedSource;
        private dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedIssuedSegment;
        private Leg loadedCanonicalPredecessor,loadedFirstLeg;
        private dev.veltraph.client.config.MapConfigService.StoredStateSnapshot loadedSettings;
        private Object loadedOptions,loadedGameMode,loadedInventory,loadedMenu;
        private ScriptingInputLock.ControlSnapshot loadedInput;
        private NormalReturnRestorationWitness loadedReturnedRestoration;
        private long loadedClockFloor=-1;
        /** Original canonical Binding metadata only; this cannot mint device or Root permission. */
        public McFarmMaintenanceAuthorityBridge.Binding loadedMaintenanceBinding(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            return source!=null&&source.originalLegIdentity()==this&&source.configIdentity()==config&&source.parentIdentity()==parent
                &&maintenance!=null&&maintenance.frame().pair().capturedPose()==source.canonicalTargetPoint()
                &&retirement!=null&&source.actualRetirementIdentity()==retirement&&retirement.cleanSuccess()?maintenance:null;
        }
        /** Raw actual new Leg fields for the one-time bind, without full source/current callbacks. */
        public boolean loadedSegmentSourceFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            return (loadedIssuedSegment==null?loadedMaintenanceSourceFieldsCurrent(source):loadedIssuedSourceFieldsCurrent(source))&&loadedFirstLeg!=null
                &&(source.initialAdmittedLegIdentity()==loadedFirstLeg||source.initialAdmittedLegIdentity()==null
                    &&loadedFirstLeg==this&&originalPredecessor==loadedCanonicalPredecessor);
        }
        public boolean loadedMaintenanceSourceFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            return source!=null&&loadedSource==source&&identity!=null&&captured!=null&&retirement==null&&guiRetirement==null
                &&source.issuedRequestIdentity()==null&&issuedReturn==null&&issuedReturnRequest==null
                &&loadedCanonicalPredecessor==source.originalLegIdentity()&&loadedCanonicalPredecessor!=null
                &&maintenance!=null&&loadedCanonicalPredecessor.maintenance==maintenance
                &&maintenance.inputOwner()==parent&&maintenance.cameraOwner()==source.cameraOwnerIdentity()
                &&maintenance.frame().pair().capturedPose()==source.canonicalTargetPoint()
                &&source.parentIdentity()==parent&&source.configIdentity()==config&&source.segmentPosition().equals(ownedUseGoal)
                &&ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&client.isSameThread()
                &&ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion
                &&client.player==captured.player()&&client.level==captured.world()&&client.getConnection()==captured.connection()
                &&source.playerIdentity()==captured.player()&&source.worldIdentity()==captured.world()&&source.connectionIdentity()==captured.connection()
                &&VeltraphBootstrap.config()==config&&lifecycleRoot==VeltraphBootstrap.moduleManager()&&loadedRawNativeFieldsCurrent()
                &&loadedRawSlotHistoryCurrent();
        }
        /** Stored typed affiliation only; neither this getter nor a current capsule lookup admits a Leg. */
        public McSuspendedFarmTravelBridge.Binding loadedIssuedBinding(dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment cap){
            return cap!=null&&loadedIssuedSegment==cap&&loadedSource==cap.sourceIdentity()&&issuedReturnRequest==cap.requestIdentity()
                &&issuedReturn!=null&&issuedReturn.loadedSegmentIdentity()==cap&&maintenance==null?issuedReturn:null;
        }
        public boolean loadedIssuedSourceFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            return source!=null&&source==loadedSource&&loadedIssuedSegment!=null&&loadedIssuedSegment.sourceIdentity()==source
                &&loadedIssuedSegment.requestIdentity()==issuedReturnRequest&&source.issuedRequestIdentity()==issuedReturnRequest
                &&loadedIssuedBinding(loadedIssuedSegment)!=null&&identity!=null&&captured!=null&&retirement==null&&guiRetirement==null
                &&loadedCanonicalPredecessor==source.originalLegIdentity()&&loadedFirstLeg!=null
                &&source.parentIdentity()==parent&&source.configIdentity()==config&&source.segmentPosition().equals(ownedUseGoal)
                &&ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&client.isSameThread()
                &&ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion
                &&source.playerIdentity()==captured.player()&&source.worldIdentity()==captured.world()&&source.connectionIdentity()==captured.connection()
                &&issuedReturnRequest.currentLoadedSegmentFieldsCurrent(source)&&loadedRawNativeFieldsCurrent()&&loadedRawSlotHistoryCurrent();
        }
        public boolean loadedIssuedRetiredFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            return source!=null&&loadedSource==source&&loadedIssuedSegment!=null&&loadedIssuedBinding(loadedIssuedSegment)!=null
                &&retirement!=null&&retirement.cleanSuccess()&&retirement.removal()==LegacyTravelRetirement.Removal.REMOVED_OWN
                &&retiredAdmissionVersion>=0&&ACTIVE.get(client)==null&&ADMISSIONS.getOrDefault(client,0L)==retiredAdmissionVersion
                &&loadedFirstLeg!=null&&source.initialAdmittedLegIdentity()==loadedFirstLeg&&loadedFirstLeg.loadedSource==source
                &&loadedCanonicalPredecessor==source.originalLegIdentity()&&source.parentIdentity()==parent&&source.configIdentity()==config
                &&source.segmentPosition().equals(ownedUseGoal)&&ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN
                &&loadedRawNativeFieldsCurrent()&&source.originalSelectionHistoryCurrent()
                &&issuedReturnRequest.currentLoadedSegmentFieldsCurrent(source);
        }
        /** Real clean read-only interval only; this method cannot publish or admit a new Leg. */
        public boolean loadedMaintenanceRetiredFieldsCurrent(McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            return source!=null&&loadedSource==source&&source.issuedRequestIdentity()==null&&issuedReturn==null&&issuedReturnRequest==null
                &&retirement!=null&&retirement.cleanSuccess()&&retirement.removal()==LegacyTravelRetirement.Removal.REMOVED_OWN
                &&retiredAdmissionVersion>=0&&ACTIVE.get(client)==null&&ADMISSIONS.getOrDefault(client,0L)==retiredAdmissionVersion
                &&loadedCanonicalPredecessor==source.originalLegIdentity()&&loadedCanonicalPredecessor!=null
                &&loadedFirstLeg!=null&&source.initialAdmittedLegIdentity()==loadedFirstLeg&&loadedFirstLeg.loadedSource==source
                &&maintenance!=null&&loadedCanonicalPredecessor.maintenance==maintenance
                &&maintenance.inputOwner()==parent&&maintenance.cameraOwner()==source.cameraOwnerIdentity()
                &&maintenance.frame().pair().capturedPose()==source.canonicalTargetPoint()
                &&source.parentIdentity()==parent&&source.configIdentity()==config&&source.segmentPosition().equals(ownedUseGoal)
                &&ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&loadedRawNativeFieldsCurrent()
                &&source.originalSelectionHistoryCurrent()&&loadedRetiredSlotHistoryCurrent();
        }
        private boolean loadedRawNativeFieldsCurrent(){return client.isSameThread()&&identity!=null&&captured!=null
            &&loadedSettings!=null&&loadedSettings.current()&&loadedInput!=null&&loadedInput.current()&&loadedInput.active()
            &&(loadedIssuedSegment==null?maintenance!=null&&maintenance.frame().helperId().equals(ScriptingInputLock.ownerModuleId())
                :issuedReturnRequest!=null&&loadedIssuedBinding(loadedIssuedSegment)!=null&&parent==issuedReturnRequest.driverIdentity()
                    &&parent.id().equals(ScriptingInputLock.ownerModuleId()))
            &&lifecycleRoot==VeltraphBootstrap.moduleManager()
            &&originalLifecycle.value()!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())
            &&client.player==captured.player()&&client.level==captured.world()&&client.getConnection()==captured.connection()
            &&VeltraphBootstrap.config()==config&&captured.config()==config&&client.player!=null&&client.level!=null&&client.getConnection()!=null
            &&client.options==loadedOptions&&client.gameMode==loadedGameMode&&client.player.getInventory()==loadedInventory
            &&client.player.inventoryMenu==loadedMenu&&client.player.containerMenu==loadedMenu&&client.player.inventoryMenu.getCarried().isEmpty()
            &&client.player.level()==client.level&&client.player.isAlive()&&!client.player.isPassenger()&&!client.player.isScoping()
            &&!client.player.isInLava()&&!client.isPaused()&&client.gui.screen()==null&&client.getCameraEntity()==client.player
            &&!client.level.tickRateManager().isEntityFrozen(client.player)&&McOwnedRotation.installed(client)
            &&rotation.requestGeneration()==identity.generation()&&McOwnedRotation.epoch(client)==identity.epoch()
            &&McOwnedRotation.publicationSerial(client)==identity.serial();}
        private boolean loadedRawSlotHistoryCurrent(){
            if(loadedIssuedSegment!=null){
                if(loadedSource==null||issuedReturnRequest==null||loadedIssuedBinding(loadedIssuedSegment)==null)return false;
                if(loadedSource.originalSelectionHistoryCurrent())return true;
                var transit=issuedReturnRequest.currentDeviceTransitIdentity();
                if(transit==null||transit.loadedSegmentIdentity()!=loadedIssuedSegment||transit.originalLegIdentity()!=this
                    ||transit.admissionIdentity().loadedSegmentIdentity()!=loadedIssuedSegment||!transit.custodySourceCurrent())return false;
                var custody=issuedReturnRequest.sourceHeader().retainedDeviceSlotCustody(transit);
                // Existing Request/Snapshot custody includes the genuine setter's returned prefix before receipt completion.
                return custody!=null&&(issuedReturnRequest.nativeCurrent()||issuedReturnRequest.nativeContinuationCurrent());
            }
            var loan=normalReturnSlotLoan;if(loan==null)return loadedSource!=null&&loadedSource.originalSelectionHistoryCurrent();
            return loan.admission!=null&&loan.owner==this&&normalReturnSelectionCustodyRawCurrent(loan.admission,loan.prior,loan.originalHistory);
        }
        private boolean loadedRetiredSlotHistoryCurrent(){
            var loan=normalReturnSlotLoan;
            if(loan==null)return loadedSource!=null&&loadedSource.originalSelectionHistoryCurrent();
            var proof=loadedReturnedRestoration;
            return proof!=null&&proof.originalLegIdentity()==this&&loan.consumed&&!loan.lost
                &&proof.originalHistory().sameStoredObservation(loan.originalHistory)&&proof.retainedLineageCurrent()
                &&proof.rawProjectionCurrent()&&proof.restoredHistory()!=null
                &&proof.restoredHistory().sameStoredObservation(loadedSource.expectedSelectionHistory())
                &&loadedSource.originalSelectionHistoryCurrent();
        }
        private boolean loadedClockCurrent(){var source=loadedSource;if(source==null)return true;long now=System.currentTimeMillis();
            if(!dev.veltraph.client.navigation.ReturnDeviceDeadlines.permitsOriginalWindow(now,source.originalStartedAtMillis(),loadedClockFloor,
                source.controllerIdentity().observedClockMillis(),source.originalDeadlineMillis()))return false;
            loadedClockFloor=now;return true;
        }
        private boolean loadedPositiveSourceCurrent(){var source=loadedSource;return source==null||loadedFirstLeg!=null
            &&source.initialAdmittedLegIdentity()==loadedFirstLeg&&loadedFirstLeg.loadedSource==source
            &&source.nativeSourceCurrent()&&loadedClockCurrent()
            &&(loadedIssuedSegment==null?loadedMaintenanceSourceFieldsCurrent(source):loadedIssuedSourceFieldsCurrent(source));}
        private boolean loadedCleanupSourceCurrent(){var source=loadedSource;return source==null||source.cleanupSourceCurrent()
            ||source.nativeSourceCurrent();}
        private boolean loadedRetiredSourceCurrent(){var source=loadedSource;return source==null||retirement!=null&&retirement.cleanSuccess()
            &&loadedFirstLeg!=null&&source.initialAdmittedLegIdentity()==loadedFirstLeg&&loadedFirstLeg.loadedSource==source
            &&(source.nativeSourceCurrent()||source.retiredLineageCurrent());}
        public Leg originalPredecessorIdentity(){return originalPredecessor;}
        private final Module parent;private final McRotationService rotation;private final long journey;
        private final ModuleManager lifecycleRoot;private final PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot> originalLifecycle;
        private final SharedMovementPolicy.Purpose ownedUsePurpose;private final Vec3 ownedUseGoal;
        private LegacyTravelPublicationIdentity.Lease identity;
        private LegacyTravelPublicationIdentity.State captured;
        private McRotationService.CleanupReceipt sourceReceipt,handedSourceReceipt;
        private Object issuedReturnQueuedInstruction;
        private McRotationService.GuiRequestRetirement guiRetirement;private McOwnedRotation.GuiAvailabilityRevocation consumedGuiProof;
        private final McFarmMaintenanceAuthorityBridge.Binding maintenance;
        private final McSuspendedFarmTravelBridge.Binding issuedReturn;
        private final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest issuedReturnRequest;
        private long positivePublishedSerial=-1;
        private PublicationSnapshot keyOnlyInitialSnapshot;
        private SelectedSlotLoan selectedDeviceSlotLoan;
        private McHeldKeys.ScopedHeldReceipt actualKeyOnlyReceipt;
        private Object keyOnlyOptions;
        private LegacyTravelPublicationIdentity.State lastObservedSample;
        private McHeldKeys.ScopedHeldReceipt lastNativeKeyReceipt;
        private String lastKeyOnlyDecision="NOT_CHECKED",lastInputDecision="NO_ORDINARY_KEY_PUBLICATION";
        private final java.util.EnumMap<LegacyTravelRetirement.Key,ProfileKeyPublication> profileKeys =
                new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
        private long profileFrameSequence;
        private ProfileInputFrame latestProfileFrame;
        private final java.util.EnumMap<LegacyTravelRetirement.Key,IssuedReturnHeldAttempt> issuedReturnHeldAttempts=new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
        /** Actual native attempt custody only; no rejected attempt is a successful gameplay/frame publication. */
        private final class IssuedReturnHeldAttempt {
            final LegacyTravelRetirement.Key name;final McHeldKeys.ScopedHeldReceipt actual;final Object instruction,options;
            final LegacyTravelPublicationIdentity.Lease originalIdentity;final LegacyTravelPublicationIdentity.State before;final long admission;
            IssuedReturnHeldAttempt(LegacyTravelRetirement.Key name,McHeldKeys.ScopedHeldReceipt actual,Object instruction,Object options,
                    LegacyTravelPublicationIdentity.State before){this.name=name;this.actual=actual;this.instruction=instruction;this.options=options;
                this.before=before;originalIdentity=identity;admission=admittedVersion;}
            boolean current(){return instruction!=null&&originalIdentity==identity&&client.isSameThread()&&ACTIVE.get(client)==Leg.this
                &&admission==admittedVersion&&ADMISSIONS.getOrDefault(client,0L)==admission&&client.options==options&&key(name)==actual.key()
                &&VeltraphBootstrap.moduleManager()==before.root()&&VeltraphBootstrap.config()==before.config()&&client.player==before.player()
                &&client.level==before.world()&&client.getConnection()==before.connection()&&originalCleanupLifecycle()
                &&actual.normallyReturnedOwnedHeldAttempt()&&actual.normalIdleHistoryCurrent()&&actual.key().isDown();}
        }
        /** Private in-flight Leg sink; its real native return can become cleanup custody before a later callback disables us. */
        public final class ImmediateHeldAttempt {
            private final LegacyTravelRetirement.Key name;private final KeyMapping mapping;private final Object instruction,options,keyHistory;
            private final dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier;
            private final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            private final LegacyPublicationSnapshotIdentity.State nativeBefore=nativeReservation();
            private final long admission=admittedVersion;
            private LegacyTravelPublicationIdentity.State before;private boolean cached,open=true;
            private void close(){open=false;}
            private ImmediateHeldAttempt(LegacyTravelRetirement.Key name,Object instruction){
                this.name=name;this.instruction=instruction;options=client.options;mapping=key(name);
                carrier=mapping instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier actual?actual:null;
                keyHistory=carrier==null?null:carrier.normalIdleKeySnapshot();
            }
            private boolean bindOriginal(LegacyTravelPublicationIdentity.State actual){
                if(before!=null||instruction==null||mapping==null||carrier==null||keyHistory==null||!carrier.normalIdleKeyCurrent(keyHistory)
                    ||originalIdentity!=identity||admission!=admittedVersion||client.options!=options||key(name)!=mapping
                    ||!LegacyPublicationSnapshotIdentity.same(nativeBefore,nativeReservation())||!issuedReturnPublicationSourceCurrent(instruction)
                    ||!identity.current(actual))return false;
                before=actual;return true;
            }
            /** Only a service-private actual receipt from THIS exact in-flight call can be cached. */
            public void cache(McHeldKeys.ScopedHeldReceipt actual){
                if(!open||cached||before==null||actual==null||!actual.immediateHeldAttemptOf(this)||actual.key()!=mapping
                    ||!actual.normallyReturnedOwnedHeldAttempt()||!actual.normalIdleHistoryCurrent()||!mapping.isDown()
                    ||originalIdentity!=identity||admission!=admittedVersion||ADMISSIONS.getOrDefault(client,0L)!=admission
                    ||client.options!=options||key(name)!=mapping||!nativeCurrent(before)
                    ||!LegacyPublicationSnapshotIdentity.same(nativeBefore,nativeReservation())||!originalCleanupLifecycle())return;
                var attempt=new IssuedReturnHeldAttempt(name,actual,instruction,options,before);
                if(!attempt.current())return;
                cached=true;issuedReturnHeldAttempts.put(name,attempt);identity.inputNativeAttemptRecorded();
            }
        }
        private record ProfileKeyPublication(McHeldKeys.ScopedHeldReceipt actual,boolean held,int nativeTick,Object options,Object issuedInstruction) {
            private ProfileKeyPublication(McHeldKeys.ScopedHeldReceipt actual,boolean held,int nativeTick,Object options){this(actual,held,nativeTick,options,null);}
        }
        private volatile LegacyTravelRetirement.RetirementOutcome retirement;
        private boolean retiring,mintingInteraction;
        private long retiredAdmissionVersion=-1,admittedVersion=-1;
        private final TravelHeading yawHeading=new TravelHeading(),pitchHeading=new TravelHeading();
        private int lastTrackTick=Integer.MIN_VALUE;
        private final Configuration capturedConfiguration;
        private Leg(Module parent,McRotationService rotation,long journey){this(parent,rotation,journey,null);}
        private Leg(Module parent,McRotationService rotation,long journey,McFarmMaintenanceAuthorityBridge.Binding maintenance){this(parent,rotation,journey,maintenance,null,null);}
        private Leg(Module parent,McRotationService rotation,long journey,McFarmMaintenanceAuthorityBridge.Binding maintenance,SharedMovementPolicy.Purpose purpose,Vec3 goal){this(parent,rotation,journey,maintenance,purpose,goal,null,null);}
        private Leg(Module parent,McRotationService rotation,long journey,McFarmMaintenanceAuthorityBridge.Binding maintenance,SharedMovementPolicy.Purpose purpose,Vec3 goal,
                McSuspendedFarmTravelBridge.Binding issuedReturn,dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request){
            this.parent=parent;this.rotation=rotation;this.journey=journey;this.maintenance=maintenance;this.issuedReturn=issuedReturn;issuedReturnRequest=request;
            ownedUsePurpose=purpose;ownedUseGoal=goal;lifecycleRoot=VeltraphBootstrap.moduleManager();originalLifecycle=new PassiveTravelLifecycleSlot<>(lifecycleRoot==null?null:lifecycleRoot.lifecycleSnapshot());this.capturedConfiguration=configuration();}
        private boolean issuedReturnCurrent(){return issuedReturn!=null&&issuedReturnRequest!=null&&issuedReturnRequest.current()&&issuedReturn.current()
            &&(entrySource==null||entrySource.entryCurrent(issuedReturnRequest,entryInstruction,entryShared,entryGoal,entryRadius));}
        private boolean issuedReturnNativeCurrent(){return issuedReturn!=null&&issuedReturn.nativeCurrent();}
        private boolean issuedReturnCleanupCurrent(){return issuedReturn!=null&&(issuedReturnNativeCurrent()||issuedReturn.cancellationCurrent(this));}
        /** Last action leaf uses the original live instruction, never retained completion or a role Boolean. */
        private boolean issuedReturnPublicationCurrent(LegacyTravelPublicationIdentity.State state){return issuedReturn==null
            ||issuedReturnRequest.nativeCurrent()&&issuedReturnNativeCurrent()&&nativeCurrent(state)
                &&lifecycleRoot!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value());}
        private Object issuedReturnPublicationSource(){return issuedReturn==null?null:issuedReturnRequest.publicationSourceSnapshot();}
        private boolean issuedReturnPublicationSourceCurrent(Object exact){return issuedReturn==null
            ||exact!=null&&issuedReturnRequest.publicationSourceSnapshotCurrent(exact)&&issuedReturnNativeCurrent();}
        /** Original private Close context can retire its actual own effect after action permission ends. */
        private boolean issuedReturnQueuedCleanupSourceCurrent(){return issuedReturn==null
            ||issuedReturnQueuedInstruction!=null&&(issuedReturnPublicationSourceCurrent(issuedReturnQueuedInstruction)
                ||dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.ownsOriginalRequest(issuedReturnRequest,this)
                    &&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.nativeCurrent());}
        private PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot> borrowOriginalLifecycle(ModuleManager root,ModuleManager.LifecycleSnapshot observed){
            if(root!=lifecycleRoot||observed==null||originalLifecycle.value()==null||!root.lifecycleSameHistory(originalLifecycle.value(),observed))
                throw new IllegalStateException("Original travel lifecycle history changed");
            return originalLifecycle;
        }
        /** Captures actual old leg custody before a genuine Root passive operation; no new leg is admitted. */
        public PassiveLifecycleContinuation capturePassiveLifecycleContinuation(VeltraphBootstrap.RootPassiveTransition actual){
            if(actual==null||!actual.sourceBeforeCurrent(client)||actual.registryIdentity()!=lifecycleRoot
                ||!lifecycleRoot.lifecycleSameHistory(originalLifecycle.value(),actual.beforeLifecycle()))return null;
            boolean beforeCurrent=retirement==null?current():retirementContinuationContext()!=null;
            if(!beforeCurrent||!actual.sourceBeforeCurrent(client))return null;
            var hardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);
            if(hardware==null||!actual.sourceBeforeCurrent(client))return null;
            var continuation=new PassiveLifecycleContinuation(actual,hardware);
            return continuation.rawCurrent()&&actual.sourceBeforeCurrent(client)?continuation:null;
        }
        public final class PassiveLifecycleContinuation {
            private final VeltraphBootstrap.RootPassiveTransition actual;
            private final PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot>.Continuation lifecycle=originalLifecycle.capture();
            private final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
            private final LegacyPublicationSnapshotIdentity.State reservation=nativeReservation();
            private final LegacyTravelPublicationIdentity.Lease retainedIdentity=identity;
            private final LegacyTravelPublicationIdentity.State retainedCaptured=captured;
            private final McRotationService.CleanupReceipt source=sourceReceipt,handed=handedSourceReceipt;
            private final McRotationService.GuiRequestRetirement gui=guiRetirement;
            private final PublicationSnapshot keyOnly=keyOnlyInitialSnapshot;
            private final McHeldKeys.ScopedHeldReceipt keyReceipt=actualKeyOnlyReceipt,lastKey=lastNativeKeyReceipt;
            private final ProfileInputFrame frame=latestProfileFrame;
            private final SelectedSlotLoan slotLoan=selectedDeviceSlotLoan;
            private final LegacyTravelRetirement.RetirementOutcome closed=retirement;
            private final long positiveSerial=positivePublishedSerial,profileSequence=profileFrameSequence,retiredVersion=retiredAdmissionVersion;
            private final boolean wasRetiring=retiring;
            private boolean prepared,committed;
            private PassiveLifecycleContinuation(VeltraphBootstrap.RootPassiveTransition actual,dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware){this.actual=actual;this.hardware=hardware;}
            public boolean rawCurrent(){return !committed&&client.isSameThread()&&lifecycle!=null&&lifecycle.rawCurrent()&&hardware.current()
                &&actual.registryIdentity()==lifecycleRoot&&VeltraphBootstrap.moduleManager()==lifecycleRoot
                &&LegacyPublicationSnapshotIdentity.same(reservation,nativeReservation())&&identity==retainedIdentity&&captured==retainedCaptured
                &&sourceReceipt==source&&handedSourceReceipt==handed&&guiRetirement==gui&&keyOnlyInitialSnapshot==keyOnly
                &&actualKeyOnlyReceipt==keyReceipt&&lastNativeKeyReceipt==lastKey&&latestProfileFrame==frame&&selectedDeviceSlotLoan==slotLoan
                &&retirement==closed&&positivePublishedSerial==positiveSerial&&profileFrameSequence==profileSequence
                &&retiredAdmissionVersion==retiredVersion&&retiring==wasRetiring;}
            public boolean prepare(){
                if(prepared||!rawCurrent()||!actual.normalReturnedCurrent(client))return false;
                var next=actual.normalReturnedLifecycle();
                if(next==null||!lifecycleRoot.lifecycleSameHistory(lifecycle.before(),actual.beforeLifecycle())
                    ||!lifecycleRoot.lifecycleCurrent(next)||!actual.normalReturnedCurrent(client)||!rawCurrent()||!lifecycle.prepare(next))return false;
                prepared=true;return true;
            }
            public boolean commit(){if(!prepared||!actual.passiveDeclarationCommitWindowCurrent()||!rawCurrent()||!lifecycle.commit())return false;committed=true;return true;}
        }
        private boolean originalCleanupLifecycle(){return lifecycleRoot!=null&&VeltraphBootstrap.moduleManager()==lifecycleRoot&&originalLifecycle.value()!=null&&lifecycleRoot.ownCleanupLifecycleCurrent(originalLifecycle.value(),parent);}
        private boolean roleCurrent(boolean cleanup){
            if(issuedReturn!=null)return cleanup?issuedReturnCleanupCurrent():issuedReturnCurrent();
            if(maintenance==null)return true;
            if(!cleanup)return McFarmMaintenanceAuthorityBridge.current(maintenance,client,config);
            if(positivePublishedSerial<0&&keyOnlyCleanupRole())return true;
            return identity!=null&&positivePublishedSerial>0&&positivePublishedSerial==identity.serial()
                &&(McFarmMaintenanceAuthorityBridge.cancellationCurrent(maintenance,client,config,rotation,identity.epoch(),positivePublishedSerial,identity.generation())
                    ||McFarmMaintenanceAuthorityBridge.retirementKeysCurrent(maintenance,client,config,rotation,identity.epoch(),positivePublishedSerial,identity.generation()));
        }
        /** Original unpublished reservation is equality only; neither its serial nor a key receipt is a camera ticket. */
        private boolean keyOnlyControlCurrent(){
            var old=keyOnlyInitialSnapshot;boolean actualMapping=false;
            if(actualKeyOnlyReceipt!=null)for(var name:LegacyTravelRetirement.Key.values())if(key(name)==actualKeyOnlyReceipt.key()){actualMapping=true;break;}
            return actualMapping&&maintenance!=null&&old!=null&&actualKeyOnlyReceipt!=null&&old.owner==this&&identity==old.originalIdentity
                &&positivePublishedSerial<0&&!identity.cameraPublishedOrQueued()&&identity.ticket()==0&&sourceReceipt==null
                &&identity.generation()==captured.requestGeneration()&&identity.epoch()==captured.epoch()&&identity.serial()==captured.publicationSerial()
                &&client.options==keyOnlyOptions&&admittedVersion==old.admission&&identity.generation()==old.generation&&identity.epoch()==old.epoch&&identity.serial()==old.serial
                &&(actualKeyOnlyReceipt.confirmed()||actualKeyOnlyReceipt.nativeSetAttempts()>0||actualKeyOnlyReceipt.restorationAttempts()>0);
        }
        private LegacyPublicationSnapshotIdentity.State nativeReservation(){return new LegacyPublicationSnapshotIdentity.State(ACTIVE.get(client),identity,
            VeltraphBootstrap.moduleManager(),client.player,client.level,client.getConnection(),VeltraphBootstrap.config(),ADMISSIONS.getOrDefault(client,0L),
            rotation.requestGeneration(),McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client));}
        /** Actual private C5 live view or synchronous closure; keys only, no camera cancellation or inventory restitution. */
        private boolean keyOnlyCleanupRole(){
            if(!client.isSameThread()||!keyOnlyControlCurrent()){lastKeyOnlyDecision="INITIAL_RESERVATION_OR_ACTUAL_KEY_RECEIPT_MISMATCH";return false;}
            try {var original=maintenance.frame().pair();var ctx=maintenance.frame().context();
                if(!(original.coordinator() instanceof FarmMaintenanceCoordinator coordinator)||coordinator!=VeltraphBootstrap.farmMaintenance()
                    ||!(original.helper() instanceof FarmMaintenanceCoordinator.Participant helper)||helper!=parent)return false;
                var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||root!=keyOnlyInitialSnapshot.root)return false;
                var live=coordinator.movementCancellationView(helper,original.token());
                var closed=live==null?coordinator.movementRetirementView(helper,original.token()):null;
                if(live==null&&closed==null){lastKeyOnlyDecision="NO_AUTHENTIC_LIVE_OR_CLOSED_SOURCE_VIEW";return false;}
                boolean sameView=live!=null?live.captureIdentity()==original.capture()&&live.sourcePolicyIdentity()==original.sourcePolicy()
                    &&live.farm()==original.farm()&&live.helper()==original.helper()&&live.token()==original.token()&&live.capturedPose()==original.capturedPose()
                    &&live.restorationWitness()==original.restorationWitness()&&live.registryIdentity()==root&&live.configStore()==config&&live.rotationServiceIdentity()==rotation
                    &&live.player()==ctx.player()&&live.world()==ctx.world()&&live.connection()==ctx.connection()
                    :closed.captureIdentity()==original.capture()&&closed.sourcePolicyIdentity()==original.sourcePolicy()&&closed.farm()==original.farm()
                    &&closed.helper()==original.helper()&&closed.token()==original.token()&&closed.capturedPose()==original.capturedPose()
                    &&closed.restorationWitness()==original.restorationWitness()&&closed.registryIdentity()==root&&closed.configStore()==config&&closed.rotationServiceIdentity()==rotation
                    &&closed.player()==ctx.player()&&closed.world()==ctx.world()&&closed.connection()==ctx.connection();
                if(!sameView||root.get(helper.id()).orElse(null)!=helper){lastKeyOnlyDecision="ORIGINAL_SOURCE_VIEW_OR_REGISTERED_HELPER_MISMATCH";return false;}
                var farm=live!=null?live.farm():closed.farm();if(root.get(farm.id()).orElse(null)!=farm)return false;
                var controllers=root.enabledControllers();Object input=root.inputOwner().orElse(null);
                boolean roles=live!=null?controllers.size()==2&&controllers.contains(farm)&&controllers.contains(helper)&&helper.isEnabled()
                    &&!helper.isPassiveObserver()&&helper.ownsGameplayInput()&&farm.isEnabled()&&farm.maintenanceOwnedBy(original.token())&&input==helper
                    :!helper.isEnabled()&&controllers.stream().allMatch(module->module==farm&&!module.ownsGameplayInput())&&input==null;
                boolean lock=helper.id().equals(ScriptingInputLock.ownerModuleId())&&(closed!=null||ScriptingInputLock.isActive());
                // Last provider callback precedes the authentic callback-free source/window and private reservation leaves.
                Object camera=McOwnedRotation.authority(client);boolean exactCamera=camera==(live!=null?original.token():null);
                boolean source=live!=null?coordinator.movementCancellationStillCurrent(live):coordinator.movementRetirementStillCurrent(closed);
                boolean fresh=source&&coordinator==VeltraphBootstrap.farmMaintenance()&&keyOnlyControlCurrent()
                    &&client.player==ctx.player()&&client.level==ctx.world()&&client.getConnection()==ctx.connection()&&VeltraphBootstrap.config()==ctx.configStore();
                boolean eligible=LegacyKeyOnlyPublicationIdentity.mayRelease(new LegacyKeyOnlyPublicationIdentity.Evidence(keyOnlyInitialSnapshot.nativeState,nativeReservation(),
                    actualKeyOnlyReceipt!=null,identity.cameraPublishedOrQueued(),roles&&lock&&exactCamera&&fresh,root.lifecycleCurrent(lifecycle)));
                lastKeyOnlyDecision=(eligible?"ELIGIBLE":"REFUSED")+(live!=null?"_LIVE":"_SOURCE_CLOSE")+":roles="+roles+",lock="+lock+",camera="+exactCamera+",source="+source+",fresh="+fresh;
                return eligible;
            }catch(RuntimeException unavailable){return false;}
        }
        public Object cameraAuthority(){return maintenance==null?parent:maintenance.cameraOwner();}
        public boolean continuationCurrent(){return retirementContinuationContext()!=null;}
        private Minecraft clientIdentity(){return LegacyTravelPublicationLease.this.client;}
        private ConfigService configIdentity(){return LegacyTravelPublicationLease.this.config;}
        private LegacyTravelPublicationIdentity.State sample(){
            long observedRevision=revision.getAsLong();var liveConfiguration=configuration();
            var root=VeltraphBootstrap.moduleManager();var p=client.player;var w=client.level;var connection=client.getConnection();
            boolean thread=client.isSameThread();Object inputOwner=root==null?null:root.inputOwner().orElse(null);
            Object authority=McOwnedRotation.authority(client);
            boolean lockOwned=ScriptingInputLock.isActive()&&parent.id().equals(ScriptingInputLock.ownerModuleId());
            boolean registered=root!=null&&root.get(parent.id()).orElse(null)==parent&&!parent.isPassiveObserver()
                &&(!ScriptingInputLock.isActive()||lockOwned);
            if(issuedReturn!=null)registered=registered&&issuedReturnCleanupCurrent();
            if(maintenance!=null)registered=registered&&(McFarmMaintenanceAuthorityBridge.current(maintenance,client,config)||roleCurrent(true));
            boolean policyCurrent=capturedConfiguration!=null&&capturedConfiguration.equals(liveConfiguration);
            boolean usable=policyCurrent&&(issuedReturn==null||issuedReturnCurrent())&&(maintenance==null||McFarmMaintenanceAuthorityBridge.current(maintenance,client,config))&&parent.ownsGameplayInput()&&thread&&p!=null&&w!=null&&connection!=null&&p.level()==w&&p.isAlive()&&!p.isInLava()&&!p.isPassenger()
                &&!client.isPaused()&&!w.tickRateManager().isEntityFrozen(p)&&client.gui.screen()==null&&!p.isScoping()&&client.getCameraEntity()==p
                &&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty()&&lockOwned
                &&client.options!=null&&McOwnedRotation.installed(client)&&VeltraphBootstrap.config()==config;
            var observed=new LegacyTravelPublicationIdentity.State(root,parent,inputOwner,authority,p,w,connection,VeltraphBootstrap.config(),observedRevision,journey,
                rotation,thread?rotation.requestGeneration():-1,McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),ACTIVE.get(client),registered,parent.isEnabled(),usable);
            lastObservedSample=observed;return observed;
        }
        public boolean current(){
            if(!client.isSameThread()||identity==null||guiRetirement!=null||lifecycleRoot==null||originalLifecycle.value()==null||!lifecycleRoot.lifecycleCurrent(originalLifecycle.value()))return false;
            Object collection=dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(client,parent,this);if(collection==null)return false;
            if(issuedReturn!=null){var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();if(life==null||!life.known()||!issuedReturnCurrent())return false;
                var state=sample();boolean accepted=identity.current(state)||refreshSourceReceipt()&&identity.current(state=sample());
                return accepted&&issuedReturnCurrent()&&issuedReturnPublicationCurrent(state)&&root.lifecycleCurrent(life)&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())&&loadedPositiveSourceCurrent();}
            if(maintenance==null){var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();if(lifecycle==null||!lifecycle.known())return false;
                var s=sample();boolean accepted=identity.current(s)||refreshSourceReceipt()&&identity.current(s=sample());
                return accepted&&dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,parent)
                    &&dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,parent,this,collection,System.nanoTime()/1_000_000)
                    &&nativeCurrent(s)&&root.lifecycleCurrent(lifecycle)&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())&&loadedPositiveSourceCurrent();
            }
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return false;var s=sample();
                boolean qualified=identity.current(s)||refreshSourceReceipt()&&identity.current(s=sample());
                return qualified&&roleCurrent(false)&&nativeCurrent(s)&&root.lifecycleCurrent(lifecycle)
                    &&dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,parent)
                    &&dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,parent,this,collection,System.nanoTime()/1_000_000)
                    &&nativeCurrent(s)&&root.lifecycleCurrent(lifecycle)&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())&&loadedPositiveSourceCurrent();
            }catch(RuntimeException unavailable){return false;}
        }
        private boolean nativeCurrent(LegacyTravelPublicationIdentity.State s){return s!=null&&client.player==s.player()&&client.level==s.world()
            &&client.getConnection()==s.connection()&&VeltraphBootstrap.moduleManager()==s.root()&&VeltraphBootstrap.config()==s.config()
            &&ACTIVE.get(client)==s.activeLeg()&&rotation.requestGeneration()==s.requestGeneration()&&McOwnedRotation.epoch(client)==s.epoch()
            &&McOwnedRotation.publicationSerial(client)==s.publicationSerial();}
        /** Same-store policy revocation allows cleanup only; absent authority requires an actually disabled captured parent. */
        public boolean cleanupCurrent(){
            if(identity==null||!client.isSameThread()||!originalCleanupLifecycle()||!loadedCleanupSourceCurrent())return false;
            if(guiRetirement!=null)return guiCleanupSample()!=null;
            if(issuedReturn!=null){var state=sample();return identity.cleanupCurrent(state)&&issuedReturnCleanupCurrent()&&nativeCurrent(state)&&originalCleanupLifecycle();}
            if(maintenance==null)return identity.cleanupCurrent(sample())&&originalCleanupLifecycle();
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return false;var s=sample();
                return identity.cleanupCurrent(s)&&roleCurrent(true)&&nativeCurrent(s)&&root.lifecycleCurrent(lifecycle)&&originalCleanupLifecycle();
            }catch(RuntimeException unavailable){return false;}
        }
        /** C5 cancellation roles grant keys/camera only, so Farm gear restitution requires original gameplay authority. */
        public boolean inventoryCleanupCurrent(){return issuedReturn==null&&guiRetirement==null&&(maintenance==null?cleanupCurrent():current());}
        public Object parent(){return parent;}public long journeyGeneration(){return journey;}
        public long cameraEpoch(){return identity==null?-1:identity.epoch();}
        public long requestGeneration(){return identity==null?-1:identity.generation();}
        public long actualTicket(){return identity==null?0:identity.ticket();}
        public DeviceUseReceipt.Context deviceContext(){return issuedReturn==null&&current()?new DeviceUseReceipt.Context(cameraAuthority(),captured.player(),captured.world(),captured.connection(),captured.config(),captured.revision(),journey,cameraEpoch()):null;}
        /** Actual return-device admission adds source identity; ordinary deviceContext remains unchanged. */
        public DeviceUseReceipt.Context deviceContextIssued(McSharedMovementNavigator.ReturnDeviceAdmission actual){
            if(!issuedDeviceSourceCurrent(actual)||!current())return null;
            var context=new DeviceUseReceipt.Context(cameraAuthority(),captured.player(),captured.world(),captured.connection(),captured.config(),captured.revision(),journey,cameraEpoch());
            return issuedDeviceSourceCurrent(actual)?context:null;
        }
        /** Exact source loan only; ordinary inventory cleanup remains unavailable to issued callers. */
        public boolean inventoryCleanupCurrentIssued(McSharedMovementNavigator.ReturnDeviceAdmission actual){
            if(actual==null||issuedReturn==null||issuedReturnRequest==null||actual.legIdentity()!=this
                ||actual.requestIdentity()!=issuedReturnRequest||maintenance!=null||!actual.cleanupSourceCurrent())return false;
            var transit=issuedReturnRequest.currentDeviceTransitIdentity();
            if(transit==null||transit.admissionIdentity()!=actual||transit.originalLegIdentity()!=this||!transit.cleanupCurrent())return false;
            var custody=issuedReturnRequest.sourceHeader().retainedDeviceSlotCustody(transit);
            boolean slot=transit.originalSlot()==actual.deviceSlot()?client.player.getInventory().getSelectedSlot()==actual.deviceSlot()
                :custody!=null&&(custody.readyCurrent()||custody.restoredCurrent());
            return slot&&guardedSample(true)!=null&&transit.cleanupCurrent()&&actual.cleanupSourceCurrent();
        }
        /** Raw actual new-Leg fields only; never calls its continuation current method recursively. */
        public boolean issuedReturnEntrySourceFieldsCurrent(dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.FinalEntryContinuation exact,
                dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request,McSharedMovementNavigator shared,Vec3 goal,double radius){
            return exact!=null&&entrySource==exact&&issuedReturnRequest==request&&entryShared==shared&&entryGoal!=null&&entryGoal.equals(goal)
                &&Double.doubleToLongBits(entryRadius)==Double.doubleToLongBits(radius)&&entryInstruction!=null
                &&issuedReturn!=null&&maintenance==null&&parent==request.driverIdentity()&&ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN
                &&ownedUseGoal==entryGoal&&(exact.loadedSegmentIdentity()==null?loadedIssuedSegment==null&&goal.equals(request.targetPosition())
                    :loadedIssuedSegment==exact.loadedSegmentIdentity()&&loadedSource==loadedIssuedSegment.sourceIdentity()
                        &&goal.equals(loadedIssuedSegment.segmentPosition())&&Double.doubleToLongBits(radius)==Double.doubleToLongBits(loadedSource.terminalRadius()))
                &&request.ownsSharedChild(shared)
                &&request.ownsPublicationLease(LegacyTravelPublicationLease.this,shared)
                &&request.currentFinalEntryInstructionCurrent(exact,entryInstruction,shared,goal,radius)
                &&client.isSameThread()&&identity!=null&&captured!=null&&retirement==null&&guiRetirement==null
                &&ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion
                &&VeltraphBootstrap.moduleManager()==captured.root()&&VeltraphBootstrap.config()==config&&captured.config()==config
                &&client.player==captured.player()&&client.level==captured.world()&&client.getConnection()==captured.connection()
                &&lifecycleRoot!=null&&originalLifecycle.value()!=null
                &&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())&&issuedReturn.nativeCurrent();
        }
        /** Read-only command issue context; it grants no camera ticket or input publication custody. */
        public LegacyTravelPublicationIdentity.State commandContext(){
            if(!current())return null;var observed=sample();return identity.current(observed)?observed:null;
        }
        /** Each OWN publication invokes the real planner and records its actual positive control ticket. */
        public long publishPose(float yaw,float pitch){
            Object instruction=issuedReturnPublicationSource();if(issuedReturn!=null&&instruction==null)return -1;
            if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90||!current())return -1;
            var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(maintenance!=null&&(lifecycle==null||!lifecycle.known()))return -1;
            var before=sample();if(!identity.current(before))return -1;
            Object publication=publicationSnapshot(instruction);if(publication==null)return -1;
            var scope=new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(cameraAuthority(),captured.player(),captured.world(),captured.connection());
            long ticket=McOwnedRotation.planScoped(client,yaw,pitch,scope,()->publicationCurrent(publication),()->true);identity.cameraAttempted(before,ticket);var after=sample();
            if(maintenance!=null&&(!roleCurrent(false)||!nativeCurrent(after)||!root.lifecycleCurrent(lifecycle)))return -1;
            if(!issuedReturnPublicationSourceCurrent(instruction)||!issuedReturnPublicationCurrent(after))return -1;
            boolean accepted=identity.published(new LegacyTravelPublicationIdentity.Publication(before,after,ticket));
            if(accepted)positivePublishedSerial=ticket;return accepted&&dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,parent,this,((PublicationSnapshot)publication).collection,System.nanoTime()/1_000_000)&&nativeCurrent(after)&&root.lifecycleCurrent(lifecycle)?ticket:-1;
        }
        public long publishYaw(float yaw){Object instruction=issuedReturnPublicationSource();if(issuedReturn!=null&&instruction==null||!current())return -1;float pitch=McOwnedRotation.requestedPitch(client);return issuedReturnPublicationSourceCurrent(instruction)?publishPose(yaw,pitch):-1;}
        /** Flight-only refusal reaches the native planner after its last authority provider callback. */
        long publishFlightYaw(float yaw,McFlightNavigator.SupportedLeaf leaf){
            Object instruction=issuedReturnPublicationSource();if(issuedReturn!=null&&instruction==null)return -1;
            if(leaf==null||!current())return -1;float pitch=McOwnedRotation.requestedPitch(client);
            if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90||!current())return -1;
            var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(lifecycle==null||!lifecycle.known())return -1;
            var before=sample();if(!identity.current(before))return -1;
            Object snapshot=publicationSnapshot(instruction);if(snapshot==null)return -1;
            var scope=new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(cameraAuthority(),captured.player(),captured.world(),captured.connection());
            long ticket=McOwnedRotation.planScoped(client,yaw,pitch,scope,()->publicationCurrent(snapshot),leaf::publicationCurrent);
            identity.cameraAttempted(before,ticket);var after=sample();
            if(maintenance!=null&&(!roleCurrent(false)||!nativeCurrent(after)||!root.lifecycleCurrent(lifecycle)))return -1;
            if(!issuedReturnPublicationSourceCurrent(instruction)||!issuedReturnPublicationCurrent(after))return -1;
            boolean accepted=identity.published(new LegacyTravelPublicationIdentity.Publication(before,after,ticket));
            if(accepted)positivePublishedSerial=ticket;return accepted?ticket:-1;
        }
        /** Package-private typed flight veto cannot replace this exact Leg's actual scoped key receipt. */
        boolean setFlightKey(LegacyTravelRetirement.Key key,boolean held,McFlightNavigator.SupportedLeaf leaf){
            return leaf!=null&&input(key,held,leaf);
        }
        /** Preserve bounded legacy tracking while publishing through the lease's actual-ticket path. */
        public long publishTracked(float yaw,float pitch){
            Object instruction=issuedReturnPublicationSource();if(issuedReturn!=null&&instruction==null)return -1;
            if(!current()||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)return -1;
            var p=client.player;int tick=p.tickCount;if(lastTrackTick==tick)return actualTicket();
            if(lastTrackTick==Integer.MIN_VALUE||tick-lastTrackTick>2){yawHeading.reset();pitchHeading.reset();}
            double speed=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);if(!Double.isFinite(speed)||speed<=0)return -1;
            float nextYaw=yawHeading.step(p.getYRot(),yaw,speed),nextPitch=pitchHeading.stepLinear(p.getXRot(),pitch,speed);
            if(!current()||!issuedReturnPublicationSourceCurrent(instruction))return -1;long ticket=publishPose(nextYaw,nextPitch);if(ticket>0)lastTrackTick=tick;return ticket;
        }
        private final class IssuedDeviceQueue {
            final McSharedMovementNavigator.ReturnDeviceAdmission admission;
            final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceQueueContinuation continuation;
            final Runnable completed;
            final dev.veltraph.client.navigation.IssuedReturnDeviceQueueState state;
            final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            final ModuleManager root=lifecycleRoot;
            final Object options=client.options,gameMode=client.gameMode,inventory=client.player.getInventory();
            final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input=ScriptingInputLock.controlSnapshot();
            final net.minecraft.client.KeyMapping[] originalKeys={client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,
                client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse};
            final Object[] unownedKeys=new Object[9],keyBindings=new Object[9];
            final Object mouse=client.mouseHandler,mouseHistory;
            final McOwnedRotation.InstallationSnapshot installation=McOwnedRotation.installationSnapshot(client);
            final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection=
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
            final long version=admittedVersion;final Vec3 goal=ownedUseGoal;
            McRotationService.CleanupReceipt originalReceipt;
            boolean revoked;
            private IssuedDeviceQueue(McSharedMovementNavigator.ReturnDeviceAdmission admission,
                    dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceQueueContinuation continuation,
                    LegacyTravelPublicationIdentity.State before,Runnable completed){
                this.admission=admission;this.continuation=continuation;this.completed=completed;
                state=new dev.veltraph.client.navigation.IssuedReturnDeviceQueueState(this,before.requestGeneration(),before.epoch(),before.publicationSerial(),
                    admission.deviceStartedAtMillis(),continuation.deadlineMillis());
                for(int i=0;i<9;i++){
                    var key=originalKeys[i];
                    if(key!=null&&(key.getClass()==net.minecraft.client.KeyMapping.class||key.getClass()==net.minecraft.client.ToggleKeyMapping.class)
                        &&key instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier)unownedKeys[i]=carrier.normalIdleUnownedSnapshot();
                    keyBindings[i]=dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.snapshot(key);
                }
                mouseHistory=mouse!=null&&mouse.getClass()==net.minecraft.client.MouseHandler.class
                    &&mouse instanceof dev.veltraph.client.service.mc.NormalIdleMouseCarrier carrier?carrier.normalIdleMouseSnapshot():null;
            }
            /** Original unowned hardware history only. Genuine Root blank setters and proven SDK aim never refresh this baseline. */
            private boolean manualHistoryCurrent(){
                if(client.mouseHandler!=mouse||mouseHistory==null||!(mouse instanceof dev.veltraph.client.service.mc.NormalIdleMouseCarrier carrier)
                    ||!carrier.normalIdleMouseCurrent(mouseHistory))return false;
                var keys=new net.minecraft.client.KeyMapping[]{client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,
                    client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse};
                for(int i=0;i<9;i++)if(keys[i]!=originalKeys[i]||unownedKeys[i]==null||keyBindings[i]==null||keys[i].isDown()
                    ||keys[i].getClass()!=net.minecraft.client.KeyMapping.class&&keys[i].getClass()!=net.minecraft.client.ToggleKeyMapping.class
                    ||!(keys[i] instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier key)||!key.normalIdleUnownedCurrent(unownedKeys[i])
                    ||!dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(keys[i],keyBindings[i]))return false;
                return true;
            }
            /** Typed full source/current-or-retained frame; no Leg.current/receipt refresh recursion. */
            private boolean sourceFieldsCurrent(){return primitiveSourceCurrent()&&continuation.current()&&issuedReturn.nativeCurrent();}
            private boolean primitiveSourceCurrent(){
                return !revoked&&issuedDeviceQueue==this&&originalIdentity==identity&&issuedReturnRequest!=null
                    &&admission.requestIdentity()==issuedReturnRequest&&admission.legIdentity()==Leg.this
                    &&continuation.requestIdentity()==issuedReturnRequest&&continuation.admissionIdentity()==admission
                    &&continuation.legIdentity()==Leg.this&&continuation.metadataCurrent()&&ownedUseGoal==goal
                    &&client.isSameThread()&&ACTIVE.get(client)==Leg.this&&ADMISSIONS.getOrDefault(client,0L)==version&&admittedVersion==version
                    &&VeltraphBootstrap.moduleManager()==root&&originalLifecycle.value()!=null&&root.lifecycleCurrent(originalLifecycle.value())
                    &&client.options==options&&client.gameMode==gameMode&&client.player==captured.player()
                    &&client.level==captured.world()&&client.getConnection()==captured.connection()&&client.player.getInventory()==inventory
                    &&client.player.getInventory().getSelectedSlot()==admission.deviceSlot()&&selection!=null&&selection.current()
                    &&input!=null&&input.current()&&input.active()&&parent.id().equals(ScriptingInputLock.ownerModuleId())
                    &&manualHistoryCurrent()&&installation!=null&&installation.current();
            }
            private boolean current(){return originalReceipt!=null&&state.receiptIdentity()==sourceReceipt&&sourceReceipt!=null
                &&sourceReceipt.generation()==state.generation()&&rotation.requestGeneration()==state.generation()&&sourceFieldsCurrent();}
            /** Only the actual RotationRequest applied callback records completion. It issues no native effect. */
            private void observedActualCompletion(){
                boolean actual=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                    var first=stream.filter(f->f.getDeclaringClass()!=IssuedDeviceQueue.class&&f.getDeclaringClass()!=Leg.class).findFirst().orElse(null);
                    return first!=null&&first.getDeclaringClass()==dev.veltraph.client.rotation.RotationRequest.class&&first.getMethodName().equals("applied");
                });
                var bound=sourceReceipt;var refreshed=bound==null?null:rotation.refreshOwnedReceipt(bound);
                long ticket=bound==null?-1:rotation.ownedPublicationTicket(bound);
                if(!actual||originalReceipt==null||state.receiptIdentity()!=bound||refreshed==null
                    ||refreshed.generation()!=state.generation()||rotation.requestGeneration()!=state.generation()
                    ||refreshed.service()!=rotation||refreshed.authority()!=cameraAuthority()||refreshed.player()!=captured.player()
                    ||refreshed.world()!=captured.world()||refreshed.connection()!=captured.connection()
                    ||ticket<=0||ticket!=McOwnedRotation.publicationSerial(client)||!primitiveSourceCurrent()
                    ||admission.deviceState()!=dev.veltraph.client.movement.device.McInstantTransmission.State.AIMING
                    ||!state.observedCompletion(this,System.currentTimeMillis())){revoked=true;state.revoke();}
            }
        }
        /** Exact real queue lineage alone can substitute for its expired original TICK instruction. */
        private boolean queuedSourceInstructionCurrent(Object exact){
            var actual=issuedDeviceQueue;
            if(actual!=null&&actual.continuation.originalInstructionIdentity()==exact)return actual.current();
            return issuedReturnPublicationSourceCurrent(exact);
        }
        /** Actual canonical SDK queue; generic and nondevice queues remain on their original strict path. */
        public McRotationService.CleanupReceipt queueOwnedIssuedDeviceSource(McSharedMovementNavigator.ReturnDeviceAdmission admission,
                float yaw,float pitch,Runnable completed){
            if(!actualReturnAdapterCaller("queueOwnedSource")||issuedDeviceQueue!=null||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90
                ||!issuedDeviceSourceCurrent(admission)||admission.deviceState()!=dev.veltraph.client.movement.device.McInstantTransmission.State.AIMING
                ||!current())return null;
            var instruction=issuedReturnRequest.publicationSourceSnapshot();
            var continuation=issuedReturnRequest.captureDeviceQueueContinuation(admission,this,instruction);
            if(continuation==null||!continuation.dispatchCurrent())return null;
            var before=sample();if(!identity.current(before)||!identity.sourceAttempted(before))return null;
            var actual=new IssuedDeviceQueue(admission,continuation,before,completed);issuedDeviceQueue=actual;
            if(!actual.sourceFieldsCurrent())return null;
            rotation.rotateTo(yaw,pitch,actual::observedActualCompletion);
            var after=sample();var receipt=rotation.cleanupReceipt(cameraAuthority());
            if(receipt==null||receipt.service()!=rotation||receipt.authority()!=cameraAuthority()||receipt.player()!=captured.player()
                ||receipt.world()!=captured.world()||receipt.connection()!=captured.connection()||receipt.generation()!=after.requestGeneration()
                ||receipt.epoch()!=after.epoch()||!actual.sourceFieldsCurrent()||!continuation.dispatchCurrent()
                ||!issuedReturnPublicationCurrent(after)||!identity.queued(before,after))return null;
            sourceReceipt=receipt;handedSourceReceipt=receipt;issuedReturnQueuedInstruction=instruction;actual.originalReceipt=receipt;
            if(!actual.state.attach(actual,receipt,after.requestGeneration(),after.epoch(),after.publicationSerial(),System.currentTimeMillis())){
                actual.revoked=true;actual.state.revoke();return null;
            }
            return actual.current()?receipt:null;
        }
        /** H2 already accepted this exact real camera receipt into the executor before this notification. */
        public void deliverIssuedDeviceQueueCompletion(McSharedMovementNavigator.ReturnDeviceAdmission admission,McRotationService.CleanupReceipt exact){
            var actual=issuedDeviceQueue;
            if(exact==null||actual==null||actual.admission!=admission||!actualReturnAdapterCaller("deliverQueuedSourceCompletion")
                ||!actual.current()||!actual.continuation.dispatchCurrent()
                ||admission.deviceState()!=dev.veltraph.client.movement.device.McInstantTransmission.State.AIMING
                ||exact!=handedSourceReceipt||exact!=sourceReceipt||!rotation.currentRequest(exact))return;
            long ticket=rotation.ownedPublicationTicket(exact);
            if(ticket<=0||ticket!=identity.ticket()||!actual.current()||!actual.state.deliver(actual,exact,ticket,System.currentTimeMillis()))return;
            if(actual.completed!=null)actual.completed.run(); // Consumed before the genuine callback's reentrant receipt read.
        }
        /** Cached factual queue observation only; no permission, refresh, clock or source provider is invoked. */
        public final class IssuedDeviceQueueObservation {
            private final Object source,callback,instruction;private final McRotationService.CleanupReceipt original,current;
            private final long generation,ticket;private final int refreshes;private final boolean observed,delivered;
            private IssuedDeviceQueueObservation(IssuedDeviceQueue actual){source=actual;callback=actual.completed;instruction=actual.continuation.originalInstructionIdentity();
                original=actual.originalReceipt;current=sourceReceipt;generation=actual.state.generation();ticket=actual.state.ticket();refreshes=actual.state.refreshCount();
                observed=actual.state.completionObserved();delivered=actual.state.completionDelivered();}
            public Object sourceIdentity(){return source;}public Object callbackIdentity(){return callback;}public Object originalInstructionIdentity(){return instruction;}
            public McRotationService.CleanupReceipt originalReceipt(){return original;}public McRotationService.CleanupReceipt currentReceipt(){return current;}
            public long queueGeneration(){return generation;}public long ownedTicket(){return ticket;}public int refreshCount(){return refreshes;}
            public boolean observedCompletion(){return observed;}public boolean deliveredCompletion(){return delivered;}
        }
        public IssuedDeviceQueueObservation issuedDeviceQueueObservation(McSharedMovementNavigator.ReturnDeviceAdmission admission){
            var actual=issuedDeviceQueue;return actual!=null&&actual.admission==admission?new IssuedDeviceQueueObservation(actual):null;
        }
        /** Source lineage is acquired only from this exact leg's synchronous queue call. */
        public boolean queueSourceRotation(float yaw,float pitch,Runnable completed){
            Object instruction=issuedReturnPublicationSource();if(issuedReturn!=null&&instruction==null)return false;
            if(!current())return false;var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(maintenance!=null&&(lifecycle==null||!lifecycle.known()))return false;
            var before=sample();if(!identity.current(before))return false;
            if(!identity.sourceAttempted(before))return false;
            rotation.rotateTo(yaw,pitch,()->{if(current()&&issuedReturnPublicationSourceCurrent(instruction)&&completed!=null)completed.run();});
            var after=sample();var receipt=rotation.cleanupReceipt(cameraAuthority());
            if(receipt==null||receipt.service()!=rotation||receipt.authority()!=cameraAuthority()||receipt.generation()!=after.requestGeneration()||receipt.epoch()!=after.epoch()
                    ||maintenance!=null&&(!roleCurrent(false)||!nativeCurrent(after)||!root.lifecycleCurrent(lifecycle))
                    ||!issuedReturnPublicationSourceCurrent(instruction)||!issuedReturnPublicationCurrent(after)||!identity.queued(before,after))return false;
            advanceDeviceSlotLoan(before.requestGeneration(),before.epoch(),before.publicationSerial(),true);
            advanceNormalReturnSlotLoan(before.requestGeneration(),before.epoch(),before.publicationSerial(),true);
            sourceReceipt=receipt;issuedReturnQueuedInstruction=instruction;return true;
        }
        /** C3 certifies its own actual publication; raw epoch and late cleanup receipts cannot refresh. */
        public boolean refreshSourceReceipt(){
            if(identity==null||sourceReceipt==null||!client.isSameThread())return false;
            var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(maintenance!=null&&(lifecycle==null||!lifecycle.known()))return false;
            var after=sample();var old=sourceReceipt;var refreshed=rotation.refreshOwnedReceipt(old);
            if(refreshed==null||refreshed.generation()!=identity.generation())return false;
            var before=new LegacyTravelPublicationIdentity.State(after.root(),after.parent(),after.inputOwner(),after.cameraAuthority(),after.player(),after.world(),after.connection(),after.config(),after.revision(),after.journey(),after.service(),after.requestGeneration(),identity.epoch(),identity.serial(),after.activeLeg(),after.registered(),after.enabled(),after.usable());
            long ownTicket=rotation.ownedPublicationTicket(old);
            if(maintenance!=null&&(!roleCurrent(false)||!nativeCurrent(after)||!root.lifecycleCurrent(lifecycle)))return false;
            if(!queuedSourceInstructionCurrent(issuedReturnQueuedInstruction)||!issuedReturnPublicationCurrent(after))return false;
            if(!identity.refreshedSource(before,after,refreshed.generation(),refreshed.epoch(),ownTicket))return false;
            advanceDeviceSlotLoan(before.requestGeneration(),before.epoch(),before.publicationSerial(),true);
            advanceNormalReturnSlotLoan(before.requestGeneration(),before.epoch(),before.publicationSerial(),true);
            var queue=issuedDeviceQueue;
            if(queue!=null&&queue.continuation.originalInstructionIdentity()==issuedReturnQueuedInstruction
                &&(!queue.current()||!queue.state.refresh(queue,old,refreshed,refreshed.generation(),after.epoch(),after.publicationSerial(),ownTicket,System.currentTimeMillis())))return false;
            sourceReceipt=refreshed;positivePublishedSerial=identity.ticket();return true;
        }
        public McRotationService.CleanupReceipt queueOwnedSource(float yaw,float pitch,Runnable completed){
            if(!queueSourceRotation(yaw,pitch,completed))return null;handedSourceReceipt=sourceReceipt;return handedSourceReceipt;
        }
        private boolean handedReceipt(McRotationService.CleanupReceipt expected){return expected!=null&&expected==handedSourceReceipt&&sourceReceipt!=null
            &&expected.service()==rotation&&expected.authority()==cameraAuthority()&&expected.generation()==identity.generation()
            &&sourceReceipt.generation()==expected.generation()&&sourceReceipt.authority()==expected.authority();}
        public McRotationService.CleanupReceipt refreshOwnedSource(McRotationService.CleanupReceipt expected){
            if(identity==null||!handedReceipt(expected))return null;
            if(!current())return refreshOwnedSourceForCleanup(expected);
            if(!handedReceipt(expected))return null;
            var actual=rotation.refreshOwnedReceipt(sourceReceipt);if(actual==null||actual.generation()!=identity.generation()||actual.epoch()!=identity.epoch()
                ||!rotation.currentRequest(actual)||!current()||!handedReceipt(expected)||!queuedSourceInstructionCurrent(issuedReturnQueuedInstruction))return null;
            var queue=issuedDeviceQueue;
            if(queue!=null&&queue.continuation.originalInstructionIdentity()==issuedReturnQueuedInstruction){
                long actualTicket=rotation.ownedPublicationTicket(sourceReceipt);
                if(!queue.current()||!queue.state.refresh(queue,sourceReceipt,actual,actual.generation(),actual.epoch(),McOwnedRotation.publicationSerial(client),actualTicket,System.currentTimeMillis()))return null;
            }
            sourceReceipt=actual;handedSourceReceipt=actual;return actual;
        }
        /** Same-store policy revocation does not hide an actual own END publication needed for keys/camera cleanup. */
        public McRotationService.CleanupReceipt refreshOwnedSourceForCleanup(McRotationService.CleanupReceipt expected){
            if(!client.isSameThread()||identity==null||!handedReceipt(expected)||ACTIVE.get(client)!=this||admittedVersion<0
                    ||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return null;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return null;
                long loanGeneration=identity.generation(),loanEpoch=identity.epoch(),loanSerial=identity.serial();
                var old=sourceReceipt;var refreshed=rotation.refreshOwnedReceipt(old);long actualTicket=rotation.ownedPublicationTicket(old);
                if(refreshed==null||refreshed.service()!=rotation||refreshed.authority()!=cameraAuthority()||refreshed.generation()!=identity.generation()
                    ||actualTicket<=0||refreshed.epoch()<identity.epoch()||refreshed.epoch()-identity.epoch()>1)return null;
                var after=sample();
                boolean role=issuedReturn!=null?issuedReturnCleanupCurrent():maintenance==null?root.get(parent.id()).orElse(null)==parent&&root.enabledControllers().size()==1
                    &&root.inputOwner().orElse(null)==parent&&McOwnedRotation.authority(client)==parent
                    :McFarmMaintenanceAuthorityBridge.cancellationCurrent(maintenance,client,config,rotation,refreshed.epoch(),actualTicket,refreshed.generation());
                if(!role)return null;
                // C5 can qualify the actual still-live pair after policy loss even before the old kernel has adopted this own serial.
                if(maintenance!=null&&!after.registered())after=new LegacyTravelPublicationIdentity.State(after.root(),after.parent(),after.inputOwner(),after.cameraAuthority(),after.player(),after.world(),after.connection(),after.config(),after.revision(),after.journey(),after.service(),after.requestGeneration(),after.epoch(),after.publicationSerial(),after.activeLeg(),true,after.enabled(),after.usable());
                if(rotation.ownedPublicationTicket(old)!=actualTicket||rotation.refreshOwnedReceipt(old)==null||!handedReceipt(expected)
                    ||maintenance!=null&&!McFarmMaintenanceAuthorityBridge.cancellationCurrent(maintenance,client,config,rotation,refreshed.epoch(),actualTicket,refreshed.generation())
                    ||issuedReturn!=null&&(!issuedReturnCleanupCurrent()||!issuedReturnQueuedCleanupSourceCurrent())
                    ||issuedReturn==null&&maintenance==null&&(root.get(parent.id()).orElse(null)!=parent||root.enabledControllers().size()!=1||root.inputOwner().orElse(null)!=parent||McOwnedRotation.authority(client)!=parent)
                    ||!nativeCurrent(after)||ACTIVE.get(client)!=this||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion||!root.lifecycleCurrent(lifecycle))return null;
                boolean alreadyOwned=actualTicket==identity.ticket()&&actualTicket==identity.serial()&&refreshed.epoch()==identity.epoch()&&identity.cleanupCurrent(after);
                if(!alreadyOwned&&!identity.refreshedSourceCleanup(after,refreshed.generation(),refreshed.epoch(),actualTicket))return null;
                advanceDeviceSlotLoan(loanGeneration,loanEpoch,loanSerial,true);
                advanceNormalReturnSlotLoan(loanGeneration,loanEpoch,loanSerial,true);
                sourceReceipt=refreshed;handedSourceReceipt=refreshed;positivePublishedSerial=actualTicket;return refreshed;
            }catch(RuntimeException unavailable){return null;}
        }
        public boolean cancelOwnedSource(McRotationService.CleanupReceipt expected){
            if(guiRetirement!=null)return guiRetirement.originalSource()==expected&&guiCleanupSample()!=null;
            if(identity==null||!handedReceipt(expected))return false;
            if(!cleanupCurrent())return retireOwnedSourceForGui(expected)!=null;
            if(!handedReceipt(expected))return false;return cancelCameraOwned();
        }
        /** Cached exact opaque actual request retirement only; this accessor publishes or adopts nothing. */
        public McRotationService.GuiRequestRetirement observedGuiRequestRetirement(){return guiRetirement;}
        /** Consume only the real handed C6 request and its private actual GUI cancellation chain. No gameplay/gear renewal. */
        public McRotationService.GuiRequestRetirement retireOwnedSourceForGui(McRotationService.CleanupReceipt expected){
            if(issuedReturn!=null||!client.isSameThread()||identity==null||guiRetirement!=null||!handedReceipt(expected)||ACTIVE.get(client)!=this
                ||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return null;
            var actual=McOwnedRotation.guiAvailabilityRevocation(client);if(actual==null)return null;
            var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(lifecycle==null||!lifecycle.known()||root!=captured.root()||!guiRoleCurrent(actual)||!root.lifecycleCurrent(lifecycle))return null;
            var originalSource=sourceReceipt;long originalGeneration=identity.generation();
            var retired=rotation.retireGuiAvailability(expected,actual,()->ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion
                &&sourceReceipt==originalSource&&handedReceipt(expected)&&guiRoleCurrent(actual)&&root.lifecycleCurrent(lifecycle));
            if(retired==null||retired.originalSource()!=expected||retired.guiProof()!=actual||retired.beforeGeneration()!=originalGeneration||retired.afterGeneration()!=originalGeneration+1
                ||retired.beforeEpoch()!=retired.afterEpoch()||retired.afterEpoch()!=actual.afterEpoch()||retired.actualTicket()!=actual.actualTicket())return null;
            var proof=retired.currentGuiProof();if(proof==null||!retired.current()||!guiRoleCurrent(proof))return null;
            var state=guiObservedState(proof);if(state==null||!retired.current()||!guiRoleCurrent(proof)||!nativeCurrent(state)
                ||!root.lifecycleCurrent(lifecycle)||ACTIVE.get(client)!=this||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return null;
            if(!identity.retiredGuiCleanup(state,proof.beforeEpoch(),proof.afterEpoch(),retired.actualTicket()))return null;
            guiRetirement=retired;consumedGuiProof=proof;positivePublishedSerial=retired.actualTicket();sourceReceipt=null;handedSourceReceipt=null;return retired;
        }
        /** Authentic live C5 source role plus GUI header; actual camera stays NULL, never copied from a historical token. */
        private boolean guiRoleCurrent(McOwnedRotation.GuiAvailabilityRevocation proof){return issuedReturn==null&&captureGuiRole(proof)!=null;}
        /** Actual callback-free original role/native capture. Neither factory nor final leaf grants gameplay/gear. */
        private final class GuiRoleSnapshot {
            final Leg owner=Leg.this;final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;
            final FarmMaintenanceCoordinator coordinator;final FarmMaintenanceCoordinator.MovementAuthorityView view;
            final McOwnedRotation.GuiAvailabilityRevocation proof;final LegacyPublicationSnapshotIdentity.State reservation;
            final Object player=client.player,world=client.level,connection=client.getConnection(),store=VeltraphBootstrap.config(),options=client.options;
            final long admission=ADMISSIONS.getOrDefault(client,0L),generation=rotation.requestGeneration(),epoch=McOwnedRotation.epoch(client),serial=McOwnedRotation.publicationSerial(client);
            private GuiRoleSnapshot(ModuleManager root,ModuleManager.LifecycleSnapshot lifecycle,FarmMaintenanceCoordinator coordinator,
                    FarmMaintenanceCoordinator.MovementAuthorityView view,McOwnedRotation.GuiAvailabilityRevocation proof){
                this.root=root;this.lifecycle=lifecycle;this.coordinator=coordinator;this.view=view;this.proof=proof;
                reservation=new LegacyPublicationSnapshotIdentity.State(owner,originalIdentity,root,player,world,connection,store,admission,generation,epoch,serial);
            }
            /** No configuration, role, provider, geometry, codec or clock callback may execute here. */
            boolean current(){
                return LegacyPublicationSnapshotIdentity.same(reservation,nativeReservation())&&owner==Leg.this&&identity==originalIdentity&&client.isSameThread()&&VeltraphBootstrap.moduleManager()==root&&root.lifecycleCurrent(lifecycle)
                    &&(coordinator==null?maintenance==null:coordinator==VeltraphBootstrap.farmMaintenance()&&coordinator.movementCancellationStillCurrent(view))
                    &&proof.current()&&client.player==player&&client.level==world&&client.getConnection()==connection&&VeltraphBootstrap.config()==store
                    &&client.options==options&&ACTIVE.get(client)==owner&&ADMISSIONS.getOrDefault(client,0L)==admission&&admission==admittedVersion
                    &&rotation.requestGeneration()==generation&&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==serial;
            }
        }
        private final class GuiCleanupSnapshot {
            final Leg owner=Leg.this;final McRotationService.GuiRequestRetirement exact;final GuiRoleSnapshot role;
            private GuiCleanupSnapshot(McRotationService.GuiRequestRetirement exact,GuiRoleSnapshot role){this.exact=exact;this.role=role;}
        }
        /** Mint only from this leg's exact actual cached cleanup and its original private role/header/AD capture. */
        public Object guiCleanupSnapshot(McRotationService.GuiRequestRetirement exact){
            if(exact==null||exact!=guiRetirement||guiCleanupSample()==null)return null;
            var proof=exact.currentGuiProof();if(proof==null||proof!=consumedGuiProof)return null;
            var role=captureGuiRole(proof);if(role==null)return null;var result=new GuiCleanupSnapshot(exact,role);
            return guiCleanupSnapshotCurrent(exact,result)?result:null;
        }
        /** Final callback-free veto only; never refreshes/adopts a descendant or returns gameplay permission. */
        public boolean guiCleanupSnapshotCurrent(McRotationService.GuiRequestRetirement exact,Object capsule){
            if(!(capsule instanceof LegacyTravelPublicationLease.Leg.GuiCleanupSnapshot actual)||actual.owner!=this||actual.exact!=exact
                ||exact==null||exact!=guiRetirement||!identity.guiCleanupOnly()||exact.currentGuiProof()!=actual.role.proof||consumedGuiProof!=actual.role.proof)return false;
            return actual.role.current()&&identity.generation()==actual.role.generation&&identity.epoch()==actual.role.epoch&&identity.serial()==actual.role.serial;
        }
        private GuiRoleSnapshot captureGuiRole(McOwnedRotation.GuiAvailabilityRevocation proof){
            if(issuedReturn!=null||proof==null||!client.isSameThread()||identity==null||proof.storeIdentity()!=config||proof.registryIdentity()!=captured.root()
                ||!proof.capturedInputActive()||!parent.id().equals(proof.capturedInputOwnerModuleId()))return null;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(root!=captured.root()||lifecycle==null||!lifecycle.known()||root.get(parent.id()).orElse(null)!=parent)return null;
                boolean roles;FarmMaintenanceCoordinator sourceCoordinator=null;FarmMaintenanceCoordinator.MovementAuthorityView sourceView=null;
                if(maintenance!=null){var pair=maintenance.frame().pair();var ctx=maintenance.frame().context();
                    if(!(pair.coordinator() instanceof FarmMaintenanceCoordinator coordinator)||coordinator!=VeltraphBootstrap.farmMaintenance()
                        ||!(pair.helper() instanceof FarmMaintenanceCoordinator.Participant helper)||helper!=parent)return null;
                    var view=coordinator.movementCancellationView(helper,pair.token());if(view==null)return null;
                    if(view.captureIdentity()!=pair.capture()||view.sourcePolicyIdentity()!=pair.sourcePolicy()||view.farm()!=pair.farm()||view.helper()!=pair.helper()
                        ||view.token()!=pair.token()||view.capturedPose()!=pair.capturedPose()||view.restorationWitness()!=pair.restorationWitness()
                        ||view.registryIdentity()!=root||view.configStore()!=config||view.rotationServiceIdentity()!=rotation
                        ||view.player()!=ctx.player()||view.world()!=ctx.world()||view.connection()!=ctx.connection())return null;
                    var farm=view.farm();if(root.get(farm.id()).orElse(null)!=farm)return null;var controllers=root.enabledControllers();Object input=root.inputOwner().orElse(null);
                    roles=controllers.size()==2&&controllers.contains(farm)&&controllers.contains(helper)&&helper.isEnabled()&&!helper.isPassiveObserver()
                        &&helper.ownsGameplayInput()&&farm.isEnabled()&&farm.maintenanceOwnedBy(pair.token())&&input==helper;
                    sourceCoordinator=coordinator;sourceView=view;
                }else {var controllers=root.enabledControllers();Object input=root.inputOwner().orElse(null);
                    roles=parent.isEnabled()&&!parent.isPassiveObserver()&&parent.ownsGameplayInput()&&controllers.size()==1&&controllers.contains(parent)&&input==parent;}
                Object camera=McOwnedRotation.authority(client);
                // After the last callback, only actual private source/header/lifecycle/AD/native identity leaves remain.
                boolean source=maintenance==null||sourceCoordinator.movementCancellationStillCurrent(sourceView)&&sourceCoordinator==VeltraphBootstrap.farmMaintenance();
                if(!roles||camera!=null||!source||!proof.current()||!root.lifecycleCurrent(lifecycle)
                    ||client.player!=captured.player()||client.level!=captured.world()||client.getConnection()!=captured.connection()||VeltraphBootstrap.config()!=captured.config()
                    ||ACTIVE.get(client)!=this||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return null;
                var result=new GuiRoleSnapshot(root,lifecycle,sourceCoordinator,sourceView,proof);
                return result.current()?result:null;
            }catch(RuntimeException unavailable){return null;}
        }
        private LegacyTravelPublicationIdentity.State guiObservedState(McOwnedRotation.GuiAvailabilityRevocation proof){
            long observedRevision=revision.getAsLong();var state=sample();if(state.cameraAuthority()!=null||!guiRoleCurrent(proof))return null;
            return new LegacyTravelPublicationIdentity.State(state.root(),state.parent(),state.inputOwner(),state.cameraAuthority(),state.player(),state.world(),state.connection(),state.config(),observedRevision,
                state.journey(),state.service(),state.requestGeneration(),state.epoch(),state.publicationSerial(),state.activeLeg(),true,state.enabled(),false);
        }
        private LegacyTravelPublicationIdentity.State guiCleanupSample(){
            if(guiRetirement==null||consumedGuiProof==null||!identity.guiCleanupOnly()||!client.isSameThread()||ACTIVE.get(client)!=this
                ||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return null;
            var proof=guiRetirement.currentGuiProof();if(proof==null)return null;
            if(!proof.descendsFrom(consumedGuiProof)||!guiRoleCurrent(proof))return null;var state=guiObservedState(proof);
            if(state==null||!guiRetirement.current()||!guiRoleCurrent(proof)||!nativeCurrent(state)||ACTIVE.get(client)!=this
                ||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return null;
            if(!identity.advancedGuiCleanup(state,consumedGuiProof.afterEpoch(),proof.afterEpoch()))return null;
            consumedGuiProof=proof;return identity.cleanupCurrent(state)?state:null;
        }
        private LegacyTravelPublicationIdentity.State guardedSample(boolean cleanup){
            if(!client.isSameThread()||identity==null||!(cleanup?originalCleanupLifecycle():lifecycleRoot!=null&&originalLifecycle.value()!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())))return null;
            Object collection=cleanup?null:dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(client,parent,this);if(!cleanup&&collection==null)return null;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return null;
                if(guiRetirement!=null)return cleanup?guiCleanupSample():null;
                var s=sample();
                if(!(cleanup?identity.cleanupCurrent(s):identity.current(s)))return null;
                boolean role;
                if(issuedReturn!=null)role=cleanup?issuedReturnCleanupCurrent():issuedReturnCurrent();
                else if(maintenance!=null)role=roleCurrent(cleanup);
                else {int count=root.enabledControllers().size();Object input=root.inputOwner().orElse(null);Object camera=McOwnedRotation.authority(client);boolean enabled=parent.isEnabled();
                    role=root.get(parent.id()).orElse(null)==parent&&(enabled?count==1&&input==parent&&camera==parent:cleanup&&count==0&&input==null&&(camera==parent||camera==null));}
                return role&&nativeCurrent(s)&&ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion&&root.lifecycleCurrent(lifecycle)
                    &&(cleanup||dev.veltraph.client.mining.commission.CommissionModule.nativeTravelCurrent(client,parent))
                    &&(cleanup||dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,parent,this,collection,System.nanoTime()/1_000_000))
                    &&(cleanup||nativeCurrent(s)&&root.lifecycleCurrent(lifecycle))
                    &&(cleanup?originalCleanupLifecycle():lifecycleRoot!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value()))
                    &&(issuedReturn==null||(cleanup?issuedReturnCleanupCurrent():issuedReturnPublicationCurrent(s)))
                    &&(issuedReturn==null||nativeCurrent(s)&&root.lifecycleCurrent(lifecycle)&&originalCleanupLifecycle())
                    &&(cleanup?loadedCleanupSourceCurrent():loadedPositiveSourceCurrent())?s:null;
            }catch(RuntimeException unavailable){return null;}
        }
        private KeyMapping key(LegacyTravelRetirement.Key key){
            if(client.options==null)return null;return switch(key){
                case FORWARD->client.options.keyUp;case BACK->client.options.keyDown;
                case LEFT->client.options.keyLeft;case RIGHT->client.options.keyRight;
                case JUMP->client.options.keyJump;case SNEAK->client.options.keyShift;case SPRINT->client.options.keySprint;
                case ATTACK->client.options.keyAttack;case USE->client.options.keyUse;
            };
        }
        private boolean input(LegacyTravelRetirement.Key key,boolean held){
            return input(key,held,null);
        }
        private boolean input(LegacyTravelRetirement.Key key,boolean held,McFlightNavigator.SupportedLeaf flightLeaf){
            Object instruction=issuedReturnPublicationSource();if(issuedReturn!=null&&instruction==null)return false;
            ImmediateHeldAttempt immediate=issuedReturn!=null&&held?new ImmediateHeldAttempt(key,instruction):null;
            if(!current()){lastInputDecision=key+"_CURRENT_GATE_REFUSED";return false;}var before=guardedSample(false);var mapping=key(key);if(before==null||mapping==null){lastInputDecision=key+"_GUARDED_NATIVE_SCOPE_OR_MAPPING_REFUSED";return false;}
            if(immediate!=null&&!immediate.bindOriginal(before)){lastInputDecision=key+"_ORIGINAL_ATTEMPT_SCOPE_OR_HISTORY_REFUSED";return false;}
            PublicationSnapshot initial=null;Object initialOptions=client.options;
            if(maintenance!=null&&positivePublishedSerial<0&&!identity.cameraPublishedOrQueued()&&keyOnlyInitialSnapshot==null){var snapshot=publicationSnapshot();if(!(snapshot instanceof LegacyTravelPublicationLease.Leg.PublicationSnapshot issued)){lastInputDecision=key+"_INITIAL_OPAQUE_SNAPSHOT_REFUSED";return false;}initial=issued;}
            java.util.function.BooleanSupplier originalFence=()->key(key)==mapping&&guardedSample(false)!=null&&identity.current(before)
                &&(flightLeaf==null||flightLeaf.publicationCurrent())&&issuedReturnPublicationSourceCurrent(instruction);
            McHeldKeys.ScopedHeldReceipt receipt;
            try{receipt=issuedReturn==null?McHeldKeys.setHeldScoped(mapping,held,originalFence)
                :immediate==null?McHeldKeys.setHeldFromObservedScoped(mapping,held,originalFence)
                    :McHeldKeys.setHeldFromObservedScoped(mapping,held,originalFence,immediate);}
            finally{if(immediate!=null)immediate.close();}
            lastNativeKeyReceipt=receipt;
            // True attempts are cached only at the original immediate normal-native-return boundary above.
            // Never manufacture or refresh cleanup custody from a later result after callbacks/retirement.
            if(issuedReturn!=null&&!held&&receipt.normallyReturnedOwnedReleaseAttempt())issuedReturnHeldAttempts.remove(key);
            if(receipt.nativeSetAttempts()>0||receipt.restorationAttempts()>0)identity.inputNativeAttemptRecorded();
            if(maintenance!=null&&positivePublishedSerial<0&&(receipt.confirmed()||receipt.nativeSetAttempts()>0||receipt.restorationAttempts()>0)){
                if(keyOnlyInitialSnapshot==null){keyOnlyInitialSnapshot=initial;keyOnlyOptions=initialOptions;}
                if(keyOnlyInitialSnapshot!=null)actualKeyOnlyReceipt=receipt;
            }
            var after=guardedSample(false);var ownAttempt=issuedReturnHeldAttempts.get(key);
            boolean actualHeldOrigin=issuedReturn==null||!held||receipt.normallyReturnedOwnedHeldAttempt()
                ||ownAttempt!=null&&ownAttempt.current();
            boolean published=receipt.confirmed()&&after!=null&&actualHeldOrigin&&issuedReturnPublicationSourceCurrent(instruction)&&identity.inputPublished(before,after);
            lastInputDecision=key+"_"+(published?"PUBLISHED":"NATIVE_RECEIPT_OR_POST_SCOPE_REFUSED");
            if(published&&receipt.normalIdleHistoryCurrent()&&client.player!=null)
                profileKeys.put(key,issuedReturn==null?new ProfileKeyPublication(receipt,held,client.player.tickCount,client.options):new ProfileKeyPublication(receipt,held,client.player.tickCount,client.options,instruction));
            else profileKeys.remove(key);
            return published;
        }
        /** Read-only native snapshot privately issued by this exact active Leg; no caller state can stand in for it. */
        private final class PublicationSnapshot {
            private final Leg owner=Leg.this;
            private final LegacyTravelPublicationIdentity.Lease originalIdentity;
            private final dev.veltraph.client.core.ModuleManager root;
            private final PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot> lifecycle;
            private final Object player,world,connection,store;
            private final long admission,generation,epoch,serial;
            private final LegacyPublicationSnapshotIdentity.State nativeState;private final Object collection,issuedInstruction;
            private PublicationSnapshot(dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,Object collection,Object instruction){
                this.root=root;this.lifecycle=borrowOriginalLifecycle(root,lifecycle);originalIdentity=identity;this.collection=collection;issuedInstruction=instruction;
                player=captured.player();world=captured.world();connection=captured.connection();store=captured.config();
                admission=admittedVersion;generation=identity.generation();epoch=identity.epoch();serial=identity.serial();
                nativeState=new LegacyPublicationSnapshotIdentity.State(owner,originalIdentity,root,player,world,connection,store,admission,generation,epoch,serial);
            }
        }
        private final class OwnedUseSnapshot {
            final Leg owner=Leg.this;final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            final ModuleManager root;final PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot> lifecycle;
            final Object sourceAuthority,rootControl,collection;final Object normalOwnerRole=nativeNormalOwnerRole;final Vec3 origin;final SharedMovementPolicy.Purpose purpose;final Vec3 goal;
            final dev.veltraph.client.game.mc.McGameContextService nativeContext;
            final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot nativeHeaders;final LegacyPublicationSnapshotIdentity.State reservation;
            private OwnedUseSnapshot(ModuleManager root,ModuleManager.LifecycleSnapshot lifecycle,Object sourceAuthority,dev.veltraph.client.game.mc.McGameContextService nativeContext,dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot nativeHeaders,Object collection){
                this.root=root;this.lifecycle=borrowOriginalLifecycle(root,lifecycle);this.sourceAuthority=sourceAuthority;this.collection=collection;origin=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());purpose=ownedUsePurpose;goal=ownedUseGoal;this.nativeContext=nativeContext;this.nativeHeaders=nativeHeaders;rootControl=VeltraphBootstrap.ownedUseControlSnapshot(maintenance==null?null:VeltraphBootstrap.farmMaintenance());reservation=nativeReservation();
            }
        }
        private final class LoadedMaintenanceUseSource {
            final Leg owner=Leg.this;final McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
            final FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority authority;final Object bridge;
            private LoadedMaintenanceUseSource(McSharedMovementNavigator.NormalReturnDeviceAdmission admission,
                    FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority authority,Object bridge){this.admission=admission;this.authority=authority;this.bridge=bridge;}
            private boolean current(){return owner==Leg.this&&loadedSource!=null&&maintenance!=null
                &&admission.legIdentity()==Leg.this&&admission.loadedSegmentSourceIdentity()==loadedSource
                &&admission.loadedMaintenanceAuthorityIdentity()==authority&&authority.admissionIdentity()==admission
                &&authority.sourceIdentity()==loadedSource&&authority.callIdentity()==loadedSource.returnCallIdentity()
                &&authority.originalInputPublication()!=null&&McFarmMaintenanceAuthorityBridge.loadedReturnOwnedUseSnapshotCurrent(maintenance,client,config,bridge)
                &&loadedPositiveSourceCurrent();}
        }
        private Object ownedUseSnapshotForNormalReturn(McSharedMovementNavigator.NormalReturnDeviceAdmission admission){
            if(loadedSource==null||maintenance==null)return ownedUseSnapshot();
            if(admission==null||admission.legIdentity()!=this||admission.loadedSegmentSourceIdentity()!=loadedSource
                ||!actualReturnAdapterCaller("applyNormalReturnDeviceSlotSelection")&&!actualReturnAdapterCaller("ownedUseSnapshot"))return null;
            var authority=admission.loadedMaintenanceAuthorityIdentity();if(authority==null||authority.admissionIdentity()!=admission)return null;
            Object collection=dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(client,parent,this);
            if(collection==null||!current())return null;
            try{var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
                if(life==null||!life.known()||guardedSample(false)==null
                    ||!(VeltraphBootstrap.gameContext() instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext))return null;
                var headers=nativeContext.ownedUseHeadersSnapshot();if(headers==null)return null;
                var bridge=McFarmMaintenanceAuthorityBridge.captureLoadedReturnOwnedUseSnapshot(maintenance,client,config,authority);
                if(bridge==null||guardedSample(false)==null||!root.lifecycleCurrent(life))return null;
                var source=new LoadedMaintenanceUseSource(admission,authority,bridge);
                var actual=new OwnedUseSnapshot(root,life,source,nativeContext,headers,collection);
                return ownedUseSnapshotCurrent(actual)?actual:null;
            }catch(RuntimeException unavailable){return null;}
        }
        private boolean loadedOwnedUseFinalFieldsCurrent(OwnedUseSnapshot actual,LoadedMaintenanceUseSource source){
            return source!=null&&source.owner==this&&actual.sourceAuthority==source&&actual.owner==this&&actual.originalIdentity==identity
                &&client.isSameThread()&&ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion
                &&VeltraphBootstrap.moduleManager()==actual.root&&actual.root.lifecycleCurrent(actual.lifecycle.value())
                &&LegacyPublicationSnapshotIdentity.same(actual.reservation,nativeReservation())
                &&identity.generation()==actual.reservation.generation()&&identity.epoch()==actual.reservation.epoch()&&identity.serial()==actual.reservation.serial()
                &&rotation.requestGeneration()==identity.generation()&&McOwnedRotation.epoch(client)==identity.epoch()
                &&McOwnedRotation.publicationSerial(client)==identity.serial()&&loadedMaintenanceSourceFieldsCurrent(loadedSource);
        }
        /** Actual ordinary Fairy admission only; private construction can never grant positive use. */
        public final class StandardNormalReturnCleanupSource {
            private final OwnedUseSnapshot ordinary;private final McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
            private final dev.veltraph.client.skills.FairySoulModule fairy;
            private final dev.veltraph.client.skills.FairySoulModule.NativeNormalReturnCleanupAdmission session;
            private VeltraphBootstrap.RootOriginalStandardNormalReturnCleanupAdmission rootSource;
            private NormalReturnSelectedSlotLoan loan;
            private StandardNormalReturnCleanupSource(OwnedUseSnapshot ordinary,McSharedMovementNavigator.NormalReturnDeviceAdmission admission,
                    dev.veltraph.client.skills.FairySoulModule fairy,dev.veltraph.client.skills.FairySoulModule.NativeNormalReturnCleanupAdmission session){
                this.ordinary=ordinary;this.admission=admission;this.fairy=fairy;this.session=session;
            }
            public Leg originalLegIdentity(){return Leg.this;}
            public dev.veltraph.client.skills.FairySoulModule originalOwner(){return fairy;}
            public Object originalOwnedControlIdentity(){return ordinary.rootControl;}
            /** Only Root's actual initial observer reads this genuine already-qualified private ordinary capsule. */
            public boolean positiveAdmissionCurrent(Minecraft exact){return exact==client&&standardNormalReturnCleanupSource==this
                &&parent==fairy&&ordinary.owner==Leg.this&&ordinary.normalOwnerRole==null&&loan==null&&normalReturnSlotLoan==null
                &&ownedUseSnapshotCurrent(ordinary)&&session.initialCurrent(Leg.this)&&admission.nativeSourceCurrent()
                &&ownedUseSnapshotCurrent(ordinary);}
            public boolean rawOriginalLoanCurrent(Minecraft exact){return exact==client&&standardNormalReturnCleanupSource==this
                &&parent==fairy&&ordinary.owner==Leg.this&&ordinary.originalIdentity==identity&&ordinary.normalOwnerRole==null
                &&loan!=null&&loan==normalReturnSlotLoan&&loan.admission==admission&&admission.normalSlotLoanIdentity()==loan&&!loan.lost
                &&session.rawSessionCurrent()&&normalReturnSelectionCustodyRawCurrent(admission,loan.prior,loan.originalHistory);}
            private boolean pendingCurrent(){var root=VeltraphBootstrap.moduleManager();var pending=root==null?null:root.pendingActualOwnDisable(fairy).orElse(null);
                return pending!=null&&rootSource!=null&&session.cleanupCurrent(Leg.this)&&rootSource.pendingCleanupCurrent(this,pending)
                    &&session.cleanupRawCurrent(Leg.this)&&rawOriginalLoanCurrent(client)&&rootSource.rawPendingCurrent(this,pending);}
        }
        /** Active standard-owner cleanup retains the actual unchanged ordinary capsule; it grants no new gameplay capability. */
        private final class OrdinaryNormalReturnCleanupSource {
            private final Leg owner=Leg.this;private final OwnedUseSnapshot ordinary;
            private OrdinaryNormalReturnCleanupSource(OwnedUseSnapshot ordinary){this.ordinary=ordinary;}
            private boolean current(){return owner==Leg.this&&ordinary!=null&&ordinary.owner==Leg.this
                &&ordinary.normalOwnerRole==null&&ordinary.purpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&ordinary.goal==ownedUseGoal
                &&!(parent instanceof dev.veltraph.client.fishing.module.FishingModule)
                &&!(parent instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule)&&ownedUseSnapshotCurrent(ordinary);}
        }
        /** Private old-slot cleanup capsule, deliberately outside OwnedUseSnapshot and every positive factory. */
        private final class OwnedNormalReturnCleanupSnapshot {
            final Leg owner=Leg.this;final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            final ModuleManager root=lifecycleRoot;final ModuleManager.LifecycleSnapshot lifecycle=originalLifecycle.value();
            final Object source;final LegacyPublicationSnapshotIdentity.State reservation=nativeReservation();
            final Object player=captured.player(),world=captured.world(),connection=captured.connection(),store=captured.config();
            final Object options=client.options,gameMode=client.gameMode,inventory=client.player.getInventory(),menu=client.player.inventoryMenu;
            final long admission=admittedVersion;
            private OwnedNormalReturnCleanupSnapshot(Object source){this.source=source;}
        }
        private final class LoadedMaintenanceCleanupSource {
            final Leg owner=Leg.this;final OwnedUseSnapshot original;
            final LoadedMaintenanceUseSource positive;final Object bridge;
            private LoadedMaintenanceCleanupSource(OwnedUseSnapshot original,LoadedMaintenanceUseSource positive,Object bridge){
                this.original=original;this.positive=positive;this.bridge=bridge;
            }
            private boolean current(){return owner==Leg.this&&original.owner==Leg.this&&original.sourceAuthority==positive
                &&positive.owner==Leg.this&&positive.admission.legIdentity()==Leg.this
                &&normalReturnSlotLoan!=null&&normalReturnSlotLoan.selectionCall!=null
                &&normalReturnSlotLoan.selectionCall.ordinary==original&&normalReturnSlotLoan.admission==positive.admission
                &&positive.admission.loadedMaintenanceAuthorityIdentity()==positive.authority
                &&McFarmMaintenanceAuthorityBridge.loadedReturnCleanupSnapshotCurrent(maintenance,client,config,positive.bridge,bridge)
                &&loadedCleanupSourceCurrent();}
        }
        private boolean normalCleanupSourceCurrent(Object exact){
            if(exact instanceof LegacyTravelPublicationLease.Leg.LoadedMaintenanceCleanupSource loaded)return loaded.current();
            if(exact==null||issuedReturn!=null||issuedReturnRequest!=null||maintenance!=null||ownedUsePurpose!=SharedMovementPolicy.Purpose.EXACT_RETURN)return false;
            if(exact instanceof LegacyTravelPublicationLease.Leg.StandardNormalReturnCleanupSource original)
                return original==standardNormalReturnCleanupSource&&original.originalLegIdentity()==this&&original.pendingCurrent();
            if(exact instanceof LegacyTravelPublicationLease.Leg.OrdinaryNormalReturnCleanupSource ordinary)return ordinary.owner==this&&ordinary.current();
            return parent instanceof dev.veltraph.client.fishing.module.FishingModule
                &&exact instanceof dev.veltraph.client.fishing.module.FishingModule.NativeNormalReturnCleanupSource fishing?fishing.belongsTo(this)&&fishing.nativeCurrent()
                :parent instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule
                    &&exact instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule.NativeNormalReturnCleanupSource foraging
                    &&foraging.belongsTo(this)&&foraging.nativeCurrent();
        }
        /** Only the genuine typed restore consumes this private source, never public caller state or a gameplay role waiver. */
        public Object normalReturnCleanupSnapshot(){
            boolean restoreCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==Leg.class&&caller.getMethodName().equals("restoreNormalReturnDeviceSlotLoanOwned");});
            if(!restoreCaller||!client.isSameThread()||identity==null||captured==null||guiRetirement!=null
                ||normalReturnSlotLoan==null||normalReturnSlotLoan.lost||normalReturnSlotLoan.consumed||!originalCleanupLifecycle())return null;
            Object source=null;
            if(loadedSource!=null&&maintenance!=null){
                var selected=normalReturnSlotLoan.selectionCall;var original=selected==null?null:selected.ordinary;
                if(original==null||original.owner!=this||!(original.sourceAuthority instanceof LegacyTravelPublicationLease.Leg.LoadedMaintenanceUseSource positive)
                    ||positive.admission!=normalReturnSlotLoan.admission||positive.admission.loadedMaintenanceAuthorityIdentity()!=positive.authority)return null;
                var bridge=McFarmMaintenanceAuthorityBridge.loadedReturnCleanupSnapshot(maintenance,client,config,positive.bridge);
                if(bridge==null)return null;source=new LoadedMaintenanceCleanupSource(original,positive,bridge);
            }else if(parent instanceof dev.veltraph.client.fishing.module.FishingModule fishing)source=fishing.captureNativeNormalReturnCleanupSource(this);
            else if(parent instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule foraging)source=foraging.captureNativeNormalReturnCleanupSource(this);
            else if(parent instanceof dev.veltraph.client.skills.FairySoulModule&&standardNormalReturnCleanupSource!=null
                &&standardNormalReturnCleanupSource.pendingCurrent())source=standardNormalReturnCleanupSource;
            else {
                // Preserve the original standard factory/current gates. A disabled standard owner cannot qualify here.
                var original=ownedUseSnapshot();
                if(!(original instanceof LegacyTravelPublicationLease.Leg.OwnedUseSnapshot ordinary)||ordinary.owner!=this
                    ||ordinary.normalOwnerRole!=null||!ownedUseSnapshotCurrent(ordinary))return null;
                source=new OrdinaryNormalReturnCleanupSource(ordinary);
            }
            if(!normalCleanupSourceCurrent(source))return null;
            var actual=new OwnedNormalReturnCleanupSnapshot(source);return normalReturnCleanupSnapshotCurrent(actual)?actual:null;
        }
        /** Refusal-only cleanup leaf. It authorizes only the already retained normal-return old selected-slot loan. */
        public boolean normalReturnCleanupSnapshotCurrent(Object capsule){
            if(!(capsule instanceof LegacyTravelPublicationLease.Leg.OwnedNormalReturnCleanupSnapshot actual)||actual.owner!=this
                ||actual.originalIdentity!=identity||guiRetirement!=null||!client.isSameThread()||ACTIVE.get(client)!=this
                ||ADMISSIONS.getOrDefault(client,0L)!=actual.admission||admittedVersion!=actual.admission
                ||actual.root==null||actual.root!=lifecycleRoot||VeltraphBootstrap.moduleManager()!=actual.root
                ||!actual.root.ownCleanupLifecycleCurrent(actual.lifecycle,parent)||!originalCleanupLifecycle()
                ||normalReturnSlotLoan==null||normalReturnSlotLoan.lost||!normalCleanupSourceCurrent(actual.source))return false;
            return LegacyPublicationSnapshotIdentity.same(actual.reservation,nativeReservation())
                &&client.player==actual.player&&client.level==actual.world&&client.getConnection()==actual.connection&&VeltraphBootstrap.config()==actual.store
                &&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.options==actual.options&&client.gameMode==actual.gameMode
                &&client.player.getInventory()==actual.inventory&&client.player.inventoryMenu==actual.menu&&client.player.containerMenu==actual.menu
                &&client.player.inventoryMenu.getCarried().isEmpty()&&client.player.level()==client.level&&client.player.isAlive()
                &&!client.player.isPassenger()&&!client.player.isInLava()&&!client.isPaused()&&client.gui.screen()==null&&client.getCameraEntity()==client.player
                &&!client.player.isScoping()&&!client.level.tickRateManager().isEntityFrozen(client.player)&&McOwnedRotation.installed(client)
                &&(actual.source instanceof LegacyTravelPublicationLease.Leg.LoadedMaintenanceCleanupSource loaded?loaded.current():true);
        }
        private boolean normalReturnSourceCurrent(McSharedMovementNavigator.NormalReturnDeviceAdmission actual){
            return actual!=null&&issuedReturn==null&&issuedReturnRequest==null&&actual.legIdentity()==this
                &&ownedUsePurpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&ownedUseGoal!=null
                &&ownedUseGoal.equals(actual.targetPosition())&&actual.nativeSourceCurrent();
        }
        /** Real ordinary capsule is retained unchanged; exact-return geometry adds a distinct actual source. */
        private final class NormalReturnOwnedUseSnapshot {
            final Leg owner=Leg.this;final McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
            final OwnedUseSnapshot ordinary;final DeviceUseReceipt.Issue issue;
            private NormalReturnOwnedUseSnapshot(McSharedMovementNavigator.NormalReturnDeviceAdmission admission,OwnedUseSnapshot ordinary){
                this.admission=admission;this.ordinary=ordinary;issue=admission.issueIdentity();
            }
        }
        /** Unchanged ordinary factory must qualify before any normal-return source is wrapped. */
        public Object ownedNormalReturnUseSnapshot(McSharedMovementNavigator.NormalReturnDeviceAdmission actual){
            if(!actualReturnAdapterCaller("ownedUseSnapshot")||!normalReturnSourceCurrent(actual)||actual.issueIdentity()==null)return null;
            if(normalReturnUse!=null)return normalReturnUse.admission==actual&&normalReturnUse.issue==actual.issueIdentity()
                &&normalReturnUseFieldsCurrent(normalReturnUse)?normalReturnUse:null;
            var original=ownedUseSnapshotForNormalReturn(actual);
            if(!(original instanceof LegacyTravelPublicationLease.Leg.OwnedUseSnapshot ordinary)||ordinary.owner!=this
                ||!ownedUseSnapshotCurrent(ordinary)||!actual.qualifiedApproachCurrent(ordinary.origin,actual.issueIdentity().goal())
                ||!normalReturnDeviceSlotCurrent(actual))return null;
            var handed=new NormalReturnOwnedUseSnapshot(actual,ordinary);normalReturnUse=handed;
            return normalReturnUseFieldsCurrent(handed)?handed:null;
        }
        private boolean normalReturnUseFieldsCurrent(NormalReturnOwnedUseSnapshot actual){
            return actual!=null&&normalReturnUse==actual&&actual.owner==this&&actual.ordinary.owner==this
                &&actual.issue!=null&&actual.admission.issueIdentity()==actual.issue&&normalReturnSourceCurrent(actual.admission)
                &&actual.ordinary.purpose==SharedMovementPolicy.Purpose.EXACT_RETURN&&actual.ordinary.goal==ownedUseGoal
                &&actual.admission.qualifiedApproachCurrent(actual.ordinary.origin,actual.issue.goal())
                &&normalReturnDeviceSlotCurrent(actual.admission)&&ownedUseSnapshotCurrent(actual.ordinary)
                &&actual.admission.nativeSourceCurrent();
        }
        /** Final-current only after actual Shared handoff; no public Issue or adapter Boolean can seed it. */
        public boolean ownedNormalReturnUseSnapshotCurrent(Object opaque){
            if(!(opaque instanceof LegacyTravelPublicationLease.Leg.NormalReturnOwnedUseSnapshot actual)||!normalReturnUseFieldsCurrent(actual))return false;
            var source=actual.admission.actualOwnedUseSourceIdentity();
            return source!=null&&actual.admission.issueIdentity()==actual.issue
                &&actual.admission.actualOwnedUseSourceIdentity()==source&&normalReturnUseFieldsCurrent(actual);
        }
        /** Private actual loan with original native before-history and exact own receipt adoption. */
        private final class NormalReturnSelectedSlotLoan {
            final Leg owner=Leg.this;final McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
            final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            final ModuleManager root=VeltraphBootstrap.moduleManager();
            final Object player=client.player,world=client.level,connection=client.getConnection(),store=config;
            final Object options=client.options,gameMode=client.gameMode,inventory=client.player.getInventory(),menu=client.player.inventoryMenu;
            final long version=admittedVersion;final int prior,selected;final java.util.List<net.minecraft.world.item.ItemStack> stacks;
            final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory;
            dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selectedHistory;
            McInventoryService.ScopedSlotReceipt selectedReceipt,restoredReceipt;
            NormalReturnSelectionCall selectionCall,restorationCall;
            long generation=identity.generation(),epoch=identity.epoch(),serial=identity.serial();boolean lost,consumed;
            private NormalReturnSelectedSlotLoan(McSharedMovementNavigator.NormalReturnDeviceAdmission admission,int prior,int selected){
                this.admission=admission;this.prior=prior;this.selected=selected;originalHistory=admission.originalSelectionHistory();selectedHistory=originalHistory;
                var copies=new java.util.ArrayList<net.minecraft.world.item.ItemStack>(36);
                for(int i=0;i<36;i++)copies.add(((net.minecraft.world.entity.player.Inventory)inventory).getItem(i).copy());stacks=java.util.List.copyOf(copies);
            }
            private boolean nativeItemsCurrent(){return admission.sourceCurrentStructural()&&passiveNativeItemsCurrent();}
            /** Semantic item comparison may run component equality code; raw identity must be rechecked afterward. */
            private boolean passiveNativeItemsCurrent(){
                if(!passiveNativeIdentityCurrent())return false;
                for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(stacks.get(i),client.player.getInventory().getItem(i))||!passiveNativeIdentityCurrent())return false;
                return passiveNativeIdentityCurrent();
            }
            /** Raw native identity only, with no item component equality or source/owner lookup. */
            private boolean passiveNativeIdentityCurrent(){
                if(lost||owner!=Leg.this||normalReturnSlotLoan!=this||issuedReturn!=null||issuedReturnRequest!=null
                    ||!client.isSameThread()||admission.legIdentity()!=this.owner
                    ||identity!=originalIdentity||VeltraphBootstrap.moduleManager()!=root||VeltraphBootstrap.config()!=store
                    ||ACTIVE.get(client)!=owner||ADMISSIONS.getOrDefault(client,0L)!=version||admittedVersion!=version
                    ||client.player!=player||client.level!=world||client.getConnection()!=connection
                    ||client.options!=options||client.gameMode!=gameMode||client.player.getInventory()!=inventory
                    ||client.player.inventoryMenu!=menu||client.player.containerMenu!=menu||!client.player.inventoryMenu.getCarried().isEmpty()
                    ||client.player.level()!=client.level||client.getCameraEntity()!=client.player||!client.player.isAlive()
                    ||client.player.isPassenger()||client.player.isScoping()||client.isPaused()||client.gui.screen()!=null
                    ||client.level.tickRateManager().isEntityFrozen(client.player))return false;
                return true;
            }
            private boolean passiveLeaf(int slot){return passiveNativeItemsCurrent()&&passiveRawLeaf(slot);}
            private boolean passiveRawLeaf(int slot){return passiveNativeIdentityCurrent()
                &&client.player.getInventory().getSelectedSlot()==slot&&identity.generation()==generation&&identity.epoch()==epoch&&identity.serial()==serial
                &&rotation.requestGeneration()==generation&&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==serial
                &&selectedHistory!=null&&selectedHistory.current();}
            private boolean leaf(int slot,boolean afterOwnSetter){return nativeItemsCurrent()&&passiveRawLeaf(slot);}
            private boolean current(int slot){
                if(lost||consumed)return false;var lifecycle=root.lifecycleSnapshot();
                boolean valid=lifecycle.known()&&guardedSample(true)!=null&&leaf(slot,false)&&root.lifecycleCurrent(lifecycle)&&admission.cleanupSourceCurrent()
                    &&!consumed&&passiveRawLeaf(slot);
                if(!valid)lost=true;return valid;
            }
            private void advance(long oldGeneration,long oldEpoch,long oldSerial,boolean certified){
                if(lost||consumed)return;
                if(!certified||generation!=oldGeneration||epoch!=oldEpoch||serial!=oldSerial||!nativeItemsCurrent()
                    ||selectedHistory==null||!selectedHistory.current()||client.player.getInventory().getSelectedSlot()!=selected){lost=true;return;}
                generation=identity.generation();epoch=identity.epoch();serial=identity.serial();current(selected);
            }
        }
        /** Private one-use normal call. Only canonical Effects.select executes the actual native setter. */
        public final class NormalReturnSelectionCall {
            private final NormalReturnSelectedSlotLoan loan;
            private final McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
            private final OwnedUseSnapshot ordinary;
            private final OwnedNormalReturnCleanupSnapshot cleanup;
            private final McInventoryService service;
            private final boolean restore;private final int before,after;
            private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory beforeHistory;
            private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory returnedHistory;
            private boolean attempted,nativeReturned,completed;
            private NormalReturnSelectionCall(NormalReturnSelectedSlotLoan loan,OwnedUseSnapshot ordinary,McInventoryService service,boolean restore){
                this.loan=loan;admission=loan.admission;this.ordinary=ordinary;cleanup=null;this.service=service;this.restore=restore;
                before=restore?loan.selected:loan.prior;after=restore?loan.prior:loan.selected;beforeHistory=loan.selectedHistory;
            }
            private NormalReturnSelectionCall(NormalReturnSelectedSlotLoan loan,OwnedNormalReturnCleanupSnapshot cleanup,McInventoryService service){
                this.loan=loan;admission=loan.admission;ordinary=null;this.cleanup=cleanup;this.service=service;restore=true;
                before=loan.selected;after=loan.prior;beforeHistory=loan.selectedHistory;
            }
            private boolean association(){return !loan.lost&&normalReturnSlotLoan==loan&&loan.owner==Leg.this
                &&loan.admission==admission&&(restore?cleanup!=null&&cleanup.owner==Leg.this&&ordinary==null:ordinary!=null&&ordinary.owner==Leg.this&&cleanup==null)
                &&(restore?loan.restorationCall==this:loan.selectionCall==this)
                &&(restore?loan.consumed:!loan.consumed);}
            public void executeNativeSetter(McInventoryService exact,net.minecraft.world.entity.player.Inventory actualInventory,int actualSlot){
                boolean canonical=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                    var first=stream.filter(f->f.getDeclaringClass()!=NormalReturnSelectionCall.class).findFirst().orElse(null);
                    return first!=null&&first.getDeclaringClass().isAnonymousClass()
                        &&first.getDeclaringClass().getEnclosingClass()==McInventoryService.class&&first.getMethodName().equals("select");
                });
                if(!canonical||attempted||completed||exact!=service||services.inventory()!=service||actualInventory!=loan.inventory||actualSlot!=after){
                    loan.lost=true;throw new IllegalStateException("Normal return private native setter association refused");
                }
                attempted=true;boolean normal=false;
                try{
                    // All full source/ordinary capsule callbacks precede the final raw/native history leaf.
                    if(!association()||guardedSample(restore)==null||!loan.passiveNativeItemsCurrent()
                        ||!(restore?normalReturnCleanupSnapshotCurrent(cleanup):ownedUseSnapshotCurrent(ordinary))
                        ||!(restore?admission.cleanupSourceCurrent():normalReturnSourceCurrent(admission))
                        ||!association()||!loan.passiveRawLeaf(before)||beforeHistory==null
                        ||!beforeHistory.sameStoredObservation(loan.selectedHistory)||!beforeHistory.current())
                        throw new IllegalStateException("Normal return original ordinary source refused");
                    actualInventory.setSelectedSlot(after);
                    // Capture only the actual native setter normal return, before post-setter callbacks.
                    returnedHistory=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(actualInventory);
                    normal=returnedHistory!=null&&returnedHistory.followsExactlyOneSetter(beforeHistory)
                        &&actualInventory.getSelectedSlot()==after;
                    if(!normal)throw new IllegalStateException("Normal return exact native setter history refused");
                    loan.selectedHistory=returnedHistory;nativeReturned=true;
                }finally{if(!normal)loan.lost=true;}
            }
            /** Called exactly once by the canonical inventory finally; never replaces a native Error. */
            public void complete(McInventoryService.ScopedSlotReceipt receipt){
                try{
                    boolean canonical=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                        var first=stream.filter(f->f.getDeclaringClass()!=NormalReturnSelectionCall.class).findFirst().orElse(null);
                        return first!=null&&first.getDeclaringClass()==McInventoryService.class;
                    });
                    if(!canonical||completed){loan.lost=true;return;}completed=true;
                    if(restore)loan.restoredReceipt=receipt;else loan.selectedReceipt=receipt;
                    if(!association()||receipt==null||!nativeReturned||receipt.expectedSlot()!=before||receipt.requestedSlot()!=after
                        ||!receipt.localSelected()||receipt.nativeSelectionHistory()==null||returnedHistory==null
                        ||!receipt.nativeSelectionHistory().sameStoredObservation(returnedHistory)
                        ||!returnedHistory.followsExactlyOneSetter(beforeHistory)||!loan.passiveLeaf(after)
                        ||!association()||!loan.passiveRawLeaf(after))loan.lost=true;
                    // A real local/no-packet selection retains strict cleanup custody, but cannot become ready.
                    if(restore&&(receipt==null||!receipt.confirmed()||!receipt.packetSubmitted()))loan.lost=true;
                }catch(RuntimeException|Error unavailable){loan.lost=true;}
            }
            /** One-way loss only, including allocation failure before canonical completion. */
            public void loseCustodyAfterCompletionFailure(){loan.lost=true;completed=true;}
        }
        /** Passive slot custody only. Never calls owner/role, sampling or full native admission. */
        public boolean normalReturnSelectionCustodyCurrent(McSharedMovementNavigator.NormalReturnDeviceAdmission actual,int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory){
            if(actual==null||actual.legIdentity()!=this||issuedReturn!=null||issuedReturnRequest!=null||originalHistory==null
                ||actual.originalSlot()!=originalSlot||actual.originalSelectionHistory()==null
                ||!actual.originalSelectionHistory().sameStoredObservation(originalHistory)||!client.isSameThread())return false;
            var loan=normalReturnSlotLoan;
            if(loan==null)return client.player!=null&&client.player.getInventory().getSelectedSlot()==originalSlot&&originalHistory.current();
            if(loan.admission!=actual||loan.prior!=originalSlot||!loan.originalHistory.sameStoredObservation(originalHistory)||loan.lost)return false;
            var call=loan.consumed?loan.restorationCall:loan.selectionCall;
            if(call==null||!call.association())return false;
            if(!call.attempted){boolean current=loan.passiveLeaf(call.before)&&call.association()&&loan.passiveRawLeaf(call.before);if(!current)loan.lost=true;return current;}
            if(!call.nativeReturned){boolean current=!call.completed&&loan.passiveLeaf(call.before)&&call.association()&&loan.passiveRawLeaf(call.before);if(!current)loan.lost=true;return current;}
            if(!call.completed){boolean current=loan.passiveLeaf(call.after)&&call.association()&&loan.passiveRawLeaf(call.after);if(!current)loan.lost=true;return current;}
            var receipt=loan.consumed?loan.restoredReceipt:loan.selectedReceipt;
            boolean exact=receipt!=null&&receipt.localSelected()&&receipt.nativeSelectionHistory()!=null&&call.returnedHistory!=null
                &&receipt.nativeSelectionHistory().sameStoredObservation(call.returnedHistory);
            if(loan.consumed)exact=exact&&receipt.confirmed()&&receipt.packetSubmitted();
            boolean current=exact&&loan.passiveLeaf(call.after)&&call.association()&&loan.passiveRawLeaf(call.after);if(!current)loan.lost=true;return current;
        }
        /** Inert final custody projection only, after caller semantic/source checks; never a new permission. */
        public boolean normalReturnSelectionCustodyRawCurrent(McSharedMovementNavigator.NormalReturnDeviceAdmission actual,int originalSlot,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory){
            if(actual==null||actual.legIdentity()!=this||issuedReturn!=null||issuedReturnRequest!=null||originalHistory==null
                ||actual.originalSlot()!=originalSlot||actual.originalSelectionHistory()==null
                ||!actual.originalSelectionHistory().sameStoredObservation(originalHistory)||!client.isSameThread())return false;
            var loan=normalReturnSlotLoan;
            if(loan==null)return client.player!=null&&client.player.getInventory().getSelectedSlot()==originalSlot&&originalHistory.current();
            if(loan.admission!=actual||loan.prior!=originalSlot||!loan.originalHistory.sameStoredObservation(originalHistory)||loan.lost)return false;
            var call=loan.consumed?loan.restorationCall:loan.selectionCall;
            if(call==null||!call.association())return false;
            if(!call.attempted){boolean current=loan.passiveRawLeaf(call.before)&&call.association()&&loan.passiveRawLeaf(call.before);if(!current)loan.lost=true;return current;}
            if(!call.nativeReturned){boolean current=!call.completed&&loan.passiveRawLeaf(call.before)&&call.association()&&loan.passiveRawLeaf(call.before);if(!current)loan.lost=true;return current;}
            if(!call.completed){boolean current=loan.passiveRawLeaf(call.after)&&call.association()&&loan.passiveRawLeaf(call.after);if(!current)loan.lost=true;return current;}
            var receipt=loan.consumed?loan.restoredReceipt:loan.selectedReceipt;
            boolean exact=receipt!=null&&receipt.localSelected()&&receipt.nativeSelectionHistory()!=null&&call.returnedHistory!=null
                &&receipt.nativeSelectionHistory().sameStoredObservation(call.returnedHistory);
            if(loan.consumed)exact=exact&&receipt.confirmed()&&receipt.packetSubmitted();
            boolean current=exact&&loan.passiveRawLeaf(call.after)&&call.association()&&loan.passiveRawLeaf(call.after);if(!current)loan.lost=true;return current;
        }
        /** Actual original ordinary source must pass before the real native setter can execute. */
        public Object selectNormalReturnDeviceSlotLoanOwned(McSharedMovementNavigator.NormalReturnDeviceAdmission actual,
                int expected,int next,java.util.function.BooleanSupplier beforeFence,java.util.function.BooleanSupplier afterFence){
            if(!actualReturnAdapterCaller("applyNormalReturnDeviceSlotSelection")||!normalReturnSourceCurrent(actual)
                ||normalReturnSlotLoan!=null||expected==next||expected!=actual.originalSlot()||next!=actual.deviceSlot()
                ||expected<0||expected>8||next<0||next>8||beforeFence==null||afterFence==null
                ||actual.normalSlotLoanIdentity()!=null||actual.originalSelectionHistory()==null||!actual.originalSelectionHistory().current()
                ||!(services.inventory() instanceof McInventoryService inventory))return null;
            var original=ownedUseSnapshotForNormalReturn(actual);
            if(!(original instanceof LegacyTravelPublicationLease.Leg.OwnedUseSnapshot ordinary)||ordinary.owner!=this
                ||!ownedUseSnapshotCurrent(ordinary))return null;
            StandardNormalReturnCleanupSource cleanupSource=null;
            if(parent instanceof dev.veltraph.client.skills.FairySoulModule fairy){
                try{var session=fairy.captureNativeNormalReturnCleanupAdmission(this,actual.originalSlot(),actual.originalSelectionHistory());
                    if(session!=null&&session.initialCurrent(this)&&ownedUseSnapshotCurrent(ordinary)){
                        cleanupSource=new StandardNormalReturnCleanupSource(ordinary,actual,fairy,session);standardNormalReturnCleanupSource=cleanupSource;
                        cleanupSource.rootSource=VeltraphBootstrap.captureOriginalStandardNormalReturnCleanupAdmission(cleanupSource);
                        if(cleanupSource.rootSource==null){standardNormalReturnCleanupSource=null;cleanupSource=null;}
                    }
                }catch(RuntimeException|Error unavailable){standardNormalReturnCleanupSource=null;cleanupSource=null;}
            }
            var candidate=new NormalReturnSelectedSlotLoan(actual,expected,next);normalReturnSlotLoan=candidate;
            if(cleanupSource!=null)cleanupSource.loan=candidate;
            // ItemStack.copy may run component code; original raw identities must survive every snapshot visit.
            if(!candidate.passiveRawLeaf(expected)){candidate.lost=true;return candidate;}
            var call=new NormalReturnSelectionCall(candidate,ordinary,inventory,false);candidate.selectionCall=call;
            inventory.selectSlotScoped(expected,next,
                ()->guardedSample(false)!=null&&beforeFence.getAsBoolean()&&candidate.passiveNativeItemsCurrent()&&ownedUseSnapshotCurrent(ordinary)
                    &&actual.nativeSourceCurrent()&&candidate.passiveRawLeaf(expected),
                ()->guardedSample(false)!=null&&afterFence.getAsBoolean()&&candidate.passiveNativeItemsCurrent()&&ownedUseSnapshotCurrent(ordinary)
                    &&actual.nativeSourceCurrent()&&candidate.passiveRawLeaf(next),call);
            if(candidate.selectedReceipt==null||!candidate.selectedReceipt.localSelected()||!candidate.current(next))candidate.lost=true;
            return candidate; // Actual partial local setter custody is retained; it is never successful admission by itself.
        }
        public boolean normalReturnDeviceSlotLoanReady(McSharedMovementNavigator.NormalReturnDeviceAdmission actual,Object exact){
            return exact instanceof LegacyTravelPublicationLease.Leg.NormalReturnSelectedSlotLoan loan&&normalReturnSlotLoan==loan
                &&loan.owner==this&&loan.admission==actual&&loan.selectedReceipt!=null&&loan.selectedReceipt.confirmed()
                &&loan.selectedReceipt.localSelected()&&loan.selectedReceipt.packetSubmitted()&&loan.current(loan.selected);
        }
        /** No-op is only an unchanged actual original history; borrowed slots require the real typed loan. */
        public boolean normalReturnDeviceSlotCurrent(McSharedMovementNavigator.NormalReturnDeviceAdmission actual){
            if(!normalReturnSourceCurrent(actual))return false;
            if(actual.originalSlot()==actual.deviceSlot())return actual.normalSlotLoanIdentity()==null
                &&actual.originalSelectionHistory()!=null&&actual.originalSelectionHistory().current()
                &&client.player.getInventory().getSelectedSlot()==actual.deviceSlot()&&actual.nativeSourceCurrent();
            var loan=actual.normalSlotLoanIdentity();return loan!=null&&normalReturnDeviceSlotLoanReady(actual,loan);
        }
        /** Consume before callbacks; exact returned normal history must follow the genuine selected receipt. */
        public boolean restoreNormalReturnDeviceSlotLoanOwned(McSharedMovementNavigator.NormalReturnDeviceAdmission actual,Object exact){
            if(!actualReturnAdapterCaller("applyNormalReturnDeviceSlotSelection")
                ||!(exact instanceof LegacyTravelPublicationLease.Leg.NormalReturnSelectedSlotLoan loan)||normalReturnSlotLoan!=loan
                ||loan.owner!=this||loan.admission!=actual||actual.normalSlotLoanIdentity()!=loan||loan.selectedReceipt==null
                ||!loan.selectedReceipt.localSelected()||loan.selectedHistory==null||!loan.current(loan.selected)
                ||!(services.inventory() instanceof McInventoryService inventory))return false;
            var original=normalReturnCleanupSnapshot();
            if(!(original instanceof LegacyTravelPublicationLease.Leg.OwnedNormalReturnCleanupSnapshot cleanup)||cleanup.owner!=this
                ||!normalReturnCleanupSnapshotCurrent(cleanup))return false;
            loan.consumed=true;
            var call=new NormalReturnSelectionCall(loan,cleanup,inventory);loan.restorationCall=call;
            var receipt=inventory.selectSlotScoped(loan.selected,loan.prior,
                ()->!loan.lost&&guardedSample(true)!=null&&beforeNormalReturnRestoreCurrent(loan,cleanup,false),
                ()->!loan.lost&&guardedSample(true)!=null&&beforeNormalReturnRestoreCurrent(loan,cleanup,true),call);
            var restored=receipt.nativeSelectionHistory();
            boolean good=!loan.lost&&loan.restoredReceipt==receipt&&receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted()&&restored!=null
                &&restored.followsExactlyOneSetter(call.beforeHistory);
            good=good&&loan.leaf(loan.prior,false)&&guardedSample(true)!=null&&loan.leaf(loan.prior,false);
            if(!good)loan.lost=true;return good;
        }
        private boolean beforeNormalReturnRestoreCurrent(NormalReturnSelectedSlotLoan loan,OwnedNormalReturnCleanupSnapshot original,boolean after){
            return loan.passiveNativeItemsCurrent()&&normalReturnCleanupSnapshotCurrent(original)&&loan.admission.cleanupSourceCurrent()
                &&loan.passiveRawLeaf(after?loan.prior:loan.selected);
        }
        /** Read-only genuine restore result for a future same-source history continuation; never a fresh capture. */
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory normalReturnRestoredSelectionHistory(
                McSharedMovementNavigator.NormalReturnDeviceAdmission actual,Object exact){
            return exact instanceof LegacyTravelPublicationLease.Leg.NormalReturnSelectedSlotLoan loan&&loan.owner==this
                &&normalReturnSlotLoan==loan&&loan.admission==actual&&!loan.lost&&loan.consumed&&loan.restoredReceipt!=null
                &&loan.restoredReceipt.confirmed()&&loan.restoredReceipt.localSelected()&&loan.restoredReceipt.packetSubmitted()
                &&loan.selectedHistory!=null&&loan.selectedHistory.current()&&loan.leaf(loan.prior,false)?loan.selectedHistory:null;
        }
        /** Immutable actual restoration fact; it grants no new Leg, source, admission or slot authority. */
        public final class NormalReturnRestorationWitness {
            private final NormalReturnSelectedSlotLoan loan;
            private final McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
            private final NormalReturnSelectionCall selection,restoration;
            private final McInventoryService.ScopedSlotReceipt selectedReceipt,restoredReceipt;
            private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory,restoredHistory;
            private final int originalSlot;
            private NormalReturnRestorationWitness(NormalReturnSelectedSlotLoan loan){
                this.loan=loan;admission=loan.admission;selection=loan.selectionCall;restoration=loan.restorationCall;
                selectedReceipt=loan.selectedReceipt;restoredReceipt=loan.restoredReceipt;
                originalHistory=loan.originalHistory;restoredHistory=restoration.returnedHistory;originalSlot=loan.prior;
            }
            public Leg originalLegIdentity(){return Leg.this;}
            public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory(){return originalHistory;}
            public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory restoredHistory(){return restoredHistory;}
            public int originalSlot(){return originalSlot;}
            /** Actual captured immutable setter/packet lineage only, including after later genuine own setters. */
            public boolean retainedLineageCurrent(){return loan.owner==Leg.this&&normalReturnSlotLoan==loan&&loan.admission==admission
                &&loan.consumed&&loan.selectionCall==selection&&loan.restorationCall==restoration
                &&loan.selectedReceipt==selectedReceipt&&loan.restoredReceipt==restoredReceipt
                &&loan.originalHistory==originalHistory&&loan.prior==originalSlot
                &&selection!=null&&restoration!=null&&selection.loan==loan&&restoration.loan==loan
                &&selection.admission==admission&&restoration.admission==admission&&!selection.restore&&restoration.restore
                &&selection.before==originalSlot&&selection.after==loan.selected&&restoration.before==loan.selected&&restoration.after==originalSlot
                &&selection.attempted&&selection.nativeReturned&&selection.completed
                &&restoration.attempted&&restoration.nativeReturned&&restoration.completed
                &&selectedReceipt!=null&&selectedReceipt.confirmed()&&selectedReceipt.localSelected()&&selectedReceipt.packetSubmitted()
                &&selectedReceipt.expectedSlot()==originalSlot&&selectedReceipt.requestedSlot()==loan.selected
                &&restoredReceipt!=null&&restoredReceipt.confirmed()&&restoredReceipt.localSelected()&&restoredReceipt.packetSubmitted()
                &&restoredReceipt.expectedSlot()==loan.selected&&restoredReceipt.requestedSlot()==originalSlot
                &&originalHistory!=null&&selection.beforeHistory!=null&&selection.beforeHistory.sameStoredObservation(originalHistory)
                &&selection.returnedHistory!=null&&selectedReceipt.nativeSelectionHistory()!=null
                &&selectedReceipt.nativeSelectionHistory().sameStoredObservation(selection.returnedHistory)
                &&selection.returnedHistory.followsExactlyOneStoredSetterObservation(originalHistory)
                &&restoration.beforeHistory!=null&&restoration.beforeHistory.sameStoredObservation(selection.returnedHistory)
                &&restoredHistory!=null&&restoration.returnedHistory==restoredHistory&&restoredReceipt.nativeSelectionHistory()!=null
                &&restoredReceipt.nativeSelectionHistory().sameStoredObservation(restoredHistory)
                &&restoredHistory.followsExactlyOneStoredSetterObservation(restoration.beforeHistory);}
            /** Semantic content checks precede final raw bindings; retiring the old Leg alone does not change the fact. */
            public boolean rawCurrent(){return normalReturnRestorationNativeFactCurrent(loan)&&loan.owner==Leg.this&&normalReturnSlotLoan==loan&&loan.admission==admission
                &&loan.selectionCall==selection&&loan.restorationCall==restoration
                &&loan.selectedReceipt==selectedReceipt&&loan.restoredReceipt==restoredReceipt
                &&loan.originalHistory==originalHistory&&loan.prior==originalSlot
                &&restoration.returnedHistory==restoredHistory&&normalReturnRestorationNativeIdentityCurrent(loan);}
            /** Inert final witness projection after caller semantic/source checks; no component equality or history capture. */
            public boolean rawProjectionCurrent(){return loan.owner==Leg.this&&normalReturnSlotLoan==loan&&loan.admission==admission
                &&loan.selectionCall==selection&&loan.restorationCall==restoration
                &&loan.selectedReceipt==selectedReceipt&&loan.restoredReceipt==restoredReceipt
                &&loan.originalHistory==originalHistory&&loan.prior==originalSlot
                &&restoration.returnedHistory==restoredHistory&&normalReturnRestorationNativeIdentityCurrent(loan);}
        }
        /** Only the actual canonical confirmed own restoration yields this read-only witness. */
        public NormalReturnRestorationWitness captureNormalReturnRestorationWitness(
                McSharedMovementNavigator.NormalReturnDeviceAdmission actual,Object exactPrivateLoan){
            if(!actualReturnAdapterCaller("applyNormalReturnDeviceSlotSelection")
                ||!(exactPrivateLoan instanceof LegacyTravelPublicationLease.Leg.NormalReturnSelectedSlotLoan loan)
                ||loan.owner!=this||normalReturnSlotLoan!=loan||loan.admission!=actual||actual.normalSlotLoanIdentity()!=loan
                ||!normalReturnRestorationNativeFactCurrent(loan))return null;
            var witness=new NormalReturnRestorationWitness(loan);if(!witness.rawCurrent())return null;
            if(loadedSource!=null)loadedReturnedRestoration=witness;return witness;
        }
        /** Stored real calls/receipts plus raw native leaves; no old ACTIVE/reservation/source lookup. */
        private boolean normalReturnRestorationNativeFactCurrent(NormalReturnSelectedSlotLoan loan){
            if(!normalReturnRestorationNativeIdentityCurrent(loan))return false;
            for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(loan.stacks.get(i),client.player.getInventory().getItem(i))||!normalReturnRestorationNativeIdentityCurrent(loan))return false;
            return normalReturnRestorationNativeIdentityCurrent(loan);
        }
        /** Raw stored call/receipt/native identities only; component equality has already returned. */
        private boolean normalReturnRestorationNativeIdentityCurrent(NormalReturnSelectedSlotLoan loan){
            var selection=loan.selectionCall;var restoration=loan.restorationCall;
            var selected=loan.selectedReceipt;var restored=loan.restoredReceipt;
            if(loan.owner!=this||normalReturnSlotLoan!=loan||loan.lost||!loan.consumed||selection==null||restoration==null
                ||selection.loan!=loan||restoration.loan!=loan||selection.admission!=loan.admission||restoration.admission!=loan.admission
                ||selection.restore||!restoration.restore||!selection.attempted||!selection.nativeReturned||!selection.completed
                ||!restoration.attempted||!restoration.nativeReturned||!restoration.completed
                ||selected==null||!selected.localSelected()||selected.expectedSlot()!=loan.prior||selected.requestedSlot()!=loan.selected
                ||restored==null||!restored.confirmed()||!restored.localSelected()||!restored.packetSubmitted()
                ||restored.expectedSlot()!=loan.selected||restored.requestedSlot()!=loan.prior
                ||loan.originalHistory==null||!selection.beforeHistory.sameStoredObservation(loan.originalHistory)
                ||selection.returnedHistory==null||selected.nativeSelectionHistory()==null
                ||!selected.nativeSelectionHistory().sameStoredObservation(selection.returnedHistory)
                ||!restoration.beforeHistory.sameStoredObservation(selection.returnedHistory)
                ||restoration.returnedHistory==null||restored.nativeSelectionHistory()==null
                ||!restored.nativeSelectionHistory().sameStoredObservation(restoration.returnedHistory)
                ||loan.selectedHistory==null||!loan.selectedHistory.sameStoredObservation(restoration.returnedHistory)
                ||!client.isSameThread()||client.player!=loan.player||client.level!=loan.world||client.getConnection()!=loan.connection
                ||client.player==null||client.level==null||client.getConnection()==null
                ||client.options!=loan.options||client.gameMode!=loan.gameMode||client.player.getInventory()!=loan.inventory
                ||VeltraphBootstrap.moduleManager()!=loan.root||VeltraphBootstrap.config()!=loan.store
                ||client.player.inventoryMenu!=loan.menu||client.player.containerMenu!=loan.menu||!client.player.inventoryMenu.getCarried().isEmpty()
                ||client.player.level()!=client.level||client.getCameraEntity()!=client.player||!client.player.isAlive()
                ||client.player.isPassenger()||client.player.isScoping()||client.isPaused()||client.gui.screen()!=null
                ||client.level.tickRateManager().isEntityFrozen(client.player))return false;
            // Last leaf is the stored own restore history; no current history is captured or adopted here.
            return client.player.getInventory().getSelectedSlot()==loan.prior
                &&restoration.returnedHistory.followsExactlyOneSetter(restoration.beforeHistory);
        }
        private void advanceNormalReturnSlotLoan(long generation,long epoch,long serial,boolean certified){
            if(normalReturnSlotLoan!=null)normalReturnSlotLoan.advance(generation,epoch,serial,certified);
        }
        /** Private factory only from actual active gameplay custody. GUI cleanup snapshots are never interchangeable. */
        private boolean actualReturnAdapterCaller(String... methods){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=Leg.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass().isAnonymousClass()
                &&first.getDeclaringClass().getEnclosingClass()==McSharedMovementNavigator.class&&java.util.Set.of(methods).contains(first.getMethodName());
        });}
        private boolean issuedDeviceSourceCurrent(McSharedMovementNavigator.ReturnDeviceAdmission actual){
            if(actual==null||issuedReturn==null||issuedReturnRequest==null||maintenance!=null||actual.legIdentity()!=this
                ||actual.requestIdentity()!=issuedReturnRequest||actual.sourceHeader()!=issuedReturnRequest.sourceHeader()
                ||ownedUsePurpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||ownedUseGoal==null
                ||!ownedUseGoal.equals(actual.targetPosition())||!actual.nativeSourceCurrent())return false;
            var transit=issuedReturnRequest.currentDeviceTransitIdentity();
            return transit!=null&&transit.admissionIdentity()==actual&&transit.originalLegIdentity()==this
                &&transit.sourceHeader()==issuedReturnRequest.sourceHeader()&&transit.nativeCurrent()
                &&issuedReturnRequest.ownsSharedChild(actual.issuerIdentity())&&issuedReturn.nativeCurrent();
        }
        /** Private issued capsule. No public record, adapter Boolean or ordinary capsule can construct it. */
        private final class OwnedIssuedUseSnapshot {
            final Leg owner=Leg.this;final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            final McSharedMovementNavigator.ReturnDeviceAdmission admission;
            final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest request=issuedReturnRequest;
            final dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit;
            final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.MovementView view;
            final Object instruction,header,rootControl;final DeviceUseReceipt.Issue issue;
            final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;
            final dev.veltraph.client.game.mc.McGameContextService nativeContext;
            final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot nativeHeaders;
            final ScriptingInputLock.ControlSnapshot input;
            final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
            final McOwnedRotation.InstallationSnapshot installation;
            final LegacyPublicationSnapshotIdentity.State reservation;
            final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selection;
            final Object inventory,options=client.options,gameMode=client.gameMode;
            final Vec3 origin,goal=ownedUseGoal;
            private OwnedIssuedUseSnapshot(McSharedMovementNavigator.ReturnDeviceAdmission actual,
                    dev.veltraph.client.game.mc.McGameContextService nativeContext,
                    dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot nativeHeaders,
                    ModuleManager root,ModuleManager.LifecycleSnapshot lifecycle){
                admission=actual;transit=request.currentDeviceTransitIdentity();view=request.currentView();instruction=request.publicationSourceSnapshot();
                header=request.sourceHeader();issue=actual.issueIdentity();this.nativeContext=nativeContext;this.nativeHeaders=nativeHeaders;this.root=root;this.lifecycle=lifecycle;
                rootControl=VeltraphBootstrap.ownedUseControlSnapshot(null);input=ScriptingInputLock.controlSnapshot();
                hardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);installation=McOwnedRotation.installationSnapshot(client);
                inventory=client.player.getInventory();selection=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
                origin=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());reservation=nativeReservation();
            }
        }
        private boolean issuedOwnedUseFieldsCurrent(OwnedIssuedUseSnapshot actual){
            if(actual==null||issuedDeviceUse!=actual||actual.owner!=this||actual.originalIdentity!=identity||guiRetirement!=null
                ||actual.admission.legIdentity()!=this||actual.admission.requestIdentity()!=issuedReturnRequest
                ||actual.request!=issuedReturnRequest||actual.transit==null||actual.transit.admissionIdentity()!=actual.admission
                ||actual.transit.originalLegIdentity()!=this||actual.header!=actual.request.sourceHeader()
                ||actual.issue==null||actual.issue!=actual.admission.issueIdentity()||actual.view==null||actual.view!=actual.request.currentView()
                ||actual.instruction==null||!actual.request.publicationSourceSnapshotCurrent(actual.instruction)
                ||!issuedDeviceSourceCurrent(actual.admission)||!actual.request.nativeCurrent())return false;
            if(VeltraphBootstrap.gameContext()!=actual.nativeContext||!actual.nativeHeaders.current())return false;
            var custody=actual.request.sourceHeader().retainedDeviceSlotCustody(actual.transit);
            if(actual.transit.originalSlot()!=actual.admission.deviceSlot()&&(custody==null||!custody.readyCurrent()))return false;
            return actual.rootControl!=null&&VeltraphBootstrap.ownedUseControlCurrent(actual.rootControl)
                &&actual.input!=null&&actual.input.current()&&actual.input.active()
                &&parent.id().equals(ScriptingInputLock.ownerModuleId())&&actual.hardware!=null&&actual.hardware.current()
                &&actual.installation!=null&&actual.installation.current()&&actual.selection!=null&&actual.selection.current()
                &&client.isSameThread()&&ACTIVE.get(client)==this&&ADMISSIONS.getOrDefault(client,0L)==admittedVersion
                &&VeltraphBootstrap.moduleManager()==actual.root&&actual.root.lifecycleCurrent(actual.lifecycle)
                &&lifecycleRoot==actual.root&&originalLifecycle.value()!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())
                &&LegacyPublicationSnapshotIdentity.same(actual.reservation,nativeReservation())
                &&identity.generation()==actual.reservation.generation()&&identity.epoch()==actual.reservation.epoch()&&identity.serial()==actual.reservation.serial()
                &&client.options==actual.options&&client.gameMode==actual.gameMode&&client.player==captured.player()
                &&client.level==captured.world()&&client.getConnection()==captured.connection()&&client.player.getInventory()==actual.inventory
                &&client.player.getInventory().getSelectedSlot()==actual.admission.deviceSlot()&&actual.request.nativeCurrent()
                &&actual.admission.nativeSourceCurrent()&&issuedReturn.nativeCurrent();
        }
        /** Actual adapter handoff occurs after this private capture; no pre-handoff capsule is final-current. */
        public Object ownedIssuedUseSnapshot(McSharedMovementNavigator.ReturnDeviceAdmission actual){
            if(!actualReturnAdapterCaller("ownedUseSnapshot")||!issuedDeviceSourceCurrent(actual)||actual.issueIdentity()==null||!current())return null;
            if(issuedDeviceUse!=null)return issuedDeviceUse.admission==actual&&issuedDeviceUse.issue==actual.issueIdentity()
                &&issuedOwnedUseFieldsCurrent(issuedDeviceUse)?issuedDeviceUse:null;
            var root=VeltraphBootstrap.moduleManager();var life=root==null?null:root.lifecycleSnapshot();
            if(life==null||!life.known()||guardedSample(false)==null
                ||!(VeltraphBootstrap.gameContext() instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext))return null;
            var headers=nativeContext.ownedUseHeadersSnapshot();if(headers==null||!issuedDeviceSourceCurrent(actual))return null;
            var handed=new OwnedIssuedUseSnapshot(actual,nativeContext,headers,root,life);issuedDeviceUse=handed;
            return issuedOwnedUseFieldsCurrent(handed)?handed:null;
        }
        /** Strict final use authority only after the real private Shared handoff and original request issue binding. */
        public boolean ownedIssuedUseSnapshotCurrent(Object opaque){
            if(!(opaque instanceof LegacyTravelPublicationLease.Leg.OwnedIssuedUseSnapshot actual)||actual.owner!=this
                ||!issuedOwnedUseFieldsCurrent(actual))return false;
            var source=actual.admission.actualOwnedUseSourceIdentity();
            return source!=null&&actual.transit.ownedUseSourceIdentity()==source&&actual.transit.issueIdentity()==actual.issue
                &&actual.transit.ackTransitIdentity()!=null&&actual.transit.nativeIssueCurrent()
                &&actual.admission.actualOwnedUseSourceIdentity()==source&&issuedOwnedUseFieldsCurrent(actual);
        }
        public Object ownedUseSnapshot(){
            if(!client.isSameThread()||identity==null||guiRetirement!=null||issuedReturn!=null)return null;
            Object collection=dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(client,parent,this);if(collection==null||!current())return null;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||guardedSample(false)==null)return null;
                if(!(VeltraphBootstrap.gameContext() instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext))return null;
                var nativeHeaders=nativeContext.ownedUseHeadersSnapshot();if(nativeHeaders==null)return null;
                // No arbitrary overridden role can be qualified from a cached Boolean.
                if(maintenance==null&&(parent.getClass().getMethod("ownsGameplayInput").getDeclaringClass()!=Module.class||parent.getClass().getMethod("isPassiveObserver").getDeclaringClass()!=Module.class)){
                    if(ownedUsePurpose!=SharedMovementPolicy.Purpose.EXACT_RETURN||issuedReturn!=null)return null;
                    if(nativeNormalOwnerRole==null){
                        if(parent instanceof dev.veltraph.client.fishing.module.FishingModule fishing)nativeNormalOwnerRole=fishing.captureNativeNormalReturnSource(this);
                        else if(parent instanceof dev.veltraph.client.foraging.replant.ForagingReplantModule foraging)nativeNormalOwnerRole=foraging.captureNativeNormalReturnSource(this);
                    }
                    if(!normalOwnerRoleCurrent(nativeNormalOwnerRole))return null;
                }
                Object sourceAuthority=maintenance==null?null:McFarmMaintenanceAuthorityBridge.ownedUseSnapshot(maintenance,client,config);
                if(maintenance!=null&&sourceAuthority==null)return null;
                var state=guardedSample(false);if(state==null||!root.lifecycleCurrent(lifecycle))return null;
                var actual=new OwnedUseSnapshot(root,lifecycle,sourceAuthority,nativeContext,nativeHeaders,collection);return ownedUseSnapshotCurrent(actual)?actual:null;
            }catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
        }
        /** Final private/source/native veto: no callback-bearing sampling, role/provider/config getter or clock. */
        public boolean ownedUseSnapshotCurrent(Object capsule){
            if(!(capsule instanceof LegacyTravelPublicationLease.Leg.OwnedUseSnapshot actual)||actual.owner!=this||actual.originalIdentity!=identity
                    ||guiRetirement!=null||!client.isSameThread()||ACTIVE.get(client)!=this||ADMISSIONS.getOrDefault(client,0L)!=admittedVersion)return false;
            if(actual.normalOwnerRole!=null&&!normalOwnerRoleCurrent(actual.normalOwnerRole))return false;
            if(VeltraphBootstrap.gameContext()!=actual.nativeContext||!actual.nativeHeaders.current())return false;
            if(maintenance!=null){
                if(actual.sourceAuthority instanceof LegacyTravelPublicationLease.Leg.LoadedMaintenanceUseSource loaded){if(!loaded.current())return false;}
                else if(!McFarmMaintenanceAuthorityBridge.ownedUseSnapshotCurrent(maintenance,client,config,actual.sourceAuthority))return false;
            }
            if(maintenance==null)for(var module:actual.root.all())if(!(module instanceof dev.veltraph.client.core.AbstractModule stored)||stored.isEnabled()&&module!=parent)return false;
            boolean eligible=actual.rootControl!=null&&VeltraphBootstrap.ownedUseControlCurrent(actual.rootControl)
                &&actual.collection!=null&&dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,parent,this,actual.collection,System.nanoTime()/1_000_000)
                &&VeltraphBootstrap.moduleManager()==actual.root&&actual.root.lifecycleCurrent(actual.lifecycle.value())&&lifecycleRoot!=null&&originalLifecycle.value()!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())
                &&LegacyPublicationSnapshotIdentity.same(actual.reservation,nativeReservation())
                &&identity.generation()==actual.reservation.generation()&&identity.epoch()==actual.reservation.epoch()&&identity.serial()==actual.reservation.serial()
                &&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.level()==client.level&&client.player.isAlive()
                &&!client.player.isPassenger()&&!client.player.isInLava()&&!client.isPaused()&&client.gui.screen()==null&&client.getCameraEntity()==client.player
                &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()&&!client.player.isScoping()
                &&!client.level.tickRateManager().isEntityFrozen(client.player)&&McOwnedRotation.installed(client);
            if(actual.sourceAuthority instanceof LegacyTravelPublicationLease.Leg.LoadedMaintenanceUseSource loaded)
                return eligible&&loaded.current()&&loadedOwnedUseFinalFieldsCurrent(actual,loaded);
            return eligible&&loadedPositiveSourceCurrent();
        }
        public Object publicationSnapshot(){return publicationSnapshot(issuedReturnPublicationSource());}
        private Object publicationSnapshot(Object instruction){
            if(issuedReturn!=null&&(instruction==null||!issuedReturnPublicationSourceCurrent(instruction)))return null;
            if(!client.isSameThread()||identity==null||captured==null)return null;
            Object collection=dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshot(client,parent,this);if(collection==null)return null;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||!current()||guardedSample(false)==null)return null;
                var snapshot=new PublicationSnapshot(root,lifecycle,collection,instruction);return publicationCurrent(snapshot)?snapshot:null;
            }catch(RuntimeException unavailable){return null;}
        }
        /** Callback-free final leaf only: no config, role, provider, camera-authority supplier or fresh receipt reads. */
        public boolean publicationCurrent(Object exactSnapshot){
            if(!client.isSameThread()||!(exactSnapshot instanceof LegacyTravelPublicationLease.Leg.PublicationSnapshot snapshot)||snapshot.owner!=this
                ||identity==null||identity!=snapshot.originalIdentity||admittedVersion!=snapshot.admission
                ||identity.generation()!=snapshot.generation||identity.epoch()!=snapshot.epoch||identity.serial()!=snapshot.serial)return false;
            var actual=new LegacyPublicationSnapshotIdentity.State(ACTIVE.get(client),identity,VeltraphBootstrap.moduleManager(),client.player,client.level,client.getConnection(),
                VeltraphBootstrap.config(),ADMISSIONS.getOrDefault(client,0L),rotation.requestGeneration(),McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client));
            return snapshot.collection!=null&&dev.veltraph.client.mining.vein.VeinMiningModule.nativeCollectionTravelSnapshotCurrent(client,parent,this,snapshot.collection,System.nanoTime()/1_000_000)
                &&LegacyPublicationSnapshotIdentity.same(snapshot.nativeState,actual)&&snapshot.root.lifecycleCurrent(snapshot.lifecycle.value())&&lifecycleRoot!=null&&originalLifecycle.value()!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value())
                &&(issuedReturn==null||issuedReturnPublicationSourceCurrent(snapshot.issuedInstruction)&&LegacyPublicationSnapshotIdentity.same(snapshot.nativeState,nativeReservation())&&snapshot.root.lifecycleCurrent(snapshot.lifecycle.value()));
        }
        /** Already-applied observation only; the request capsule cannot renew publicationCurrent. */
        private boolean publicationObservationCurrent(PublicationSnapshot snapshot,Object observation){
            if(issuedReturn==null||snapshot==null||snapshot.owner!=this||!client.isSameThread()||identity==null
                ||identity!=snapshot.originalIdentity||admittedVersion!=snapshot.admission||identity.generation()!=snapshot.generation
                ||identity.epoch()!=snapshot.epoch||identity.serial()!=snapshot.serial||observation==null)return false;
            if(!issuedReturnRequest.publicationObservationSnapshotCurrent(observation))return false;
            return LegacyPublicationSnapshotIdentity.same(snapshot.nativeState,nativeReservation())
                &&snapshot.root.lifecycleCurrent(snapshot.lifecycle.value())&&lifecycleRoot!=null&&originalLifecycle.value()!=null
                &&lifecycleRoot.lifecycleCurrent(originalLifecycle.value());
        }
        private static final java.util.List<LegacyTravelRetirement.Key> PROFILE_MOVEMENT_KEYS=java.util.List.of(
            LegacyTravelRetirement.Key.FORWARD,LegacyTravelRetirement.Key.BACK,LegacyTravelRetirement.Key.LEFT,
            LegacyTravelRetirement.Key.RIGHT,LegacyTravelRetirement.Key.JUMP,LegacyTravelRetirement.Key.SNEAK,LegacyTravelRetirement.Key.SPRINT);
        /** Opaque observation of seven REAL original scoped key publications, not a new input capability. */
        public final class ProfileInputFrame {
            private final Leg issuer=Leg.this;private final Object actualScope;
            private final java.util.EnumMap<LegacyTravelRetirement.Key,ProfileKeyPublication> actualKeys;
            private final int issuedTick;private final long sequence;
            private final dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope queuedScope;
            private final long queuedTicket,queuedEpoch;
            private McOwnedRotation.QueuedPoseObservation originalQueuedObservation;private DirectionalPoseBinding directionalPoseBinding;
            private AppliedFrameObservation appliedObservation;
            private final class AppliedFrameObservation {
                final GroundProfileMotionCarrier.ObservedStep step;final Object source,nativeAi;
                AppliedFrameObservation(GroundProfileMotionCarrier.ObservedStep step,Object source,Object nativeAi){this.step=step;this.source=source;this.nativeAi=nativeAi;}
            }
            private final java.util.EnumMap<LegacyTravelRetirement.Key,KeyMapping> hardware=new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
            private final java.util.EnumMap<LegacyTravelRetirement.Key,Object> histories=new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
            private final java.util.EnumMap<LegacyTravelRetirement.Key,Boolean> heldHardware=new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
            private ProfileInputFrame(Object scope,int tick,long sequence){actualScope=scope;issuedTick=tick;this.sequence=sequence;
                queuedScope=new dev.veltraph.client.rotation.OwnedRotationTrajectory.Scope(cameraAuthority(),captured.player(),captured.world(),captured.connection());
                queuedTicket=positivePublishedSerial;queuedEpoch=identity.epoch();
                actualKeys=new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
                for(var key:PROFILE_MOVEMENT_KEYS)actualKeys.put(key,profileKeys.get(key));
                for(var name:LegacyTravelRetirement.Key.values()){var mapping=key(name);hardware.put(name,mapping);
                    histories.put(name,mapping instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier?carrier.normalIdleKeySnapshot():null);
                    heldHardware.put(name,mapping!=null&&mapping.isDown());}}
            /** Source classification only; this Boolean grants no action or observation permission. */
            public boolean hasIssuedReturnSource(){return issuedReturn!=null;}
            public int issuedTick(){return issuedTick;}
            public long sequence(){return sequence;}
            public boolean held(LegacyTravelRetirement.Key key){var actual=actualKeys.get(key);return actual!=null&&actual.held();}
            public boolean actualPress(LegacyTravelRetirement.Key key){var actual=actualKeys.get(key);
                return actual!=null&&actual.held()&&actual.actual().nativeSetAttempts()>0&&actual.actual().confirmed();}
            /** Actual current stored scope/history only; deliberately no role/provider/config callback. */
            private boolean nativeInputCurrent(){return nativeInputCurrent(null);}
            private boolean frameScopeCurrent(AppliedFrameObservation observation){return observation==null?publicationCurrent(actualScope)
                :observation==appliedObservation&&publicationObservationCurrent((PublicationSnapshot)actualScope,observation.source);}
            private boolean nativeInputCurrent(AppliedFrameObservation observation){
                if(latestProfileFrame!=this||issuer!=Leg.this||!frameScopeCurrent(observation)||client.player==null
                        ||client.player.tickCount<issuedTick||(long)client.player.tickCount-issuedTick>1)return false;
                for(var key:PROFILE_MOVEMENT_KEYS){var entry=actualKeys.get(key);var mapping=key(key);
                    if(entry==null||entry!=profileKeys.get(key)||entry.nativeTick()!=issuedTick||entry.options()!=client.options
                            ||!entry.actual().confirmed()||!entry.actual().normalIdleHistoryCurrent()
                            ||entry.actual().key()!=mapping||mapping==null||mapping.isDown()!=entry.held()
                            ||issuedReturn!=null&&entry.issuedInstruction()!=((PublicationSnapshot)actualScope).issuedInstruction)return false;}
                for(var name:LegacyTravelRetirement.Key.values()){var mapping=key(name);var snapshot=histories.get(name);
                    if(mapping==null||mapping!=hardware.get(name)||snapshot==null
                            ||!(mapping instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier)
                            ||!carrier.normalIdleKeyCurrent(snapshot)||mapping.isDown()!=heldHardware.get(name))return false;}
                return frameScopeCurrent(observation);
            }
            /** Exact actual mixin handout and its post-travel native endpoint, never a copied observation. */
            private boolean completedNativeStepCurrent(GroundProfileMotionCarrier.ObservedStep exact){
                var p=client.player;
                if(p==null||p.getClass()!=net.minecraft.client.player.LocalPlayer.class||!((Object)p instanceof GroundProfileMotionCarrier motion)
                    ||exact==null||!motion.ownsProfileStep(exact)||motion.profileNativeAfter(exact.nativeIdentity())!=exact
                    ||motion.expectedProfileFrame()!=this||exact.issuedInputs()!=this||exact.before()==null||exact.after()==null
                    ||(long)exact.before().nativeTick()-issuedTick!=1||exact.after().nativeTick()!=exact.before().nativeTick()
                    ||p.tickCount!=exact.after().nativeTick())return false;
                var after=exact.after();var velocity=p.getDeltaMovement();
                return new Vec3(p.getX(),p.getY(),p.getZ()).equals(after.position())
                    &&new Vec3(velocity.x,velocity.y,velocity.z).equals(after.velocity())&&p.onGround()==after.onGround()
                    &&p.isSprinting()==after.sprinting()&&Float.floatToIntBits(p.getYRot())==Float.floatToIntBits(after.yaw())
                    &&Float.floatToIntBits(p.getSpeed())==Float.floatToIntBits(after.travelSpeed());
            }
            /** Called only after the real carrier stores its normally returned step; read-only source attribution. */
            public void recordObservedNativeStep(GroundProfileMotionCarrier.ObservedStep exact){
                if(issuedReturn==null||appliedObservation!=null||!client.isSameThread()||!nativeCurrent()||!completedNativeStepCurrent(exact)
                    ||!((Object)client.player instanceof GroundOwnedNativeTickCarrier tick))return;
                Object nativeAi=tick.nativeOwnedAiStepIdentity();if(nativeAi==null)return;
                var snapshot=(PublicationSnapshot)actualScope;
                Object source=issuedReturnRequest.publicationObservationSnapshot(snapshot.issuedInstruction);
                if(source==null||!issuedReturnRequest.publicationObservationSnapshotCurrent(source)||!nativeCurrent()
                    ||!completedNativeStepCurrent(exact)||tick.nativeOwnedAiStepIdentity()!=nativeAi)return;
                appliedObservation=new AppliedFrameObservation(exact,source,nativeAi);
            }
            /** No new input/camera/frame permission: only the same already-applied genuine native step. */
            public boolean observedNativeCurrent(GroundProfileMotionCarrier.ObservedStep exact){
                if(issuedReturn==null)return nativeCurrent();
                var observation=appliedObservation;
                return observation!=null&&observation.step==exact&&completedNativeStepCurrent(exact)
                    &&nativeInputCurrent(observation)&&completedNativeStepCurrent(exact)
                    &&(directionalPoseBinding==null||directionalPoseBinding.current())
                    &&publicationObservationCurrent((PublicationSnapshot)actualScope,observation.source);
            }
            public boolean observedDirectionalPoseCurrent(GroundProfileMotionCarrier.ObservedStep exact){return issuedReturn==null?directionalPoseCurrent()
                :directionalPoseBinding!=null&&observedNativeCurrent(exact)&&directionalPoseBinding.current();}
            private final class DirectionalPoseBinding {
                final McOwnedRotation.QueuedPoseObservation original;final net.minecraft.client.player.ClientInput input=client.player.input;
                DirectionalPoseBinding(McOwnedRotation.QueuedPoseObservation original){this.original=original;}
                boolean current(){return client.player!=null&&Float.isFinite(client.player.getYRot())&&Float.isFinite(client.player.getXRot())&&Math.abs(client.player.getXRot())<=90&&client.player.input==input&&original.directionalHistoryCurrent()&&(client.player.tickCount==issuedTick?original.nativeCurrent():original.appliedNativeCurrent());}
            }
            /** Original generic frames retain their original semantics; a directional binding only narrows. */
            public boolean nativeCurrent(){return nativeInputCurrent()&&(directionalPoseBinding==null||directionalPoseBinding.current());}
            public boolean bindDirectionalPose(McOwnedRotation.QueuedPoseObservation exact){
                if(directionalPoseBinding!=null)return directionalPoseBinding.original==exact&&nativeCurrent();
                if(exact==null||exact!=originalQueuedObservation||!nativeInputCurrent()||client.player.tickCount!=issuedTick
                    ||client.player.input==null||client.player.input.getClass()!=net.minecraft.client.player.KeyboardInput.class||!exact.nativeCurrent()||!exact.bindDirectionalHistory())return false;
                directionalPoseBinding=new DirectionalPoseBinding(exact);return nativeCurrent();
            }
            /** Callback-free original seven-key/history ownership, separate from failed pose attribution. */
            public boolean directionalInputCurrent(){return directionalPoseBinding!=null&&nativeInputCurrent()&&client.player!=null
                &&client.player.tickCount==issuedTick+1&&client.player.input==directionalPoseBinding.input;}
            public boolean directionalPoseCurrent(){return directionalPoseBinding!=null&&nativeInputCurrent()&&directionalPoseBinding.current();}
            public boolean directionalVectorMatches(net.minecraft.client.player.ClientInput input,net.minecraft.world.phys.Vec2 vector){
                if(vector==null||!directionalInputCurrent()||input!=directionalPoseBinding.input)return false;var actual=input.keyPresses;
                if(actual==null||actual.forward()!=held(LegacyTravelRetirement.Key.FORWARD)||actual.backward()!=held(LegacyTravelRetirement.Key.BACK)
                    ||actual.left()!=held(LegacyTravelRetirement.Key.LEFT)||actual.right()!=held(LegacyTravelRetirement.Key.RIGHT)
                    ||actual.jump()!=held(LegacyTravelRetirement.Key.JUMP)||actual.shift()!=held(LegacyTravelRetirement.Key.SNEAK)||actual.sprint()!=held(LegacyTravelRetirement.Key.SPRINT))return false;
                var expected=new net.minecraft.world.phys.Vec2((held(LegacyTravelRetirement.Key.LEFT)?1:0)-(held(LegacyTravelRetirement.Key.RIGHT)?1:0),
                    (held(LegacyTravelRetirement.Key.FORWARD)?1:0)-(held(LegacyTravelRetirement.Key.BACK)?1:0)).normalized();
                return Float.floatToIntBits(expected.x)==Float.floatToIntBits(vector.x)&&Float.floatToIntBits(expected.y)==Float.floatToIntBits(vector.y)&&directionalInputCurrent();
            }
            /** The actual installed trajectory's stored END, qualified by this privately issued frame and OWN positive ticket. */
            public McOwnedRotation.QueuedPoseObservation ownedQueuedPose(){
                if(!nativeCurrent()||queuedTicket<=0||queuedTicket!=positivePublishedSerial||queuedTicket!=identity.ticket())return null;
                var read=originalQueuedObservation;
                if(read==null){read=McOwnedRotation.readQueuedPose(client,queuedScope,issuedTick,queuedTicket,queuedEpoch);
                    if(read!=null&&read.nativeCurrent()&&nativeCurrent())originalQueuedObservation=read;}
                return read!=null&&read.nativeCurrent()&&nativeCurrent()?read:null;
            }
            /** Unknown active queue is additional refusal only; this does not mint a camera ticket. */
            public boolean unqualifiedQueuedPose(){return nativeCurrent()&&McOwnedRotation.hasQueuedApplication(client)&&ownedQueuedPose()==null;}
            public boolean belongsTo(Leg exact){return issuer==exact;}
        }
        private MovementPublicationObservation latestMovementObservation;
        /** Historical publication only; it is never a movement, use, gear, cleanup or Root admission grant. */
        public final class MovementPublicationObservation {
            private final Leg owner=Leg.this;private final LegacyTravelPublicationIdentity.Lease actualIdentity=identity;
            private final ModuleManager root=VeltraphBootstrap.moduleManager();private final PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot> life=borrowOriginalLifecycle(root,root.lifecycleSnapshot());
            private final LegacyPublicationSnapshotIdentity.State reservation=nativeReservation();
            private final Object options=client.options;private final int tick=client.player.tickCount;
            private final long positiveTicket=positivePublishedSerial;
            private final java.util.EnumMap<LegacyTravelRetirement.Key,ProfileKeyPublication> issued=new java.util.EnumMap<>(LegacyTravelRetirement.Key.class);
            private MovementPublicationObservation(){for(var key:PROFILE_MOVEMENT_KEYS)issued.put(key,profileKeys.get(key));}
            public boolean observedCurrent(){
                if(latestMovementObservation!=this||owner!=Leg.this||actualIdentity!=identity||!client.isSameThread()||client.player==null||options!=client.options
                    ||client.player.tickCount<tick||(long)client.player.tickCount-tick>1||positiveTicket<=0||positiveTicket!=positivePublishedSerial||positiveTicket!=identity.ticket())return false;
                if(!LegacyPublicationSnapshotIdentity.same(reservation,nativeReservation())||!root.lifecycleCurrent(life.value()))return false;
                for(var name:PROFILE_MOVEMENT_KEYS){var receipt=issued.get(name);var mapping=key(name);
                    if(receipt==null||receipt!=profileKeys.get(name)||receipt.nativeTick()!=tick||receipt.options()!=options||!receipt.actual().confirmed()
                        ||receipt.actual().key()!=mapping||mapping==null||mapping.isDown()!=receipt.held()||!receipt.actual().normalIdleHistoryCurrent())return false;}
                return LegacyPublicationSnapshotIdentity.same(reservation,nativeReservation())&&root.lifecycleCurrent(life.value());
            }
        }
        /** Pure handout of existing accepted seven publications; never creates/replaces a ProfileInputFrame. */
        public MovementPublicationObservation observeMovementPublication(){
            if(!client.isSameThread()||identity==null||client.player==null||ACTIVE.get(client)!=this||positivePublishedSerial<=0||identity.ticket()!=positivePublishedSerial)return null;
            var cached=latestMovementObservation;if(cached!=null&&cached.observedCurrent())return cached;
            int tick=client.player.tickCount;
            for(var name:PROFILE_MOVEMENT_KEYS){var receipt=profileKeys.get(name);
                if(receipt==null||receipt.nativeTick()!=tick||receipt.options()!=client.options||!receipt.actual().confirmed()||receipt.actual().key()!=key(name)
                    ||key(name)==null||key(name).isDown()!=receipt.held()||!receipt.actual().normalIdleHistoryCurrent())return null;}
            var root=VeltraphBootstrap.moduleManager();if(root==null||!root.lifecycleSnapshot().known())return null;
            var actual=new MovementPublicationObservation();latestMovementObservation=actual;return actual.observedCurrent()?actual:null;
        }
        /** Only original input() receipts can populate this frame; caller Boolean/state cannot replace them. */
        public ProfileInputFrame profileInputFrame(){
            if(!client.isSameThread()||client.player==null||profileFrameSequence==Long.MAX_VALUE)return null;
            Object snapshot=publicationSnapshot();if(snapshot==null)return null;int tick=client.player.tickCount;
            for(var name:PROFILE_MOVEMENT_KEYS){var entry=profileKeys.get(name);
                if(entry==null||entry.nativeTick()!=tick||entry.options()!=client.options||!entry.actual().confirmed()
                        ||!entry.actual().normalIdleHistoryCurrent()||entry.actual().key()!=key(name)
                        ||key(name)==null||key(name).isDown()!=entry.held()
                        ||issuedReturn!=null&&entry.issuedInstruction()!=((PublicationSnapshot)snapshot).issuedInstruction)return null;}
            var issued=new ProfileInputFrame(snapshot,tick,++profileFrameSequence);latestProfileFrame=issued;
            return issued.nativeCurrent()?issued:null;
        }
        /** Ordinary slot request delegates to the same leaf; additional Device fences can only refuse. */
        /** Real canonical inventory call and journal selection; this method never completes or fabricates a receipt. */
        public boolean selectIssuedDeviceSlotOwned(McSharedMovementNavigator.ReturnDeviceAdmission admission,
                dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual,
                dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.Snapshot.DeviceSlotCustody.SelectionCall selection,
                int expected,int next,java.util.function.BooleanSupplier beforeFence,java.util.function.BooleanSupplier afterFence){
            if(!actualReturnAdapterCaller("applyIssuedDeviceSlotSelection")||admission==null||actual==null||selection==null
                ||issuedReturnRequest==null||actual.transitIdentity().admissionIdentity()!=admission||admission.legIdentity()!=this
                ||actual.expectedSlot()!=expected||actual.requestedSlot()!=next||!actual.belongsTo(issuedReturnRequest.sourceHeader())
                ||!selection.matchesRequestCall(actual)||beforeFence==null||afterFence==null
                ||!(services.inventory() instanceof McInventoryService inventory)||actual.inventoryServiceIdentity()!=inventory
                ||!(actual.cleanupRequested()?actual.cleanupCurrent():actual.nativeCurrent()))return false;
            var custody=issuedReturnRequest.sourceHeader().retainedDeviceSlotCustody(actual.transitIdentity());if(custody==null)return false;
            var receipt=inventory.selectSlotScoped(expected,next,
                ()->guardedSample(actual.cleanupRequested())!=null&&beforeFence.getAsBoolean()&&actual.custodySourceCurrent()
                    &&selection.matchesRequestCall(actual)&&issuedReturnNativeCurrent(),
                ()->guardedSample(actual.cleanupRequested())!=null&&afterFence.getAsBoolean()&&actual.custodySourceCurrent()
                    &&selection.matchesRequestCall(actual)&&issuedReturnNativeCurrent(),selection);
            return receipt!=null&&receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted()
                &&receipt.expectedSlot()==expected&&receipt.requestedSlot()==next&&actual.custodySourceCurrent()
                &&(actual.cleanupRequested()?custody.restoredCurrent():custody.readyCurrent())
                &&services.inventory().selectedSlot()==next&&actual.custodySourceCurrent()&&issuedReturnNativeCurrent();
        }
        public boolean selectSlotOwned(int expectedCurrent,int next){return selectSlotOwned(expectedCurrent,next,()->true,()->true);}
        public boolean selectSlotOwned(int expectedCurrent,int next,java.util.function.BooleanSupplier beforeDeviceFence,java.util.function.BooleanSupplier afterDeviceFence){
            if(issuedReturn!=null||beforeDeviceFence==null||afterDeviceFence==null||!(services.inventory() instanceof McInventoryService inventory))return false;
            Object snapshot=publicationSnapshot();if(snapshot==null)return false;
            var receipt=inventory.selectSlotScoped(expectedCurrent,next,
                ()->guardedSample(false)!=null&&beforeDeviceFence.getAsBoolean()&&publicationCurrent(snapshot),
                ()->guardedSample(false)!=null&&afterDeviceFence.getAsBoolean()&&publicationCurrent(snapshot));
            return receipt.confirmed()&&publicationCurrent(snapshot)&&services.inventory().selectedSlot()==next&&publicationCurrent(snapshot);
        }
        /** Opaque exact-prior-slot loan, available only to the ordinary commission device adapter. */
        private final class SelectedSlotLoan {
            private final Leg owner=Leg.this;private final Object nonce=new Object(),adapterContext;
            private final LegacyTravelPublicationIdentity.Lease originalIdentity=identity;
            private final ModuleManager root=VeltraphBootstrap.moduleManager();
            private final Object originalPlayer=client.player,originalWorld=client.level,originalConnection=client.getConnection();
            private final Object store=VeltraphBootstrap.config(),options=client.options,gameMode=client.gameMode;
            private final Object nativeInventory=client.player.getInventory(),menu=client.player.inventoryMenu;
            private final long admission=admittedVersion;private final int prior,selected;
            private final java.util.List<net.minecraft.world.item.ItemStack> stacks;
            private McInventoryService.ScopedSlotReceipt selectedReceipt;
            private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
            private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory selectedHistory=originalHistory;
            private long generation=identity.generation(),epoch=identity.epoch(),serial=identity.serial();
            private boolean lost,consumed;
            private SelectedSlotLoan(Object adapterContext,int prior,int selected){this.adapterContext=adapterContext;this.prior=prior;this.selected=selected;
                var copies=new java.util.ArrayList<net.minecraft.world.item.ItemStack>(36);
                for(int i=0;i<36;i++)copies.add(client.player.getInventory().getItem(i).copy());stacks=java.util.List.copyOf(copies);
            }
            private boolean nativeItemsCurrent(){
                if(!client.isSameThread()||owner!=Leg.this||nonce==null||adapterContext==null||maintenance!=null
                    ||!(parent instanceof dev.veltraph.client.mining.commission.CommissionModule)
                    ||identity!=originalIdentity||root!=VeltraphBootstrap.moduleManager()||store!=config||store!=VeltraphBootstrap.config()
                    ||ACTIVE.get(client)!=owner||ADMISSIONS.getOrDefault(client,0L)!=admission||admittedVersion!=admission
                    ||client.player!=originalPlayer||client.level!=originalWorld||client.getConnection()!=originalConnection
                    ||client.options!=options||client.gameMode!=gameMode||client.player.getInventory()!=nativeInventory
                    ||client.player.inventoryMenu!=menu||client.player.containerMenu!=menu||!client.player.inventoryMenu.getCarried().isEmpty()
                    ||client.player.level()!=client.level||client.getCameraEntity()!=client.player||!client.player.isAlive()
                    ||client.player.isPassenger()||client.player.isScoping()||client.isPaused()||client.gui.screen()!=null
                    ||client.level.tickRateManager().isEntityFrozen(client.player))return false;
                for(int i=0;i<36;i++)if(!net.minecraft.world.item.ItemStack.matches(stacks.get(i),client.player.getInventory().getItem(i)))return false;
                return true;
            }
            private boolean leaf(int slot){return leaf(slot,false);}
            private boolean leaf(int slot,boolean afterOwnSetter){return nativeItemsCurrent()&&(afterOwnSetter||selectedHistory!=null&&selectedHistory.current())&&client.player.getInventory().getSelectedSlot()==slot
                &&identity.generation()==generation&&identity.epoch()==epoch&&identity.serial()==serial
                &&rotation.requestGeneration()==generation&&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==serial;}
            private boolean current(int slot){
                if(lost||consumed)return false;
                var lifecycle=root.lifecycleSnapshot();
                boolean valid=lifecycle.known()&&guardedSample(true)!=null&&leaf(slot)&&root.lifecycleCurrent(lifecycle);
                if(!valid)lost=true;return valid;
            }
            /** Only a certified mutation executed by this same Leg advances this cleanup lineage. */
            private void advance(long oldGeneration,long oldEpoch,long oldSerial,boolean certified){
                if(lost||consumed)return;
                if(!certified||generation!=oldGeneration||epoch!=oldEpoch||serial!=oldSerial||!nativeItemsCurrent()
                    ||selectedHistory==null||!selectedHistory.current()||client.player.getInventory().getSelectedSlot()!=selected){lost=true;return;}
                generation=identity.generation();epoch=identity.epoch();serial=identity.serial();current(selected);
            }
        }
        /** Initial action retains all ordinary publication gates; supplied fences only veto this native selection. */
        public Object selectDeviceSlotLoanOwned(Object privateAdapterContext,int expectedCurrent,int next,java.util.function.BooleanSupplier beforeDeviceFence,java.util.function.BooleanSupplier afterDeviceFence){
            if(privateAdapterContext==null||maintenance!=null||issuedReturn!=null||!(parent instanceof dev.veltraph.client.mining.commission.CommissionModule)||selectedDeviceSlotLoan!=null
                ||expectedCurrent<0||expectedCurrent>8||next<0||next>8||beforeDeviceFence==null||afterDeviceFence==null
                ||!(services.inventory() instanceof McInventoryService inventory))return null;
            Object snapshot=publicationSnapshot();if(snapshot==null)return null;
            var candidate=new SelectedSlotLoan(privateAdapterContext,expectedCurrent,next);
            if(candidate.originalHistory==null||!candidate.originalHistory.current())return null;
            selectedDeviceSlotLoan=candidate; // Retain pending bookkeeping before the real observer/setter/packet invocation.
            var receipt=inventory.selectSlotScoped(expectedCurrent,next,
                ()->guardedSample(false)!=null&&beforeDeviceFence.getAsBoolean()&&candidate.leaf(expectedCurrent)&&publicationCurrent(snapshot),
                ()->guardedSample(false)!=null&&afterDeviceFence.getAsBoolean()&&candidate.leaf(next,true)&&publicationCurrent(snapshot));
            candidate.selectedReceipt=receipt;candidate.selectedHistory=receipt.nativeSelectionHistory();
            if(!receipt.localSelected()||receipt.expectedSlot()!=expectedCurrent||receipt.requestedSlot()!=next||candidate.selectedHistory==null
                ||!candidate.selectedHistory.followsExactlyOneSetter(candidate.originalHistory)||!candidate.current(next))candidate.lost=true;
            return candidate; // Even partial local selection retains its exact real receipt; it grants no successful device admission.
        }
        public boolean deviceSlotLoanReady(Object privateAdapterContext,Object exactLoan){
            return exactLoan instanceof LegacyTravelPublicationLease.Leg.SelectedSlotLoan loan&&loan.owner==this&&loan.adapterContext==privateAdapterContext
                &&selectedDeviceSlotLoan==loan&&loan.selectedReceipt!=null&&loan.selectedReceipt.confirmed()&&loan.selectedReceipt.localSelected()
                &&loan.selectedReceipt.packetSubmitted()&&loan.current(loan.selected);
        }
        /** No slots, policy revision, permission callback or continuation can be supplied to this cleanup method. */
        public boolean restoreDeviceSlotLoanOwned(Object privateAdapterContext,Object exactLoan){
            if(!(exactLoan instanceof LegacyTravelPublicationLease.Leg.SelectedSlotLoan loan)||loan.owner!=this||loan.adapterContext!=privateAdapterContext||selectedDeviceSlotLoan!=loan
                ||loan.selectedReceipt==null||!loan.selectedReceipt.localSelected()||loan.selectedHistory==null||!(services.inventory() instanceof McInventoryService inventory)
                ||!loan.current(loan.selected))return false;
            loan.consumed=true; // Consume before observer/setter callbacks; failed or reentrant restoration cannot retry.
            var receipt=inventory.selectSlotScoped(loan.selected,loan.prior,
                ()->!loan.lost&&guardedSample(true)!=null&&loan.leaf(loan.selected),
                ()->!loan.lost&&guardedSample(true)!=null&&loan.leaf(loan.prior,true));
            var restoredHistory=receipt.nativeSelectionHistory();
            boolean restored=receipt.confirmed()&&receipt.localSelected()&&receipt.packetSubmitted()&&restoredHistory!=null
                &&restoredHistory.followsExactlyOneSetter(loan.selectedHistory);
            if(restored)loan.selectedHistory=restoredHistory; // Only the actual own one-use restore receipt advances slot history.
            restored=restored&&loan.leaf(loan.prior)&&guardedSample(true)!=null&&loan.leaf(loan.prior);
            if(!restored)loan.lost=true;return restored;
        }
        private void advanceDeviceSlotLoan(long generation,long epoch,long serial,boolean certified){
            if(selectedDeviceSlotLoan!=null)selectedDeviceSlotLoan.advance(generation,epoch,serial,certified);
        }
        /** Publish the controller's normal neutral movement output through seven actual scoped native leaves. */
        public boolean publishNeutralMovementOwned(){
            return NeutralMovementPublication.publish(key->switch(key){
                case FORWARD->setForwardHeld(false);case BACK->setBackHeld(false);case LEFT->setLeftHeld(false);case RIGHT->setRightHeld(false);
                case JUMP->setJumpHeld(false);case SNEAK->setSneakHeld(false);case SPRINT->setSprintHeld(false);
            });
        }
        /** Diagnostic only: cached real operands plus callback-free native scalars; no fresh role/provider/config callbacks. */
        public String publicationDiagnostic(){
            if(!client.isSameThread())return "WRONG_CLIENT_THREAD";
            var last=lastObservedSample;var keyReceipt=lastNativeKeyReceipt;var actual=actualKeyOnlyReceipt;var snapshot=keyOnlyInitialSnapshot;
            String observed=last==null?"NONE":"registered="+last.registered()+",enabled="+last.enabled()+",usable="+last.usable()
                +",inputParent="+(last.inputOwner()==parent)+",cameraCaptured="+(last.cameraAuthority()==cameraAuthority())
                +",root="+System.identityHashCode(last.root())+",sampleActive="+System.identityHashCode(last.activeLeg())
                +",revision="+last.revision()+",capturedRevision="+(captured==null?-1:captured.revision())
                +",pwcSame="+(captured!=null&&last.player()==captured.player()&&last.world()==captured.world()&&last.connection()==captured.connection())
                +",storeSame="+(captured!=null&&last.config()==captured.config());
            String receipt=keyReceipt==null?"NONE":"id="+System.identityHashCode(keyReceipt)+",status="+keyReceipt.status()+",held="+keyReceipt.held()
                +",key="+System.identityHashCode(keyReceipt.key())+",setAttempts="+keyReceipt.nativeSetAttempts()+",resetAttempts="+keyReceipt.restorationAttempts();
            return "leg="+System.identityHashCode(this)+",parent="+System.identityHashCode(parent)+",camera="+System.identityHashCode(cameraAuthority())
                +",active="+System.identityHashCode(ACTIVE.get(client))+",lockActive="+ScriptingInputLock.isActive()+",lockOwner="+ScriptingInputLock.ownerModuleId()+",admitted="+admittedVersion+",liveAdmission="+ADMISSIONS.getOrDefault(client,0L)
                +",ownG="+requestGeneration()+",nativeG="+rotation.requestGeneration()+",ownE="+cameraEpoch()+",nativeE="+McOwnedRotation.epoch(client)
                +",ownS="+(identity==null?-1:identity.serial())+",nativeS="+McOwnedRotation.publicationSerial(client)+",ticket="+actualTicket()+",positiveHistory="+positivePublishedSerial
                +",keys="+(identity!=null&&identity.keysPublished())+",cameraAttemptedOrQueued="+(identity!=null&&identity.cameraPublishedOrQueued())
                +",deviceSlotLoan="+(selectedDeviceSlotLoan==null?"NONE":"prior="+selectedDeviceSlotLoan.prior+",selected="+selectedDeviceSlotLoan.selected
                    +",selectedReceipt="+(selectedDeviceSlotLoan.selectedReceipt!=null&&selectedDeviceSlotLoan.selectedReceipt.confirmed())
                    +",localSelected="+(selectedDeviceSlotLoan.selectedReceipt!=null&&selectedDeviceSlotLoan.selectedReceipt.localSelected())
                    +",packetSubmitted="+(selectedDeviceSlotLoan.selectedReceipt!=null&&selectedDeviceSlotLoan.selectedReceipt.packetSubmitted())
                    +",consumed="+selectedDeviceSlotLoan.consumed+",lost="+selectedDeviceSlotLoan.lost)
                +",initialSnapshot="+System.identityHashCode(snapshot)+",actualKeyOnlyReceipt="+System.identityHashCode(actual)+",optionsSame="+(client.options==keyOnlyOptions)
                +",lastInputDecision="+lastInputDecision+",keyOnlyDecision="+lastKeyOnlyDecision+",lastSample={"+observed+"},lastNativeKeyReceipt={"+receipt+"}";
        }
        public boolean setForwardHeld(boolean held){return input(LegacyTravelRetirement.Key.FORWARD,held);}
        public boolean setBackHeld(boolean held){return input(LegacyTravelRetirement.Key.BACK,held);}
        public boolean setLeftHeld(boolean held){return input(LegacyTravelRetirement.Key.LEFT,held);}
        public boolean setRightHeld(boolean held){return input(LegacyTravelRetirement.Key.RIGHT,held);}
        public boolean setJumpHeld(boolean held){return input(LegacyTravelRetirement.Key.JUMP,held);}
        public boolean setSneakHeld(boolean held){return input(LegacyTravelRetirement.Key.SNEAK,held);}
        public boolean setSprintHeld(boolean held){return input(LegacyTravelRetirement.Key.SPRINT,held);}
        private boolean releaseKeyScoped(LegacyTravelRetirement.Key key){
            var before=guardedSample(true);var mapping=key(key);if(before==null||mapping==null||!identity.mayReleaseKeys(before))return false;
            if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()){
                if(issuedReturn==null||!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.ownsOriginalRequest(issuedReturnRequest,this))return false;
                // A true key must come from this Leg's genuine normally returned publication and unchanged native history.
                // Already-false extra vanilla keys may only remain neutral under the original Context ledger.
                var own=profileKeys.get(key);var attempted=issuedReturnHeldAttempts.get(key);
                boolean originalPublication=own!=null&&own.options()==client.options&&own.held()&&own.actual().key()==mapping
                    &&own.actual().confirmed()&&own.actual().normallyReturnedOwnedHeldAttempt()&&own.actual().normalIdleHistoryCurrent();
                boolean originalAttempt=attempted!=null&&attempted.actual.key()==mapping&&attempted.current();
                if(mapping.isDown()&&!originalPublication&&!originalAttempt)return false;
                var actual=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.beginOriginalLegKeyCall(this,mapping);if(actual==null)return false;
                try{return McHeldKeys.setHeldScoped(mapping,false,()->actual.current()&&key(key)==mapping&&guardedSample(true)!=null&&identity.cleanupCurrent(before)).confirmed()&&actual.current()&&cleanupCurrent();}
                finally{actual.close();}
            }
            return McHeldKeys.setHeldScoped(mapping,false,()->key(key)==mapping&&guardedSample(true)!=null&&identity.cleanupCurrent(before)).confirmed()&&cleanupCurrent();
        }
        private boolean releaseKeyOwned(LegacyTravelRetirement.Key key){return releaseKeyScoped(key);}
        /** Release each of all nine vanilla keys only under this leg's own publication custody. */
        public boolean releaseAllNineOwned(){
            for(var key:LegacyTravelRetirement.Key.values())if(!releaseKeyOwned(key))return false;
            return cleanupCurrent();
        }
        public boolean cancelCameraOwned(){
            dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.LegCameraCall call=null;
            if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()){
                call=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.beginOriginalLegCameraCall(this);if(call==null)return false;
            }
            try{
            if(guiRetirement!=null)return guiCleanupSample()!=null; // Actual Root GUI cancel already happened; never cancel twice.
            if(!cleanupCurrent())return false;var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(maintenance!=null&&(lifecycle==null||!lifecycle.known()))return false;
            var before=guardedSample(true);if(before==null)return false;
            boolean source=sourceReceipt!=null&&rotation.ownsCleanup(sourceReceipt,true);
            if(maintenance!=null&&!McFarmMaintenanceAuthorityBridge.cancellationCurrent(maintenance,client,config,rotation,identity.epoch(),positivePublishedSerial,identity.generation())){
                if(!source||!McFarmMaintenanceAuthorityBridge.retirementCameraCurrent(maintenance,client,config,rotation,sourceReceipt,identity.epoch(),positivePublishedSerial,identity.generation()))return false;
            }
            if(maintenance!=null&&(!nativeCurrent(before)||!root.lifecycleCurrent(lifecycle)))return false;
            if(call!=null&&!call.current())return false;
            if(source){if(!rotation.cancelOwned(sourceReceipt,true))return false;}
            else {if(!identity.mayCancelCamera(before))return false;McOwnedRotation.cancel(client);}
            var after=sample();
            // Closed provider proof uses the expected result of this actual guarded cancel, never sampled raw ids.
            if(maintenance!=null&&!after.registered()&&before.epoch()<Long.MAX_VALUE&&before.requestGeneration()<Long.MAX_VALUE
                    &&McFarmMaintenanceAuthorityBridge.retirementKeysCurrent(maintenance,client,config,rotation,before.epoch()+1,positivePublishedSerial,before.requestGeneration()+(source?1:0))){
                after=new LegacyTravelPublicationIdentity.State(after.root(),after.parent(),after.inputOwner(),after.cameraAuthority(),after.player(),after.world(),after.connection(),after.config(),after.revision(),after.journey(),after.service(),after.requestGeneration(),after.epoch(),after.publicationSerial(),after.activeLeg(),true,after.enabled(),after.usable());
            }
            if(maintenance!=null&&(!nativeCurrent(after)||!root.lifecycleCurrent(lifecycle)))return false;
            if(call!=null&&!call.current())return false;
            boolean certified=identity.cancelled(before,after,source);
            advanceDeviceSlotLoan(before.requestGeneration(),before.epoch(),before.publicationSerial(),certified);
            advanceNormalReturnSlotLoan(before.requestGeneration(),before.epoch(),before.publicationSerial(),certified);
            sourceReceipt=null;handedSourceReceipt=null;return certified;
            }finally{if(call!=null)call.close();}
        }
        /** An actual clean closed Leg is the sole factory authority; the returned handle publishes nothing. */
        public RetiredParentInteraction retiredParentInteraction(){
            if(!client.isSameThread()||maintenance!=null||issuedReturn!=null||mintingInteraction)return null;mintingInteraction=true;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return null;
                var context=retirementContinuationContext();if(context==null||context.cameraAuthority()!=parent)return null;
                long rotationRevision=capturedConfiguration.epochs().get(KEYS.indexOf(RotationConfig.ROTATION_SPEED_MULTIPLIER));
                var proof=LegacyRetiredParentInteractionIdentity.Lease.capture(context,retiredAdmissionVersion,rotationRevision);
                if(proof==null)return null;var candidate=new RetiredParentInteraction(this,proof,root,lifecycle);
                if(!candidate.current())return null;var fresh=sample();boolean sole=candidate.roster(false);
                if(!candidate.finalNative(fresh,false,lifecycle)||!identity.consumeRetiredInteraction(fresh,sole,retiredAdmissionVersion,ADMISSIONS.getOrDefault(client,0L)))return null;
                return candidate;
            }catch(RuntimeException unavailable){return null;}finally{mintingInteraction=false;}
        }
        /** Cached actual cleanup result, including partial releases and foreign supersession. */
        public LegacyTravelRetirement.RetirementOutcome retirementOutcome(){return retirement;}
        /** Exact local fail-stop context after MISMATCH; a newer/foreign scope never receives this proof. */
        public LegacyTravelPublicationIdentity.State postRetirementOwnParentContext(){
            var outcome=retirement;
            return outcome!=null&&outcome.localFailStopCandidate()?closedOwnParentContext(false,false):null;
        }
        /** Cleanup-only proof for ending the original workflow; it grants no handoff or new admission. */
        public LegacyTravelPublicationIdentity.State retiredCleanupParentContext(){
            var outcome=retirement;
            if(maintenance!=null)return outcome!=null&&outcome.cleanSuccess()?closedOwnParentContext(true,false):null;
            return outcome!=null&&outcome.removal()==LegacyTravelRetirement.Removal.REMOVED_OWN
                    &&(outcome.cleanSuccess()||outcome.localFailStopCandidate())?closedOwnParentContext(false,true):null;
        }
        /** Fresh read-only proof for consuming one cached CLEAN child retirement before a handoff. */
        public LegacyTravelPublicationIdentity.State retirementContinuationContext(){
            var outcome=retirement;
            return outcome!=null&&outcome.cleanSuccess()&&outcome.removal()==LegacyTravelRetirement.Removal.REMOVED_OWN
                    ?closedOwnParentContext(true,false):null;
        }
        /** Read-only retired handoff state. The private caller supplies only its just-validated typed source outcome and rechecks it after these callbacks. */
        private LegacyTravelPublicationIdentity.State retiredIssuedLoadedSample(boolean retainedSource){
            if(!retainedSource||retirement==null||!retirement.cleanSuccess()||loadedIssuedSegment==null||issuedReturn==null)return null;
            long observedRevision=revision.getAsLong();var liveConfiguration=configuration();
            var root=VeltraphBootstrap.moduleManager();var p=client.player;var w=client.level;var connection=client.getConnection();
            boolean thread=client.isSameThread();Object inputOwner=root==null?null:root.inputOwner().orElse(null);
            Object authority=McOwnedRotation.authority(client);
            boolean lockOwned=ScriptingInputLock.isActive()&&parent.id().equals(ScriptingInputLock.ownerModuleId());
            boolean registered=root!=null&&root.get(parent.id()).orElse(null)==parent&&!parent.isPassiveObserver()
                &&(!ScriptingInputLock.isActive()||lockOwned);
            boolean policyCurrent=capturedConfiguration!=null&&capturedConfiguration.equals(liveConfiguration);
            boolean usable=policyCurrent&&parent.ownsGameplayInput()&&thread&&p!=null&&w!=null&&connection!=null&&p.level()==w
                &&p.isAlive()&&!p.isInLava()&&!p.isPassenger()&&!client.isPaused()&&!w.tickRateManager().isEntityFrozen(p)
                &&client.gui.screen()==null&&!p.isScoping()&&client.getCameraEntity()==p
                &&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty()&&lockOwned
                &&client.options!=null&&McOwnedRotation.installed(client)&&VeltraphBootstrap.config()==config;
            return new LegacyTravelPublicationIdentity.State(root,parent,inputOwner,authority,p,w,connection,VeltraphBootstrap.config(),observedRevision,journey,
                rotation,thread?rotation.requestGeneration():-1,McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),ACTIVE.get(client),registered,parent.isEnabled(),usable);
        }
        private LegacyTravelPublicationIdentity.State closedOwnParentContext(boolean continuation,boolean allowDisabledCleanup){
            if(!client.isSameThread()||identity==null||!(continuation?lifecycleRoot!=null&&originalLifecycle.value()!=null&&lifecycleRoot.lifecycleCurrent(originalLifecycle.value()):originalCleanupLifecycle()))return null;
            try {var root=VeltraphBootstrap.moduleManager();
                if(issuedReturn!=null){var life=root==null?null:root.lifecycleSnapshot();if(life==null||!life.known())return null;
                    var successor=continuation&&loadedIssuedSegment!=null?issuedReturnRequest.currentLoadedSegmentIdentity():null;
                    boolean nextSelected=successor!=null&&successor!=loadedIssuedSegment&&successor.predecessorIdentity()==this;
                    boolean role=continuation?(nextSelected?issuedReturn.retiredLoadedContextCurrent(this,successor):issuedReturnNativeCurrent()):issuedReturnCleanupCurrent();
                    var observed=nextSelected?retiredIssuedLoadedSample(role):sample();
                    long version=ADMISSIONS.getOrDefault(client,0L);
                    boolean fresh=continuation?identity.ownsRetiredContinuation(observed,role,retiredAdmissionVersion,version)
                        :identity.ownsRetiredCleanupParent(observed,role,retiredAdmissionVersion,version);
                    return fresh&&nativeCurrent(observed)&&ACTIVE.get(client)==null&&ADMISSIONS.getOrDefault(client,0L)==retiredAdmissionVersion
                        &&(continuation?(nextSelected?issuedReturn.retiredLoadedContextCurrent(this,successor):issuedReturnNativeCurrent()):issuedReturnCleanupCurrent())&&root.lifecycleCurrent(life)
                        &&(continuation?lifecycleRoot.lifecycleCurrent(originalLifecycle.value()):originalCleanupLifecycle())?observed:null;
                }
                if(maintenance!=null){var lifecycle=root==null?null:root.lifecycleSnapshot();if(lifecycle==null||!lifecycle.known())return null;
                    var s=sample();boolean role=continuation?roleCurrent(false):roleCurrent(true);long version=ADMISSIONS.getOrDefault(client,0L);
                    boolean fresh=continuation?identity.ownsRetiredContinuation(s,role,retiredAdmissionVersion,version)
                        :allowDisabledCleanup?identity.ownsRetiredCleanupParent(s,role,retiredAdmissionVersion,version):identity.ownsRetiredParent(s,role,retiredAdmissionVersion,version);
                    return fresh&&role&&(continuation?roleCurrent(false):roleCurrent(true))&&nativeCurrent(s)&&ADMISSIONS.getOrDefault(client,0L)==retiredAdmissionVersion&&root.lifecycleCurrent(lifecycle)
                        &&(continuation?lifecycleRoot.lifecycleCurrent(originalLifecycle.value()):originalCleanupLifecycle())
                        &&(continuation?loadedRetiredSourceCurrent():loadedCleanupSourceCurrent())?s:null;
                }
                boolean registered=root!=null&&root.get(parent.id()).orElse(null)==parent&&!parent.isPassiveObserver();
                int controllers=root==null?-1:root.enabledControllers().size();
                Object inputOwner=root==null?null:root.inputOwner().orElse(null);
                boolean sole=registered&&(parent.isEnabled()&&parent.ownsGameplayInput()&&controllers==1&&inputOwner==parent
                        ||allowDisabledCleanup&&!parent.isEnabled()&&controllers==0&&inputOwner==null);
                var observed=sample();
                long liveVersion=ADMISSIONS.getOrDefault(client,0L);
                boolean fresh=continuation?identity.ownsRetiredContinuation(observed,sole,retiredAdmissionVersion,liveVersion)
                        :allowDisabledCleanup?identity.ownsRetiredCleanupParent(observed,sole,retiredAdmissionVersion,liveVersion)
                        :identity.ownsRetiredParent(observed,sole,retiredAdmissionVersion,liveVersion);
                return fresh&&(continuation?lifecycleRoot.lifecycleCurrent(originalLifecycle.value()):originalCleanupLifecycle())?observed:null;
            }catch(RuntimeException unavailable){return null;}
        }
        /** Wrapper-owned retirement: foreign/newer scope is never changed by this transaction. */
        public LegacyTravelRetirement.RetirementOutcome retireOwned(){
            if(dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.active()
                &&!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.claimOriginalLegRetirement(this))
                return LegacyTravelRetirement.notRetired(identity!=null&&identity.keysPublished(),identity!=null&&identity.cameraPublishedOrQueued());
            var prior=retirement;if(prior!=null&&prior.logicalRetired())return prior;
            if(!client.isSameThread()){
                retirement=LegacyTravelRetirement.notRetired(identity!=null&&identity.keysPublished(),identity!=null&&identity.cameraPublishedOrQueued());return retirement;
            }
            if(retiring)return LegacyTravelRetirement.notRetired(identity!=null&&identity.keysPublished(),identity!=null&&identity.cameraPublishedOrQueued());
            retiring=true;try {retirement=LegacyTravelRetirement.retire(new LegacyTravelRetirement.Effects(){
                public boolean keysPublished(){return identity!=null&&identity.keysPublished();}
                public boolean cameraPublishedOrQueued(){return identity!=null&&identity.cameraPublishedOrQueued();}
                public boolean activeLegMatches(){return ACTIVE.get(client)==Leg.this;}
                public boolean cleanupFence(){return cleanupCurrent();}
                public boolean release(LegacyTravelRetirement.Key key){return releaseKeyOwned(key);}
                public boolean cancelCamera(){return cancelCameraOwned();}
                public LegacyTravelRetirement.Removal retireLogical(){
                    if(identity!=null)identity.closed();sourceReceipt=null;handedSourceReceipt=null;
                    var active=ACTIVE.get(client);
                    if(active==Leg.this){
                        ACTIVE.remove(client);long version=ADMISSIONS.getOrDefault(client,0L);
                        retiredAdmissionVersion=version==Long.MAX_VALUE?Long.MAX_VALUE:version+1;
                        ADMISSIONS.put(client,retiredAdmissionVersion);
                        return LegacyTravelRetirement.Removal.REMOVED_OWN;
                    }
                    return active==null?LegacyTravelRetirement.Removal.ALREADY_ABSENT:LegacyTravelRetirement.Removal.FOREIGN_PRESERVED;
                }
            });return retirement;}finally{retiring=false;}
        }
        /** Compatibility Boolean certifies completed cleanup and the final ownership fence. */
        public boolean closeOwned(){return retireOwned().cleanSuccess();}
    }
    /** Read-only interaction custody. The originating Leg remains closed and cannot emit any movement. */
    public final class RetiredParentInteraction {
        private final Leg source;private final LegacyRetiredParentInteractionIdentity.Lease proof;
        private final ModuleManager originalRoot;private final ModuleManager.LifecycleSnapshot originalEnabledLifecycle;
        private McRotationService.SourceAimOutcome sourceAim;private McRotationService.SourceAimCancellation sourceAimCancellation;
        private McRotationService.DirectPublicationReceipt own;
        private McRotationService.DirectCancellationReceipt ownCancellation;
        private CleanupTransaction issuedCleanup;private boolean issuingCleanup;
        private RetiredParentInteraction(Leg source,LegacyRetiredParentInteractionIdentity.Lease proof,ModuleManager originalRoot,ModuleManager.LifecycleSnapshot originalEnabledLifecycle){
            this.source=source;this.proof=proof;this.originalRoot=originalRoot;this.originalEnabledLifecycle=originalEnabledLifecycle;
            var initial=source.rotation.observedDirectPublication();
            if(initial!=null&&initial.current()&&proof.seedOwn(asProof(initial),initial.sameTickRetained()))own=initial;
        }
        private boolean roster(boolean cleanup){
            var root=VeltraphBootstrap.moduleManager();if(root==null||root.get(source.parent.id()).orElse(null)!=source.parent)return false;
            int controllers=root.enabledControllers().size();return source.parent.isEnabled()?controllers==1
                    :cleanup&&controllers==0&&root.inputOwner().isEmpty();
        }
        private boolean finalNative(LegacyTravelPublicationIdentity.State s,boolean cleanup,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle){
            if(originalRoot==null||VeltraphBootstrap.moduleManager()!=originalRoot||originalEnabledLifecycle==null
                ||!(cleanup?originalRoot.ownCleanupLifecycleCurrent(originalEnabledLifecycle,source.parent):originalRoot.lifecycleCurrent(originalEnabledLifecycle)))return false;
            if(!cleanup&&!source.capturedConfiguration.equals(configuration()))return false;
            var root=VeltraphBootstrap.moduleManager();String id=source.parent.id();
            int controllers=root==null?-1:root.enabledControllers().size();
            Object input=root==null?null:root.inputOwner().orElse(null);Object camera=McOwnedRotation.authority(client);
            boolean enabled=source.parent.isEnabled();
            boolean role=enabled?controllers==1&&input==source.parent&&camera==source.parent
                    :cleanup&&controllers==0&&input==null&&camera==null;
            boolean lock=!ScriptingInputLock.isActive()||id.equals(ScriptingInputLock.ownerModuleId());
            return role&&lock&&root==s.root()&&root.get(id).orElse(null)==source.parent&&VeltraphBootstrap.moduleManager()==root
                &&client.player==s.player()&&client.level==s.world()&&client.getConnection()==s.connection()&&VeltraphBootstrap.config()==s.config()
                &&source.rotation.requestGeneration()==s.requestGeneration()&&McOwnedRotation.epoch(client)==s.epoch()&&McOwnedRotation.publicationSerial(client)==s.publicationSerial()
                &&ADMISSIONS.getOrDefault(client,0L)==proof.admissionVersion()&&ACTIVE.get(client)==null&&root.lifecycleCurrent(lifecycle)
                &&(cleanup?originalRoot.ownCleanupLifecycleCurrent(originalEnabledLifecycle,source.parent):originalRoot.lifecycleCurrent(originalEnabledLifecycle));
        }
        public boolean current(){
            if(!client.isSameThread())return false;try{var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return false;var s=source.sample();boolean role=roster(false);
                return proof.current(s,role,ADMISSIONS.getOrDefault(client,0L))&&finalNative(s,false,lifecycle);
            }catch(RuntimeException unavailable){return false;}
        }
        public boolean cleanupCurrent(){
            if(!client.isSameThread())return false;try{var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return false;var s=source.sample();boolean role=roster(true);
                return proof.cleanupCurrent(s,role,ADMISSIONS.getOrDefault(client,0L))&&(sourceAimCancellation!=null?sourceAimCancellation.cleanupCurrent(!source.parent.isEnabled()):ownCancellation!=null?ownCancellation.cleanupCurrent(!source.parent.isEnabled()):sourceAim!=null?sourceAim.cleanupCurrent(!source.parent.isEnabled()):own==null||own.cleanupCurrent(!source.parent.isEnabled()))&&finalNative(s,true,lifecycle)
                    &&(sourceAimCancellation!=null?sourceAimCancellation.cleanupNativeCurrent(!source.parent.isEnabled()):sourceAim==null||sourceAim.cleanupNativeCurrent(!source.parent.isEnabled()));
            }catch(RuntimeException unavailable){return false;}
        }
        /** Refusal only, used inside the independently guarded C7 call after config/heading reads. */
        public java.util.function.BooleanSupplier publicationFence(){
            if(!current())return ()->false;
            return ()->{if(!client.isSameThread())return false;try{var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known())return false;var s=source.sample();boolean role=roster(false);
                return proof.publicationFence(s,role,ADMISSIONS.getOrDefault(client,0L))&&finalNative(s,false,lifecycle);
            }catch(RuntimeException unavailable){return false;}};
        }
        public boolean advance(McRotationService.DirectPublicationReceipt receipt){
            if(!client.isSameThread()||receipt==null)return false;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||!receipt.current())return false;var s=source.sample();boolean role=roster(false);
                if(!receipt.current()||!finalNative(s,false,lifecycle))return false;
                boolean accepted=proof.advance(asProof(receipt),receipt.newPublication(),s,role,ADMISSIONS.getOrDefault(client,0L));
                if(accepted)own=receipt;return accepted;
            }catch(RuntimeException unavailable){return false;}
        }
        public boolean advanceRefusal(McRotationService.SourceAimOutcome exact){return exact!=null&&exact.metadataOnlyRefusal()&&advanceSourceAimOutcome(exact,true);}
        public boolean advanceAimCleanup(McRotationService.SourceAimOutcome exact){return exact!=null&&!exact.metadataOnlyRefusal()&&advanceSourceAimOutcome(exact,false);}
        private boolean advanceSourceAimOutcome(McRotationService.SourceAimOutcome exact,boolean metadataOnly){
            if(!client.isSameThread()||exact==null||sourceAimCancellation!=null)return false;
            if(sourceAim==exact)return cleanupCurrent();
            if(sourceAim!=null||proof.cancelled()||exact.priorPublication()!=own)return false;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||!exact.cleanupCurrent(!source.parent.isEnabled()))return false;
                var s=source.sample();boolean role=roster(true);var published=exact.cleanupPublication();
                if(metadataOnly!=(published==null)||!exact.cleanupCurrent(!source.parent.isEnabled())||!finalNative(s,true,lifecycle)||!exact.cleanupNativeCurrent(!source.parent.isEnabled()))return false;
                boolean accepted=proof.advanceAimCleanup(exact.before(),exact.after(),published==null?null:asProof(published),metadataOnly,s,role,ADMISSIONS.getOrDefault(client,0L));
                if(accepted){sourceAim=exact;if(published!=null)own=published;}return accepted;
            }catch(RuntimeException unavailable){return false;}
        }
        public McRotationService.SourceAimCancellation cancelSourceAimOwnedCamera(boolean allowDisabled){
            var exact=sourceAim;if(exact==null||sourceAimCancellation!=null||!allowDisabled&&!source.parent.isEnabled()||!cleanupCurrent())return null;
            var cancelled=source.rotation.cancelSourceAimOwned(exact,this::cleanupCurrent,allowDisabled);
            return advanceSourceAimCancellation(cancelled)?cancelled:null;
        }
        public boolean advanceSourceAimCancellation(McRotationService.SourceAimCancellation exact){
            if(!client.isSameThread()||exact==null||sourceAim==null||exact.outcome()!=sourceAim||sourceAimCancellation!=null)return false;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||!exact.cleanupCurrent(!source.parent.isEnabled()))return false;
                var s=source.sample();boolean role=roster(true);
                if(!exact.cleanupCurrent(!source.parent.isEnabled())||!finalNative(s,true,lifecycle)||!exact.cleanupNativeCurrent(!source.parent.isEnabled()))return false;
                boolean accepted=proof.advanceSourceAimCancellation(exact.before(),exact.after(),exact.nativeCancelled(),exact.actualTicket(),s,role,ADMISSIONS.getOrDefault(client,0L));
                if(accepted)sourceAimCancellation=exact;return accepted;
            }catch(RuntimeException unavailable){return false;}
        }
        /** Exact retained native custody only, never a late observed service generation. */
        public McRotationService.SourceAimOutcome sourceAimCleanupOutcome(){return sourceAim!=null&&cleanupCurrent()?sourceAim:null;}
        /** Only the exact privately retained parent publication is exposed; no late service lookup. */
        public McRotationService.DirectPublicationReceipt ownPublicationReceipt(){return sourceAim==null&&!proof.cancelled()&&own!=null&&cleanupCurrent()?own:null;}
        public boolean ownsCamera(){return !proof.cancelled()&&own!=null&&cleanupCurrent();}
        /** Refusal only, checked by the native C9 producer immediately before its single ordinary cancel. */
        public java.util.function.BooleanSupplier cancellationFence(){return this::cleanupCurrent;}
        public McRotationService.DirectCancellationReceipt cancelOwnedCamera(boolean allowDisabled){
            var exact=ownPublicationReceipt();if(exact==null||!allowDisabled&&!source.parent.isEnabled())return null;
            var receipt=source.rotation.cancelDirectOwned(exact,cancellationFence(),allowDisabled);
            return advanceCleanup(receipt)?receipt:null;
        }
        /** Consumes only C9's privately minted cancellation of this proof's exact old publication. */
        public boolean advanceCleanup(McRotationService.DirectCancellationReceipt receipt){
            if(!client.isSameThread()||receipt==null||proof.cancelled()||own==null||receipt.publication()!=own)return false;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||!receipt.cleanupCurrent(!source.parent.isEnabled()))return false;
                var s=source.sample();boolean role=roster(true);
                if(receipt.publication()!=own||!receipt.cleanupCurrent(!source.parent.isEnabled())||!finalNative(s,true,lifecycle))return false;
                boolean accepted=proof.advanceCleanup(receipt.before(),receipt.after(),receipt.actualTicket(),s,role,ADMISSIONS.getOrDefault(client,0L));
                if(accepted)ownCancellation=receipt;return accepted;
            }catch(RuntimeException unavailable){return false;}
        }
        /** A real COMPLETE invocation and original native inventory handle are required before any cancellation. */
        public CleanupTransaction beginCleanupTransaction(GardenActionModule.NormalCompletionReceipt completion,PestInventoryLease.OwnedRestoreHandle inventory){
            if(!client.isSameThread()||issuingCleanup||issuedCleanup!=null||completion==null||inventory==null)return null;
            issuingCleanup=true;var originalHardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||completion.interaction()!=this||completion.parent()!=source.parent
                    ||inventory.nativeClient()!=client||inventory.originalParent()!=source.parent||!completion.current()||!inventory.currentOriginal()||!current())return null;
                var candidate=new CleanupTransaction(this,completion,inventory,originalHardware);
                if(candidate.issuer==null||!completion.current()||!inventory.currentOriginal()||!candidate.current()||!root.lifecycleCurrent(lifecycle))return null;
                issuedCleanup=candidate;return candidate;
            }catch(RuntimeException unavailable){return null;}finally{issuingCleanup=false;}
        }
        private DirectRotationPublicationIdentity.Proof asProof(McRotationService.DirectPublicationReceipt receipt){
            long delta=receipt.after().generation()-receipt.before().generation();
            var mode=receipt.sameTickRetained()?DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK
                    :delta==2?DirectRotationPublicationIdentity.Mode.TRACK:DirectRotationPublicationIdentity.Mode.AIM;
            return new DirectRotationPublicationIdentity.Proof(receipt.before(),receipt.after(),receipt.actualTicket(),mode);
        }
    }

    public enum CleanupStage { ISSUED,KEYS_CONFIRMED,CAMERA_CONFIRMED,INVENTORY_ISSUING,INVENTORY_AWAITING,MENU_ISSUING,COMPLETE,ABORTED }
    public enum CleanupStatus { CONFIRMED,NOT_REQUIRED,REFUSED,UNKNOWN }
    public enum CleanupAbort { MANUAL_CANCEL,CONFIGURATION_CHANGED,WORLD_CHANGED,EXPIRED,SCOPE_REVOKED,UNKNOWN }
    public static final class KeyReleaseOutcome {
        private final CleanupStatus status;private final java.util.List<McHeldKeys.ScopedHeldReceipt> receipts;
        private KeyReleaseOutcome(CleanupStatus status,java.util.List<McHeldKeys.ScopedHeldReceipt> receipts){this.status=status;this.receipts=java.util.List.copyOf(receipts);}
        public CleanupStatus status(){return status;}public boolean confirmed(){return status==CleanupStatus.CONFIRMED&&receipts.size()==9&&receipts.stream().allMatch(McHeldKeys.ScopedHeldReceipt::confirmed);}
        public java.util.List<McHeldKeys.ScopedHeldReceipt> receipts(){return receipts;}
    }
    public static final class CameraCleanupOutcome {
        private final CleanupStatus status;private final McRotationService.DirectCancellationReceipt receipt;
        private CameraCleanupOutcome(CleanupStatus status,McRotationService.DirectCancellationReceipt receipt){this.status=status;this.receipt=receipt;}
        public CleanupStatus status(){return status;}public boolean required(){return status!=CleanupStatus.NOT_REQUIRED;}
        public boolean confirmed(){return status==CleanupStatus.NOT_REQUIRED||status==CleanupStatus.CONFIRMED&&receipt!=null;}
        public McRotationService.DirectCancellationReceipt receipt(){return receipt;}
    }
    /** Pre-cancellation original gear permission, stamped only by the actual issuing transaction. */
    public final class InventoryCleanupPermit {
        private final CleanupTransaction transaction;private final PestInventoryLease.OwnedRestoreHandle original;
        private InventoryCleanupPermit(CleanupTransaction transaction,PestInventoryLease.OwnedRestoreHandle original){this.transaction=transaction;this.original=original;}
        public Object transactionIdentity(){return transaction;}public Module parent(){return transaction.interaction.source.parent;}
        public Minecraft nativeClient(){return client;}public PestInventoryLease.OwnedRestoreHandle originalHandle(){return original;}
        public long deadlineMillis(){return transaction.completion.deadlineMillis();}
        public boolean inventoryIssueCurrent(){return transaction.stage==CleanupStage.INVENTORY_ISSUING&&transaction.inventoryInvocation&&transaction.permissionCurrent();}
        /** Original policy/scene permission only; never call the now-restored handle's pre-inverse snapshot. */
        public boolean currentOriginal(){return transaction.permissionCurrent()&&transaction.inventoryPermissionStage();}
        public boolean cleanupCurrent(){return currentOriginal();}
        /** Passive observation only, from the real server-after producer retaining this exact issued permit. */
        public void observeAuthoritativeRestoration(dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.AuthoritativeRestorationReceipt receipt){
            if(!client.isSameThread()||receipt==null||receipt.permit()!=this||transaction.stage!=CleanupStage.INVENTORY_AWAITING
                    ||transaction.inventory==null||transaction.inventory.permit()!=this||transaction.inventory.handle()!=original)return;
            try {if(transaction.inventory.authoritativeReceipt()!=receipt)return;var completed=transaction.complete();if(completed!=null&&completed.current())completed.armNormalIdleCancellation();}
            catch(RuntimeException unavailable){} // Observation cannot broaden cleanup or issue another effect.
        }
    }
    /** The transaction itself issues effects; no caller list, Boolean or interface can manufacture completion. */
    public final class CleanupTransaction {
        private final RetiredParentInteraction interaction;private final GardenActionModule.NormalCompletionReceipt completion;
        private final PestInventoryLease.OwnedRestoreHandle original;private final InventoryCleanupPermit permit;
        private final Object originalOptions;private final LegacyCleanupTransactionIdentity.Issuer issuer;
        private CleanupStage stage=CleanupStage.ISSUED;private CleanupAbort aborted;
        private KeyReleaseOutcome keys;private CameraCleanupOutcome camera;private PestInventoryLease.OwnedRestoreReceipt inventory;
        private GardenActionModule.OwnedMenuCleanupReceipt menu;private NativeParentCleanupToken token;
        private boolean keysInvocation,cameraInvocation,inventoryInvocation,menuInvocation,completing;
        private ModuleManager.OwnDisableReceipt actualDisable;
        private final long issuedAt;private final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot originalHardware;private dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot releasedHardware;
        private CleanupTransaction(RetiredParentInteraction interaction,GardenActionModule.NormalCompletionReceipt completion,PestInventoryLease.OwnedRestoreHandle original,dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot originalHardware){
            this.originalHardware=originalHardware;this.interaction=interaction;this.completion=completion;this.original=original;originalOptions=client.options;permit=new InventoryCleanupPermit(this,original);issuedAt=services.clock().nowMillis();issuer=LegacyCleanupTransactionIdentity.Issuer.capture(completion,original,permit,issuedAt,completion.deadlineMillis());
        }
        public CleanupStage stage(){return stage;}public CleanupAbort abortCause(){return aborted;}public InventoryCleanupPermit inventoryPermit(){return permit;}
        public KeyReleaseOutcome keyOutcome(){return keys;}public CameraCleanupOutcome cameraOutcome(){return camera;}
        public PestInventoryLease.OwnedRestoreReceipt inventoryOutcome(){return inventory;}public GardenActionModule.OwnedMenuCleanupReceipt menuOutcome(){return menu;}
        /** Exact cached private handle only; reading it grants no permission and performs no completion/effects. */
        public NativeParentCleanupToken completionToken(){return token;}
        public boolean current(){return stage!=CleanupStage.ABORTED&&stage!=CleanupStage.COMPLETE&&permissionCurrent();}
        private boolean inventoryPermissionStage(){return keys!=null&&keys.confirmed()&&camera!=null&&camera.confirmed()
            &&(stage==CleanupStage.INVENTORY_ISSUING||stage==CleanupStage.INVENTORY_AWAITING||stage==CleanupStage.MENU_ISSUING||stage==CleanupStage.COMPLETE);}
        private boolean cleanupScopeCurrent(){return client.isSameThread()&&stage!=CleanupStage.ABORTED&&interaction.cleanupCurrent();}
        private boolean permissionCurrent(){
            if(!cleanupScopeCurrent())return false;
            try {var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||!completion.strictPolicyCurrent()||!completion.cleanupSourceCurrent()
                    ||!interaction.source.capturedConfiguration.equals(configuration())||services.clock().nowMillis()>=completion.deadlineMillis())return false;
                var s=interaction.source.sample();boolean role=interaction.roster(true);
                return interaction.proof.cleanupCurrent(s,role,ADMISSIONS.getOrDefault(client,0L))&&interaction.finalNative(s,true,lifecycle);
            }catch(RuntimeException unavailable){return false;}
        }
        public KeyReleaseOutcome releaseKeysOwned(){
            if(keys!=null)return keys;if(keysInvocation||stage!=CleanupStage.ISSUED)return new KeyReleaseOutcome(CleanupStatus.UNKNOWN,java.util.List.of());
            keysInvocation=true;var actual=new java.util.ArrayList<McHeldKeys.ScopedHeldReceipt>();
            try {for(var name:LegacyTravelRetirement.Key.values()){
                    var mapping=interaction.source.key(name);if(mapping==null||!cleanupScopeCurrent())break;
                    var receipt=McHeldKeys.setHeldScoped(mapping,false,()->interaction.source.key(name)==mapping&&cleanupScopeCurrent());actual.add(receipt);
                    if(!receipt.confirmed()||!cleanupScopeCurrent())break;
                }
                keys=new KeyReleaseOutcome(actual.size()==9&&actual.stream().allMatch(McHeldKeys.ScopedHeldReceipt::confirmed)&&cleanupScopeCurrent()?CleanupStatus.CONFIRMED:CleanupStatus.REFUSED,actual);
                if(keys.confirmed()&&issuer.keys(keys,keys.receipts().size(),cleanupScopeCurrent())){
                    stage=CleanupStage.KEYS_CONFIRMED;
                    boolean history=originalHardware!=null&&originalHardware.mousePoseCurrent();
                    for(var receipt:keys.receipts())history=history&&receipt.normalIdleHistoryAdvancedFrom(originalHardware.keyObservation(receipt.key()));
                    if(history)releasedHardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.capture(client);
                }return keys;
            }finally{keysInvocation=false;}
        }
        public CameraCleanupOutcome cancelCameraOwned(){
            if(camera!=null)return camera;if(cameraInvocation||stage!=CleanupStage.KEYS_CONFIRMED||keys==null||!keys.confirmed())return new CameraCleanupOutcome(CleanupStatus.UNKNOWN,null);
            cameraInvocation=true;try{
                if(!cleanupScopeCurrent())camera=new CameraCleanupOutcome(CleanupStatus.REFUSED,null);
                else if(interaction.own==null&&!interaction.proof.cancelled())camera=new CameraCleanupOutcome(CleanupStatus.NOT_REQUIRED,null);
                else {var receipt=interaction.cancelOwnedCamera(true);camera=new CameraCleanupOutcome(receipt!=null&&cleanupScopeCurrent()?CleanupStatus.CONFIRMED:CleanupStatus.REFUSED,receipt);}
                if(camera.confirmed()&&issuer.camera(camera,true,cleanupScopeCurrent()))stage=CleanupStage.CAMERA_CONFIRMED;return camera;
            }finally{cameraInvocation=false;}
        }
        public PestInventoryLease.OwnedRestoreReceipt restoreInventoryOwned(){
            if(inventory!=null)return inventory;if(inventoryInvocation||stage!=CleanupStage.CAMERA_CONFIRMED||!permissionCurrent())return null;
            if(!issuer.beginInventory(permit,original,services.clock().nowMillis(),permissionCurrent()))return null;
            stage=CleanupStage.INVENTORY_ISSUING;inventoryInvocation=true;
            try {inventory=original.restoreIssued(permit);if(inventory!=null&&!issuer.inventory(inventory,inventory.permit(),inventory.handle()))return null;return inventory;}
            finally{inventoryInvocation=false;if(stage!=CleanupStage.ABORTED)stage=CleanupStage.INVENTORY_AWAITING;}
        }
        /** Read-only observation of the real core STOPPED/log normal-return hook. Whole-hook confirmation is required by token.current(). */
        public boolean observeActualDisableReceipt(ModuleManager.OwnDisableReceipt receipt){
            if(!client.isSameThread()||receipt==null||receipt.parent()!=interaction.source.parent||actualDisable!=null&&actualDisable!=receipt
                ||!receipt.returnedCurrent()&&!receipt.current())return false;
            actualDisable=receipt;return true;
        }
        /** No source/provider/receipt/clock callbacks are allowed in this final native leaf. */
        private boolean freshKeysLeaf(){
            if(keys==null||!keys.confirmed())return false;
            var expected=new Object[9];var actual=new Object[9];var down=new boolean[9];
            for(var name:LegacyTravelRetirement.Key.values()){
                var receipt=keys.receipts().get(name.ordinal());var mapping=interaction.source.key(name);
                if(receipt.held())return false;expected[name.ordinal()]=receipt.key();actual[name.ordinal()]=mapping;down[name.ordinal()]=mapping==null||mapping.isDown();
            }
            return dev.veltraph.client.navigation.LegacyCleanupKeyLeafIdentity.current(originalOptions,client.options,expected,actual,down);
        }
        private final class CompletionLeaf {
            final ModuleManager root;final ModuleManager.LifecycleSnapshot lifecycle;
            final LegacyTravelPublicationIdentity.State scope;
            final Object policy,inventorySnapshot,menuSnapshot;
            final ModuleManager.OwnDisableReceipt disable;final long now;
            private CompletionLeaf(ModuleManager root,ModuleManager.LifecycleSnapshot lifecycle,LegacyTravelPublicationIdentity.State scope,
                    Object policy,Object inventorySnapshot,Object menuSnapshot,ModuleManager.OwnDisableReceipt disable,long now){
                this.root=root;this.lifecycle=lifecycle;this.scope=scope;this.policy=policy;this.inventorySnapshot=inventorySnapshot;this.menuSnapshot=menuSnapshot;this.disable=disable;this.now=now;
            }
            /** Producer finalCurrent methods use only authentic stored/native fields. All callbacks already ran. */
            boolean current(boolean confirmedDisable){
                if(disable==null||disable!=actualDisable||disable.parent()!=interaction.source.parent
                    ||!(confirmedDisable?root.ownDisableMatches(disable,lifecycle,interaction.source.parent)
                        :disable.returnedCurrent()||root.ownDisableMatches(disable,lifecycle,interaction.source.parent)))return false;
                if(!completion.finalPolicyCurrent(policy)||!completion.finalPolicyTimeCurrent(policy,now)||!inventory.finalNativeCurrent(inventorySnapshot)||!menu.finalNativeCurrent(menuSnapshot))return false;
                if(!(config instanceof dev.veltraph.client.config.JsonConfigService||config instanceof dev.veltraph.client.config.MapConfigService)
                    ||!interaction.source.capturedConfiguration.equals(configuration()))return false;
                return freshKeysLeaf()&&client.isSameThread()&&stage!=CleanupStage.ABORTED&&root==VeltraphBootstrap.moduleManager()
                    &&client.player==scope.player()&&client.level==scope.world()&&client.getConnection()==scope.connection()&&VeltraphBootstrap.config()==scope.config()
                    &&client.options==originalOptions&&interaction.source.rotation.requestGeneration()==scope.requestGeneration()
                    &&McOwnedRotation.epoch(client)==scope.epoch()&&McOwnedRotation.publicationSerial(client)==scope.publicationSerial()
                    &&scope.requestGeneration()==interaction.proof.generation()&&scope.epoch()==interaction.proof.epoch()&&scope.publicationSerial()==interaction.proof.serial()
                    &&ACTIVE.get(client)==null&&ADMISSIONS.getOrDefault(client,0L)==interaction.proof.admissionVersion()&&root.lifecycleCurrent(lifecycle);
            }
        }
        /** Capture authentic producer snapshots before callback group; finish with only callback-free evidence. */
        private CompletionLeaf checkedCompletionLeaf(boolean confirmedDisable){
            try {
                var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
                if(lifecycle==null||!lifecycle.known()||inventory==null||menu==null||actualDisable==null)return null;
                var policy=completion.finalPolicySnapshot();var inventorySnapshot=inventory.finalNativeSnapshot();var menuSnapshot=menu.finalNativeSnapshot();
                if(policy==null||inventorySnapshot==null||menuSnapshot==null)return null;
                if(!permissionCurrent()||inventory.handle()!=original||inventory.permit()!=permit||!inventory.currentOwned()||!inventory.restartQualified()
                    ||menu.permit()!=permit||menu.completion()!=completion||!menu.currentOwned()||!menu.restartQualified()
                    ||completion.parent().isEnabled()||completion.parent().state()!=MacroState.STOPPED)return null;
                var scope=interaction.source.sample();boolean role=interaction.roster(true);
                if(!interaction.proof.cleanupCurrent(scope,role,ADMISSIONS.getOrDefault(client,0L))||!interaction.finalNative(scope,true,lifecycle))return null;
                long now=services.clock().nowMillis();
                if(now<issuedAt||now>=completion.deadlineMillis())return null;
                var leaf=new CompletionLeaf(root,lifecycle,scope,policy,inventorySnapshot,menuSnapshot,actualDisable,now);
                return leaf.current(confirmedDisable)?leaf:null;
            }catch(RuntimeException unavailable){return null;}
        }
        public NativeParentCleanupToken complete(){
            if(token!=null)return token;if(completing||inventory==null||stage!=CleanupStage.INVENTORY_AWAITING||!permissionCurrent())return null;
            completing=true;try {
                stage=CleanupStage.MENU_ISSUING;menuInvocation=true;
                try{menu=completion.cleanupMenuIssued(permit);}finally{menuInvocation=false;}
                var leaf=checkedCompletionLeaf(true);if(leaf==null)return null;
                // Pure ordered bookkeeping consumes the callback group's sampled time; no clock/provider callback follows the leaf.
                if(!issuer.complete(inventory,menu,permit,completion,leaf.now,true,true))return null;
                var candidate=new NativeParentCleanupToken(this,leaf.root,leaf.lifecycle,leaf.scope,actualDisable);
                stage=CleanupStage.COMPLETE;token=candidate;return token;
            }catch(RuntimeException unavailable){return null;}finally{completing=false;if(token==null&&stage==CleanupStage.MENU_ISSUING)stage=CleanupStage.INVENTORY_AWAITING;}
        }
        public void abort(CleanupAbort cause){if(!client.isSameThread())return;aborted=cause==null?CleanupAbort.UNKNOWN:cause;if(issuer!=null)issuer.abort();stage=CleanupStage.ABORTED;}
        public String diagnostic(){return "stage="+stage+",abort="+aborted+",keys="+(keys==null?"NONE":keys.status())+",camera="+(camera==null?"NONE":camera.status())
            +",inventory="+(inventory==null?"NONE":inventory.toString())+",menu="+(menu==null?"NONE":menu.status())+",scope={"+interaction.source.publicationDiagnostic()+"}";}
    }
    public final class NativeParentCleanupToken {
        private final CleanupTransaction transaction;private final ModuleManager root;private final ModuleManager.LifecycleSnapshot disabled;
        private final LegacyTravelPublicationIdentity.State scope;private final ModuleManager.OwnDisableReceipt actualDisable;private boolean consumed,consuming;private NormalIdlePermit idlePermit;private String idlePermitDiagnostic="NOT_OBSERVED";
        /** Cached original-boundary result only; no guard/supplier/certificate lookup. */
        public String cachedNormalIdleDiagnostic(){return idlePermitDiagnostic+(idlePermit==null?"":"; control="+idlePermit.idleControlDiagnostic+"; witness="+idlePermit.idleWitnessDiagnostic+"; callback="+idlePermit.actualInputDiagnostic+"; hardware={"+(idlePermit.hardware==null?"NONE":idlePermit.hardware.cachedCurrentDiagnostic())+"}");}
        private NormalIdlePermit missingIdlePermit(String reason){idlePermitDiagnostic=reason;return null;}
        private NativeParentCleanupToken(CleanupTransaction transaction,ModuleManager root,ModuleManager.LifecycleSnapshot disabled,LegacyTravelPublicationIdentity.State scope,ModuleManager.OwnDisableReceipt actualDisable){this.transaction=transaction;this.root=root;this.disabled=disabled;this.scope=scope;this.actualDisable=actualDisable;}
        /** Registration of actual normal idle observation only; this grants no gameplay or new admission. */
        public boolean armNormalIdleCancellation(){return McOwnedRotation.armNormalIdle(client,this);}
        public NormalIdlePermit normalIdlePermit(){
            if(idlePermit!=null)return idlePermit;
            if(!current())return missingIdlePermit("TOKEN_CURRENT_FALSE");
            if(transaction.camera==null)return missingIdlePermit("CAMERA_OUTCOME_NULL");
            if(transaction.camera.receipt()==null)return missingIdlePermit("ACTUAL_CANCEL_RECEIPT_NULL");
            if(transaction.camera.receipt().actualTicket()<=0)return missingIdlePermit("ACTUAL_TICKET_NONPOSITIVE");
            if(transaction.releasedHardware==null)return missingIdlePermit("ISSUED_SCOPED9_HISTORY_UNAVAILABLE");
            if(!transaction.releasedHardware.current())return missingIdlePermit("RELEASED_HARDWARE_CHANGED_BEFORE_ARM");
            var cancel=transaction.camera.receipt();
            if(cancel.after().generation()!=scope.requestGeneration())return missingIdlePermit("CANCEL_GENERATION_MISMATCH");
            if(cancel.after().epoch()!=scope.epoch())return missingIdlePermit("CANCEL_EPOCH_MISMATCH");
            if(cancel.actualTicket()!=scope.publicationSerial())return missingIdlePermit("CANCEL_TICKET_SERIAL_MISMATCH");
            if(!cancel.cleanupCurrent(true))return missingIdlePermit("ACTUAL_CANCEL_CLEANUP_FALSE");
            if(!root.ownDisableMatches(actualDisable,disabled,parent()))return missingIdlePermit("WHOLE_DISABLE_MATCH_FALSE");
            if(!root.lifecycleCurrent(disabled))return missingIdlePermit("DISABLED_LIFECYCLE_CHANGED");
            idlePermit=new NormalIdlePermit(this);idlePermitDiagnostic="PERMIT_ISSUED";return idlePermit;
        }
        public Module parent(){return transaction.interaction.source.parent;}
        public McOwnedRotation.NormalIdleCertificate observedNormalIdleCertificate(){return idlePermit==null?null:idlePermit.latest();}
        public boolean consumed(){return consumed;}
        public boolean current(){
            if(consumed||consuming||!client.isSameThread()||transaction.actualDisable!=actualDisable||root!=VeltraphBootstrap.moduleManager()
                ||!root.ownDisableMatches(actualDisable,disabled,parent())||!root.lifecycleCurrent(disabled))return false;
            var leaf=transaction.checkedCompletionLeaf(true);
            // No callback-bearing helper follows this last authentic native/key/private-admission leaf.
            return leaf!=null&&leaf.root==root&&leaf.disable==actualDisable&&root.lifecycleCurrent(disabled)&&leaf.current(true);
        }
    }
    /** Private normal provenance; only actual McOwned operation objects can request a witness. */
    public final class NormalIdlePermit {
        private final NativeParentCleanupToken token;private dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
        private McOwnedRotation.NormalIdleCertificate latest;private String idleControlDiagnostic="NOT_OBSERVED",idleWitnessDiagnostic="NOT_OBSERVED";
        private boolean controlUnavailable(String reason){idleControlDiagnostic=reason;return false;}
        private NormalIdleWitness witnessUnavailable(String reason){idleWitnessDiagnostic=reason;return null;}
        private NormalIdlePermit(NativeParentCleanupToken token){this.token=token;hardware=token.transaction.releasedHardware;}
        public NativeParentCleanupToken token(){return token;}public Module parent(){return token.parent();}
        public long ownedTicket(){return token.transaction.camera.receipt().actualTicket();}
        private String actualInputDiagnostic="NOT_OBSERVED";
        public boolean actualInput(dev.veltraph.client.service.mc.McInputService exact){
            if(services.input()!=exact){actualInputDiagnostic="ACTUAL_RELEASE_INPUT_IDENTITY_MISMATCH expected="+System.identityHashCode(services.input())+" actual="+System.identityHashCode(exact);return false;}
            boolean active=exact.actualNormalReleaseCurrent();actualInputDiagnostic=active?"EXACT_INSTALLED_RELEASE_CALLBACK_ACTIVE":"EXACT_RELEASE_CALLBACK_NOT_ACTIVE";return active;
        }
        public McOwnedRotation.NormalIdleCertificate latest(){return latest;}
        public boolean originalRegistered(){return !token.consumed&&token.transaction.token==token&&token.transaction.stage==CleanupStage.COMPLETE
            &&token.root.ownDisableMatches(token.actualDisable,token.disabled,parent())&&ACTIVE.get(client)==null
            &&ADMISSIONS.getOrDefault(client,0L)==token.transaction.interaction.proof.admissionVersion();}
        public boolean armCurrent(){long expected=expectedEpoch();return expected>=0&&nativeControlCurrent(expected);}
        private long expectedEpoch(){return latest==null?token.scope.epoch():latest.certifiedEpoch(this);}
        public boolean nativeControlCurrent(long expected){
            if(!originalRegistered())return controlUnavailable("ORIGINAL_REGISTRATION_ADM_CORE_FALSE");
            if(!client.isSameThread())return controlUnavailable("CLIENT_THREAD_FALSE");
            if(!McOwnedRotation.installed(client))return controlUnavailable("INSTALLATION_MISSING");
            if(token.root!=VeltraphBootstrap.moduleManager())return controlUnavailable("ROOT_CHANGED");
            if(VeltraphBootstrap.config()!=config)return controlUnavailable("CONFIG_STORE_CHANGED");
            if(client.player!=token.scope.player())return controlUnavailable("PLAYER_CHANGED");
            if(client.level!=token.scope.world())return controlUnavailable("WORLD_CHANGED");
            if(client.getConnection()!=token.scope.connection())return controlUnavailable("CONNECTION_CHANGED");
            if(client.options!=token.transaction.originalOptions)return controlUnavailable("OPTIONS_CHANGED");
            if(hardware==null)return controlUnavailable("HARDWARE_HISTORY_NULL");
            if(!hardware.current())return controlUnavailable("HARDWARE_HISTORY_OR_POSE_CHANGED");
            if(token.transaction.interaction.source.rotation.requestGeneration()!=token.scope.requestGeneration())return controlUnavailable("SERVICE_GENERATION_CHANGED");
            if(McOwnedRotation.publicationSerial(client)!=ownedTicket())return controlUnavailable("ACTUAL_PUBLICATION_SERIAL_CHANGED");
            if(McOwnedRotation.epoch(client)!=expected)return controlUnavailable("ACTUAL_EPOCH_CHANGED");
            idleControlDiagnostic="EXACT_NATIVE_CONTROL_CURRENT";return true;
        }
        private boolean nativeControlWithoutHardware(long expected){return originalRegistered()&&client.isSameThread()&&McOwnedRotation.installed(client)
            &&token.root==VeltraphBootstrap.moduleManager()&&VeltraphBootstrap.config()==config&&client.player==token.scope.player()&&client.level==token.scope.world()
            &&client.getConnection()==token.scope.connection()&&client.options==token.transaction.originalOptions
            &&token.transaction.interaction.source.rotation.requestGeneration()==token.scope.requestGeneration()&&McOwnedRotation.publicationSerial(client)==ownedTicket()&&McOwnedRotation.epoch(client)==expected;}
        public NormalIdleWitness witness(McOwnedRotation.NormalOperation operation){
            if(operation==null)return witnessUnavailable("ACTUAL_OPERATION_NULL");
            if(!operation.belongsTo(this))return witnessUnavailable("ACTUAL_OPERATION_IDENTITY_FALSE");
            if(!originalRegistered())return witnessUnavailable("ORIGINAL_REGISTRATION_ADM_CORE_FALSE");
            if(parent().isEnabled())return witnessUnavailable("PARENT_ENABLED");
            if(!token.root.lifecycleCurrent(token.disabled))return witnessUnavailable("DISABLED_LIFECYCLE_CHANGED");
            try {long epoch=expectedEpoch();if(epoch<0)return witnessUnavailable("PREDECESSOR_CERT_NOT_CURRENT");if(!nativeControlCurrent(epoch))return witnessUnavailable("NATIVE_CONTROL_FALSE");var transaction=token.transaction;
                var policy=transaction.completion.finalPolicySnapshot();var inventory=transaction.inventory.finalNativeSnapshot();var menu=transaction.menu.finalNativeSnapshot();
                if(policy==null)return witnessUnavailable("ACTUAL_POLICY_SNAPSHOT_NULL");
                if(inventory==null)return witnessUnavailable("ACTUAL_INVENTORY_SNAPSHOT_NULL");
                if(menu==null)return witnessUnavailable("ACTUAL_MENU_SNAPSHOT_NULL");
                if(!transaction.completion.cleanupSourceCurrent())return witnessUnavailable("NORMAL_CLEANUP_SOURCE_FALSE");
                if(!transaction.completion.strictPolicyCurrent())return witnessUnavailable("ORIGINAL_STRICT_POLICY_FALSE");
                var input=token.root.inputOwner().orElse(null);var camera=McOwnedRotation.authority(client);long now=services.clock().nowMillis();
                if(input!=null)return witnessUnavailable("ACTUAL_ROOT_INPUT_OWNER_PRESENT");
                if(camera!=null)return witnessUnavailable("ACTUAL_CAMERA_OWNER_PRESENT");
                if(now<transaction.issuedAt)return witnessUnavailable("CLOCK_BEFORE_ISSUED_AT");
                if(latest!=null&&!latest.sampledTimeCurrent(now))return witnessUnavailable("PREDECESSOR_CLOCK_SOURCE_FALSE");
                var result=new NormalIdleWitness(this,operation,policy,inventory,menu,now,epoch,hardware);
                if(!result.current(epoch,hardware))return witnessUnavailable("FINAL_NATIVE_WITNESS_FALSE");
                idleWitnessDiagnostic="EXACT_NATIVE_WITNESS_CURRENT";return result;
            }catch(RuntimeException unavailable){return witnessUnavailable("ORIGINAL_BOUNDARY_EXCEPTION_"+unavailable.getClass().getSimpleName());}
        }
        /** Source input history advances only after the real Root drop's actual privately retained nine receipts. */
        public boolean acceptRootDrop(McOwnedRotation.NormalRootInputDrop exact){
            if(exact==null||!exact.belongsTo(this)||hardware==null||!hardware.mousePoseCurrent())return false;
            for(var key:LegacyTravelRetirement.Key.values()){var receipt=exact.actualKey(key.ordinal());
                if(receipt==null||!receipt.confirmed()||receipt.held()||receipt.key()!=token.transaction.interaction.source.key(key)
                    ||!receipt.normalIdleHistoryAdvancedFrom(hardware.keyObservation(receipt.key())))return false;}
            var after=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.capture(client);if(after==null)return false;
            if(!exact.afterCurrent(after))return false;hardware=after;after.watchAcceptedNormalDropAttempts();return true;
        }
        public boolean acceptNativeCancellation(McOwnedRotation.NormalIdleCertificate exact){
            if(exact==null||!exact.belongsTo(this)||exact.predecessor()!=latest||!exact.nativeCurrent(this))return false;latest=exact;return true;
        }
    }
    public final class NormalIdleWitness {
        private final NormalIdlePermit permit;private final McOwnedRotation.NormalOperation operation;private final Object policy,inventory,menu;
        private final long now,epoch;private final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
        private NormalIdleWitness(NormalIdlePermit permit,McOwnedRotation.NormalOperation operation,Object policy,Object inventory,Object menu,long now,long epoch,dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware){this.permit=permit;this.operation=operation;this.policy=policy;this.inventory=inventory;this.menu=menu;this.now=now;this.epoch=epoch;this.hardware=hardware;}
        public long beforeEpoch(){return epoch;}public Object hardwareIdentity(){return hardware;}
        public boolean sampledTimeCurrent(long sample){return sample>=now&&permit.token.transaction.completion.finalPolicyTimeCurrent(policy,sample);}
        public dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Scope identity(long observedEpoch){var t=permit.token;var x=t.transaction;
            return new dev.veltraph.client.rotation.NormalIdleCancellationIdentity.Scope(operation.installationIdentity(),operation.providerIdentity(),t,t.root,t.parent(),client.player,client.level,client.getConnection(),config,x.interaction.source.rotation,client.options,hardware,
                ADMISSIONS.getOrDefault(client,0L),x.interaction.source.rotation.requestGeneration(),McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),permit.ownedTicket(),
                t.root.ownDisableMatches(t.actualDisable,t.disabled,t.parent()),nativeFieldsCurrent(observedEpoch),client.player!=null&&client.level!=null,operation.rootCauseCurrent());
        }

        public boolean nativeFieldsCurrent(long expectedEpoch){var token=permit.token;var t=token.transaction;
            if(!operation.belongsTo(permit)||!operation.rootCauseCurrent()||!permit.originalRegistered()||!token.root.lifecycleCurrent(token.disabled)
                ||!t.completion.finalPolicyCurrent(policy)||!t.completion.finalPolicyTimeCurrent(policy,now)||!t.inventory.finalNativeCurrent(inventory)||!t.menu.finalNativeCurrent(menu)
                ||!(config instanceof dev.veltraph.client.config.JsonConfigService||config instanceof dev.veltraph.client.config.MapConfigService)
                ||!t.interaction.source.capturedConfiguration.equals(configuration())||!t.freshKeysLeaf())return false;
            return client.isSameThread()&&client.player!=null&&client.level!=null&&client.getConnection()!=null
                &&client.player.level()==client.level&&client.player.isAlive()&&!client.player.isPassenger()&&!client.player.isInLava()&&!client.isPaused()
                &&!client.level.tickRateManager().isEntityFrozen(client.player)&&client.gui.screen()==null&&!client.player.isScoping()&&client.getCameraEntity()==client.player
                &&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
                &&permit.nativeControlWithoutHardware(expectedEpoch)&&token.root.lifecycleCurrent(token.disabled);
        }
        public boolean current(long expectedEpoch,dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot expectedHardware){return nativeFieldsCurrent(expectedEpoch)&&expectedHardware!=null&&expectedHardware.current();}
    }
    /** Physical sample predicates only: no role/provider/configuration supplier or clock may run at this leaf. */
    private boolean reentryNativeUsableLeaf(Module expectedParent){
        var p=client.player;var w=client.level;var connection=client.getConnection();
        if(p==null||w==null||connection==null)return false;
        return dev.veltraph.client.navigation.LegacyParentCleanupUsabilityIdentity.usable(
            new dev.veltraph.client.navigation.LegacyParentCleanupUsabilityIdentity.Evidence(client.isSameThread(),p.level()==w,p.isAlive(),p.isInLava(),p.isPassenger(),
                client.isPaused(),w.tickRateManager().isEntityFrozen(p),client.gui.screen()==null,p.isScoping(),client.getCameraEntity()==p,
                p.containerMenu==p.inventoryMenu,p.containerMenu.getCarried().isEmpty(),ScriptingInputLock.isActive()&&expectedParent.id().equals(ScriptingInputLock.ownerModuleId()),
                client.options!=null,McOwnedRotation.installed(client),VeltraphBootstrap.config()==config));
    }
    /** Admission consumes one real completed cleanup token through the actual core and Parent source enable transition. */
    public Leg beginAfterParentCleanup(long journey,Module expectedParent,NativeParentCleanupToken token,ModuleManager.OwnEnableReceipt enable){
        if(!client.isSameThread()||journey<1||expectedParent==null||token==null||enable==null||token.parent()!=expectedParent
            ||token.consumed||token.consuming||token.transaction.token!=token||token.transaction.stage!=CleanupStage.COMPLETE||ACTIVE.get(client)!=null)return null;
        token.consuming=true;
        try {
            var transaction=token.transaction;var interaction=transaction.interaction;var root=VeltraphBootstrap.moduleManager();
            var idle=token.observedNormalIdleCertificate();long expectedEpoch=token.scope.epoch();
            if(idle!=null){if(token.idlePermit==null||idle!=token.idlePermit.latest()||!idle.belongsTo(token.idlePermit))return null;
                expectedEpoch=idle.certifiedEpoch(token.idlePermit);if(expectedEpoch<0)return null;}
            long version=ADMISSIONS.getOrDefault(client,0L);var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(root!=token.root||lifecycle==null||!lifecycle.known()||version!=interaction.proof.admissionVersion()||version==Long.MAX_VALUE
                ||!root.ownDisableMatches(token.actualDisable,token.disabled,expectedParent)||!root.ownEnableMatches(enable,token.disabled,expectedParent)
                ||!(services.rotation() instanceof McRotationService rotation)||rotation!=interaction.source.rotation)return null;
            var source=transaction.completion.observeOwnReentry(enable,token.disabled);
            if(source==null||source.normalCompletion()!=transaction.completion||source.parent()!=expectedParent||!source.current()
                ||transaction.inventory==null||transaction.menu==null||transaction.inventory.handle()!=transaction.original||transaction.inventory.permit()!=transaction.permit
                ||transaction.menu.completion()!=transaction.completion||transaction.menu.permit()!=transaction.permit)return null;
            var inventorySnapshot=transaction.inventory.finalNativeSnapshot();var menuSnapshot=transaction.menu.finalReentryNativeSnapshot(source);
            if(inventorySnapshot==null||menuSnapshot==null)return null;
            // Every following source/config/role/camera callback occurs before the final authentic leaves and ACTIVE commit.
            if(root.get(expectedParent.id()).orElse(null)!=expectedParent||root.inputOwner().orElse(null)!=expectedParent||McOwnedRotation.authority(client)!=expectedParent)return null;
            var leg=new Leg(expectedParent,rotation,journey);var before=leg.sample();var after=leg.sample();
            leg.identity=LegacyTravelPublicationIdentity.Lease.admitExpected(before,after,leg,expectedParent,version,ADMISSIONS.getOrDefault(client,0L));
            if(leg.identity==null||!dev.veltraph.client.navigation.LegacyParentCleanupReentryIdentity.sameControlScopeAtCertifiedEpoch(token.scope,before,after,expectedParent,expectedEpoch))return null;
            int controllers=root.enabledControllers().size();Object input=root.inputOwner().orElse(null);Object camera=McOwnedRotation.authority(client);
            long now=services.clock().nowMillis();
            if(controllers!=1||input!=expectedParent||camera!=expectedParent||!source.sampledTimeCurrent(now)||now<transaction.issuedAt||idle!=null&&!idle.sampledTimeCurrent(now))return null;
            // Known final stores, concrete producer observations, keys, control and lifecycle are callback-free.
            if(!(config instanceof dev.veltraph.client.config.JsonConfigService||config instanceof dev.veltraph.client.config.MapConfigService)
                ||!interaction.source.capturedConfiguration.equals(configuration())||!source.current()
                ||!transaction.inventory.finalNativeCurrent(inventorySnapshot)||!transaction.menu.finalReentryNativeCurrent(menuSnapshot,source)
                ||!transaction.freshKeysLeaf()||!ScriptingInputLock.isActive()||!expectedParent.id().equals(ScriptingInputLock.ownerModuleId())
                ||client.getCameraEntity()!=client.player||client.isPaused()||client.player.isScoping()||client.level.tickRateManager().isEntityFrozen(client.player)
                ||!root.ownDisableMatches(token.actualDisable,token.disabled,expectedParent)
                ||!root.ownEnableMatches(enable,token.disabled,expectedParent)||root!=VeltraphBootstrap.moduleManager()||VeltraphBootstrap.config()!=config
                ||client.player!=token.scope.player()||client.level!=token.scope.world()||client.getConnection()!=token.scope.connection()
                ||client.options!=transaction.originalOptions||rotation.requestGeneration()!=token.scope.requestGeneration()
                ||McOwnedRotation.epoch(client)!=expectedEpoch||McOwnedRotation.publicationSerial(client)!=token.scope.publicationSerial()
                ||idle!=null&&(token.idlePermit==null||token.idlePermit.latest()!=idle||!idle.nativeCurrent(token.idlePermit))
                ||ACTIVE.get(client)!=null||ADMISSIONS.getOrDefault(client,0L)!=version||!reentryNativeUsableLeaf(expectedParent)
                ||!dev.veltraph.client.navigation.LegacyParentCleanupReentryIdentity.storedOnlyEnabled(root,lifecycle,expectedParent)||!root.lifecycleCurrent(lifecycle))return null;
            if(!transaction.issuer.consume(now,true,true))return null;
            leg.captured=before;leg.admittedVersion=version+1;token.consumed=true;ACTIVE.put(client,leg);ADMISSIONS.put(client,version+1);return leg;
        }catch(RuntimeException unavailable){return null;}finally{token.consuming=false;}
    }

}
