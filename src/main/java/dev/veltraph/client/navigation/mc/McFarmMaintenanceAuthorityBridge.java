package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.navigation.DynamicRouteConfigStamp;
import dev.veltraph.client.navigation.MaintenanceMovementAuthorityFrame;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.Optional;

/** Reads the issued Farm maintenance pair; never captures an arbitrary opaque native camera owner. */
public final class McFarmMaintenanceAuthorityBridge {
    private McFarmMaintenanceAuthorityBridge(){}
    public static final class Binding {
        private final MaintenanceMovementAuthorityFrame frame;
        private final FarmMaintenanceCoordinator coordinator;
        private final FarmMaintenanceCoordinator.Participant helper;
        private final FarmModule farm;
        private final FarmMaintenanceLease.Token token;
        private final DynamicRouteConfigStamp configStamp;
        private final SharedMovementPolicy.Purpose purpose;
        private final Vec3 goal;
        private Binding(MaintenanceMovementAuthorityFrame frame,FarmMaintenanceCoordinator coordinator,
                FarmMaintenanceCoordinator.Participant helper,FarmModule farm,FarmMaintenanceLease.Token token,
                DynamicRouteConfigStamp configStamp,SharedMovementPolicy.Purpose purpose,Vec3 goal){
            this.frame=frame;this.coordinator=coordinator;this.helper=helper;this.farm=farm;this.token=token;
            this.configStamp=configStamp;this.purpose=purpose;this.goal=goal;
        }
        public Module inputOwner(){return helper;}
        public FarmMaintenanceLease.Token cameraOwner(){return token;}
        public MaintenanceMovementAuthorityFrame frame(){return frame;}
    }
    public static Optional<Binding> capture(Minecraft client,Module inputOwner,ConfigService config,
            DynamicRouteConfigStamp configStamp,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        if(client==null||!client.isSameThread()||!(inputOwner instanceof FarmMaintenanceCoordinator.Participant helper))return Optional.empty();
        var coordinator=VeltraphBootstrap.farmMaintenance();
        if(coordinator==null||!coordinator.active())return Optional.empty();
        var token=coordinator.tokenFor(helper);
        if(token==null)return Optional.empty();
        var view=coordinator.movementAuthorityView(helper,token,purpose,goal);
        if(view==null)return Optional.empty();
        var observation=observe(client,config,configStamp,coordinator,helper,token,purpose,goal,view);
        var frame=MaintenanceMovementAuthorityFrame.capture(observation);
        if(frame.isEmpty())return Optional.empty();
        var binding=new Binding(frame.get(),coordinator,helper,view.farm(),token,configStamp,purpose,goal);
        return current(binding,client,config)?Optional.of(binding):Optional.empty();
    }
    public static boolean current(Binding binding,Minecraft client,ConfigService config){
        if(binding==null||client==null||!client.isSameThread()||binding.coordinator!=VeltraphBootstrap.farmMaintenance())return false;
        var view=binding.coordinator.movementAuthorityView(binding.helper,binding.token,binding.purpose,binding.goal);
        return view!=null&&binding.frame.current(observe(client,config,binding.configStamp,binding.coordinator,
            binding.helper,binding.token,binding.purpose,binding.goal,view));
    }
    private static MaintenanceMovementAuthorityFrame.Observation observe(Minecraft client,ConfigService config,
            DynamicRouteConfigStamp stamp,FarmMaintenanceCoordinator coordinator,FarmMaintenanceCoordinator.Participant helper,
            FarmMaintenanceLease.Token token,SharedMovementPolicy.Purpose purpose,Vec3 goal,
            FarmMaintenanceCoordinator.MovementAuthorityView view){
        var modules=VeltraphBootstrap.moduleManager();
        if(modules==null||stamp==null||client.player==null||client.level==null||client.getConnection()==null)return null;
        var controllers=modules.enabledControllers();var farm=view.farm();
        boolean registered=view.configStore()==config&&modules==view.registryIdentity()&&modules.get(helper.id()).orElse(null)==helper&&modules.get(farm.id()).orElse(null)==farm
            &&controllers.size()==2&&controllers.contains(farm)&&controllers.contains(helper)
            &&view.helper()==helper&&view.token()==token&&coordinator.tokenFor(helper)==token
            &&coordinator.effectiveInputOwner()==helper&&coordinator.rotationAuthorityToken()==token
            &&coordinator.capturedPose(token)==view.capturedPose();
        boolean healthy=client.player==view.player()&&client.level==view.world()&&client.getConnection()==view.connection()
            &&client.player.level()==client.level&&client.player.isAlive()&&!client.isPaused()&&client.gui.screen()==null
            &&client.getCameraEntity()==client.player&&client.player.containerMenu==client.player.inventoryMenu
            &&client.player.inventoryMenu.getCarried().isEmpty()&&!client.player.isScoping()
            &&!client.level.tickRateManager().isEntityFrozen(client.player)&&McOwnedRotation.installed(client);
        var pair=new MaintenanceMovementAuthorityFrame.Pair(coordinator,view.captureIdentity(),view.sourcePolicyIdentity(),farm,helper,token,view.capturedPose(),view.restorationWitness());
        var context=new MaintenanceMovementAuthorityFrame.Context(client.player,client.level,client.getConnection(),config,
            config.revision(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER).orElse(-1),
            config.revision(dev.veltraph.client.config.TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));
        boolean policyCurrent=VeltraphBootstrap.config()==config&&stamp.current(config);
        var finalView=coordinator.movementAuthorityView(helper,token,purpose,goal);
        boolean sourcePhaseProved=finalView!=null&&finalView.captureIdentity()==view.captureIdentity()
            &&finalView.sourcePolicyIdentity()==view.sourcePolicyIdentity()&&finalView.registryIdentity()==modules&&finalView.phase()==view.phase()
            &&finalView.token()==token&&finalView.capturedPose()==view.capturedPose()&&finalView.restorationWitness()==view.restorationWitness()
            &&finalView.player()==view.player()&&finalView.world()==view.world()&&finalView.connection()==view.connection();
        return new MaintenanceMovementAuthorityFrame.Observation(pair,context,view.phase(),purpose,goal,view.capturedPose().position(),
            modules.inputOwner().orElse(null),McOwnedRotation.authority(client),helper.id(),ScriptingInputLock.ownerModuleId(),
            registered,farm.isEnabled()&&farm.isSuspendedForMaintenance()&&farm.maintenanceOwnedBy(token),
            helper.isEnabled(),!helper.isPassiveObserver()&&helper.ownsGameplayInput(),coordinator.active(),sourcePhaseProved,healthy,
            policyCurrent,ScriptingInputLock.isActive());
    }
    private static final class LoadedReturnOwnedUseSnapshot {
        final Binding binding;final FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority source;
        private LoadedReturnOwnedUseSnapshot(Binding binding,FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority source){this.binding=binding;this.source=source;}
    }
    /** Actual private restored loaded-return source alone can wrap the unchanged canonical maintenance Binding. */
    public static Object captureLoadedReturnOwnedUseSnapshot(Binding binding,Minecraft client,ConfigService config,
            FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority source){
        if(binding==null||source==null||client==null||!client.isSameThread()||config!=binding.frame.context().configStore()
            ||!loadedReturnBindingFieldsCurrent(binding,client,config,source)||!source.current())return null;
        var actual=new LoadedReturnOwnedUseSnapshot(binding,source);
        return loadedReturnOwnedUseSnapshotCurrent(binding,client,config,actual)?actual:null;
    }
    private static boolean loadedReturnBindingFieldsCurrent(Binding binding,Minecraft client,ConfigService config,
            FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority source){
        if(binding==null||source==null||client==null||!client.isSameThread()||binding.coordinator!=VeltraphBootstrap.farmMaintenance()
            ||source.coordinatorIdentity()!=binding.coordinator||source.originalFarm()!=binding.farm||source.inputOwner()!=binding.helper
            ||source.cameraOwner()!=binding.token||source.configStoreIdentity()!=config||config!=binding.frame.context().configStore()
            ||VeltraphBootstrap.config()!=config||binding.purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN
            ||source.canonicalTargetPoint()!=binding.frame.pair().capturedPose()||!binding.goal.equals(source.canonicalTarget()))return false;
        var pair=binding.frame.pair();var context=binding.frame.context();
        return pair.coordinator()==binding.coordinator&&pair.capture()==source.originalCaptureIdentity()
            &&pair.sourcePolicy()==source.sourcePolicyIdentity()&&pair.farm()==binding.farm&&pair.helper()==binding.helper
            &&pair.token()==binding.token&&pair.restorationWitness()==source.restorationWitnessIdentity()
            &&source.registryIdentity()==VeltraphBootstrap.moduleManager()&&client.player==context.player()
            &&client.level==context.world()&&client.getConnection()==context.connection();
    }
    /** Exact source/native final veto only; no movement view or helper-active factory is reused. */
    public static boolean loadedReturnOwnedUseSnapshotCurrent(Binding binding,Minecraft client,ConfigService config,Object opaque){
        return opaque instanceof LoadedReturnOwnedUseSnapshot actual&&actual.binding==binding
            &&loadedReturnBindingFieldsCurrent(binding,client,config,actual.source)&&actual.source.nativeCurrent()
            &&loadedReturnBindingFieldsCurrent(binding,client,config,actual.source);
    }
    private static final class LoadedReturnCleanupSnapshot {
        final LoadedReturnOwnedUseSnapshot original;
        private LoadedReturnCleanupSnapshot(LoadedReturnOwnedUseSnapshot original){this.original=original;}
    }
    public static Object loadedReturnCleanupSnapshot(Binding binding,Minecraft client,ConfigService config,Object opaque){
        if(!(opaque instanceof LoadedReturnOwnedUseSnapshot original)||original.binding!=binding
            ||!loadedReturnBindingFieldsCurrent(binding,client,config,original.source)||!original.source.cleanupCurrent())return null;
        var actual=new LoadedReturnCleanupSnapshot(original);
        return loadedReturnCleanupSnapshotCurrent(binding,client,config,opaque,actual)?actual:null;
    }
    public static boolean loadedReturnCleanupSnapshotCurrent(Binding binding,Minecraft client,ConfigService config,Object original,Object opaque){
        return original instanceof LoadedReturnOwnedUseSnapshot source&&opaque instanceof LoadedReturnCleanupSnapshot actual
            &&actual.original==source&&source.binding==binding&&loadedReturnBindingFieldsCurrent(binding,client,config,source.source)
            &&source.source.cleanupFinalNative()&&loadedReturnBindingFieldsCurrent(binding,client,config,source.source);
    }
    public static Object loadedReturnCleanupCameraOwner(Object opaque){return opaque instanceof LoadedReturnCleanupSnapshot actual
        ?actual.original.source.cleanupCameraOwner():null;}
    private static final class OwnedUseSnapshot {
        final Binding original;final FarmMaintenanceCoordinator.MovementGameplayAuthority source;
        private OwnedUseSnapshot(Binding original,FarmMaintenanceCoordinator.MovementGameplayAuthority source){this.original=original;this.source=source;}
    }
    /** Only actual issued Binding and genuine GAMEPLAY source mint the opaque last-use capsule. */
    public static Object ownedUseSnapshot(Binding binding,Minecraft client,ConfigService config){
        if(binding==null||client==null||!client.isSameThread()||config!=binding.frame.context().configStore())return null;
        var source=binding.coordinator.movementGameplayAuthority(binding.helper,binding.token,binding.purpose,binding.goal);
        if(source==null||!current(binding,client,config))return null;
        var actual=new OwnedUseSnapshot(binding,source);return ownedUseSnapshotCurrent(binding,client,config,actual)?actual:null;
    }
    /** No movementAuthorityView/role/provider/settings/config callback at this final source leaf. */
    public static boolean ownedUseSnapshotCurrent(Binding binding,Minecraft client,ConfigService config,Object exact){
        return exact instanceof OwnedUseSnapshot actual&&actual.original==binding&&binding!=null&&client!=null&&client.isSameThread()
            &&binding.coordinator==VeltraphBootstrap.farmMaintenance()&&config==binding.frame.context().configStore()&&VeltraphBootstrap.config()==config
            &&binding.coordinator.movementGameplayAuthorityStillCurrent(actual.source,binding.helper,binding.token,binding.purpose,binding.goal);
    }
    /** Pair proof plus the caller's original held native fence; fresh raw getter values are not a receipt. */
    public static boolean cancellationCurrent(Binding binding,Minecraft client,ConfigService config,
            dev.veltraph.client.service.mc.McRotationService rotation,long expectedEpoch,long expectedSerial,long expectedGeneration){
        if(binding==null||client==null||!client.isSameThread()||binding.coordinator!=VeltraphBootstrap.farmMaintenance()
                ||config!=binding.frame.context().configStore()||VeltraphBootstrap.config()!=config||rotation==null
                ||expectedEpoch<0||expectedSerial<=0||expectedGeneration<0||McOwnedRotation.epoch(client)!=expectedEpoch
                ||McOwnedRotation.publicationSerial(client)!=expectedSerial||rotation.requestGeneration()!=expectedGeneration
                ||!McOwnedRotation.installed(client)||McOwnedRotation.authority(client)!=binding.token)return false;
        var view=binding.coordinator.movementCancellationView(binding.helper,binding.token);
        if(view==null||view.configStore()!=config||view.rotationServiceIdentity()!=rotation)return false;
        var modules=VeltraphBootstrap.moduleManager();if(modules==null)return false;var controllers=modules.enabledControllers();
        boolean paired=modules==view.registryIdentity()&&modules.get(binding.helper.id()).orElse(null)==binding.helper
            &&modules.get(binding.farm.id()).orElse(null)==binding.farm&&controllers.size()==2
            &&controllers.contains(binding.farm)&&controllers.contains(binding.helper);
        var pair=new MaintenanceMovementAuthorityFrame.Pair(binding.coordinator,view.captureIdentity(),view.sourcePolicyIdentity(),
            view.farm(),view.helper(),view.token(),view.capturedPose(),view.restorationWitness());
        var ctx=new MaintenanceMovementAuthorityFrame.Context(client.player,client.level,client.getConnection(),config,-1,-1);
        var observation=new MaintenanceMovementAuthorityFrame.Observation(pair,ctx,view.phase(),binding.purpose,binding.goal,
            view.capturedPose().position(),modules.inputOwner().orElse(null),McOwnedRotation.authority(client),binding.helper.id(),
            ScriptingInputLock.ownerModuleId(),paired,binding.farm.maintenanceOwnedBy(binding.token),binding.helper.isEnabled(),
            !binding.helper.isPassiveObserver()&&binding.helper.ownsGameplayInput(),binding.coordinator.active(),true,false,false,ScriptingInputLock.isActive());
        boolean eligible=binding.frame.cancellationCurrent(observation);
        return dev.veltraph.client.navigation.MaintenanceAuthorityReadFence.afterReads(eligible,
            ()->cancellationSourceCurrent(binding,view,client,config,rotation),
            ()->nativeFenceCurrent(binding,client,config,rotation,expectedEpoch,expectedSerial,expectedGeneration,view,null));
    }
    /** Source-authenticated closed helper window grants key release only; bare null authority is unavailable. */
    public static boolean retirementKeysCurrent(Binding binding,Minecraft client,ConfigService config,
            dev.veltraph.client.service.mc.McRotationService rotation,long expectedEpoch,long expectedSerial,long expectedGeneration){
        if(binding==null||client==null||!client.isSameThread()||binding.coordinator!=VeltraphBootstrap.farmMaintenance()
                ||rotation==null||config!=binding.frame.context().configStore()||VeltraphBootstrap.config()!=config)return false;
        var closed=binding.coordinator.movementRetirementView(binding.helper,binding.token);
        if(closed==null||closed.configStore()!=config||closed.rotationServiceIdentity()!=rotation)return false;
        var modules=VeltraphBootstrap.moduleManager();if(modules==null)return false;
        boolean registered=modules==closed.registryIdentity()&&modules.get(binding.helper.id()).orElse(null)==binding.helper
            &&modules.get(binding.farm.id()).orElse(null)==binding.farm;
        boolean noForeign=modules.enabledControllers().stream().allMatch(module->module==binding.farm&&!module.ownsGameplayInput());
        var pair=new MaintenanceMovementAuthorityFrame.Pair(binding.coordinator,closed.captureIdentity(),closed.sourcePolicyIdentity(),
            closed.farm(),closed.helper(),closed.token(),closed.capturedPose(),closed.restorationWitness());
        var ctx=new MaintenanceMovementAuthorityFrame.Context(client.player,client.level,client.getConnection(),config,-1,-1);
        var current=binding.coordinator.movementRetirementView(binding.helper,binding.token);
        boolean eligible=binding.frame.retirementKeysCurrent(new MaintenanceMovementAuthorityFrame.RetirementObservation(pair,ctx,closed,current,
            current!=null,registered,!binding.helper.isEnabled(),noForeign,modules.inputOwner().isEmpty(),McOwnedRotation.authority(client)==null,
            binding.helper.id().equals(ScriptingInputLock.ownerModuleId()),expectedEpoch,McOwnedRotation.epoch(client),expectedSerial,
            McOwnedRotation.publicationSerial(client),expectedGeneration,rotation.requestGeneration()));
        return dev.veltraph.client.navigation.MaintenanceAuthorityReadFence.afterReads(eligible,
            ()->retirementSourceCurrent(binding,closed,client,config,rotation),
            ()->nativeFenceCurrent(binding,client,config,rotation,expectedEpoch,expectedSerial,expectedGeneration,null,closed));
    }
    private static boolean sameCancellationView(FarmMaintenanceCoordinator.MovementAuthorityView first,
            FarmMaintenanceCoordinator.MovementAuthorityView last){
        return last!=null&&first.captureIdentity()==last.captureIdentity()&&first.token()==last.token()
            &&first.farm()==last.farm()&&first.helper()==last.helper()&&first.sourcePolicyIdentity()==last.sourcePolicyIdentity()
            &&first.registryIdentity()==last.registryIdentity()&&first.restorationWitness()==last.restorationWitness()
            &&first.configStore()==last.configStore()&&first.rotationServiceIdentity()==last.rotationServiceIdentity()
            &&first.capturedPose()==last.capturedPose()&&first.phase()==last.phase()
            &&first.player()==last.player()&&first.world()==last.world()&&first.connection()==last.connection();
    }
    private static boolean cancellationSourceCurrent(Binding binding,FarmMaintenanceCoordinator.MovementAuthorityView original,
            Minecraft client,ConfigService config,dev.veltraph.client.service.mc.McRotationService rotation){
        if(binding.coordinator!=VeltraphBootstrap.farmMaintenance()||config!=binding.frame.context().configStore()
                ||VeltraphBootstrap.config()!=config||rotation!=original.rotationServiceIdentity())return false;
        var modules=VeltraphBootstrap.moduleManager();if(modules==null||modules!=original.registryIdentity())return false;
        var controllers=modules.enabledControllers();
        if(controllers.size()!=2||!controllers.contains(binding.farm)||!controllers.contains(binding.helper)
                ||modules.get(binding.helper.id()).orElse(null)!=binding.helper||modules.get(binding.farm.id()).orElse(null)!=binding.farm
                ||!binding.helper.isEnabled()||binding.helper.isPassiveObserver()||!binding.helper.ownsGameplayInput()
                ||!binding.farm.maintenanceOwnedBy(binding.token)||modules.inputOwner().orElse(null)!=binding.helper
                ||McOwnedRotation.authority(client)!=binding.token||!ScriptingInputLock.isActive()
                ||!binding.helper.id().equals(ScriptingInputLock.ownerModuleId()))return false;
        // This final provider callback must still name the exact original capture/token, not merely be non-null.
        var last=binding.coordinator.movementCancellationView(binding.helper,binding.token);
        return sameCancellationView(original,last);
    }
    private static boolean retirementSourceCurrent(Binding binding,FarmMaintenanceCoordinator.MovementRetirementView original,
            Minecraft client,ConfigService config,dev.veltraph.client.service.mc.McRotationService rotation){
        if(binding.coordinator!=VeltraphBootstrap.farmMaintenance()||config!=binding.frame.context().configStore()
                ||VeltraphBootstrap.config()!=config||original.configStore()!=config||original.rotationServiceIdentity()!=rotation)return false;
        var modules=VeltraphBootstrap.moduleManager();if(modules==null||modules!=original.registryIdentity())return false;
        if(modules.get(binding.helper.id()).orElse(null)!=binding.helper||modules.get(binding.farm.id()).orElse(null)!=binding.farm
                ||binding.helper.isEnabled()||!modules.enabledControllers().stream().allMatch(module->module==binding.farm&&!module.ownsGameplayInput())
                ||!modules.inputOwner().isEmpty()||McOwnedRotation.authority(client)!=null
                ||!binding.helper.id().equals(ScriptingInputLock.ownerModuleId()))return false;
        // The source window cannot survive its original finally, or be replaced by another closure.
        return binding.coordinator.movementRetirementView(binding.helper,binding.token)==original;
    }
    /** Re-read camera, then callback-free source/window and native leaves; no raw id or role is adopted. */
    private static boolean nativeFenceCurrent(Binding binding,Minecraft client,ConfigService config,
            dev.veltraph.client.service.mc.McRotationService rotation,long expectedEpoch,long expectedSerial,long expectedGeneration,
            FarmMaintenanceCoordinator.MovementAuthorityView live,FarmMaintenanceCoordinator.MovementRetirementView retired){
        var ctx=binding.frame.context();
        Object registry=live!=null?live.registryIdentity():retired.registryIdentity();
        Object expectedCamera=retired==null?binding.token:null;
        if(!client.isSameThread()||McOwnedRotation.authority(client)!=expectedCamera)return false;
        // These last coordinator methods touch only private fields and immutable snapshots, no helper/config callbacks.
        boolean sourceCurrent=retired==null?binding.coordinator.movementCancellationStillCurrent(live)
            :binding.coordinator.movementRetirementStillCurrent(retired);
        return sourceCurrent&&binding.coordinator==VeltraphBootstrap.farmMaintenance()
            &&VeltraphBootstrap.moduleManager()==registry&&client.player==ctx.player()&&client.level==ctx.world()
            &&client.getConnection()==ctx.connection()&&config==ctx.configStore()&&VeltraphBootstrap.config()==config
            &&binding.frame.helperId().equals(ScriptingInputLock.ownerModuleId())&&(retired!=null||ScriptingInputLock.isActive())
            &&expectedEpoch>=0&&expectedSerial>0&&expectedGeneration>=0&&McOwnedRotation.installed(client)
            &&McOwnedRotation.epoch(client)==expectedEpoch&&McOwnedRotation.publicationSerial(client)==expectedSerial
            &&rotation.requestGeneration()==expectedGeneration;
    }
    /** Camera cancellation under a closed null Root scope additionally requires the actual original queued-service receipt. */
    public static boolean retirementCameraCurrent(Binding binding,Minecraft client,ConfigService config,
            dev.veltraph.client.service.mc.McRotationService rotation,
            dev.veltraph.client.service.mc.McRotationService.CleanupReceipt originalReceipt,
            long expectedEpoch,long expectedSerial,long expectedGeneration){
        return retirementKeysCurrent(binding,client,config,rotation,expectedEpoch,expectedSerial,expectedGeneration)
            &&originalReceipt!=null&&originalReceipt.service()==rotation&&originalReceipt.authority()==binding.token
            &&originalReceipt.generation()==expectedGeneration&&originalReceipt.epoch()==expectedEpoch
            &&rotation.ownsCleanup(originalReceipt,true);
    }
    private static final class QueuedRotationSource {
        final Binding binding;final Object gameplay;final dev.veltraph.client.service.mc.McRotationService rotation;
        final FarmMaintenanceCoordinator.MovementAuthorityView original;
        private QueuedRotationSource(Binding binding,Object gameplay,dev.veltraph.client.service.mc.McRotationService rotation,FarmMaintenanceCoordinator.MovementAuthorityView original){this.binding=binding;this.gameplay=gameplay;this.rotation=rotation;this.original=original;}
    }
    /** Actual issued maintenance Binding and the coordinator's original native service mint this capsule. */
    public static Object queuedRotationSourceSnapshot(Binding binding,Minecraft client,ConfigService config,dev.veltraph.client.service.mc.McRotationService rotation){
        if(binding==null||client==null||!client.isSameThread()||rotation==null)return null;
        var gameplay=ownedUseSnapshot(binding,client,config);if(gameplay==null)return null;
        var view=binding.coordinator.movementCancellationView(binding.helper,binding.token);
        if(view==null||view.rotationServiceIdentity()!=rotation||!sameQueuedPair(binding,view.captureIdentity(),view.sourcePolicyIdentity(),view.farm(),view.helper(),view.token(),view.capturedPose(),view.restorationWitness()))return null;
        var exact=new QueuedRotationSource(binding,gameplay,rotation,view);
        return queuedRotationSourceCurrent(binding,client,config,rotation,exact)?exact:null;
    }
    /** Final stored source leaf: no settings, role, authority supplier or coordinator view callback. */
    public static boolean queuedRotationSourceCurrent(Binding binding,Minecraft client,ConfigService config,dev.veltraph.client.service.mc.McRotationService rotation,Object capsule){
        return capsule instanceof QueuedRotationSource exact&&exact.binding==binding&&exact.rotation==rotation
            &&ownedUseSnapshotCurrent(binding,client,config,exact.gameplay)
            &&binding.coordinator.movementCancellationStillCurrent(exact.original);
    }
    public static final class QueuedRotationPassiveContinuation {
        private final QueuedRotationSource original;private final OwnedUseSnapshot gameplay;
        private final FarmMaintenanceCoordinator.MaintenancePassiveContinuation maintenance;
        private final Thread thread=Thread.currentThread();private boolean prepared,committed;
        private QueuedRotationPassiveContinuation(QueuedRotationSource original,OwnedUseSnapshot gameplay,
                FarmMaintenanceCoordinator.MaintenancePassiveContinuation maintenance){this.original=original;this.gameplay=gameplay;this.maintenance=maintenance;}
        public boolean prepare(){
            if(prepared||committed||!rawCurrent()||!original.binding.coordinator.movementCancellationStillCurrent(original.original))return false;
            prepared=true;return true;
        }
        public boolean rawCurrent(){return !committed&&Thread.currentThread()==thread&&original.gameplay==gameplay
            &&gameplay.original==original.binding&&original.rotation!=null&&maintenance!=null;}
        public boolean commit(){if(!prepared||!maintenance.commitWindowCurrent()||!rawCurrent())return false;committed=true;return true;}
    }
    public static QueuedRotationPassiveContinuation captureQueuedRotationPassiveContinuation(Binding binding,Minecraft client,ConfigService config,
            dev.veltraph.client.service.mc.McRotationService rotation,Object capsule,FarmMaintenanceCoordinator.MaintenancePassiveContinuation maintenance){
        if(!(capsule instanceof QueuedRotationSource exact)||exact.binding!=binding||exact.rotation!=rotation
                ||!(exact.gameplay instanceof OwnedUseSnapshot gameplay)||gameplay.original!=binding||maintenance==null
                ||!queuedRotationSourceCurrent(binding,client,config,rotation,exact)||!maintenance.retainMovement(gameplay.source))return null;
        return new QueuedRotationPassiveContinuation(exact,gameplay,maintenance);
    }
    private static boolean sameQueuedPair(Binding binding,Object capture,Object policy,Object farm,Object helper,Object token,Object pose,Object witness){
        var pair=binding.frame.pair();return pair.capture()==capture&&pair.sourcePolicy()==policy&&pair.farm()==farm&&pair.helper()==helper
            &&pair.token()==token&&pair.capturedPose()==pose&&pair.restorationWitness()==witness;
    }
    private static final class QueuedRotationCleanup {
        final QueuedRotationSource source;final FarmMaintenanceCoordinator.MovementAuthorityView live;
        final FarmMaintenanceCoordinator.MovementRetirementView retired;
        private QueuedRotationCleanup(QueuedRotationSource source,FarmMaintenanceCoordinator.MovementAuthorityView live,FarmMaintenanceCoordinator.MovementRetirementView retired){this.source=source;this.live=live;this.retired=retired;}
    }
    /** Cleanup alone: exact original capture/token and an actual coordinator live/own-disable closure. */
    public static Object queuedRotationCleanupSnapshot(Binding binding,Minecraft client,ConfigService config,dev.veltraph.client.service.mc.McRotationService rotation,Object original){
        if(!(original instanceof QueuedRotationSource source)||source.binding!=binding||source.rotation!=rotation||client==null||!client.isSameThread())return null;
        FarmMaintenanceCoordinator.MovementAuthorityView live=null;FarmMaintenanceCoordinator.MovementRetirementView retired=null;
        live=binding.coordinator.movementCancellationView(binding.helper,binding.token);
        if(live==null)retired=binding.coordinator.movementRetirementView(binding.helper,binding.token);
        if(live==null&&retired==null)return null;
        var exact=new QueuedRotationCleanup(source,live,retired);
        return queuedRotationCleanupCurrent(binding,client,config,rotation,original,exact)?exact:null;
    }
    public static Object queuedRotationCleanupCamera(Object capsule){
        return capsule instanceof QueuedRotationCleanup exact&&exact.retired==null?exact.source.binding.token:null;
    }
    /** No fresh provider observation is turned into custody. Closure identity is the originally minted one. */
    public static boolean queuedRotationCleanupCurrent(Binding binding,Minecraft client,ConfigService config,dev.veltraph.client.service.mc.McRotationService rotation,Object original,Object capsule){
        if(!(original instanceof QueuedRotationSource source)||!(capsule instanceof QueuedRotationCleanup exact)||exact.source!=source||source.binding!=binding||source.rotation!=rotation
            ||client==null||!client.isSameThread()||binding.coordinator!=VeltraphBootstrap.farmMaintenance()||VeltraphBootstrap.config()!=config||config!=binding.frame.context().configStore())return false;
        if(exact.live!=null&&exact.retired==null)return exact.live.rotationServiceIdentity()==rotation
            &&sameQueuedPair(binding,exact.live.captureIdentity(),exact.live.sourcePolicyIdentity(),exact.live.farm(),exact.live.helper(),exact.live.token(),exact.live.capturedPose(),exact.live.restorationWitness())
            &&binding.coordinator.movementCancellationStillCurrent(exact.live);
        return exact.live==null&&exact.retired!=null&&exact.retired.rotationServiceIdentity()==rotation
            &&sameQueuedPair(binding,exact.retired.captureIdentity(),exact.retired.sourcePolicyIdentity(),exact.retired.farm(),exact.retired.helper(),exact.retired.token(),exact.retired.capturedPose(),exact.retired.restorationWitness())
            &&binding.coordinator.movementRetirementStillCurrent(exact.retired);
    }

    /** Deprecated bare absence cannot prove the source retirement closure or native publication. */
    public static boolean inactiveCleanup(Binding binding,Minecraft client,ConfigService config){return false;}

}
