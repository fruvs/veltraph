package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.game.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Exact farm/helper control lease. Helpers are explicitly registered and activation is deferred. */
public final class FarmMaintenanceCoordinator {
    /** Implemented only by coordinator modules whose onEnable performs no native action or key change. */
    public interface Participant extends Module {
        /** Pure fresh capability check before capture, lease reservation, suspension or input changes. */
        default Optional<String> preReservationReadiness(){return Optional.empty();}
        default boolean prepareActivation(FarmMaintenanceLease.Token token){return true;}
        default boolean activatePrepared(FarmMaintenanceLease.Token token,BooleanSupplier activation){return activation.getAsBoolean();}
        default void cancelPrepared(FarmMaintenanceLease.Token token){}
    }
    /** Registered once by Root; only this exact George helper may request the typed Hub/Garden admission. */
    public interface GeorgeParticipant extends Participant {
        Optional<dev.veltraph.client.inventory.selling.GeorgeWarpPermit> pendingGeorgeWarp();
        void advanceIssuedGeorgeWarp();
    }
    private GeorgeParticipant registeredGeorge;private GeorgeMaintenanceAdmission georgeAdmission;
    public void registerGeorge(GeorgeParticipant helper,Policy policy,Consumer<FarmMaintenanceLease.Token> committed){
        if(registeredGeorge!=null)throw new IllegalStateException("Exact George helper already registered");
        registeredGeorge=Objects.requireNonNull(helper);register(helper,policy,committed);
    }
    /** Adapter to fresh server-acknowledged named loadout evidence, revalidated against actual gear. */
    public interface LoadoutProof {
        Object captureBinding();
        boolean restored(Object originalBinding,Object witness);
    }
    public record Policy(Set<GameArea> areas,Predicate<Screen> screenAllowed,Supplier<String> settings,
                         LoadoutProof loadout,long timeoutMs){
        public Policy {
            areas=Set.copyOf(areas);Objects.requireNonNull(screenAllowed);Objects.requireNonNull(settings);Objects.requireNonNull(loadout);
            if(timeoutMs<=0||areas.stream().anyMatch(area->!area.isSkyBlock()||area==GameArea.UNKNOWN||area==GameArea.LIMBO))throw new IllegalArgumentException("Invalid maintenance policy");
        }
    }
    public enum ReturnStatus { ACTIVE, READY, FAILED }
    private static final List<EquipmentSlot> PROTECTED=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND);
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final ModuleManager modules;private final GameContextService context;
    private final FarmMaintenanceLease lease=new FarmMaintenanceLease();private final McSavedAnchorRecovery returning;
    private final Map<Participant,Policy> policies=new IdentityHashMap<>();
    private final Map<Participant,Consumer<FarmMaintenanceLease.Token>> committedCallbacks=new IdentityHashMap<>();
    private static final class BeforeHelperLifecycle {
        final ModuleManager.LifecycleSnapshot original;private ModuleManager.LifecycleSnapshot current;
        BeforeHelperLifecycle(ModuleManager.LifecycleSnapshot actual){original=current=actual;}
    }
    private record Capture(FarmModule farm,Participant helper,Policy policy,LocalPlayer player,ClientLevel world,Object connection,
                           String profile,Map<String,String> farmSettings,String helperSettings,int slot,ItemStack tool,
                           Map<EquipmentSlot,ItemStack> gear,FarmReturnPoint pose,Pose posture,Object binding,boolean[] keys,
                           dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalPublication,
                           BeforeHelperLifecycle beforeHelperLineage,java.util.UUID originalAccount,Object originalUser,net.minecraft.client.Options options,
                           net.minecraft.client.KeyMapping[] keyMappings,Object[] keyBindingHistories){
        ModuleManager.LifecycleSnapshot beforeHelper(){return beforeHelperLineage.current;}
    }
    private dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness engineWitness;
    private FarmMaintenancePreparation<dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness> preparation;
    private java.util.function.Function<Module,Boolean> deferredActivator=module->false;
    private Runnable deferredFreshStart;private boolean[] pausedKeys;private FarmPreparationPose preparationPose;
    private Capture captured;private FarmMaintenanceLease.Token token;private Object witness;private long settledAt=-1;
    private boolean rollingBack,completing,activating;
    private MovementRetirementView movementRetirement;
    private BooleanSupplier externalControlOwned=()->false;
    /** Configuration binding also runs during Fabric init, before Minecraft establishes its client thread. */
    public void externalControlOwned(BooleanSupplier owner){externalControlOwned=Objects.requireNonNull(owner);}
    public FarmMaintenanceCoordinator(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context){
        this.client=client;this.services=services;this.config=config;this.modules=modules;this.context=context;returning=new McSavedAnchorRecovery(client,services,config,new SavedReturnAdapter());
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Maintenance lease requires client thread");}
    /** Registration stores deferred policy only; no native observation or action occurs at startup. */
    public void register(Participant helper,Policy policy){if(policies.putIfAbsent(Objects.requireNonNull(helper),Objects.requireNonNull(policy))!=null)throw new IllegalArgumentException("Maintenance participant already registered");}
    public void register(Participant helper,Policy policy,Consumer<FarmMaintenanceLease.Token> committed){register(helper,policy);committedCallbacks.put(helper,Objects.requireNonNull(committed));}
    /** Public requests are data only; arbitrary activation callbacks cannot acquire a native reservation. */
    public boolean activate(Module module,BooleanSupplier standardActivation){
        thread();if(!(module instanceof Participant helper))return standardActivation.getAsBoolean();
        queueMaintenanceIntent(helper);return false;
    }
    private PendingMaintenanceIntent pendingMaintenanceIntent;
    /** Private-created request data cannot authorize suspension, activation, input or camera publication. */
    public final class PendingMaintenanceIntent {
        private final Participant helper;private final Object request=new Object();private final Thread ownerThread=Thread.currentThread();
        private final Object player=client.player,world=client.level,connection=client.getConnection(),user=client.getUser();
        private boolean claimed;private dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceIntentDispatch claimedRoot;
        private PendingMaintenanceIntent(Participant helper){this.helper=helper;}
        public Object requestIdentity(){return request;}
        public Participant helperIdentity(){return helper;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        /** Matching context is request freshness only, never native or workflow authority. */
        public boolean requestContextCurrent(){return !claimed&&pendingMaintenanceIntent==this&&Thread.currentThread()==ownerThread
            &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.getUser()==user
            &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this;}
        public boolean claimedRequestContextCurrent(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceIntentDispatch exact){
            return claimed&&claimedRoot!=null&&claimedRoot==exact&&exact.matchesIntent(this)&&Thread.currentThread()==ownerThread
                &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.getUser()==user
                &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this;
        }
        public boolean claimForRoot(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceIntentDispatch actual){
            if(claimed||pendingMaintenanceIntent!=this||actual==null||!actual.matchesIntent(this)||Thread.currentThread()!=ownerThread)return false;
            boolean named=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=PendingMaintenanceIntent.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class
                    &&caller.getMethodName().equals("dispatchOriginalMaintenanceIntent");});
            if(!named)return false;claimed=true;claimedRoot=actual;pendingMaintenanceIntent=null;return true;
        }
    }
    private void queueMaintenanceIntent(Participant helper){if(helper!=null&&policies.containsKey(helper))pendingMaintenanceIntent=new PendingMaintenanceIntent(helper);}
    public PendingMaintenanceIntent pendingMaintenanceIntent(){return pendingMaintenanceIntent;}
    public PendingMaintenanceIntent peekPendingIntentForRoot(){return pendingMaintenanceIntent;}
    public void retireMaintenanceIntent(Participant exact){var pending=pendingMaintenanceIntent;if(pending!=null&&pending.helper==exact)pendingMaintenanceIntent=null;}
    /** Actual private Root transaction claims its one-use invocation before any providers or native observations. */
    public boolean activateFromOriginalRoot(Module module,BooleanSupplier standardActivation,
            dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceTransactionInvocation rootInvocation){
        thread();if(!(module instanceof Participant helper))return standardActivation.getAsBoolean();
        if(rootInvocation==null||rootInvocation.coordinatorIdentity()!=this||rootInvocation.moduleIdentity()!=module
                ||admissionAttempt!=null||captureRetirement!=null||rollingBack||completing
                ||failedRollbackCustody!=null&&failedRollbackCustody.retainsOriginalTuple())return false;
        var actual=new AdmissionAttempt(rootInvocation,module);admissionAttempt=actual;
        try{
            if(!rootInvocation.claimOriginalAdmission(actual)||!rootInvocation.beforeWorkflowCurrent())return false;
            if(activating&&lease.state()==FarmMaintenanceLease.State.PREPARED&&captured!=null&&captured.helper()==helper){
                if(!identities(false)||!rootInvocation.originalInvocationCurrent())return false;
                boolean returned=standardActivation.getAsBoolean();return returned&&rootInvocation.originalInvocationCurrent();
            }
            var reserved=startTransactionOriginal(helper,standardActivation,actual);
            if(reserved!=null&&rootInvocation.originalInvocationCurrent()){retireMaintenanceIntent(helper);return true;}
            if(actual.original!=null&&captured==actual.original&&token==actual.originalToken)rollback();return false;
        }catch(RuntimeException|Error failure){
            if(actual.original!=null&&captured==actual.original&&token==actual.originalToken){
                try{rollback();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)try{failure.addSuppressed(cleanup);}catch(Throwable ignored){}}
            }
            throw failure;
        }finally{if(admissionAttempt==actual)admissionAttempt=null;}
    }
    private Runnable preparedObservationBaseline=()->{};
    /** Root captures passive native state after successful reservation setup, before deferred dispatch. */
    public void preparedObservationBaseline(Runnable baseline){preparedObservationBaseline=Objects.requireNonNull(baseline);}
    public void deferredActivator(java.util.function.Function<Module,Boolean> activation){deferredActivator=Objects.requireNonNull(activation);}
    /** Only the exact PREPARED helper observer waits for its actual lease commit. */
    public boolean deferFreshStart(Module helper,Runnable observer){
        thread();if(!activating||lease.state()!=FarmMaintenanceLease.State.PREPARED||captured==null||captured.helper()!=helper)return false;
        if(deferredFreshStart!=null)throw new IllegalStateException("Duplicate prepared helper activation observer");
        deferredFreshStart=Objects.requireNonNull(observer);return true;
    }
    private boolean known(){var ctx=context.current();return ctx.onHypixel()&&ctx.inSkyBlock()&&ctx.area().isSkyBlock()&&ctx.area()!=GameArea.UNKNOWN&&ctx.area()!=GameArea.LIMBO;}
    /** No slot selection, suspension, input release, loadout capture or helper readiness mutation. */
    public Optional<String> startEligibility(Participant helper){
        thread();var policy=policies.get(helper);
        if(policy==null||lease.active())return Optional.of("No explicit maintenance policy or another lease active");
        if(externalControlOwned.getAsBoolean())return Optional.of("Another response owns gameplay control");
        if(!policy.areas().contains(GameArea.GARDEN))return Optional.of("Maintenance policy does not permit Garden acquisition");
        if(helper.isEnabled()||helper.isPassiveObserver()||!modules.all().contains(helper))return Optional.of("Maintenance helper must be registered and stopped");
        var capability=helper.preReservationReadiness();
        if(capability.isPresent())return capability;
        var controllers=modules.enabledControllers();
        if(controllers.size()!=1||!(controllers.getFirst() instanceof FarmModule farm)||farm.isSuspended())return Optional.of("Exactly one unsuspended farm is required");
        if(!known()||context.current().area()!=GameArea.GARDEN||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()
                ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Healthy Garden player with closed inventory is required");
        if(farm.maintenanceEngineReadinessError().isPresent())return farm.maintenanceEngineReadinessError();
        if(!farm.selectedFarmingToolReady()||!client.player.onGround()||!(client.player.getPose()==Pose.STANDING||client.player.getPose()==Pose.CROUCHING))return Optional.of("Validated selected farm tool and supported posture are required");
        var terrain=new McNavigator(client,services.input(),config);
        if(!terrain.standingClear(position()))return Optional.of("Farm return pose must have actual loaded safe support");
        return Optional.empty();
    }
    /** Standard guard/preStartCheck runs inside this reservation; commit is after successful deferred activation. */
    private AdmissionAttempt admissionAttempt;
    public final class AdmissionAttempt {
        private final dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceTransactionInvocation rootInvocation;
        private final Module selected;private final Thread ownerThread=Thread.currentThread();
        private Capture original;private FarmMaintenanceLease.Token originalToken;
        private AdmissionAttempt(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceTransactionInvocation rootInvocation,Module selected){this.rootInvocation=rootInvocation;this.selected=selected;}
        public dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceTransactionInvocation rootInvocationIdentity(){return rootInvocation;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        public Module moduleIdentity(){return selected;}
        private boolean originalCurrent(){return admissionAttempt==this&&Thread.currentThread()==ownerThread&&rootInvocation!=null&&rootInvocation.originalInvocationCurrent();}
    }
    /** Data-only compatibility request; only the real Root transaction can later install a reservation. */
    public FarmMaintenanceLease.Token startTransaction(Participant helper,BooleanSupplier guardedActivation){thread();queueMaintenanceIntent(helper);return null;}
    private FarmMaintenanceLease.Token startTransactionOriginal(Participant helper,BooleanSupplier guardedActivation,AdmissionAttempt actual){
        if(!actual.originalCurrent()||actual.selected!=helper||startEligibility(helper).isPresent()||!actual.originalCurrent())return null;
        var farm=(FarmModule)modules.enabledControllers().getFirst();var policy=policies.get(helper);var p=client.player;
        // Retain the genuine original Root run publication before any farm suspension or helper enable.
        var originalPublication=dev.veltraph.client.VeltraphBootstrap.originalRootPublicationFor(farm);
        var beforeHelper=modules.lifecycleSnapshot();
        var originalOptions=client.options;var originalMappings=heldMappings();var originalBindings=new Object[originalMappings.length];
        for(int i=0;i<originalMappings.length;i++)originalBindings[i]=dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.snapshot(originalMappings[i]);
        if(originalPublication==null||originalPublication!=actual.rootInvocation.originalInputPublication()
            ||!modules.lifecycleSameHistory(beforeHelper,actual.rootInvocation.originalBeforeLifecycle())||!originalPublication.currentFor(farm)||!actual.originalCurrent())return null;
        for(int i=0;i<originalMappings.length;i++)if(originalBindings[i]==null
            ||!dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(originalMappings[i],originalBindings[i]))return null;
        Object binding=policy.loadout().captureBinding();if(binding==null)return null;
        var gear=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);PROTECTED.forEach(slot->gear.put(slot,p.getItemBySlot(slot).copy()));
        int slot=farm.validatedFarmingToolSlot();
        var originalCapture=new Capture(farm,helper,policy,p,client.level,client.getConnection(),McFarmCycleCoordinator.profileId(farm),farmSettings(),policy.settings().get(),slot,
                p.getInventory().getItem(slot).copy(),Map.copyOf(gear),new FarmReturnPoint(client.level.dimension().identifier().toString(),GameArea.GARDEN,position(),p.getYRot(),p.getXRot()),p.getPose(),binding,keys(),originalPublication,new BeforeHelperLifecycle(beforeHelper),p.getUUID(),client.getUser(),originalOptions,originalMappings,originalBindings);
        // All newly introduced observation callbacks finish before the reservation is installed.
        Object originalSettings=knownGameplaySettingsSnapshot(originalCapture);
        long now=services.clock().nowMillis();
        if(!actual.originalCurrent()||lease.active()||captured!=null||token!=null||!modules.lifecycleCurrent(beforeHelper)
            ||client.player!=originalCapture.player()||client.level!=originalCapture.world()||client.getConnection()!=originalCapture.connection()
            ||!capturedBindingsCurrent(originalCapture)||!originalPublication.currentFor(farm)
            ||!actual.originalCurrent()||!modules.lifecycleCurrent(beforeHelper))return null;
        captured=originalCapture;token=lease.prepare(farm,helper,now,policy.timeoutMs());
        actual.original=originalCapture;actual.originalToken=token;
        retainedSavedMaintenanceEnable=new SavedMaintenanceEnableSource(captured,token,originalSettings);
        if(!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
                ||!rootSource.affiliates(this)||selectedSavedMaintenancePause!=null){rollback();return null;}
        var pause=new SavedMaintenancePauseCall(actual,originalCapture,token,retainedSavedMaintenanceEnable);selectedSavedMaintenancePause=pause;
        dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation nativePause=null;
        try{
            nativePause=rootSource.beginOriginalMaintenancePause(pause);
            if(nativePause==null){rollback();return null;}
            boolean returned;
            pause.nativeSuspendAttempted=true;pause.suspending=true;
            try{returned=farm.suspendForMaintenance(token);pause.suspendedSuccessfully=returned;pause.suspendReturned=true;}
            finally{pause.suspending=false;}
            rootSource.finishOriginalMaintenanceSuspend(nativePause);
            if(!returned){rollback();return null;}
            pause.restoringPosture=true;
            try{services.input().setSneakHeld(captured.posture()==Pose.CROUCHING);pause.postureReturned=true;}
            finally{pause.restoringPosture=false;}
            rootSource.finishOriginalMaintenancePause(nativePause);
            pause.pausedHeld=keys();
        }finally{
            try{if(nativePause!=null)rootSource.closeOriginalMaintenancePause(nativePause);}
            catch(RuntimeException|Error unavailable){pause.observerFailed=true;}
            finally{pause.closed=true;
                if(captured==pause.original&&token==pause.originalToken)retainedSavedMaintenancePause=pause;
                if(selectedSavedMaintenancePause==pause)selectedSavedMaintenancePause=null;}
        }
        retainedSavedMaintenancePause=pause;
        if(pause.observerFailed||!pause.completedOriginalPauseProof()){rollback();return null;}
        if(!helper.prepareActivation(token)){rollback();return null;}
        pausedKeys=keys();engineWitness=null;deferredFreshStart=null;
        preparationPose=new FarmPreparationPose(token,captured.pose().position(),preparationObservation(false));
        preparation=new FarmMaintenancePreparation<>(lease,token,farm,helper,new FarmMaintenancePreparation.Port<>(){
            public boolean valid(){return !externalControlOwned.getAsBoolean()&&identities(false)&&farm.maintenanceOwnedBy(token)&&protectedRestored()
                    &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
                    &&farm.maintenanceEngineReadinessError().isEmpty()
                    &&retainedSavedMaintenancePause!=null&&retainedSavedMaintenancePause.retainedOriginalResourcesCurrent()
                    &&(engineWitness==null||farm.maintenanceEngineUnchanged(token,engineWitness))
                    &&preparationPose.valid(token,preparationObservation(false));}
            public Optional<dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness> captureWitness(){
                if(farm.maintenanceWitnessReadinessError(token).isPresent()||!preparationPose.captureSettled(token,preparationObservation(true),true))return Optional.empty();
                var value=farm.captureMaintenanceEngineWitness(token);value.ifPresent(w->{engineWitness=w;
                    var declaration=retainedSavedMaintenanceEnable;if(declaration!=null)declaration.bindOriginalWitness(w);
                });return value;
            }
            public boolean activate(dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint){
                activating=true;pendingSavedMaintenanceEnable=retainedSavedMaintenanceEnable;
                try{return helper.activatePrepared(token,()->deferredActivator.apply(helper))&&helper.isEnabled()&&identities(false)&&protectedRestored()
                        &&farm.maintenanceEngineUnchanged(token,checkpoint)&&preparationPose.valid(token,preparationObservation(false))&&Arrays.equals(pausedKeys,keys())
                        &&client.player.getInventory().getSelectedSlot()==captured.slot();}
                finally{activating=false;pendingSavedMaintenanceEnable=null;}
            }
            public void committed(dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint){
                var committedPause=retainedSavedMaintenancePause;
                try{
                McHeldKeys.setHeld(client.options.keyUse,false);
                if(helper==registeredGeorge)georgeAdmission=new GeorgeMaintenanceAdmission(farm,helper,token,
                        new GeorgeMaintenanceAdmission.Context(captured.player(),captured.world(),captured.connection(),captured.player().getUUID()));
                var observer=deferredFreshStart;deferredFreshStart=null;if(observer!=null)observer.run();
                var callback=committedCallbacks.get(helper);if(callback!=null)callback.accept(token);
                }finally{if(committedPause!=null&&committedPause.original==captured&&committedPause.originalToken==token
                    &&lease.state()==FarmMaintenanceLease.State.HELPER_ACTIVE)committedPause.retireOriginalResources();}
            }
            public void rollback(){FarmMaintenanceCoordinator.this.rollback();}
        });
        var reservedToken=token;var reservedCapture=captured;
        boolean baselineCaptured=dev.veltraph.client.observation.unexpected.UnexpectedBaselineCapture.capture(
                preparedObservationBaseline,
                ()->token==reservedToken&&captured==reservedCapture&&lease.state()==FarmMaintenanceLease.State.PREPARED&&lease.owns(reservedToken),
                ()->cancel(reservedToken,"Prepared observation baseline unavailable"),
                failure->dev.veltraph.client.log.VeltraphLog.basic("Observation","Prepared baseline unavailable: "+failure.getClass().getSimpleName()));
        if(!baselineCaptured)return null;
        return token; // Reserved and paused; guard/onEnable/native helper actions have not run.
    }
    /** Root invokes after manual/critical/healer handling and the original terminal TIMER update. */
    public void dispatchPrepared(){
        thread();var pending=preparation;if(pending==null||lease.state()!=FarmMaintenanceLease.State.PREPARED
                ||failedRollbackCustody!=null&&failedRollbackCustody.retainsOriginalTuple())return;
        try{pending.tick(services.clock().nowMillis());}
        catch(RuntimeException failure){if(lease.state()==FarmMaintenanceLease.State.PREPARED)rollback();else cancel(token,"Prepared helper commit failed");}
    }
    private FarmPreparationPose.Observation preparationObservation(boolean support){
        var p=client.player;var receipt=NativeTeleportSignal.read(client);
        boolean supported=p!=null&&client.level!=null&&support&&new McNavigator(client,services.input(),config).standingClear(position());
        return new FarmPreparationPose.Observation(p,client.level,client.getConnection(),receipt.serial(),p==null?null:position(),
                p==null?Float.NaN:p.getYRot(),p==null?Float.NaN:p.getXRot(),p==null?null:p.getPose(),p!=null&&p.onGround(),supported,
                p==null?Double.NaN:Math.hypot(p.getDeltaMovement().x,p.getDeltaMovement().z),pausedKeys!=null&&Arrays.equals(pausedKeys,keys()));
    }
    private enum RollbackDisposition { NO_NATIVE_PAUSE_DATA_RETIREMENT, PRESERVE_OWNED_CUSTODY, COMPLETED_TYPED_ROLLBACK }
    /** Retains the original tuple and partial native episode; never grants another attempt or effect. */
    private final class FailedRollbackCustody {
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final SavedMaintenanceEnableSource declaration;private final SavedMaintenancePauseCall pause;
        private SavedMaintenanceRollbackCall partial;private boolean terminalRequested;private String terminalReason;private SavedMaintenanceRollbackTerminal terminal;private SavedMaintenanceFailedPauseQuiescence failedPauseGate;
        private FailedRollbackCustody(Capture original,FarmMaintenanceLease.Token token,SavedMaintenanceEnableSource declaration,
                SavedMaintenancePauseCall pause,SavedMaintenanceRollbackCall partial){
            this.original=original;originalToken=token;this.declaration=declaration;this.pause=pause;this.partial=partial;
        }
        private boolean retainsOriginalTuple(){return captured==original&&token==originalToken;}
    }
    private FailedRollbackCustody failedRollbackCustody,pendingRollbackFailureCustody;
    /** Pure negative veto for the retained failed tuple; this never authorizes a native cleanup. */
    public boolean failedRollbackBlocksOriginalProducer(Module exact){
        var failed=failedRollbackCustody;
        return failed!=null&&failed.retainsOriginalTuple()&&exact!=null
            &&(exact==failed.original.farm()&&modules.originalModuleLifecycleCurrent(failed.original.beforeHelper(),exact)
                ||exact==failed.original.helper()&&(failed.partial!=null&&failed.partial.helperStoppedCurrent()
                    ||failed.partial==null&&modules.originalModuleLifecycleCurrent(failed.original.beforeHelper(),exact)
                    ||failed.declaration!=null&&failed.declaration.completedHelperLifecycle!=null&&modules.originalModuleLifecycleCurrent(failed.declaration.completedHelperLifecycle,exact)));
    }
    private void rollback(){
        if(rollingBack)return;
        var original=captured;var originalToken=token;var declaration=retainedSavedMaintenanceEnable;
        var pendingDeclaration=pendingSavedMaintenanceEnable;var inputCall=selectedSavedMaintenanceInputCall;
        var originalPause=retainedSavedMaintenancePause!=null?retainedSavedMaintenancePause:selectedSavedMaintenancePause;
        // Active-helper failures keep their existing cancellation path; PREPARED cannot impersonate it.
        var phase=lease.state();
        if(phase==FarmMaintenanceLease.State.HELPER_ACTIVE||phase==FarmMaintenanceLease.State.RESTORING
                ||phase==FarmMaintenanceLease.State.RETURNING||phase==FarmMaintenanceLease.State.READY){
            cancel(originalToken,"Active helper maintenance failure");return;
        }
        if(phase!=FarmMaintenanceLease.State.PREPARED)return;
        if(failedRollbackCustody!=null&&failedRollbackCustody.retainsOriginalTuple())return;
        // Allocate failure data before any rollback native effect; finally only installs stored fields.
        var failureCustody=new FailedRollbackCustody(original,originalToken,declaration,originalPause,null);
        pendingRollbackFailureCustody=failureCustody;
        RollbackDisposition disposition=RollbackDisposition.PRESERVE_OWNED_CUSTODY;
        rollingBack=true;Throwable primaryFailure=null;
        try{disposition=rollbackOriginal();}
        catch(RuntimeException|Error failure){primaryFailure=failure;throw failure;}
        finally{
            if(captured==original&&token==originalToken){
                if(disposition==RollbackDisposition.NO_NATIVE_PAUSE_DATA_RETIREMENT||disposition==RollbackDisposition.COMPLETED_TYPED_ROLLBACK){
                    if(disposition==RollbackDisposition.NO_NATIVE_PAUSE_DATA_RETIREMENT)lease.rollback(originalToken);
                    if(lease.state()==FarmMaintenanceLease.State.IDLE){
                        captured=null;token=null;witness=null;engineWitness=null;preparation=null;deferredFreshStart=null;pausedKeys=null;preparationPose=null;
                        if(retainedSavedMaintenanceEnable==declaration)retainedSavedMaintenanceEnable=null;
                        if(pendingSavedMaintenanceEnable==pendingDeclaration)pendingSavedMaintenanceEnable=null;
                        if(selectedSavedMaintenanceInputCall==inputCall)selectedSavedMaintenanceInputCall=null;
                        if(retainedSavedMaintenancePause==originalPause)retainedSavedMaintenancePause=null;
                        if(failedRollbackCustody!=null&&failedRollbackCustody.original==original&&failedRollbackCustody.originalToken==originalToken)failedRollbackCustody=null;
                    }else failedRollbackCustody=failureCustody;
                }else if(failedRollbackCustody==null||!failedRollbackCustody.retainsOriginalTuple())
                    failedRollbackCustody=failureCustody;
            }
            if(pendingRollbackFailureCustody==failureCustody)pendingRollbackFailureCustody=null;
            rollingBack=false;
            if(failedRollbackCustody==failureCustody&&failureCustody.retainsOriginalTuple()){
                if(failureCustody.partial==null&&failureCustody.pause!=null){
                    var failedPauseGate=new SavedMaintenanceFailedPauseQuiescence(failureCustody);failureCustody.failedPauseGate=failedPauseGate;
                    failedPauseGate.gate=failureCustody.original.farm().beginFailedPauseQuiescence(failedPauseGate);
                }
                failureCustody.terminalRequested=true;
                if(failureCustody.terminalReason==null)failureCustody.terminalReason="Original rollback failed";
                try{finishFailedRollbackTerminal(failureCustody);}
                catch(RuntimeException|Error failure){if(primaryFailure!=null)suppressRollbackFailure(primaryFailure,failure);else throw failure;}
            }
        }
    }
    private RollbackDisposition rollbackOriginal(){
        var old=captured;var reservation=token;var declaration=retainedSavedMaintenanceEnable;
        var pause=retainedSavedMaintenancePause!=null?retainedSavedMaintenancePause:selectedSavedMaintenancePause;
        var pendingDeclaration=pendingSavedMaintenanceEnable;
        // Only a never-entered pause permits pure data retirement. Attempted/partial pause keeps its token.
        var selectedPause=selectedSavedMaintenancePause;
        if(old!=null&&reservation!=null&&(pause==null||!pause.nativeSuspendAttempted)
                &&(selectedPause==null||!selectedPause.nativeSuspendAttempted)&&!old.farm().maintenanceOwnedBy(reservation)
                &&!old.helper().isEnabled())return RollbackDisposition.NO_NATIVE_PAUSE_DATA_RETIREMENT;
        // No complete original pause receipt means no rollback native action, including cleanup.
        if(old==null||reservation==null||declaration==null||pause==null||pause.original!=old||pause.originalToken!=reservation
            ||pause.rootCall==null||!pause.rootCall.acceptedNormalPauseProof()||selectedSavedMaintenanceRollbackCall!=null
            ||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
            ||!rootSource.affiliates(this))return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
        var failureCustody=pendingRollbackFailureCustody;
        if(failureCustody==null||failureCustody.original!=old||failureCustody.originalToken!=reservation||failureCustody.partial!=null)
            return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
        var actual=new SavedMaintenanceRollbackCall(old,reservation,declaration,pause,engineWitness);
        failureCustody.partial=actual;selectedSavedMaintenanceRollbackCall=actual;
        dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation rootRollback=null;
        Throwable primary=null;
        try{
            actual.quiescence=old.farm().beginSavedRollbackQuiescence(actual);
            if(actual.quiescence==null)return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            rootRollback=rootSource.beginOriginalRollback(actual);if(rootRollback==null)return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            // Only the exact genuinely enabled helper may execute its own normal disable.
            if(actual.helperWasEnabled){
                completing=true;
                try{old.helper().setEnabled(false);}finally{completing=false;}
                if(actual.disabled==null||!actual.disabled.closedCurrent())return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            }
            if(!actual.restoredBeforeResume())return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            actual.capturingResources=true;
            try{actual.resources=McIssuedReturnInventory.captureSavedMaintenanceRollback(client,actual);}
            finally{actual.capturingResources=false;}
            if(actual.resources==null||!actual.resources.currentFinal())return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            if(actual.checkpoint!=null&&(!old.farm().maintenanceEngineUnchanged(reservation,actual.checkpoint)
                ||!old.farm().rebindForMaintenance(reservation,actual.checkpoint)))return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            if(!actual.restoredBeforeResume())return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            actual.resumingOriginalFarm=true;
            try{if(!old.farm().resumeAfterMaintenance(reservation))return RollbackDisposition.PRESERVE_OWNED_CUSTODY;}
            finally{actual.resumingOriginalFarm=false;}
            actual.farmReturnedTrue=true;
            if(!rootSource.publishOriginalRollback(actual,rootRollback))return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            restoreKeysForOriginalRollback(actual);
            if(!actual.resources.currentFinal()||!actual.afterOriginalInputCurrent())return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
            actual.restoreReturnedNormally=true;
        }catch(RuntimeException|Error failure){primary=failure;throw failure;}
        finally{
            try{if(rootRollback!=null){rootSource.closeOriginalRollback(rootRollback);
                actual.finalCompletionAccepted=actual.restoreReturnedNormally&&rollbackCompletionConfirmed(rootRollback);
                if(actual.restoreReturnedNormally&&!actual.finalCompletionAccepted)throw new IllegalStateException("Original rollback whole native completion unavailable");
                actual.finalizingResources=true;
                try{actual.finalResourcesAccepted=actual.finalCompletionAccepted&&actual.resources!=null&&actual.resources.currentFinal()&&actual.pause.retainedOriginalResourcesCurrent();}
                finally{actual.finalizingResources=false;}
                if(actual.finalCompletionAccepted&&!actual.finalResourcesAccepted)throw new IllegalStateException("Original rollback protected resources changed during completion");}}
            catch(RuntimeException|Error failure){actual.closeObserverFailed=true;
                if(primary!=null)suppressRollbackFailure(primary,failure);else{primary=failure;throw failure;}}
            finally{
                try{if(actual.resources!=null&&actual.finalCompletionAccepted&&actual.finalResourcesAccepted&&!actual.closeObserverFailed){
                    actual.resources.close();actual.resourcesClosed=true;}}
                catch(RuntimeException|Error failure){actual.closeObserverFailed=true;
                    if(primary!=null)suppressRollbackFailure(primary,failure);else{primary=failure;throw failure;}}
                finally{
                    actual.resourcesFinalized=true;
                    if((!actual.restoreReturnedNormally||actual.closeObserverFailed||!actual.finalCompletionAccepted||!actual.finalResourcesAccepted)
                            &&captured==old&&token==reservation)
                        failedRollbackCustody=failureCustody;
                    if(selectedSavedMaintenanceRollbackCall==actual)selectedSavedMaintenanceRollbackCall=null;
                }
            }
        }
        if(!actual.restoreReturnedNormally||actual.closeObserverFailed||!actual.finalCompletionAccepted||!actual.finalResourcesAccepted
                ||!actual.resourcesClosed||captured!=old||token!=reservation||lease.state()!=FarmMaintenanceLease.State.PREPARED||!lease.owns(reservation))
            return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
        lease.rollback(reservation);actual.leaseRetired=lease.state()==FarmMaintenanceLease.State.IDLE;
        if(!actual.leaseRetired||!old.farm().completeSavedRollbackQuiescence(actual.quiescence,actual))return RollbackDisposition.PRESERVE_OWNED_CUSTODY;
        actual.pause.retireOriginalResources();
        return RollbackDisposition.COMPLETED_TYPED_ROLLBACK;
    }
    private boolean rollbackCompletionConfirmed(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation actual){
        try{return actual!=null&&actual.normalReturnedCurrent(client);}catch(RuntimeException|Error unavailable){return false;}
    }
    private static void suppressRollbackFailure(Throwable primary,Throwable secondary){
        if(primary==null||secondary==null||primary==secondary)return;
        try{primary.addSuppressed(secondary);}catch(Throwable unavailable){/* Preserve the original native primary identity. */}
    }
    private SavedMaintenanceRollbackCall selectedSavedMaintenanceRollbackCall;
    /** Actual PREPARED rollback transaction, never a SavedResume/ARRIVED declaration. */
    public final class SavedMaintenanceRollbackCall {
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final SavedMaintenanceEnableSource declaration;private final SavedMaintenancePauseCall pause;
        private final dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint;
        private final Thread ownerThread=Thread.currentThread();private final boolean helperWasEnabled;
        private final ModuleManager.LifecycleSnapshot helperBefore;
        private dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation rootCall;
        private ModuleManager.ActualDisableStart disabling;private ModuleManager.OwnDisableReceipt disabled;
        private ModuleManager.LifecycleSnapshot stopped;
        private McIssuedReturnInventory.SavedMaintenanceRollbackSnapshot resources;
        private boolean rootClaimed,disableClaimed,inputClaimed,farmReturnedTrue,restoringKeys,restoreReturnedNormally;
        private boolean capturingResources,resourcesClosed,resourcesFinalized,closeObserverFailed,leaseRetired;private int nextRestoreIndex;
        private FarmModule.SavedRollbackQuiescence quiescence;
        private boolean quiescenceClaimed,resumingOriginalFarm,finalizingResources,finalCompletionAccepted,finalResourcesAccepted;
        public boolean originalFarmLifecycleCurrent(){return modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm());}
        public boolean claimQuiescenceCurrent(FarmModule farm){return farm==original.farm()&&!quiescenceClaimed
            &&selectedSavedMaintenanceRollbackCall==this&&rollingBack&&Thread.currentThread()==ownerThread
            &&captured==original&&token==originalToken&&lease.state()==FarmMaintenanceLease.State.PREPARED&&lease.owns(originalToken);}
        public boolean claimQuiescence(FarmModule.SavedRollbackQuiescence gate){
            if(gate==null||!claimQuiescenceCurrent(original.farm())||!original.farm().savedRollbackQuiescenceCurrent(gate,this))return false;
            quiescence=gate;quiescenceClaimed=true;return true;
        }
        public boolean originalFarmResumeCurrent(FarmModule.SavedRollbackQuiescence gate){return gate==quiescence&&quiescenceClaimed
            &&resumingOriginalFarm&&!farmReturnedTrue&&rawCurrent()&&restoredBeforeResume();}
        /** After the farm's providers, retain only exact stored original lifecycle/tuple/source fields. */
        public boolean originalFarmResumeFinalCurrent(FarmModule.SavedRollbackQuiescence gate){return gate==quiescence&&quiescenceClaimed
            &&resumingOriginalFarm&&!farmReturnedTrue&&selectedSavedMaintenanceRollbackCall==this&&rollingBack&&Thread.currentThread()==ownerThread
            &&captured==original&&token==originalToken&&lease.state()==FarmMaintenanceLease.State.PREPARED&&lease.owns(originalToken)
            &&originalFarmLifecycleCurrent()&&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
            &&client.options==original.options()&&capturedBindingsCurrent(original);}
        public boolean releaseQuiescenceCurrent(FarmModule.SavedRollbackQuiescence gate){return gate==quiescence&&quiescenceClaimed
            &&leaseRetired&&finalCompletionAccepted&&finalResourcesAccepted&&resourcesClosed&&!closeObserverFailed
            &&captured==original&&token==originalToken&&Thread.currentThread()==ownerThread&&rollingBack
            &&lease.state()==FarmMaintenanceLease.State.IDLE;}
        public boolean quiescenceHeld(){return quiescenceClaimed&&quiescence!=null&&original.farm().savedRollbackQuiescenceCurrent(quiescence,this);}
        private SavedMaintenanceRollbackKey restoreInstruction;
        private SavedMaintenanceRollbackCall(Capture original,FarmMaintenanceLease.Token token,SavedMaintenanceEnableSource declaration,
                SavedMaintenancePauseCall pause,dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint){
            this.original=original;originalToken=token;this.declaration=declaration;this.pause=pause;this.checkpoint=checkpoint;
            helperWasEnabled=original.helper().isEnabled();helperBefore=helperWasEnabled?declaration.completedHelperLifecycle:original.beforeHelper();
        }
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public SavedMaintenanceEnableSource originalEnableDeclaration(){return declaration;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation originalCompletedPause(){return pause.rootCall;}
        public FarmModule farm(){return original.farm();}public Participant helperIdentity(){return original.helper();}
        public FarmMaintenanceLease.Token maintenanceToken(){return originalToken;}
        public Object originalCaptureIdentity(){return original;}public Object originalPauseIdentity(){return original;}
        public Object originalCheckpointIdentity(){return checkpoint;}
        public boolean helperWasEnabled(){return helperWasEnabled;}
        public ModuleManager.LifecycleSnapshot originalEnabledLifecycle(){return helperBefore;}
        private boolean rawCurrent(){return selectedSavedMaintenanceRollbackCall==this&&rollingBack&&Thread.currentThread()==ownerThread
            &&captured==original&&token==originalToken&&retainedSavedMaintenanceEnable==declaration&&declaration.original==original
            &&retainedSavedMaintenancePause==pause&&pause.original==original&&pause.originalToken==originalToken
            &&pause.rootCall!=null&&pause.rootCall.acceptedNormalPauseProof()&&engineWitness==checkpoint
            &&lease.owns(originalToken)&&lease.state()==FarmMaintenanceLease.State.PREPARED&&policies.get(original.helper())==original.policy()
            &&modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())&&declaration.originalSettingsCurrent()
            &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
            &&client.options==original.options()&&client.getUser()==original.originalUser()&&original.originalAccount().equals(original.player().getUUID())
            &&capturedBindingsCurrent(original)&&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==modules&&dev.veltraph.client.VeltraphBootstrap.config()==config;}
        private boolean helperStoppedCurrent(){return helperWasEnabled?disabled!=null&&disabled.closedCurrent()&&stopped!=null&&modules.lifecycleCurrent(stopped)
            :helperBefore!=null&&modules.lifecycleCurrent(helperBefore)&&!original.helper().isEnabled();}
        public boolean claimOriginalRollback(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation actual){
            if(rootClaimed||actual==null||!actual.matchesRollback(this)||!rawCurrent())return false;
            rootClaimed=true;rootCall=actual;return beforeNativeCurrent();
        }
        public boolean beforeNativeCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesRollback(this)&&!farmReturnedTrue&&rawCurrent()
            &&helperBefore!=null&&modules.lifecycleCurrent(helperBefore)&&original.farm().maintenanceOwnedBy(originalToken);}
        public boolean claimOriginalDisable(ModuleManager.ActualDisableStart actual){
            if(disableClaimed||!helperWasEnabled||actual==null||actual.registryIdentity()!=modules||actual.parent()!=original.helper()
                ||!actual.startedFrom(helperBefore)||!actual.startingCurrent()||!beforeNativeCurrent())return false;
            disableClaimed=true;disabling=actual;return true;
        }
        public boolean beforeDisableCurrent(){return disableClaimed&&disabling!=null&&completing&&rawCurrent()
            &&disabling.startedFrom(helperBefore)&&disabling.disabledCurrent()&&original.farm().maintenanceOwnedBy(originalToken);}
        public boolean matchesCompletedDisable(ModuleManager.OwnDisableReceipt receipt){
            if(!disableClaimed||disabled!=null||receipt==null||receipt.actualStart()!=disabling||!receipt.closedCurrent()||!rawCurrent())return false;
            var next=receipt.stoppedSnapshot();
            if(next==null||!modules.ownDisableFromMatches(receipt,helperBefore,next,original.helper()))return false;
            disabled=receipt;stopped=next;return helperStoppedCurrent()&&rawCurrent();
        }
        private boolean restoredBeforeResume(){return !farmReturnedTrue&&rawCurrent()&&helperStoppedCurrent()&&identities(false)&&protectedRestored()
            &&original.farm().maintenanceOwnedBy(originalToken)&&original.farm().maintenanceEngineReadinessError().isEmpty()
            &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried()==ItemStack.EMPTY
            &&client.player.getInventory().getSelectedSlot()==original.slot()&&client.player.getPose()==original.posture()
            &&position().distanceTo(original.pose().position())<=.001
            &&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(client.player.getYRot()-original.pose().yaw()))<=.001
            &&Math.abs(client.player.getXRot()-original.pose().pitch())<=.001
            &&Arrays.equals(pausedKeys==null?pause.pausedHeld:pausedKeys,keys())&&rawCurrent();}
        public boolean prepublicationCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesRollback(this)&&farmReturnedTrue&&!restoreReturnedNormally
            &&helperStoppedCurrent()&&rawCurrent()&&resources!=null&&resources.currentFinal()
            &&original.farm().isEnabled()&&!original.farm().isSuspended()&&original.originalPublication().nativeHistoryCurrent();}
        public boolean claimOriginalPublication(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputInvocation actual){
            if(inputClaimed||actual==null||!actual.matchesSavedMaintenanceRollback(this))return false;inputClaimed=true;return prepublicationCurrent();}
        public boolean afterOriginalInputCurrent(){return inputClaimed&&prepublicationCurrent();}
        public boolean resourceCaptureCurrent(){return capturingResources&&resources==null&&!farmReturnedTrue&&!inputClaimed&&resourceSnapshotCurrent();}
        public boolean resourceSnapshotCurrent(){
            var terminal=selectedFailedRollbackTerminal;
            if(terminal!=null&&terminal.partial==this&&terminal.checkingResources)
                return !resourcesClosed&&resourcesFinalized&&resources!=null&&terminal.storedOriginalCurrent();
            if(finalizingResources)return !resourcesClosed&&finalCompletionAccepted&&rootClaimed&&rootCall!=null
                &&rootCall.closedSourceMatches(this)&&quiescenceHeld()&&selectedSavedMaintenanceRollbackCall==this&&rollingBack
                &&Thread.currentThread()==ownerThread&&captured==original&&token==originalToken&&lease.state()==FarmMaintenanceLease.State.PREPARED
                &&lease.owns(originalToken)&&resources!=null;
            return !resourcesClosed&&rootClaimed&&rootCall!=null&&rootCall.matchesRollback(this)
                &&rawCurrent()&&helperStoppedCurrent()&&(capturingResources||resources!=null);
        }
        public boolean restorationEpisodeCurrent(){return restoringKeys&&restoreInstruction!=null&&restoreInstruction.source==this
            &&restoreInstruction.index==nextRestoreIndex&&nextRestoreIndex>=0&&nextRestoreIndex<9&&inputClaimed&&farmReturnedTrue
            &&!restoreReturnedNormally&&rawCurrent()&&helperStoppedCurrent();}
        public boolean normalNativeRollbackReturnedCurrent(){return !closeObserverFailed&&restoreReturnedNormally&&!leaseRetired&&nextRestoreIndex==9&&!restoringKeys
            &&selectedSavedMaintenanceRollbackCall==this&&Thread.currentThread()==ownerThread&&captured==original&&token==originalToken
            &&lease.state()==FarmMaintenanceLease.State.PREPARED&&lease.owns(originalToken)&&helperStoppedCurrent()&&original.farm().isEnabled()&&!original.farm().isSuspended()
            &&pause.rootCall.acceptedNormalPauseProof()&&client.player==original.player()&&client.level==original.world()
            &&client.getConnection()==original.connection()&&capturedBindingsCurrent(original);}
    }
    public final class SavedMaintenanceRollbackKey {
        private final SavedMaintenanceRollbackCall source;private final int index;private final net.minecraft.client.KeyMapping key;
        private final boolean held;private boolean claimed;private dev.veltraph.client.service.mc.McHeldKeys.RollbackMaintenanceKeyInvocation nativeCall;
        private SavedMaintenanceRollbackKey(SavedMaintenanceRollbackCall source,int index){this.source=source;this.index=index;key=source.original.keyMappings()[index];held=source.original.keys()[index];}
        public Minecraft client(){return FarmMaintenanceCoordinator.this.client;}public int index(){return index;}
        public net.minecraft.client.KeyMapping key(){return key;}public boolean desiredHeld(){return held;}public boolean directSetter(){return index<5;}
        public SavedMaintenanceRollbackCall sourceCall(){return source;}public FarmModule farm(){return source.farm();}public FarmModule originalFarm(){return source.farm();}
        public Object originalBindingHistory(){return source.original.keyBindingHistories()[index];}
        public dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation rootRollbackIdentity(){return source.rootCall;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalPublicationIdentity(){return source.originalInputPublication();}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return source.originalInputPublication();}
        public boolean beforeNativeCurrent(){return nativeBeforeCurrent();}
        public boolean normalNativeCurrent(){return claimed&&nativeCall!=null&&nativeCall.matchesInstruction(this)&&nativeBeforeCurrent();}
        public boolean nativeBeforeCurrent(){return source.restorationEpisodeCurrent()&&source.restoreInstruction==this&&source.nextRestoreIndex==index
            &&source.rootCall!=null&&source.rootCall.beforeRestoreNativeCurrent(source.farm(),client)&&source.afterOriginalInputCurrent()
            &&capturedBindingsCurrent(source.original)&&key==source.original.keyMappings()[index];}
        public boolean finalNativeSourceCurrent(){return claimed&&nativeCall!=null&&nativeCall.matchesInstruction(this)&&source.restorationEpisodeCurrent()
            &&source.resources!=null&&source.resources.currentFinal()&&source.rootCall.finalRestoreNativeCurrent(source,client)
            &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried()==ItemStack.EMPTY
            &&client.player.getInventory().getSelectedSlot()==source.original.slot()&&capturedBindingsCurrent(source.original)
            &&source.declaration.originalSettingsCurrent()&&source.restorationEpisodeCurrent();}
        public boolean finalNativeCurrent(dev.veltraph.client.service.mc.McHeldKeys.RollbackMaintenanceKeyInvocation exact){
            return claimed&&nativeCall==exact&&exact!=null&&exact.matchesInstruction(this)&&finalNativeSourceCurrent();}
        public boolean claimNativeInstruction(dev.veltraph.client.service.mc.McHeldKeys.RollbackMaintenanceKeyInvocation actual){
            if(claimed||actual==null||!actual.matchesInstruction(this)||source.restoreInstruction!=this)return false;
            claimed=true;nativeCall=actual;return nativeBeforeCurrent();}
    }
    private void restoreKeysForOriginalRollback(SavedMaintenanceRollbackCall actual){
        if(actual==null||selectedSavedMaintenanceRollbackCall!=actual||actual.restoringKeys||actual.nextRestoreIndex!=0)
            throw new IllegalStateException("Original rollback key restoration unavailable");
        actual.restoringKeys=true;
        try{for(int i=0;i<9;i++){
            var instruction=new SavedMaintenanceRollbackKey(actual,i);actual.restoreInstruction=instruction;
            var returned=dev.veltraph.client.service.mc.McHeldKeys.restoreSavedMaintenanceRollbackKey(instruction);
            if(returned==null||returned.instruction()!=instruction||!returned.normallyReturned()||!returned.confirmed()||!instruction.nativeBeforeCurrent())
                throw new IllegalStateException("Original rollback key restoration refused at "+i);
            actual.nextRestoreIndex=i+1;actual.restoreInstruction=null;
        }}finally{actual.restoringKeys=false;actual.restoreInstruction=null;}
    }
    public SavedMaintenanceRollbackCall pendingSavedMaintenanceRollbackDisableForRoot(ModuleManager.ActualDisableStart actual){
        var call=selectedSavedMaintenanceRollbackCall;
        return call!=null&&call.helperWasEnabled&&completing&&actual!=null&&actual.registryIdentity()==modules&&actual.parent()==call.helperIdentity()?call:null;
    }
    /** Only the exact prepared pair may pass the controller conflict; standard readiness is unchanged. */
    public boolean permitsActivationConflict(Module running,Module starting){
        thread();return lease.state()==FarmMaintenanceLease.State.PREPARED&&captured!=null
            &&!(failedRollbackCustody!=null&&failedRollbackCustody.retainsOriginalTuple())&&running==captured.farm()&&starting==captured.helper()&&identities(false);
    }
    private Map<String,String> farmSettings(){var values=new TreeMap<String,String>();try{for(var field:FarmingConfig.class.getFields())if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(IllegalAccessException error){throw new IllegalStateException(error);}return Map.copyOf(values);}
    private Object admittedPlayer(){return georgeAdmission==null?captured.player():georgeAdmission.current().player();}
    private Object admittedWorld(){return georgeAdmission==null?captured.world():georgeAdmission.current().world();}
    public record GeorgeCapture(FarmModule farm,FarmMaintenanceLease.Token token,FarmReturnPoint originalPose,UUID playerId,Object player,Object world,Object connection){}
    public Optional<GeorgeCapture> capturedGeorge(GeorgeParticipant helper,FarmMaintenanceLease.Token candidate){
        thread();return helper==registeredGeorge&&captured!=null&&captured.helper()==helper&&candidate==token&&georgeAdmission!=null
                ?Optional.of(new GeorgeCapture(captured.farm(),token,captured.pose(),georgeAdmission.original().playerId(),admittedPlayer(),admittedWorld(),captured.connection())):Optional.empty();
    }
    private boolean georgeProtected(){
        if(captured==null||registeredGeorge!=captured.helper()||token==null||georgeAdmission==null||engineWitness==null
                ||!captured.farm().maintenanceOwnedBy(token)||!captured.farm().maintenanceEngineUnchanged(token,engineWitness)
                ||!captured.farm().isEnabled()||!registeredGeorge.isEnabled()||externalControlOwned.getAsBoolean()
                ||!McFarmCycleCoordinator.profileId(captured.farm()).equals(captured.profile())||!farmSettings().equals(captured.farmSettings())
                ||!Objects.equals(captured.policy().settings().get(),captured.helperSettings()))return false;
        for(var controller:modules.enabledControllers())if(controller!=captured.farm()&&controller!=registeredGeorge)return false;
        return true;
    }
    private boolean protectedOnCurrentPlayer(){
        return captured!=null&&client.player!=null&&ItemStack.matches(captured.tool(),client.player.getInventory().getItem(captured.slot()))
                &&PROTECTED.stream().allMatch(slot->ItemStack.matches(captured.gear().get(slot),client.player.getItemBySlot(slot)));
    }
    public boolean bindGeorgeWarp(GeorgeParticipant helper,FarmMaintenanceLease.Token candidate,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit){
        thread();return helper==registeredGeorge&&candidate==token&&georgeProtected()&&identities(true)&&protectedRestored()
                &&lease.validate(token,captured.farm(),helper,services.clock().nowMillis(),true)
                &&georgeAdmission.bind(captured.farm(),helper,token,permit,true);
    }
    public boolean pendingGeorgeWarp(GeorgeParticipant helper,FarmMaintenanceLease.Token candidate,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit){
        thread();return helper==registeredGeorge&&candidate==token&&georgeAdmission!=null&&georgeAdmission.pending(helper,candidate,permit);
    }
    /** Transit validates original engine/profile/lease, without adopting any unadmitted native pointers. */
    public boolean validGeorgeTransit(GeorgeParticipant helper,FarmMaintenanceLease.Token candidate,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit){
        thread();return pendingGeorgeWarp(helper,candidate,permit)&&georgeProtected()
                &&lease.validate(token,captured.farm(),helper,services.clock().nowMillis(),true)
                &&client.getConnection()==captured.connection()
                &&(client.player==null||client.player.isAlive()&&georgeAdmission.original().playerId().equals(client.player.getUUID()));
    }
    public boolean admitGeorgeWarp(GeorgeParticipant helper,FarmMaintenanceLease.Token candidate,dev.veltraph.client.inventory.selling.GeorgeWarpPermit permit,
            dev.veltraph.client.inventory.selling.GeorgeWarpPermit.Observation arrival){
        thread();if(!validGeorgeTransit(helper,candidate,permit)||arrival==null||arrival.player()!=client.player||arrival.world()!=client.level
                ||arrival.connection()!=client.getConnection()||client.player==null||client.level==null||client.gui.screen()!=null
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()||!protectedOnCurrentPlayer())return false;
        var destination=permit.pending().destination();var body=position();
        if(!client.player.onGround()||Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)>.02
                ||!known()||context.current().area()!=destination.area()||!client.level.dimension().identifier().toString().equals(destination.dimension())
                ||!body.equals(arrival.position())||body.distanceTo(destination.position())>destination.radius()
                ||!new McNavigator(client,services.input(),config).standingClear(body))return false;
        var nativeReceipt=NativeTeleportSignal.read(client);
        return nativeReceipt.serial()==arrival.nativeSerial()&&Objects.equals(nativeReceipt.position(),arrival.nativeTeleport())
                &&georgeAdmission.commit(captured.farm(),helper,token,permit,arrival,true);
    }
    private boolean identities(boolean committed){
        if(captured==null||client.player!=admittedPlayer()||client.level!=admittedWorld()||client.getConnection()!=captured.connection()||!client.player.isAlive()
                ||!known()||!captured.policy().areas().contains(context.current().area())||!captured.farm().isEnabled()
                ||!McFarmCycleCoordinator.profileId(captured.farm()).equals(captured.profile())||!farmSettings().equals(captured.farmSettings())
                ||!Objects.equals(captured.policy().settings().get(),captured.helperSettings()))return false;
        if(committed&&(!captured.helper().isEnabled()||!captured.farm().maintenanceOwnedBy(token)))return false;
        for(var controller:modules.enabledControllers())if(controller!=captured.farm()&&controller!=captured.helper())return false;
        return true;
    }
    private boolean screenPermitted(){return client.gui.screen()==null||(captured!=null&&captured.helper().isEnabled()&&captured.helper().allowsScreen(client.gui.screen().getTitle().getString())&&captured.policy().screenAllowed().test(client.gui.screen()));}
    public boolean valid(FarmMaintenanceLease.Token candidate){
        thread();boolean ok=lease.validate(candidate,captured==null?null:captured.farm(),captured==null?null:captured.helper(),services.clock().nowMillis(),identities(true)&&screenPermitted());
        if(!ok&&candidate==token&&captured!=null&&lease.state()!=FarmMaintenanceLease.State.PREPARED)stopCaptured();return ok;
    }
    public boolean permitsRunningLocation(Module module,GameContext ctx){thread();return captured!=null&&lease.state()!=FarmMaintenanceLease.State.PREPARED&&identities(true)&&screenPermitted()
            &&captured.policy().areas().contains(ctx.area())&&(module==captured.farm()||module==captured.helper());}
    public boolean permitsScreenForFarm(Module module,String title){thread();return captured!=null&&module==captured.farm()&&lease.state()!=FarmMaintenanceLease.State.PREPARED&&identities(true)
            &&client.gui.screen()!=null&&Objects.equals(title,client.gui.screen().getTitle().getString())&&screenPermitted();}
    public boolean suppressAutoRecovery(FarmModule farm){thread();return captured!=null&&farm==captured.farm()&&farm.maintenanceOwnedBy(token)&&lease.active();}
    public Module effectiveInputOwner(){thread();return captured!=null&&identities(true)&&lease.active()?captured.helper():null;}
    public boolean beginRestore(FarmMaintenanceLease.Token candidate){thread();return valid(candidate)&&lease.beginRestore(candidate);}
    private boolean protectedRestored(){
        if(captured==null||client.player!=admittedPlayer())return false;
        if(!ItemStack.matches(captured.tool(),client.player.getInventory().getItem(captured.slot())))return false;
        return PROTECTED.stream().allMatch(slot->ItemStack.matches(captured.gear().get(slot),client.player.getItemBySlot(slot)));
    }
    private boolean restoration(Object proof){return protectedRestored()&&captured.policy().loadout().restored(captured.binding(),proof);}
    private LoadedReturnStartup loadedReturnStartup;
    /** Private original beginReturn scope; this marker alone grants no movement or device permission. */
    private final class LoadedReturnStartup {
        final Capture original=captured;final FarmMaintenanceLease.Token issued=token;final Object restored=witness;
        final Thread thread=Thread.currentThread();
        private boolean current(){return loadedReturnStartup==this&&Thread.currentThread()==thread&&captured==original
            &&token==issued&&witness==restored&&lease.owns(issued)&&lease.state()==FarmMaintenanceLease.State.RESTORING;}
    }
    public boolean beginReturn(FarmMaintenanceLease.Token candidate,Object restoredWitness){
        thread();if(loadedReturnStartup!=null||!valid(candidate)||lease.state()!=FarmMaintenanceLease.State.RESTORING||client.gui.screen()!=null
                ||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()||!restoration(restoredWitness))return false;
        witness=restoredWitness;var startup=new LoadedReturnStartup();loadedReturnStartup=startup;
        boolean started;
        try{services.inventory().selectSlot(captured.slot());
            started=captured.helper()==registeredGeorge
                ?returning.start(captured.pose(),context.current().area(),.35,.01)
                :returning.start(captured.pose(),context.current().area());
        }finally{if(loadedReturnStartup==startup)loadedReturnStartup=null;}
        if(!started){cancel(candidate,"Captured farm pose could not be reached");return false;}
        settledAt=-1;return lease.beginReturn(candidate);
    }
    public ReturnStatus tickReturn(FarmMaintenanceLease.Token candidate){
        thread();if(!valid(candidate)||!restoration(witness)||client.gui.screen()!=null||!originalReturnSelectedSlotCurrent()){
            if(candidate==token)cancel(candidate,"Farming restoration changed during return");return ReturnStatus.FAILED;
        }
        if(lease.state()==FarmMaintenanceLease.State.READY)return readyNow()?ReturnStatus.READY:failReturn(candidate);
        if(lease.state()!=FarmMaintenanceLease.State.RETURNING)return ReturnStatus.FAILED;
        returning.tick(context.current().area());
        services.input().setSneakHeld(captured.posture()==Pose.CROUCHING);
        if(!returning.active()&&!returning.arrived(context.current().area()))return failReturn(candidate);
        if(!readyNow()){settledAt=-1;return ReturnStatus.ACTIVE;}
        long now=services.clock().nowMillis();if(settledAt<0)settledAt=now;
        if(now-settledAt<250)return ReturnStatus.ACTIVE;
        lease.ready(candidate);return ReturnStatus.READY;
    }
    private boolean readyNow(){return context.current().area()==GameArea.GARDEN&&returning.arrived(GameArea.GARDEN)&&client.player.getPose()==captured.posture()
            &&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean readyAfterOwnDisable(McSavedAnchorRecovery.ArrivalForOwnDisable original){
        return context.current().area()==GameArea.GARDEN&&returning.arrivedAfterOwnDisable(original,GameArea.GARDEN)
                &&client.player.getPose()==captured.posture()&&client.player.getInventory().getSelectedSlot()==captured.slot()
                &&client.player.containerMenu==client.player.inventoryMenu
                &&client.player.containerMenu.getCarried().isEmpty();
    }
    private ReturnStatus failReturn(FarmMaintenanceLease.Token candidate){cancel(candidate,"Exact farm return/posture proof failed");return ReturnStatus.FAILED;}
    private String cachedResumeOutcome="NOT_ATTEMPTED";
    public String cachedResumeOutcome(){return cachedResumeOutcome;}
    public boolean resume(FarmMaintenanceLease.Token candidate,Object proof){
        cachedResumeOutcome="ENTRY";
        thread();if(!valid(candidate)||lease.state()!=FarmMaintenanceLease.State.READY||!restoration(proof)||!readyNow()){cachedResumeOutcome="ENTRY_GUARD_FALSE";return false;}
        var arrived=returning.arrivalForOwnDisable(GameArea.GARDEN,captured.helper());
        if(arrived==null||selectedSavedMaintenanceResumeCall!=null
            ||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
            ||!rootSource.affiliates(this)||retainedSavedMaintenanceEnable==null)return false;
        var originalResume=new SavedMaintenanceResumeCall(captured,candidate,proof,arrived,retainedSavedMaintenanceEnable,engineWitness);
        selectedSavedMaintenanceResumeCall=originalResume;
        dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceResumeInvocation rootResume=null;
        try{
        rootResume=rootSource.beginOriginalResume(originalResume);
        if(rootResume==null)return false;
        // End the helper while the farm is still suppressed; its cleanup cannot erase newly resumed
        // farm controls. onDisable's generic cancellation hook is ignored only in this exact handoff.
        completing=true;
        try{captured.helper().setEnabled(false);}
        catch(RuntimeException failure){cachedResumeOutcome="HELPER_CLEANUP_THREW";completing=false;cancel(candidate,"Helper cleanup failed");return false;}
        finally{completing=false;}
        if(!identities(false)||!captured.farm().maintenanceOwnedBy(candidate)||!restoration(proof)||!readyAfterOwnDisable(arrived)){
            cachedResumeOutcome="AFTER_HELPER_CLEANUP_GUARD_FALSE";cancel(candidate,"Helper cleanup changed restoration/pose");return false;
        }
        // All restoration/arrival providers finish before this retained native negative resource window.
        if(!protectedRestored()){cachedResumeOutcome="ORIGINAL_PROTECTED_RESOURCES_CHANGED";cancel(candidate,"Original protected resources changed");return false;}
        originalResume.capturingResources=true;
        try{originalResume.resources=dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.captureSavedMaintenance(client,originalResume);}
        finally{originalResume.capturingResources=false;}
        if(originalResume.resources==null||!originalResume.resources.currentFinal()){
            cachedResumeOutcome="ORIGINAL_NATIVE_RESOURCES_UNAVAILABLE";cancel(candidate,"Original native resource window unavailable");return false;}
        Boolean originalEngineUnchanged=null,originalEngineRebound=null;
        if(engineWitness!=null&&(!(originalEngineUnchanged=captured.farm().maintenanceEngineUnchanged(candidate,engineWitness))
                ||!(originalEngineRebound=captured.farm().rebindForMaintenance(candidate,engineWitness)))){cachedResumeOutcome="ENGINE_REBIND_FALSE | originalUnchanged="+originalEngineUnchanged+",originalRebound="+originalEngineRebound;cancel(candidate,"Exact maintenance checkpoint rebind failed");return false;}
        if(!captured.farm().resumeAfterMaintenance(candidate)){cachedResumeOutcome="RESUME_AFTER_MAINTENANCE_FALSE";cancel(candidate,"Farm maintenance owner changed");return false;}
        originalResume.farmReturnedTrue=true;
        if(!rootSource.publishOriginalResume(originalResume,rootResume)){cachedResumeOutcome="ORIGINAL_INPUT_RESUME_REFUSED";cancel(candidate,"Original input lineage could not resume");return false;}
        cachedResumeOutcome="FINAL_RESUME_SEQUENCE_ENTERED";
        restoreKeysForOriginalResume(originalResume);
        if(originalResume.resources==null||!originalResume.resources.currentFinal())throw new IllegalStateException("Original native resources changed during key restoration");
        boolean originalLeaseResumed=lease.resume(candidate);
        originalResume.restoreReturnedNormally=originalLeaseResumed;
        if(!originalLeaseResumed)return false;
        captured=null;token=null;witness=null;engineWitness=null;preparation=null;deferredFreshStart=null;pausedKeys=null;preparationPose=null;
        if(georgeAdmission!=null)georgeAdmission.cancel();georgeAdmission=null;
        if(retainedSavedMaintenanceEnable==originalResume.declaration)retainedSavedMaintenanceEnable=null;
        pendingSavedMaintenanceEnable=null;
        // Logical terminal data only: restore9 and the actual lease resume already succeeded.
        if(!returning.retireCompletedMaintenanceJourney(originalResume))originalResume.closeObserverFailed=true;
        var selectedReturn=originalResume.originalAlignmentSource;
        if(selectedReturn!=null&&selectedSavedReturnSource==selectedReturn&&selectedReturn.original==originalResume.original
                &&selectedReturn.originalToken==originalResume.originalToken)selectedSavedReturnSource=null;
        return true;
        }finally{
            try{if(rootResume!=null)rootSource.closeOriginalResume(rootResume);}
            catch(RuntimeException|Error unavailable){originalResume.closeObserverFailed=true;}
            finally{
                try{var resources=originalResume.resources;if(resources!=null)resources.close();}
                catch(RuntimeException|Error unavailable){originalResume.closeObserverFailed=true;}
                finally{originalResume.capturingResources=false;originalResume.resourcesClosed=true;if(selectedSavedMaintenanceResumeCall==originalResume)selectedSavedMaintenanceResumeCall=null;}
            }
        }
    }
    public void tick(){thread();var failed=failedRollbackCustody;
        if(failed!=null&&failed.retainsOriginalTuple()){if(failed.terminalRequested)finishFailedRollbackTerminal(failed);return;}
        if(lease.state()==FarmMaintenanceLease.State.PREPARED){
        if(!lease.validate(token,captured==null?null:captured.farm(),captured==null?null:captured.helper(),services.clock().nowMillis(),identities(false)))rollback();
    }else if(lease.active())valid(token);}
    public boolean active(){thread();return lease.active();}
    /** Exact PREPARED caller authorization; no new source contest sample or activation is created. */
    public boolean preparedActivationValid(Participant helper,FarmMaintenanceLease.Token candidate){
        thread();return captured!=null&&captured.helper()==helper&&candidate==token&&lease.state()==FarmMaintenanceLease.State.PREPARED
                &&lease.owns(candidate)&&!externalControlOwned.getAsBoolean()&&identities(false)&&captured.farm().maintenanceOwnedBy(candidate)&&protectedRestored();
    }
    /** Cached transaction input identity also covers the paused ACK-drain phase, which owns no camera. */
    public String controlOwnerId(){
        thread();var capture=captured;
        return capture!=null&&token!=null&&lease.owns(token)&&capture.farm().isEnabled()
                &&client.player==admittedPlayer()&&client.level==admittedWorld()&&client.getConnection()==capture.connection()
                ?(capture.helper().isEnabled()?capture.helper().id():capture.farm().id()):null;
    }
    /** Read-only frame identity; tick-owned validation retains settings, geometry and gameplay authority. */
    public Object rotationAuthorityToken(){
        thread();var capture=captured;
        return capture!=null&&token!=null&&lease.owns(token)&&lease.state()!=FarmMaintenanceLease.State.PREPARED
                &&capture.helper().isEnabled()&&capture.farm().isEnabled()
                &&(registeredGeorge!=capture.helper()||registeredGeorge.pendingGeorgeWarp().isEmpty())
                &&client.player==admittedPlayer()&&client.level==admittedWorld()
                &&client.getConnection()==capture.connection()?token:null;
    }

    public void cancelActive(String reason){thread();if(retainedLoadoutLoan!=null){retainedLoadoutLoan.revoked=true;retainedLoadoutLoan.applied.revokeRetainedGear();}pendingMaintenanceIntent=null;cancel(token,reason);}
    public void beforeDisable(Module module){thread();if(retainedLoadoutLoan!=null&&module==retainedLoadoutLoan.farm){retainedLoadoutLoan.revoked=true;retainedLoadoutLoan.applied.revokeRetainedGear();}if(captured!=null&&(module==captured.farm()||module==captured.helper()))cancel(token,"Captured controller stopped");}
    public void cancel(FarmMaintenanceLease.Token candidate,String reason){
        thread();if(candidate!=token)return;
        if(lease.state()==FarmMaintenanceLease.State.PREPARED||failedRollbackCustody!=null&&failedRollbackCustody.retainsOriginalTuple()){
            requestOriginalRollbackTerminal(candidate,reason);return;
        }
        if(rollingBack||completing)return;lease.cancel(candidate,reason);stopCaptured();
    }
    /** PREPARED cancellation shares the original rollback transaction; a failed cause is never retried. */
    private void requestOriginalRollbackTerminal(FarmMaintenanceLease.Token candidate,String reason){
        if(candidate!=token||captured==null)return;
        var failed=failedRollbackCustody;
        if(failed!=null&&failed.retainsOriginalTuple()){
            if(!failed.terminalRequested){failed.terminalRequested=true;failed.terminalReason=reason;}
            finishFailedRollbackTerminal(failed);return;
        }
        var pending=pendingRollbackFailureCustody;
        if(pending!=null&&pending.retainsOriginalTuple()){
            if(!pending.terminalRequested){pending.terminalRequested=true;pending.terminalReason=reason;}
            return;
        }
        if(rollingBack||completing||activating)return;
        rollback();
        failed=failedRollbackCustody;
        if(failed!=null&&failed.retainsOriginalTuple()&&!failed.terminalRequested){failed.terminalRequested=true;failed.terminalReason=reason;}
    }
    public final class OriginalRollbackManualStop {
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final ModuleManager.ManualStopStart start;private final Thread thread=Thread.currentThread();private boolean consumed;
        private OriginalRollbackManualStop(Capture original,FarmMaintenanceLease.Token token,ModuleManager.ManualStopStart start){this.original=original;originalToken=token;this.start=start;}
    }
    /** Passive original tuple capture at the real registered Core before-stop hook. No native/model effect. */
    public OriginalRollbackManualStop captureOriginalRollbackManualStop(ModuleManager.ManualStopStart start){
        if(start==null||start.registryIdentity()!=modules||!start.startingCurrent()||!client.isSameThread()
            ||captured==null||token==null||lease.state()!=FarmMaintenanceLease.State.PREPARED
            ||!modules.originalModuleLifecycleCurrent(captured.beforeHelper(),captured.farm()))return null;
        return new OriginalRollbackManualStop(captured,token,start);
    }
    /** Actual normal Core stop retires only the captured old transaction data; replacement controls are untouched. */
    public void retireOriginalRollbackManualStop(ModuleManager.ManualStopReceipt receipt,OriginalRollbackManualStop original){
        if(original==null||original.consumed||Thread.currentThread()!=original.thread||receipt==null||!receipt.matches(original.start)
            ||!receipt.disabledOwnerFromStart(original.original.farm())||captured!=original.original||token!=original.originalToken
            ||lease.state()!=FarmMaintenanceLease.State.PREPARED||!lease.owns(original.originalToken))return;
        original.consumed=true;retireOriginalRollbackStoppedData(original.original,original.originalToken,"Original manual Core stop completed");
    }
    public final class OriginalRollbackOwnStop {
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final ModuleManager.ActualDisableStart start;private final Thread thread=Thread.currentThread();private boolean consumed;
        private OriginalRollbackOwnStop(Capture original,FarmMaintenanceLease.Token token,ModuleManager.ActualDisableStart start){this.original=original;originalToken=token;this.start=start;}
    }
    /** Original Farm stop only; helper rollback disables and the selected terminal's own stop keep their transaction. */
    public OriginalRollbackOwnStop captureOriginalRollbackOwnStop(ModuleManager.ActualDisableStart start){
        if(start==null||start.registryIdentity()!=modules||!start.startingCurrent()||!client.isSameThread()||rollingBack||selectedFailedRollbackTerminal!=null
            ||captured==null||token==null||start.parent()!=captured.farm()||lease.state()!=FarmMaintenanceLease.State.PREPARED
            ||!modules.originalModuleLifecycleCurrent(captured.beforeHelper(),captured.farm()))return null;
        return new OriginalRollbackOwnStop(captured,token,start);
    }
    public void retireOriginalRollbackOwnStop(ModuleManager.OwnDisableReceipt receipt,OriginalRollbackOwnStop original){
        if(original==null||original.consumed||Thread.currentThread()!=original.thread||receipt==null||receipt.actualStart()!=original.start
            ||!receipt.closedCurrent()||receipt.parent()!=original.original.farm()
            ||captured!=original.original||token!=original.originalToken||lease.state()!=FarmMaintenanceLease.State.PREPARED||!lease.owns(original.originalToken))return;
        original.consumed=true;retireOriginalRollbackStoppedData(original.original,original.originalToken,"Original Core farm stop completed");
    }
    private void retireOriginalRollbackStoppedData(Capture old,FarmMaintenanceLease.Token oldToken,String reason){
        if(captured!=old||token!=oldToken)return;
        var pause=retainedSavedMaintenancePause;var failed=failedRollbackCustody;
        lease.cancel(oldToken,reason);
        captured=null;token=null;witness=null;engineWitness=null;preparation=null;deferredFreshStart=null;pausedKeys=null;preparationPose=null;
        if(retainedSavedMaintenanceEnable!=null&&retainedSavedMaintenanceEnable.original==old)retainedSavedMaintenanceEnable=null;
        if(pendingSavedMaintenanceEnable!=null&&pendingSavedMaintenanceEnable.original==old)pendingSavedMaintenanceEnable=null;
        if(selectedSavedMaintenanceInputCall!=null&&selectedSavedMaintenanceInputCall.original==old)selectedSavedMaintenanceInputCall=null;
        try{if(failed!=null&&failed.original==old&&failed.originalToken==oldToken){
            failedRollbackCustody=null;var partial=failed.partial;
            if(partial!=null&&partial.resources!=null&&!partial.resourcesClosed){partial.resourcesClosed=true;partial.resources.close();}
        }}finally{if(pause!=null&&pause.original==old&&pause.originalToken==oldToken){
            pause.retireOriginalResources();if(retainedSavedMaintenancePause==pause)retainedSavedMaintenancePause=null;}}
    }
    private SavedMaintenanceRollbackTerminal selectedFailedRollbackTerminal;
    /** Private source transaction retained after the genuine rollback closes; never retries its failed resume. */
    /** Privately minted negative control for an actual partial pause; contains no normal resume/publication phase. */
    public final class SavedMaintenanceFailedPauseQuiescence {
        private final FailedRollbackCustody failed;private final Thread thread=Thread.currentThread();private boolean claimed;
        private FarmModule.SavedRollbackQuiescence gate;
        private SavedMaintenanceFailedPauseQuiescence(FailedRollbackCustody failed){this.failed=failed;}
        public FarmModule farm(){return failed.original.farm();}public FarmMaintenanceLease.Token maintenanceToken(){return failed.originalToken;}
        public boolean originalFarmLifecycleCurrent(){return modules.originalModuleLifecycleCurrent(failed.original.beforeHelper(),farm());}
        public boolean claimCurrent(){return !claimed&&failedRollbackCustody==failed&&failed.failedPauseGate==this&&failed.retainsOriginalTuple()
            &&failed.partial==null&&failed.pause!=null&&Thread.currentThread()==thread
            &&lease.state()==FarmMaintenanceLease.State.PREPARED&&lease.owns(failed.originalToken)&&originalFarmLifecycleCurrent();}
        public boolean claim(FarmModule.SavedRollbackQuiescence exact){
            if(exact==null||!claimCurrent()||!farm().failedPauseQuiescenceCurrent(exact,this))return false;
            gate=exact;claimed=true;return true;
        }
        private boolean held(){return claimed&&gate!=null&&farm().failedPauseQuiescenceCurrent(gate,this)&&originalFarmLifecycleCurrent();}
    }
    public final class SavedMaintenanceRollbackTerminal {
        private final FailedRollbackCustody failed;private final SavedMaintenanceRollbackCall partial;
        private final Thread thread=Thread.currentThread();private boolean started,closed,claimed,checkingResources;
        private dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation rootCleanup;
        private SavedMaintenanceRollbackTerminal(FailedRollbackCustody failed){this.failed=failed;partial=failed.partial;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        public SavedMaintenanceRollbackCall originalRollbackCall(){return partial;}
        public dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackInvocation originalRollbackIdentity(){return partial==null?null:partial.rootCall;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return failed.original.originalPublication();}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation originalCompletedPause(){return failed.pause==null?null:failed.pause.rootCall;}
        /** Zero effects only; independent native prefix absence is still required by the Root cleanup issuer. */
        public boolean failedPauseOnlyCurrent(){return storedOriginalCurrent()&&partial==null&&failed.pause!=null&&failed.pause.rootCall!=null
            &&failed.pause.rootCall.closedPauseSourceMatches(failed.pause)&&failed.failedPauseGate!=null&&failed.failedPauseGate.held();}
        public boolean zeroNativeRollbackCurrent(){return storedOriginalCurrent()&&partial!=null&&partial.rootCall==null&&!partial.farmReturnedTrue&&!partial.inputClaimed
            &&partial.nextRestoreIndex==0&&partial.restoreInstruction==null&&partial.resources==null&&(!partial.helperWasEnabled||partial.disabled==null)
            &&originalCompletedPause()!=null&&originalCompletedPause().acceptedNormalPauseProof();}
        public FarmModule farm(){return failed.original.farm();}public Participant helperIdentity(){return failed.original.helper();}
        public FarmMaintenanceLease.Token maintenanceToken(){return failed.originalToken;}public Minecraft client(){return FarmMaintenanceCoordinator.this.client;}
        private boolean storedOriginalCurrent(){
            if(selectedFailedRollbackTerminal!=this||failedRollbackCustody!=failed||!failed.retainsOriginalTuple()||failed.terminal!=this
                ||!failed.terminalRequested||!started||closed||Thread.currentThread()!=thread||rollingBack||completing
                ||lease.state()!=FarmMaintenanceLease.State.PREPARED||!lease.owns(failed.originalToken)
                ||client.player!=failed.original.player()||client.level!=failed.original.world()||client.getConnection()!=failed.original.connection()
                ||client.options!=failed.original.options()||client.getUser()!=failed.original.originalUser()
                ||!failed.original.originalAccount().equals(failed.original.player().getUUID())||!capturedBindingsCurrent(failed.original)
                ||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=FarmMaintenanceCoordinator.this
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules||dev.veltraph.client.VeltraphBootstrap.config()!=config)return false;
            if(partial==null)return failed.partial==null&&failed.pause!=null&&failed.pause.closed&&failed.failedPauseGate!=null&&failed.failedPauseGate.held()
                &&modules.originalModuleLifecycleCurrent(failed.original.beforeHelper(),failed.original.helper());
            return partial==failed.partial&&partial.resourcesFinalized&&!partial.restoringKeys&&partial.quiescenceHeld()&&partial.originalFarmLifecycleCurrent()
                &&(partial.helperStoppedCurrent()||partial.helperWasEnabled&&modules.originalModuleLifecycleCurrent(partial.helperBefore,partial.original.helper()));
        }
        public boolean claimNativeCleanup(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation actual){
            if(claimed||actual==null||!actual.matchesTerminal(this)||!storedOriginalCurrent())return false;
            claimed=true;rootCleanup=actual;return true;
        }
        public boolean originalCleanupResourceJournalRetained(){return partial!=null&&partial.resources!=null&&!partial.resourcesClosed
                ||failed.pause!=null&&failed.pause.originalResourceJournalRetained();}
        public boolean beforeCleanupNativeCurrent(){return claimed&&rootCleanup!=null&&storedOriginalCurrent();}
        /** Stored source leaf; per-key original native history and OTHER8 conservation remain native-owned. */
        public boolean finalCleanupNativeCurrent(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceRollbackCleanupInvocation exact){
            if(!claimed||rootCleanup!=exact||exact==null||!storedOriginalCurrent())return false;
            if(failed.pause==null||!failed.pause.retainedOriginalResourcesCurrent())return false;
            if(partial==null)return failedPauseOnlyCurrent()&&storedOriginalCurrent();
            if(partial.resources==null)return !partial.inputClaimed&&partial.nextRestoreIndex==0&&partial.restoreInstruction==null&&!partial.capturingResources&&storedOriginalCurrent();
            if(partial.resourcesClosed)return false;
            checkingResources=true;
            try{return partial.resources.currentFinal()&&failed.pause.retainedOriginalResourcesCurrent()&&storedOriginalCurrent();}
            finally{checkingResources=false;}
        }
    }
    private void finishFailedRollbackTerminal(FailedRollbackCustody failed){
        if(failed==null||failed!=failedRollbackCustody||!failed.retainsOriginalTuple()||!failed.terminalRequested||rollingBack||completing
                ||failed.terminal!=null||failed.pause==null||!failed.pause.closed
                ||(failed.partial==null?failed.failedPauseGate==null||!failed.failedPauseGate.held():!failed.partial.resourcesFinalized)
                ||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
                ||!rootSource.affiliates(this)||!rootSource.originalFailedRollbackDispatchCurrent(this))return;
        var terminal=new SavedMaintenanceRollbackTerminal(failed);failed.terminal=terminal;terminal.started=true;
        selectedFailedRollbackTerminal=terminal;Throwable primaryFailure=null;
        try{
            var completion=rootSource.finishFailedOriginalRollback(terminal);
            if(completion!=null&&completion.acceptedFor(terminal,client)&&terminal.finalCleanupNativeCurrent(terminal.rootCleanup)){
                // Native negative cleanup already conserved the original prefix. Stop only this exact old Core farm once.
                var old=failed.original;var oldToken=failed.originalToken;
                if(partialHelperStillOriginal(terminal)){
                    var helperBeforeStop=modules.lifecycleSnapshot();
                    old.helper().setEnabled(false);
                    var helperStop=modules.completedOwnDisableFrom(helperBeforeStop,old.helper()).orElse(null);
                    if(helperStop==null||!helperStop.closedCurrent())return;
                }
                if(modules.originalModuleLifecycleCurrent(old.beforeHelper(),old.farm())&&old.farm().isEnabled()){
                    var beforeStop=modules.lifecycleSnapshot();
                    old.farm().setEnabled(false);
                    var stopped=modules.completedOwnDisableFrom(beforeStop,old.farm()).orElse(null);
                    if(stopped!=null&&stopped.closedCurrent()&&captured==old&&token==oldToken){
                        lease.cancel(oldToken,failed.terminalReason);
                        captured=null;token=null;witness=null;engineWitness=null;preparation=null;deferredFreshStart=null;pausedKeys=null;preparationPose=null;
                        if(retainedSavedMaintenanceEnable==failed.declaration)retainedSavedMaintenanceEnable=null;
                        if(retainedSavedMaintenancePause==failed.pause)retainedSavedMaintenancePause=null;
                        if(pendingSavedMaintenanceEnable==failed.declaration)pendingSavedMaintenanceEnable=null;
                        if(failedRollbackCustody==failed)failedRollbackCustody=null;
                    }
                }
            }
        }catch(RuntimeException|Error failure){primaryFailure=failure;throw failure;}
        finally{
            try{if(terminal.partial!=null&&terminal.partial.resources!=null&&!terminal.partial.resourcesClosed){
                terminal.partial.resources.close();terminal.partial.resourcesClosed=true;}}
            catch(RuntimeException|Error failure){if(primaryFailure!=null)suppressRollbackFailure(primaryFailure,failure);else throw failure;}
            finally{
                try{if(failed.pause!=null)failed.pause.retireOriginalResources();}
                catch(RuntimeException|Error failure){if(primaryFailure!=null)suppressRollbackFailure(primaryFailure,failure);else throw failure;}
                finally{terminal.closed=true;if(selectedFailedRollbackTerminal==terminal)selectedFailedRollbackTerminal=null;}
            }
        }
    }
    private boolean partialHelperStillOriginal(SavedMaintenanceRollbackTerminal terminal){
        var partial=terminal.partial;return partial!=null&&partial.helperWasEnabled&&!partial.helperStoppedCurrent()
            &&modules.originalModuleLifecycleCurrent(partial.helperBefore,partial.original.helper())&&partial.original.helper().isEnabled();
    }
    private CaptureRetirement captureRetirement;
    /** Refuses new reservation reentry only; this private marker grants no native cleanup. */
    private final class CaptureRetirement {
        final Capture original;final SavedMaintenanceEnableSource declaration;
        final FarmMaintenanceLease.Token originalToken=token;final Object originalWitness=witness;
        final SavedReturnAlignmentSource savedSource=selectedSavedReturnSource;
        final LoadedReturnDeviceAuthority loadedSource=selectedLoadedReturnDeviceAuthority;
        Throwable failure;boolean helperAttempted,farmAttempted;MovementRetirementView originalMovementRetirement;
        CaptureRetirement(Capture original,SavedMaintenanceEnableSource declaration){this.original=original;this.declaration=declaration;}
        void failed(Throwable originalFailure){if(failure==null)failure=originalFailure;else if(failure!=originalFailure)failure.addSuppressed(originalFailure);}
        void throwFailure(){if(failure instanceof RuntimeException originalFailure)throw originalFailure;if(failure instanceof Error originalFailure)throw originalFailure;}
    }
    /** Original captured actors are checked independently; this marker grants no actor or native permission. */
    private void stopFailedLoadedActors(CaptureRetirement retiring,MovementRetirementView closed){
        var old=retiring.original;var declaration=retiring.declaration;if(old==null)return;
        if(!retiring.helperAttempted){
            retiring.helperAttempted=true;
            try{
                if(declaration!=null&&declaration.original==old&&declaration.completedHelperLifecycle!=null
                    &&modules.originalModuleLifecycleCurrent(declaration.completedHelperLifecycle,old.helper())&&old.helper().isEnabled()){
                    movementRetirement=closed;
                    try{old.helper().setEnabled(false);}finally{if(movementRetirement==closed)movementRetirement=null;}
                }
            }catch(RuntimeException|Error originalFailure){retiring.failed(originalFailure);}
        }
        if(!retiring.farmAttempted){
            retiring.farmAttempted=true;
            try{if(modules.originalModuleLifecycleCurrent(old.beforeHelper(),old.farm())&&old.farm().isEnabled())old.farm().setEnabled(false);}
            catch(RuntimeException|Error originalFailure){retiring.failed(originalFailure);}
        }
    }
    private void stopCaptured(){
        if(captureRetirement!=null){captureRetirement.throwFailure();return;}
        var retained=retainedOperation;if(retained!=null){retained.revoked=true;retainedOperation=null;if(retained.applied!=null)retained.applied.revokeRetainedGear();}
        if(retainedLoadoutLoan!=null){retainedLoadoutLoan.revoked=true;retainedLoadoutLoan.applied.revokeRetainedGear();}
        var old=captured;var oldToken=token;var oldWitness=witness;
        var originalSavedReturn=selectedSavedReturnSource;var originalDeclaration=retainedSavedMaintenanceEnable;
        var originalLoadedReturn=selectedLoadedReturnDeviceAuthority;
        var originalPending=pendingSavedMaintenanceEnable;var originalInputCall=selectedSavedMaintenanceInputCall;
        var retiring=new CaptureRetirement(old,originalDeclaration);captureRetirement=retiring;
        try{
            var closed=old==null||oldToken==null?null:new MovementRetirementView(old,oldToken,oldWitness,admittedPlayer(),admittedWorld(),config);
            retiring.originalMovementRetirement=closed;
            // Detach the exact old tuple before callbacks. New transactions are refused until this stack closes.
            if(captured==old&&token==oldToken){captured=null;token=null;witness=null;engineWitness=null;preparation=null;
                deferredFreshStart=null;pausedKeys=null;preparationPose=null;}
            var oldGeorge=georgeAdmission;if(oldGeorge!=null){oldGeorge.cancel();if(georgeAdmission==oldGeorge)georgeAdmission=null;}
            if(old!=null)old.helper().cancelPrepared(oldToken);
            closingSavedReturnSource=originalSavedReturn!=null&&originalSavedReturn.original==old
                &&originalSavedReturn.originalToken==oldToken&&originalSavedReturn.restoredWitness==oldWitness?originalSavedReturn:null;
            var originalClosing=closingSavedReturnSource;
            closingLoadedReturnDeviceAuthority=originalLoadedReturn!=null&&originalLoadedReturn.original==old
                &&originalLoadedReturn.originalToken==oldToken&&originalLoadedReturn.restored==oldWitness?originalLoadedReturn:null;
            var originalLoadedClosing=closingLoadedReturnDeviceAuthority;
            try{returning.cancel();}
            catch(RuntimeException|Error originalFailure){if(originalLoadedReturn==null)throw originalFailure;retiring.failed(originalFailure);}
            finally{if(retiring.failure==null){if(closingSavedReturnSource==originalClosing)closingSavedReturnSource=null;
                if(closingLoadedReturnDeviceAuthority==originalLoadedClosing)closingLoadedReturnDeviceAuthority=null;}}
            if(originalLoadedReturn!=null){stopFailedLoadedActors(retiring,closed);retiring.throwFailure();}
            else if(old!=null){
                try{
                    // Only the confirmed original helper activation may be stopped; a reenabled instance is foreign.
                    if(originalDeclaration!=null&&originalDeclaration.original==old&&originalDeclaration.completedHelperLifecycle!=null
                        &&modules.originalModuleLifecycleCurrent(originalDeclaration.completedHelperLifecycle,old.helper())&&old.helper().isEnabled()){
                        movementRetirement=closed;
                        try{old.helper().setEnabled(false);}finally{if(movementRetirement==closed)movementRetirement=null;}
                    }
                }catch(RuntimeException ignored){/* The old farm is checked independently below. */}
                finally{
                    if(modules.originalModuleLifecycleCurrent(old.beforeHelper(),old.farm())&&old.farm().isEnabled())old.farm().setEnabled(false);
                }
            }
        }catch(RuntimeException|Error originalFailure){
            if(originalLoadedReturn!=null)retiring.failed(originalFailure);
            throw originalFailure;
        }finally{
            if(originalLoadedReturn!=null&&retiring.failure!=null){
                // Retain exact negative closing custody. Existing Root/native history guards remain mandatory.
                if(originalLoadedReturn.original==old&&originalLoadedReturn.originalToken==oldToken&&originalLoadedReturn.restored==oldWitness){
                    closingLoadedReturnDeviceAuthority=originalLoadedReturn;
                    if(originalSavedReturn!=null&&originalSavedReturn.original==old&&originalSavedReturn.originalToken==oldToken
                        &&originalSavedReturn.restoredWitness==oldWitness)closingSavedReturnSource=originalSavedReturn;
                }
                stopFailedLoadedActors(retiring,retiring.originalMovementRetirement);
            }else{
            if(selectedSavedReturnSource==originalSavedReturn)selectedSavedReturnSource=null;
            if(selectedLoadedReturnDeviceAuthority==originalLoadedReturn)selectedLoadedReturnDeviceAuthority=null;
            if(retainedSavedMaintenanceEnable==originalDeclaration)retainedSavedMaintenanceEnable=null;
            if(pendingSavedMaintenanceEnable==originalPending)pendingSavedMaintenanceEnable=null;
            if(selectedSavedMaintenanceInputCall==originalInputCall)selectedSavedMaintenanceInputCall=null;
            if(captureRetirement==retiring)captureRetirement=null;
            if(failedRollbackCustody!=null&&failedRollbackCustody.original==old&&failedRollbackCustody.originalToken==oldToken)failedRollbackCustody=null;
            var oldPause=retainedSavedMaintenancePause;
            if(oldPause!=null&&oldPause.original==old&&oldPause.originalToken==oldToken){oldPause.retireOriginalResources();retainedSavedMaintenancePause=null;}
            }
        }
    }
    private SavedMaintenancePauseCall selectedSavedMaintenancePause,retainedSavedMaintenancePause;
    /** Private actual old-farm suspension episode; data markers alone never prove native effects. */
    public final class SavedMaintenancePauseCall {
        private final AdmissionAttempt admission;private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final SavedMaintenanceEnableSource declaration;private final Thread ownerThread=Thread.currentThread();
        private dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation rootCall;
        private boolean claimed,suspending,suspendReturned,suspendedSuccessfully,restoringPosture,postureReturned,closed,observerFailed;
        private boolean capturingResources,resourcesClosed;
        private dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.SavedMaintenancePauseSnapshot resources;
        private boolean[] pausedHeld;private boolean nativeSuspendAttempted;
        private SavedMaintenancePauseCall(AdmissionAttempt admission,Capture original,FarmMaintenanceLease.Token token,SavedMaintenanceEnableSource declaration){
            this.admission=admission;this.original=original;originalToken=token;this.declaration=declaration;}
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public SavedMaintenanceEnableSource originalEnableDeclaration(){return declaration;}
        public FarmModule farm(){return original.farm();}public Participant helperIdentity(){return original.helper();}
        public FarmMaintenanceLease.Token maintenanceToken(){return originalToken;}
        public Object originalCaptureIdentity(){return original;}public AdmissionAttempt actualAdmissionAttempt(){return admission;}
        public net.minecraft.client.KeyMapping originalPostureKey(){return original.keyMappings()[5];}
        public boolean desiredPostureHeld(){return original.posture()==Pose.CROUCHING;}
        public int originalToolSlot(){return original.slot();}
        /** Selected native instruction classification survives negative source invalidation until the real call leaves. */
        public boolean postureInstructionSelected(){return restoringPosture&&!postureReturned&&selectedSavedMaintenancePause==this
            &&Thread.currentThread()==ownerThread;}
        public boolean originalResourcesCurrentBeforeNative(){return beforeNativeCurrent()&&protectedRestored()&&beforeNativeCurrent();}
        public dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.SavedMaintenancePauseSnapshot captureOriginalResources(
                dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
            if(resources!=null||resourcesClosed||capturingResources||rootCall!=actual||actual==null||!actual.matchesPause(this)
                ||!originalResourcesCurrentBeforeNative())return null;
            capturingResources=true;
            try{resources=dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.captureSavedMaintenancePause(client,this);return resources;}
            finally{capturingResources=false;}
        }
        public boolean resourceCaptureCurrent(){return capturingResources&&resources==null&&!resourcesClosed&&!suspendReturned
            &&beforeNativeCurrent();}
        /** Exact original episode only; the raw stack graph and mutation journal prove the resources separately. */
        public boolean resourceSnapshotCurrent(){return !resourcesClosed&&(capturingResources||resources!=null)&&claimed&&rootCall!=null
            &&rootCall.matchesPause(this)&&(rawAffiliationCurrent()||historicalPreparedResourcesCurrent());}
        /** Closed original pause facts remain negative history while this exact PREPARED transaction owns them. */
        private boolean historicalPreparedResourcesCurrent(){
            if(!closed||!claimed||rootCall==null||!rootCall.closedPauseSourceMatches(this)||Thread.currentThread()!=ownerThread
                ||captured!=original||token!=originalToken||retainedSavedMaintenancePause!=this
                ||retainedSavedMaintenanceEnable!=declaration||declaration.original!=original||!lease.owns(originalToken)
                ||lease.state()!=FarmMaintenanceLease.State.PREPARED||!modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||client.getUser()!=original.originalUser()||!original.originalAccount().equals(original.player().getUUID())
                ||!capturedBindingsCurrent(original)||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=FarmMaintenanceCoordinator.this
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules||dev.veltraph.client.VeltraphBootstrap.config()!=config)return false;
            var rollback=selectedSavedMaintenanceRollbackCall;
            var failed=failedRollbackCustody;
            var partial=rollback!=null&&rollback.pause==this?rollback:failed!=null&&failed.pause==this?failed.partial:null;
            if(partial!=null&&partial.helperWasEnabled)return partial.helperStoppedCurrent()
                ||modules.originalModuleLifecycleCurrent(partial.helperBefore,original.helper());
            var helperHistory=declaration.completedHelperLifecycle;
            return modules.originalModuleLifecycleCurrent(helperHistory!=null?helperHistory:original.beforeHelper(),original.helper());
        }
        public boolean originalResourceJournalRetained(){return resources!=null&&!resourcesClosed;}
        /** The same originally captured raw42 graph/journal; this method never takes another snapshot. */
        public boolean retainedOriginalResourcesCurrent(){return originalResourceJournalRetained()&&resources.currentFinal();}
        public void closeOriginalResources(dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
            if(actual==null||rootCall!=actual||!actual.matchesPause(this)||Thread.currentThread()!=ownerThread||resourcesClosed)return;
            // Root closing its observation does not retire an original PREPARED resource journal.
            if(captured==original&&token==originalToken&&lease.state()==FarmMaintenanceLease.State.PREPARED&&lease.owns(originalToken))return;
            retireOriginalResources();
        }
        private void retireOriginalResources(){if(resourcesClosed)return;resourcesClosed=true;if(resources!=null)resources.close();}
        public boolean completedOriginalPauseProof(){return closed&&!observerFailed&&rootCall!=null&&rootCall.acceptedNormalPauseProof()
            &&rootCall.matchesPause(this)&&suspendReturned&&suspendedSuccessfully&&postureReturned;}

        public boolean claimOriginalPause(dev.veltraph.client.VeltraphBootstrap.RootOriginalNativeMaintenancePauseInvocation actual){
            if(claimed||actual==null||selectedSavedMaintenancePause!=this||!actual.matchesPause(this)||Thread.currentThread()!=ownerThread)return false;
            boolean named=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=SavedMaintenancePauseCall.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class&&caller.getMethodName().equals("beginSavedMaintenanceOriginalPause");});
            if(!named)return false;claimed=true;rootCall=actual;return beforeNativeCurrent();
        }
        private boolean rawAffiliationCurrent(){return selectedSavedMaintenancePause==this&&!closed&&!observerFailed&&Thread.currentThread()==ownerThread
            &&admissionAttempt==admission&&admission.original==original&&admission.originalToken==originalToken&&admission.originalCurrent()
            &&captured==original&&token==originalToken&&retainedSavedMaintenanceEnable==declaration&&declaration.original==original
            &&lease.owns(originalToken)&&lease.state()==FarmMaintenanceLease.State.PREPARED&&declaration.originalSettingsCurrent()
            &&modules.lifecycleCurrent(original.beforeHelper())&&client.player==original.player()&&client.level==original.world()
            &&client.getConnection()==original.connection()&&client.options==original.options()&&client.getUser()==original.originalUser()
            &&original.originalAccount().equals(original.player().getUUID())&&capturedBindingsCurrent(original)
            &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==modules&&dev.veltraph.client.VeltraphBootstrap.config()==config;}
        public boolean beforeNativeCurrent(){return claimed&&rootCall!=null&&rootCall.matchesPause(this)&&!suspendReturned&&rawAffiliationCurrent();}
        public boolean suspensionEpisodeCurrent(){return suspending&&claimed&&rootCall!=null&&rootCall.matchesPause(this)&&rawAffiliationCurrent();}
        public boolean normalSuspendReturnedCurrent(){return claimed&&rootCall!=null&&rootCall.matchesPause(this)&&suspendReturned
            &&suspendedSuccessfully&&!suspending&&original.farm().maintenanceOwnedBy(originalToken)&&rawAffiliationCurrent();}
        public boolean postureEpisodeCurrent(){return restoringPosture&&!postureReturned&&normalSuspendReturnedCurrent();}
        public boolean normalPostureReturnedCurrent(){return postureReturned&&!restoringPosture&&normalSuspendReturnedCurrent();}
    }
    private SavedMaintenanceEnableSource retainedSavedMaintenanceEnable,pendingSavedMaintenanceEnable;
    /** Declared before the original farm pause. Only genuine Root before-enable may consume it. */
    public final class SavedMaintenanceEnableSource {
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final Thread ownerThread=Thread.currentThread();private final Object originalSettings;
        private dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint;
        private ModuleManager.ActualEnableStart start;private boolean claimed,witnessBound;
        private ModuleManager.LifecycleSnapshot completedHelperLifecycle;
        private SavedMaintenanceEnableSource(Capture original,FarmMaintenanceLease.Token token,Object originalSettings){this.original=original;originalToken=token;this.originalSettings=originalSettings;}
        private void bindOriginalWitness(dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness witness){
            if(!witnessBound&&retainedSavedMaintenanceEnable==this&&captured==original&&token==originalToken&&engineWitness==witness){
                witnessBound=true;checkpoint=witness;
            }
        }
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public FarmModule farm(){return original.farm();}public Participant helperIdentity(){return original.helper();}
        public FarmMaintenanceLease.Token maintenanceToken(){return originalToken;}
        public Object originalCheckpointIdentity(){return checkpoint;}
        public Object originalPauseIdentity(){return original;}
        public ModuleManager.LifecycleSnapshot originalDisabledLifecycle(){return original.beforeHelper();}
        public boolean originalSettingsCurrent(){return originalSettings!=null&&knownGameplaySettingsCurrent(original.policy().settings(),originalSettings);}
        /** Source affiliation only. Root retains the genuine closed enable and strict input publication receipts. */
        public boolean nativePublicationAffiliationCurrent(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication exact){
            var phase=lease.state();
            return exact!=null&&exact==original.originalPublication()&&retainedSavedMaintenanceEnable==this
                &&captured==original&&token==originalToken&&lease.owns(originalToken)&&witnessBound&&checkpoint!=null&&engineWitness==checkpoint
                &&(phase==FarmMaintenanceLease.State.HELPER_ACTIVE||phase==FarmMaintenanceLease.State.RESTORING
                    ||phase==FarmMaintenanceLease.State.RETURNING||phase==FarmMaintenanceLease.State.READY)
                &&originalSettingsCurrent()&&original.farm().maintenanceOwnedBy(originalToken)
                &&modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())
                &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
                &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this;
        }
        public boolean claimOriginalEnable(ModuleManager.ActualEnableStart actual){
            if(claimed||actual==null||pendingSavedMaintenanceEnable!=this||retainedSavedMaintenanceEnable!=this
                ||Thread.currentThread()!=ownerThread||actual.registryIdentity()!=modules||actual.parent()!=original.helper())return false;
            boolean namedRoot=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=SavedMaintenanceEnableSource.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class
                    &&caller.getMethodName().equals("captureOriginalRootEnable");
            });
            if(!namedRoot)return false;claimed=true;start=actual;return beforeCurrent(actual);
        }
        public boolean beforeCurrent(ModuleManager.ActualEnableStart actual){
            return claimed&&start==actual&&pendingSavedMaintenanceEnable==this&&retainedSavedMaintenanceEnable==this&&activating
                &&Thread.currentThread()==ownerThread&&captured==original&&token==originalToken&&lease.owns(originalToken)
                &&lease.state()==FarmMaintenanceLease.State.PREPARED&&witnessBound&&checkpoint!=null&&engineWitness==checkpoint
                &&original.originalPublication()!=null&&originalSettingsCurrent()&&actual.registryIdentity()==modules&&actual.parent()==original.helper()
                &&actual.startingCurrent()&&actual.startedFrom(original.beforeHelper())
                &&original.beforeHelper()!=null&&original.beforeHelper().known()&&modules.lifecycleCurrent(original.beforeHelper())
                &&original.farm().maintenanceOwnedBy(originalToken)&&client.player==original.player()&&client.level==original.world()
                &&client.getConnection()==original.connection()&&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this;
        }
        /** Pure affiliation of the actual claimed helper enable after its real enabled field publication. */
        public boolean nativeEnableEpisodeCurrent(ModuleManager.ActualEnableStart exact){
            return claimed&&start!=null&&exact==start&&pendingSavedMaintenanceEnable==this&&retainedSavedMaintenanceEnable==this
                &&activating&&Thread.currentThread()==ownerThread&&captured==original&&token==originalToken
                &&lease.owns(originalToken)&&lease.state()==FarmMaintenanceLease.State.PREPARED&&witnessBound&&checkpoint!=null
                &&engineWitness==checkpoint&&originalSettingsCurrent()&&exact.registryIdentity()==modules&&exact.parent()==original.helper()
                &&exact.enabledCurrent()&&exact.startedFrom(original.beforeHelper())
                &&modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())&&original.farm().maintenanceOwnedBy(originalToken)
                &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
                &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this
                &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==modules&&dev.veltraph.client.VeltraphBootstrap.config()==config;
        }
        public boolean matchesCompletedEnable(ModuleManager.OwnEnableReceipt returned){
            boolean matched=claimed&&start!=null&&returned!=null&&returned.actualStart()==start&&pendingSavedMaintenanceEnable==this
                &&retainedSavedMaintenanceEnable==this&&captured==original&&token==originalToken&&engineWitness==checkpoint
                &&original.originalPublication()!=null&&originalSettingsCurrent()&&Thread.currentThread()==ownerThread
                &&modules.ownEnableMatches(returned,original.beforeHelper(),original.helper())
                &&original.farm().maintenanceOwnedBy(originalToken)&&client.player==original.player()&&client.level==original.world()
                &&client.getConnection()==original.connection()&&lease.state()==FarmMaintenanceLease.State.PREPARED;
            if(!matched||completedHelperLifecycle!=null)return false;
            var confirmed=modules.lifecycleSnapshot();
            if(!modules.ownEnableMatches(returned,original.beforeHelper(),original.helper())||!modules.lifecycleCurrent(confirmed)
                ||captured!=original||token!=originalToken||pendingSavedMaintenanceEnable!=this)return false;
            completedHelperLifecycle=confirmed;return true;
        }
    }
    /** Existing privately selected deferred preparation only; no ready/current policy getter creates it. */
    public SavedMaintenanceEnableSource pendingSavedMaintenanceTransactionForRoot(Module exact){
        var source=pendingSavedMaintenanceEnable;
        return source!=null&&activating&&source.original.helper()==exact&&source==retainedSavedMaintenanceEnable
            &&captured==source.original&&token==source.originalToken&&retainedSavedMaintenancePause!=null
            &&retainedSavedMaintenancePause.original==source.original&&retainedSavedMaintenancePause.completedOriginalPauseProof()
            ?source:null;
    }
    public SavedMaintenanceEnableSource pendingSavedMaintenanceEnableForRoot(ModuleManager.ActualEnableStart actual){
        var declared=pendingSavedMaintenanceEnable;
        return actual!=null&&declared!=null&&declared==retainedSavedMaintenanceEnable&&actual.registryIdentity()==modules
            &&actual.parent()==declared.original.helper()?declared:null;
    }
    private SavedMaintenanceResumeCall selectedSavedMaintenanceResumeCall;
    /** Declared only inside genuine original resume, before helper disable and all final native handoff effects. */
    public final class SavedMaintenanceResumeCall {
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;private final Object restoredProof;
        private final McSavedAnchorRecovery.ArrivalForOwnDisable arrival;private final SavedMaintenanceEnableSource declaration;
        private final SavedReturnAlignmentSource originalAlignmentSource=selectedSavedReturnSource;
        private final dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint;
        private final Thread ownerThread=Thread.currentThread();private boolean rootClaimed,disableClaimed,inputClaimed;
        private boolean farmReturnedTrue,restoreReturnedNormally,closeObserverFailed,restoringKeys;
        private int nextRestoreIndex;private SavedMaintenanceRestoreKey restoreInstruction;
        private dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceResumeInvocation rootCall;
        private ModuleManager.ActualDisableStart disabling;private ModuleManager.OwnDisableReceipt disabled;
        private ModuleManager.LifecycleSnapshot stopped;
        private dev.veltraph.client.rewarp.mc.McIssuedReturnInventory.SavedMaintenanceSnapshot resources;
        private boolean capturingResources,resourcesClosed;
        private SavedMaintenanceResumeCall(Capture original,FarmMaintenanceLease.Token token,Object proof,
                McSavedAnchorRecovery.ArrivalForOwnDisable arrival,SavedMaintenanceEnableSource declaration,
                dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint){
            this.original=original;originalToken=token;restoredProof=proof;this.arrival=arrival;this.declaration=declaration;this.checkpoint=checkpoint;
        }
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        /** Existing exact driver/arrival data; neither inspection supplies native effect authority. */
        public McSavedAnchorRecovery returningDriverIdentity(){return returning;}
        public Object originalArrivalIdentity(){return arrival;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public SavedMaintenanceEnableSource originalEnableDeclaration(){return declaration;}
        public FarmModule farm(){return original.farm();}public Participant helperIdentity(){return original.helper();}
        public Object originalCheckpointIdentity(){return checkpoint;}public Object originalPauseIdentity(){return original;}
        public FarmMaintenanceLease.Token maintenanceToken(){return originalToken;}
        public ModuleManager.LifecycleSnapshot originalEnabledLifecycle(){return declaration.completedHelperLifecycle;}
        private boolean affiliationCurrent(){return selectedSavedMaintenanceResumeCall==this&&Thread.currentThread()==ownerThread
            &&captured==original&&token==originalToken&&witness==restoredProof&&engineWitness==checkpoint
            &&retainedSavedMaintenanceEnable==declaration&&declaration.original==original&&checkpoint!=null
            &&lease.owns(originalToken)&&lease.state()==FarmMaintenanceLease.State.READY&&declaration.originalSettingsCurrent()
            &&modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())
            &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
            &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this;}
        public boolean claimOriginalResume(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceResumeInvocation actual){
            if(rootClaimed||actual==null||selectedSavedMaintenanceResumeCall!=this||!actual.matchesResume(this)
                ||Thread.currentThread()!=ownerThread)return false;
            rootClaimed=true;rootCall=actual;return beforeNativeCurrent();
        }
        public boolean beforeNativeCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesResume(this)&&affiliationCurrent()
            &&declaration.completedHelperLifecycle!=null&&modules.lifecycleCurrent(declaration.completedHelperLifecycle)
            &&original.farm().maintenanceOwnedBy(originalToken)&&returning.arrived(GameArea.GARDEN)&&affiliationCurrent();}
        public boolean claimOriginalDisable(ModuleManager.ActualDisableStart actual){
            if(disableClaimed||actual==null||selectedSavedMaintenanceResumeCall!=this||actual.registryIdentity()!=modules
                ||actual.parent()!=original.helper()||Thread.currentThread()!=ownerThread)return false;
            boolean namedRoot=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=SavedMaintenanceResumeCall.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class
                    &&caller.getMethodName().equals("captureOriginalRootDisable");
            });
            if(!namedRoot)return false;disableClaimed=true;disabling=actual;return beforeDisableCurrent();
        }
        public boolean beforeDisableCurrent(){return disableClaimed&&disabling!=null&&rootClaimed&&affiliationCurrent()&&completing
            &&disabling.startingCurrent()&&disabling.startedFrom(declaration.completedHelperLifecycle)
            &&original.farm().maintenanceOwnedBy(originalToken);}
        public boolean matchesCompletedDisable(ModuleManager.OwnDisableReceipt receipt){
            if(!disableClaimed||disabling==null||receipt==null||receipt.actualStart()!=disabling||!receipt.closedCurrent()
                ||!affiliationCurrent()||disabled!=null)return false;
            var normalStopped=receipt.stoppedSnapshot();
            if(normalStopped==null||!modules.ownDisableFromMatches(receipt,declaration.completedHelperLifecycle,normalStopped,original.helper()))return false;
            disabled=receipt;stopped=normalStopped;return affiliationCurrent()&&modules.lifecycleCurrent(stopped);
        }
        public boolean resumePublicationCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesResume(this)&&farmReturnedTrue
            &&!restoreReturnedNormally&&affiliationCurrent()&&disabled!=null&&disabled.closedCurrent()&&modules.lifecycleCurrent(stopped)
            &&original.farm().isEnabled()&&!original.farm().isSuspended()
            &&returning.arrivedAfterOwnDisable(arrival,GameArea.GARDEN)&&affiliationCurrent();}
        public boolean prepublicationCurrent(){return resumePublicationCurrent()&&original.originalPublication()!=null
            &&original.originalPublication().nativeHistoryCurrent()&&resumePublicationCurrent();}
        public boolean claimOriginalPublication(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputInvocation actual){
            if(inputClaimed||actual==null||selectedSavedMaintenanceResumeCall!=this||!actual.matchesSavedMaintenanceResume(this))return false;
            inputClaimed=true;return prepublicationCurrent();
        }
        public boolean afterOriginalInputCurrent(){return inputClaimed&&resumePublicationCurrent();}
        /** Read-only private actual restore episode; no Root/native getter is called here. */
        public boolean restorationEpisodeCurrent(){return restoringKeys&&selectedSavedMaintenanceResumeCall==this
            &&restoreInstruction!=null&&restoreInstruction.source==this&&restoreInstruction.index==nextRestoreIndex
            &&nextRestoreIndex>=0&&nextRestoreIndex<9&&rootClaimed&&inputClaimed&&farmReturnedTrue&&!restoreReturnedNormally
            &&Thread.currentThread()==ownerThread&&captured==original&&token==originalToken&&witness==restoredProof
            &&engineWitness==checkpoint&&retainedSavedMaintenanceEnable==declaration;}
        /** Private factory window after the actual helper normal disable and final original restoration observations. */
        public boolean resourceCaptureCurrent(){return capturingResources&&resources==null&&!farmReturnedTrue&&!inputClaimed
            &&!restoringKeys&&!restoreReturnedNormally&&resourceSnapshotCurrent();}
        /** Pure episode affiliation; the retained native graph/journal supplies resource facts separately. */
        public boolean resourceSnapshotCurrent(){
            if(resourcesClosed||selectedSavedMaintenanceResumeCall!=this||Thread.currentThread()!=ownerThread||!rootClaimed||rootCall==null
                ||disabled==null||!disabled.closedCurrent()||stopped==null||!modules.lifecycleCurrent(stopped)
                ||!modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())||!declaration.originalSettingsCurrent()
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||client.getUser()!=original.originalUser()||!original.originalAccount().equals(original.player().getUUID())
                ||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=FarmMaintenanceCoordinator.this
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules||dev.veltraph.client.VeltraphBootstrap.config()!=config)return false;
            if(restoreReturnedNormally)return inputClaimed&&farmReturnedTrue&&nextRestoreIndex==9&&!restoringKeys
                &&lease.state()==FarmMaintenanceLease.State.RESUMED&&captured==null&&token==null;
            return (capturingResources||resources!=null)&&rootCall.matchesResume(this)&&captured==original&&token==originalToken
                &&witness==restoredProof&&engineWitness==checkpoint&&retainedSavedMaintenanceEnable==declaration
                &&declaration.original==original&&declaration.checkpoint==checkpoint&&lease.owns(originalToken)
                &&lease.state()==FarmMaintenanceLease.State.READY;
        }
        public boolean normalReturnedCurrent(){return !closeObserverFailed&&resources!=null&&resources.currentFinal()&&selectedSavedMaintenanceResumeCall==this&&rootClaimed&&inputClaimed&&restoreReturnedNormally&&nextRestoreIndex==9&&!restoringKeys
            &&Thread.currentThread()==ownerThread&&rootCall!=null&&rootCall.matchesResume(this)&&captured==null&&token==null
            &&disabled!=null&&disabled.closedCurrent()&&modules.lifecycleCurrent(stopped)
            &&modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())&&original.farm().isEnabled()&&!original.farm().isSuspended()
            &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection();}
    }
    /** Private actual per-key instruction; values and bindings are retained from the original farm custody. */
    public final class SavedMaintenanceRestoreKey {
        private final SavedMaintenanceResumeCall source;private final int index;private final net.minecraft.client.KeyMapping key;
        private final boolean held;private boolean claimed;private dev.veltraph.client.service.mc.McHeldKeys.SavedMaintenanceKeyInvocation nativeCall;
        private SavedMaintenanceRestoreKey(SavedMaintenanceResumeCall source,int index){this.source=source;this.index=index;
            key=source.original.keyMappings()[index];held=source.original.keys()[index];}
        public net.minecraft.client.Minecraft client(){return FarmMaintenanceCoordinator.this.client;}
        public int index(){return index;}
        public net.minecraft.client.KeyMapping key(){return key;}public boolean desiredHeld(){return held;}public boolean directSetter(){return index<5;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return source.originalInputPublication();}
        public FarmModule farm(){return source.farm();}
        public boolean beforeNativeCurrent(){return nativeBeforeCurrent();}
        public boolean normalNativeCurrent(){return claimed&&nativeCall!=null&&nativeCall.matchesInstruction(this)&&nativeBeforeCurrent();}
        public SavedMaintenanceResumeCall sourceCall(){return source;}
        public dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceResumeInvocation rootResumeIdentity(){return source.rootCall;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalPublicationIdentity(){return source.originalInputPublication();}
        public FarmModule originalFarm(){return source.farm();}
        public Object originalBindingHistory(){return source.original.keyBindingHistories()[index];}
        /** Held-key history is proved by the native producer's old before/prefix receipts, not ignored here. */
        public boolean nativeBeforeCurrent(){return source.restoringKeys&&source.restoreInstruction==this&&source.nextRestoreIndex==index
            &&selectedSavedMaintenanceResumeCall==source&&source.rootCall!=null&&source.rootCall.matchesResume(source)
            &&source.rootCall.beforeRestoreNativeCurrent(source.original.farm(),client)&&source.inputClaimed&&source.farmReturnedTrue
            &&!source.restoreReturnedNormally&&source.affiliationCurrent()&&capturedBindingsCurrent(source.original)
            &&key==source.original.keyMappings()[index]&&source.original.farm().isEnabled()&&!source.original.farm().isSuspended()
            &&source.disabled!=null&&source.disabled.closedCurrent()&&modules.lifecycleCurrent(source.stopped);
        }
        /** Last stored source leaf after all providers. No arrival, policy, clock, role or native-history callback. */
        public boolean finalNativeSourceCurrent(){
            var root=source.rootCall;var original=source.original;
            if(!claimed||nativeCall==null||!nativeCall.matchesInstruction(this)||root==null||!source.restoringKeys||selectedSavedMaintenanceResumeCall!=source||source.restoreInstruction!=this
                ||source.nextRestoreIndex!=index||index<0||index>=9||!source.rootClaimed||!source.inputClaimed||!source.farmReturnedTrue
                ||source.restoreReturnedNormally||Thread.currentThread()!=source.ownerThread||captured!=original||token!=source.originalToken
                ||witness!=source.restoredProof||engineWitness!=source.checkpoint||retainedSavedMaintenanceEnable!=source.declaration
                ||source.declaration.original!=original||source.declaration.checkpoint!=source.checkpoint
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||key!=original.keyMappings()[index]||source.stopped==null
                ||!modules.lifecycleCurrent(source.stopped)||!modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())
                ||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=FarmMaintenanceCoordinator.this
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules||dev.veltraph.client.VeltraphBootstrap.config()!=config)return false;
            if(source.resources==null||!source.resources.currentFinal())return false;
            if(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu
                ||client.player.inventoryMenu.getCarried()!=ItemStack.EMPTY||client.player.getInventory().getSelectedSlot()!=original.slot())return false;
            if(!root.finalRestoreNativeCurrent(source,client)||!capturedBindingsCurrent(original)||!source.declaration.originalSettingsCurrent())return false;
            return selectedSavedMaintenanceResumeCall==source&&source.restoreInstruction==this&&source.nextRestoreIndex==index
                &&source.restoringKeys&&captured==original&&token==source.originalToken&&witness==source.restoredProof
                &&engineWitness==source.checkpoint&&retainedSavedMaintenanceEnable==source.declaration
                &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
                &&client.options==original.options()&&modules.lifecycleCurrent(source.stopped);
        }
        public boolean finalNativeCurrent(dev.veltraph.client.service.mc.McHeldKeys.SavedMaintenanceKeyInvocation exact){
            return claimed&&nativeCall!=null&&nativeCall==exact&&exact.matchesInstruction(this)&&finalNativeSourceCurrent();
        }
        public boolean claimNativeInstruction(dev.veltraph.client.service.mc.McHeldKeys.SavedMaintenanceKeyInvocation actual){
            if(claimed||actual==null||source.restoreInstruction!=this||!actual.matchesInstruction(this))return false;
            boolean nativeCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=SavedMaintenanceRestoreKey.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.McHeldKeys.class
                    &&caller.getMethodName().equals("restoreSavedMaintenanceKey");
            });
            if(!nativeCaller)return false;claimed=true;nativeCall=actual;return nativeBeforeCurrent();
        }
    }
    private net.minecraft.client.KeyMapping[] heldMappings(){var o=client.options;return new net.minecraft.client.KeyMapping[]{
        o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};}
    private boolean capturedBindingsCurrent(Capture original){
        if(original==null||client.options!=original.options()||original.keyMappings().length!=9||original.keyBindingHistories().length!=9)return false;
        var actual=heldMappings();
        for(int i=0;i<actual.length;i++)if(actual[i]!=original.keyMappings()[i]||original.keyBindingHistories()[i]==null
            ||!dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(actual[i],original.keyBindingHistories()[i]))return false;
        return client.options==original.options();
    }
    private void restoreKeysForOriginalResume(SavedMaintenanceResumeCall original){
        if(original==null||selectedSavedMaintenanceResumeCall!=original||original.restoringKeys||original.nextRestoreIndex!=0)
            throw new IllegalStateException("Original native farm key restoration unavailable");
        original.restoringKeys=true;
        try{
            for(int i=0;i<9;i++){
                var instruction=new SavedMaintenanceRestoreKey(original,i);original.restoreInstruction=instruction;
                var returned=dev.veltraph.client.service.mc.McHeldKeys.restoreSavedMaintenanceKey(instruction);
                if(returned==null||returned.instruction()!=instruction||!returned.normallyReturned()||!returned.confirmed()
                    ||!instruction.nativeBeforeCurrent())throw new IllegalStateException("Original native farm key restoration refused at "+i);
                original.nextRestoreIndex=i+1;original.restoreInstruction=null;
            }
        }finally{original.restoringKeys=false;original.restoreInstruction=null;}
    }
    public SavedMaintenanceResumeCall pendingSavedMaintenanceDisableForRoot(ModuleManager.ActualDisableStart actual){
        var call=selectedSavedMaintenanceResumeCall;return call!=null&&actual!=null&&actual.registryIdentity()==modules
            &&actual.parent()==call.original.helper()&&completing?call:null;
    }
    private SavedMaintenanceInputCall selectedSavedMaintenanceInputCall;
    /** Exact actual Root scheduled update can mint this call; no provider is sampled before selection. */
    public SavedMaintenanceInputCall savedMaintenanceInputForRoot(dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceInputInvocation actual){
        var declaration=retainedSavedMaintenanceEnable;
        if(actual==null||declaration==null||!actual.matchesDeclaration(declaration)||actual.coordinatorIdentity()!=this
            ||actual.originalPublication()!=declaration.originalInputPublication()||actual.selectedModule()!=declaration.helperIdentity())return null;
        boolean namedRoot=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var caller=frames.skip(1).findFirst().orElse(null);return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class
                &&caller.getMethodName().equals("publishSavedMaintenanceOriginalInput");
        });
        if(!namedRoot)return null;
        var call=new SavedMaintenanceInputCall(declaration,actual);selectedSavedMaintenanceInputCall=call;return call;
    }
    public final class SavedMaintenanceInputCall {
        private final SavedMaintenanceEnableSource declaration;private final dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceInputInvocation rootCall;
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;
        private final Thread ownerThread=Thread.currentThread();private boolean claimed;
        private SavedMaintenanceInputCall(SavedMaintenanceEnableSource declaration,dev.veltraph.client.VeltraphBootstrap.RootSavedMaintenanceInputInvocation actual){
            this.declaration=declaration;rootCall=actual;original=declaration.original;originalToken=declaration.originalToken;
        }
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public SavedMaintenanceEnableSource originalEnableDeclaration(){return declaration;}
        public FarmModule farm(){return original.farm();}public Participant helperIdentity(){return original.helper();}
        public Object originalCheckpointIdentity(){return declaration.checkpoint;}
        public Object originalPauseIdentity(){return original;}public FarmMaintenanceLease.Token maintenanceToken(){return originalToken;}
        public boolean claimOriginalPublication(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputInvocation actual){
            if(claimed||selectedSavedMaintenanceInputCall!=this||actual==null||!actual.matchesSavedMaintenance(this)
                ||Thread.currentThread()!=ownerThread)return false;
            boolean namedRoot=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=SavedMaintenanceInputCall.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class
                    &&caller.getMethodName().equals("publishSavedMaintenanceOriginalInput");
            });
            if(!namedRoot)return false;claimed=true;return currentFinalNative();
        }
        public boolean currentFinalNative(){return storedInputAffiliationCurrent()
            &&original.originalPublication()!=null&&original.originalPublication().nativeHistoryCurrent()
            &&storedInputAffiliationCurrent();}
        /** Post-set source leaf only. The strict Root setter carries actual before/after input history. */
        public boolean afterOriginalInputCurrent(){return storedInputAffiliationCurrent();}
        private boolean storedInputAffiliationCurrent(){
            var phase=lease.state();
            return claimed&&selectedSavedMaintenanceInputCall==this&&Thread.currentThread()==ownerThread
                &&rootCall.matchesDeclaration(declaration)&&rootCall.coordinatorIdentity()==FarmMaintenanceCoordinator.this
                &&retainedSavedMaintenanceEnable==declaration&&captured==original&&token==originalToken&&lease.owns(originalToken)
                &&(phase==FarmMaintenanceLease.State.HELPER_ACTIVE||phase==FarmMaintenanceLease.State.RESTORING
                    ||phase==FarmMaintenanceLease.State.RETURNING||phase==FarmMaintenanceLease.State.READY)
                &&declaration.completedHelperLifecycle!=null&&modules.lifecycleCurrent(declaration.completedHelperLifecycle)
                &&declaration.checkpoint!=null&&engineWitness==declaration.checkpoint&&declaration.originalSettingsCurrent()
                &&original.originalPublication()!=null
                &&original.farm().maintenanceOwnedBy(originalToken)&&client.player==original.player()&&client.level==original.world()
                &&client.getConnection()==original.connection()&&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this;
        }
    }
    private SavedReturnAlignmentSource selectedSavedReturnSource,closingSavedReturnSource;
    private LoadedReturnDeviceAuthority selectedLoadedReturnDeviceAuthority,closingLoadedReturnDeviceAuthority;
    /** Installed only into this coordinator's final saved-return driver. No public constructor exists. */
    public final class SavedReturnAdapter {
        private SavedReturnAdapter(){}
        SavedReturnAlignmentSource issue(McSavedAnchorRecovery.AlignmentCall call){
            thread();var original=captured;var restored=witness;
            if(call==null||!call.matchingDriver(returning)||!call.admissionCurrent()||original==null
                ||call.target()!=original.pose()||original.originalPublication()==null
                ||retainedSavedMaintenanceEnable==null||retainedSavedMaintenanceEnable.original!=original
                ||lease.state()!=FarmMaintenanceLease.State.RETURNING
                ||!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService)
                ||!(context instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext)
                ||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
                ||!rootSource.affiliates(FarmMaintenanceCoordinator.this)||restored==null)return null;
            var lifecycle=modules.lifecycleSnapshot();var settings=retainedSavedMaintenanceEnable.originalSettings;
            var control=dev.veltraph.client.VeltraphBootstrap.ownedUseControlSnapshot(FarmMaintenanceCoordinator.this);
            var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();var location=nativeContext.gameplaySignalsSnapshot();
            if(!lifecycle.known()||settings==null||control==null||input==null||!input.active()||location==null
                ||!Objects.equals(input.ownerModuleId(),original.helper().id())||!identities(true)||!restoration(restored)
                ||!call.admissionCurrent()||captured!=original||witness!=restored)return null;
            var actual=new SavedReturnAlignmentSource(this,call,original,token,restored,lifecycle,settings,control,input,location,rootSource,engineWitness);
            if(!actual.observedCurrent()||!call.admissionCurrent())return null;
            selectedSavedReturnSource=actual;
            return actual.currentFinalNative()?actual:null;
        }

        /** Actual installed LoadedReturnCall alone requests this distinct restored-maintenance device source. */
        LoadedReturnDeviceAuthority issueLoadedReturnDeviceAuthority(McSavedAnchorRecovery.LoadedReturnCall call,
                dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment segment,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NormalReturnDeviceAdmission admission){
            thread();var original=captured;var restored=witness;var declaration=retainedSavedMaintenanceEnable;var startup=loadedReturnStartup;
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var first=frames.filter(f->f.getDeclaringClass()!=SavedReturnAdapter.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==McSavedAnchorRecovery.LoadedReturnCall.class
                    &&first.getMethodName().equals("issueRestoredMaintenanceDeviceAuthority");});
            if(!caller||call==null||segment==null||admission==null||original==null||restored==null||declaration==null
                ||!call.matchingSavedReturnAdapter(returning,this)||call.issuedRequestIdentity()!=null
                ||segment.returnCallIdentity()!=call||admission.loadedSegmentSourceIdentity()!=segment
                ||segment.sharedChildIdentity()!=admission.issuerIdentity()||segment.canonicalTargetPoint()!=original.pose()
                ||!call.canonicalTarget().equals(original.pose().position())||!segment.segmentPosition().equals(admission.targetPosition())
                ||admission.originalSlot()!=original.slot()||declaration.original!=original||declaration.originalToken!=token
                ||declaration.completedHelperLifecycle==null||declaration.checkpoint==null||engineWitness!=declaration.checkpoint
                ||!(lease.state()==FarmMaintenanceLease.State.RETURNING||lease.state()==FarmMaintenanceLease.State.RESTORING&&startup!=null&&startup.current())||original.originalPublication()==null
                ||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
                ||!rootSource.affiliates(FarmMaintenanceCoordinator.this)
                ||!(context instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext)
                ||!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService))return null;
            var previous=selectedLoadedReturnDeviceAuthority;
            if(previous!=null&&!previous.retired&&!previous.revoked)return null;
            var history=call.expectedSelectionHistory();var lifecycle=modules.lifecycleSnapshot();
            var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
            var control=dev.veltraph.client.VeltraphBootstrap.ownedUseControlSnapshot(FarmMaintenanceCoordinator.this);
            var location=nativeContext.gameplaySignalsSnapshot();
            var settings=config instanceof dev.veltraph.client.config.MapConfigService map?map.storedStateSnapshot()
                :config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
            var installation=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(client);
            if(history==null||!history.current()||admission.originalSelectionHistory()==null
                ||!admission.originalSelectionHistory().sameStoredObservation(history)||!lifecycle.known()
                ||input==null||!input.active()||!Objects.equals(input.ownerModuleId(),original.helper().id())
                ||control==null||location==null||settings==null||installation==null||!identities(true)||!restoration(restored)
                ||!declaration.originalSettingsCurrent()||!original.farm().maintenanceEngineUnchanged(token,engineWitness)
                ||!call.nativeDispatchCurrent(call.travelIdentity())||!segment.nativeRealmCurrent()
                ||client.player.getInventory().getSelectedSlot()!=original.slot()||captured!=original||witness!=restored)return null;
            var actual=new LoadedReturnDeviceAuthority(this,call,segment,admission,original,token,restored,declaration,
                lifecycle,input,control,location,settings,installation,history,rootSource,engineWitness,startup);
            // Genuine one-time private handoff is installed before the final bound source leaf; no current-before-bind cycle.
            if(!admission.bindLoadedMaintenanceDeviceAuthority(actual)){actual.revoked=true;return null;}
            selectedLoadedReturnDeviceAuthority=actual;
            if(!actual.current()||captured!=original||witness!=restored){actual.revoked=true;return null;}
            return actual;
        }
    }
    /** Original saved-return ALIGN source. Read-only methods cannot mint another capture or journey. */
    public final class SavedReturnAlignmentSource {
        private final SavedReturnAdapter adapter;private final McSavedAnchorRecovery.AlignmentCall call;
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;private final Object restoredWitness;
        private ModuleManager.LifecycleSnapshot lifecycle;private final Object settings,control;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot location;
        private final BooleanSupplier external;private final dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness engine;
        private SavedReturnAlignmentSource(SavedReturnAdapter adapter,McSavedAnchorRecovery.AlignmentCall call,Capture original,
                FarmMaintenanceLease.Token originalToken,Object restoredWitness,ModuleManager.LifecycleSnapshot lifecycle,
                Object settings,Object control,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,
                dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot location,BooleanSupplier external,
                dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness engine){
            this.adapter=adapter;this.call=call;this.original=original;this.originalToken=originalToken;this.restoredWitness=restoredWitness;
            this.lifecycle=lifecycle;this.settings=settings;this.control=control;this.input=input;this.location=location;this.external=external;this.engine=engine;
        }
        public FarmMaintenanceLease.Token cameraOwner(){return originalToken;}
        public Participant inputOwner(){return original.helper();}
        public FarmReturnPoint target(){return original.pose();}
        public Object rotationServiceIdentity(){return services.rotation();}
        public ModuleManager registryIdentity(){return modules;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public FarmModule originalFarm(){return original.farm();}
        public SavedMaintenanceEnableSource originalEnableDeclaration(){return retainedSavedMaintenanceEnable!=null
            &&retainedSavedMaintenanceEnable.original==original?retainedSavedMaintenanceEnable:null;}
        /** Original private source affiliation for the Root publication producer; excludes that producer to avoid recursion. */
        public boolean publicationAffiliationCurrent(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication exact){
            return exact!=null&&original.originalPublication()==exact&&selectedSavedReturnSource==this&&originalLive(false)
                &&retainedSavedMaintenanceEnable!=null&&retainedSavedMaintenanceEnable.original==original
                &&retainedSavedMaintenanceEnable.originalSettingsCurrent()&&call.matchingDriver(returning)
                &&call.currentFinalNative()&&rawCurrent(false)&&selectedSavedReturnSource==this&&originalLive(false);
        }
        public Object originalCheckpointIdentity(){return engine;}
        public Object originalPauseIdentity(){return original;}
        private boolean rawCurrent(boolean cleanup){return rawCurrentAtLifecycle(lifecycle,cleanup);}
        private boolean rawCurrentAtLifecycle(ModuleManager.LifecycleSnapshot projected,boolean cleanup){
            if(!client.isSameThread()||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=FarmMaintenanceCoordinator.this
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules||dev.veltraph.client.VeltraphBootstrap.config()!=config
                ||externalControlOwned!=external||policies.get(original.helper())!=original.policy()
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.getCameraEntity()!=original.player()||client.gui.screen()!=null||!original.player().isAlive()
                ||original.player().containerMenu!=original.player().inventoryMenu||!original.player().inventoryMenu.getCarried().isEmpty()
                ||original.player().getInventory().getSelectedSlot()!=original.slot()||!input.current()
                ||!knownGameplaySettingsCurrent(original.policy().settings(),settings)
                ||!(cleanup?modules.ownCleanupLifecycleCurrent(projected,original.helper()):modules.lifecycleCurrent(projected)))return false;
            if(!ItemStack.matches(original.tool(),original.player().getInventory().getItem(original.slot())))return false;
            for(var slot:PROTECTED)if(!ItemStack.matches(original.gear().get(slot),original.player().getItemBySlot(slot)))return false;
            return client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
                &&externalControlOwned==external&&policies.get(original.helper())==original.policy()
                &&(cleanup?modules.ownCleanupLifecycleCurrent(projected,original.helper()):modules.lifecycleCurrent(projected));
        }
        private boolean originalLive(boolean cleanup){
            return captured==original&&token==originalToken&&witness==restoredWitness&&lease.owns(originalToken)
                &&(lease.state()==FarmMaintenanceLease.State.RETURNING||(cleanup&&lease.state()==FarmMaintenanceLease.State.READY))
                &&!rollingBack&&!activating&&(cleanup||!completing)&&engineWitness==engine
                &&original.farm().maintenanceOwnedBy(originalToken);
        }
        private boolean observedCurrent(){
            return originalLive(false)&&identities(true)&&restoration(restoredWitness)
                &&engine!=null&&original.farm().maintenanceEngineUnchanged(originalToken,engine)
                &&location.current()&&dev.veltraph.client.VeltraphBootstrap.ownedUseControlCurrent(control)
                &&rawCurrent(false)&&originalLive(false)&&call.currentFinalNative();
        }
        /** Providers finish here; the service must repeat currentFinalNative after its own provider reads. */
        public boolean current(){return selectedSavedReturnSource==this&&observedCurrent()&&currentFinalNative();}
        /** No policy/loadout/settings supplier, role, authority supplier, clock or context collector is invoked. */
        public boolean currentFinalNative(){return selectedSavedReturnSource==this&&originalLive(false)
            &&original.originalPublication()!=null&&original.originalPublication().currentForSavedReturn(this)
            &&call.matchingDriver(returning)&&call.currentFinalNative()&&rawCurrent(false)
            &&selectedSavedReturnSource==this&&originalLive(false);}
        public boolean cleanupCurrent(){
            if(selectedSavedReturnSource!=this||!call.cleanupFinalNative())return false;
            if(closingSavedReturnSource==this)return cleanupFinalNative();
            return originalLive(true)&&location.current()&&dev.veltraph.client.VeltraphBootstrap.ownedUseControlCurrent(control)
                &&cleanupFinalNative();
        }
        /** A closed source exists solely inside the actual original returning.cancel stack. */
        public boolean cleanupFinalNative(){
            boolean live=originalLive(true),closed=closingSavedReturnSource==this&&captured==null&&token==null&&witness==null;
            return selectedSavedReturnSource==this&&(live||closed)&&call.matchingDriver(returning)&&call.cleanupFinalNative()
                &&rawCurrent(true)&&selectedSavedReturnSource==this
                &&(originalLive(true)||(closingSavedReturnSource==this&&captured==null&&token==null&&witness==null));
        }
        public Object cleanupCameraOwner(){return closingSavedReturnSource==this?null:originalToken;}
        /** Exact private closed-source metadata retirement; this alone never grants a native write. */
        public boolean closedForMetadataRetirement(){return closingSavedReturnSource==this&&selectedSavedReturnSource==this
            &&captured==null&&token==null&&witness==null&&call.matchingDriver(returning)&&call.cleanupFinalNative();}
    }

    /** Distinct actual restored loaded-return device authority; the HELPER_ACTIVE gameplay source is unchanged. */
    public final class LoadedReturnDeviceAuthority {
        private final SavedReturnAdapter adapter;private final McSavedAnchorRecovery.LoadedReturnCall call;
        private final dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment segment;
        private final dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NormalReturnDeviceAdmission admission;
        private final Capture original;private final FarmMaintenanceLease.Token originalToken;private final Object restored;
        private final SavedMaintenanceEnableSource declaration;private final ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;private final Object control;
        private final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot location;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory;
        private final BooleanSupplier external;private final dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness engine;
        private final Object inventory,menu,gameMode;private final Thread ownerThread=Thread.currentThread();
        private final dev.veltraph.client.VeltraphBootstrap.RootPrivatePassiveSource passiveRoles;
        private dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness returnedRestoration;
        private dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt noLoanTerminal;
        private boolean retired,revoked;private final LoadedReturnStartup startup;
        private LoadedReturnDeviceAuthority(SavedReturnAdapter adapter,McSavedAnchorRecovery.LoadedReturnCall call,
                dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment segment,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NormalReturnDeviceAdmission admission,
                Capture original,FarmMaintenanceLease.Token originalToken,Object restored,SavedMaintenanceEnableSource declaration,
                ModuleManager.LifecycleSnapshot lifecycle,dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,
                Object control,dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot location,
                dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings,
                dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation,
                dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory,
                BooleanSupplier external,dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness engine,LoadedReturnStartup startup){
            this.adapter=adapter;this.call=call;this.segment=segment;this.admission=admission;this.original=original;
            this.originalToken=originalToken;this.restored=restored;this.declaration=declaration;this.lifecycle=lifecycle;
            this.input=input;this.control=control;this.location=location;this.settings=settings;this.installation=installation;
            this.originalHistory=originalHistory;this.external=external;this.engine=engine;this.startup=startup;
            inventory=original.player().getInventory();menu=original.player().inventoryMenu;gameMode=client.gameMode;
            var actual=dev.veltraph.client.VeltraphBootstrap.originalNativeInstallationFor(client);
            passiveRoles=actual==null?null:actual.passiveRoleDeclaration();
        }
        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        public dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NormalReturnDeviceAdmission admissionIdentity(){return admission;}
        public dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment sourceIdentity(){return segment;}
        public McSavedAnchorRecovery.LoadedReturnCall callIdentity(){return call;}
        public FarmReturnPoint canonicalTargetPoint(){return original.pose();}public Vec3 canonicalTarget(){return original.pose().position();}
        public Vec3 selectedSegmentTarget(){return segment.segmentPosition();}
        public Participant inputOwner(){return original.helper();}public FarmMaintenanceLease.Token cameraOwner(){return originalToken;}
        public FarmModule originalFarm(){return original.farm();}public SavedMaintenanceEnableSource originalEnableDeclaration(){return declaration;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public Object originalCheckpointIdentity(){return engine;}public Object originalCaptureIdentity(){return original;}
        public Object restorationWitnessIdentity(){return restored;}public Object sourcePolicyIdentity(){return original.policy();}
        public ModuleManager registryIdentity(){return modules;}public ConfigService configStoreIdentity(){return config;}
        public Object rotationServiceIdentity(){return services.rotation();}
        public int originalSlot(){return original.slot();}
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalSelectionHistory(){return originalHistory;}
        private boolean liveEpisodeCurrent(boolean cleanup){return !revoked&&selectedLoadedReturnDeviceAuthority==this
            &&Thread.currentThread()==ownerThread&&captured==original&&token==originalToken&&witness==restored
            &&retainedSavedMaintenanceEnable==declaration&&declaration.original==original&&declaration.originalToken==originalToken
            &&engineWitness==engine&&engine==declaration.checkpoint&&lease.owns(originalToken)
            &&(lease.state()==FarmMaintenanceLease.State.RETURNING||lease.state()==FarmMaintenanceLease.State.RESTORING&&startup!=null&&startup.current()
                ||cleanup&&lease.state()==FarmMaintenanceLease.State.READY)
            &&!rollingBack&&!activating&&(cleanup||!completing)&&original.farm().maintenanceOwnedBy(originalToken);}
        private boolean closedEpisodeCurrent(){return !revoked&&selectedLoadedReturnDeviceAuthority==this
            &&closingLoadedReturnDeviceAuthority==this&&Thread.currentThread()==ownerThread&&captureRetirement!=null
            &&captureRetirement.original==original&&captureRetirement.declaration==declaration
            &&captured==null&&token==null&&witness==null&&engineWitness==null;}
        private boolean sceneStoredCurrent(boolean cleanup){
            if(!client.isSameThread()||!(cleanup?liveEpisodeCurrent(true)||closedEpisodeCurrent():liveEpisodeCurrent(false))
                ||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=FarmMaintenanceCoordinator.this
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules||dev.veltraph.client.VeltraphBootstrap.config()!=config
                ||externalControlOwned!=external||policies.get(original.helper())!=original.policy()
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||client.getUser()!=original.originalUser()||client.gameMode!=gameMode
                ||!original.originalAccount().equals(original.player().getUUID())||client.getCameraEntity()!=original.player()
                ||original.player().getInventory()!=inventory||original.player().inventoryMenu!=menu||original.player().containerMenu!=menu
                ||!original.player().inventoryMenu.getCarried().isEmpty()||client.gui.screen()!=null||client.isPaused()
                ||!original.player().isAlive()||original.player().isPassenger()||original.player().isScoping()||original.player().isInLava()
                ||original.player().level()!=client.level||client.level.tickRateManager().isEntityFrozen(original.player())
                ||settings==null||!settings.current()||installation==null||!installation.current()||input==null||!input.current()
                ||!knownGameplaySettingsCurrent(original.policy().settings(),declaration.originalSettings)
                ||!(cleanup?modules.ownCleanupLifecycleCurrent(lifecycle,original.helper()):modules.lifecycleCurrent(lifecycle)))return false;
            for(var module:modules.all())if(!(module instanceof dev.veltraph.client.core.AbstractModule stored)
                ||stored.isEnabled()&&module!=original.farm()&&module!=original.helper()
                    &&(passiveRoles==null||!passiveRoles.declaredActor(module)||!passiveRoles.originalRoleHistoryCurrent(modules)))return false;
            return original.originalPublication()!=null&&declaration.originalSettingsCurrent()
                &&externalControlOwned==external&&policies.get(original.helper())==original.policy()
                &&(cleanup?liveEpisodeCurrent(true)||closedEpisodeCurrent():liveEpisodeCurrent(false));
        }
        private boolean resourcesCurrent(boolean cleanup){
            if(!sceneStoredCurrent(cleanup)||!ItemStack.matches(original.tool(),original.player().getInventory().getItem(original.slot())))return false;
            for(var slot:PROTECTED)if(!ItemStack.matches(original.gear().get(slot),original.player().getItemBySlot(slot)))return false;
            return sceneStoredCurrent(cleanup);
        }
        private boolean originalWindowCurrent(){long now=System.currentTimeMillis();return now>=segment.originalStartedAtMillis()
            &&now>=segment.controllerIdentity().observedClockMillis()&&now<segment.originalDeadlineMillis()
            &&call.originalJourneyDeadlineMillis()>=0&&now<call.originalJourneyDeadlineMillis()&&lease.deadlineCurrent(originalToken,now);}
        /** Excludes the Root publication call: the Root proves that actual receipt around this exact source veto. */
        public boolean publicationAffiliationCurrent(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication exact){return !retired
            &&exact==original.originalPublication()&&sceneStoredCurrent(false)&&call.matchingSavedReturnAdapter(returning,adapter)
            &&call.nativeSceneCurrent()&&segment.nativeRealmCurrent()&&admission.loadedMaintenanceDeviceFieldsCurrent(this)
            &&admission.loadedMaintenanceDeviceSlotFieldsCurrent(this)&&originalWindowCurrent()&&sceneStoredCurrent(false);}
        /** Distinct cleanup-only attachment, including the exact original cancel stack after tuple detachment. */
        public boolean cleanupPublicationAffiliationCurrent(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication exact){return exact==original.originalPublication()
            &&sceneStoredCurrent(true)&&call.matchingSavedReturnAdapter(returning,adapter)&&call.cleanupSceneCurrent()
            &&(closedEpisodeCurrent()?segment.cleanupSourceCurrent():segment.nativeRealmCurrent()||segment.cleanupSourceCurrent())
            &&admission.loadedMaintenanceDeviceCleanupFieldsCurrent(this)&&admission.loadedMaintenanceDeviceCleanupSlotFieldsCurrent(this)
            &&sceneStoredCurrent(true);}
        public boolean current(){return !retired&&liveEpisodeCurrent(false)&&identities(true)&&restoration(restored)
            &&original.farm().maintenanceEngineUnchanged(originalToken,engine)&&location.current()
            &&dev.veltraph.client.VeltraphBootstrap.ownedUseControlCurrent(control)&&nativeCurrent();}
        /** Final exact original source/native facts; no policy/loadout/view/provider callback or new snapshot. */
        public boolean nativeCurrent(){return !retired&&resourcesCurrent(false)&&publicationAffiliationCurrent(original.originalPublication())
            &&original.originalPublication().currentForLoadedReturnDevice(this)&&sceneStoredCurrent(false)
            &&admission.loadedMaintenanceDeviceFieldsCurrent(this)&&admission.loadedMaintenanceDeviceSlotFieldsCurrent(this)
            &&originalWindowCurrent()&&liveEpisodeCurrent(false);}
        public boolean cleanupCurrent(){return resourcesCurrent(true)&&cleanupFinalNative();}
        public boolean cleanupFinalNative(){return cleanupPublicationAffiliationCurrent(original.originalPublication())
            &&original.originalPublication().currentForLoadedReturnDeviceCleanup(this)&&sceneStoredCurrent(true)
            &&admission.loadedMaintenanceDeviceCleanupFieldsCurrent(this)&&admission.loadedMaintenanceDeviceCleanupSlotFieldsCurrent(this);}
        public Object cleanupCameraOwner(){return closedEpisodeCurrent()?null:originalToken;}
        private boolean originalSharedCaller(String method){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var first=frames.filter(f->f.getDeclaringClass()!=LoadedReturnDeviceAuthority.class).findFirst().orElse(null);
            return first!=null&&(first.getDeclaringClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class
                ||first.getDeclaringClass().getEnclosingClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class)
                &&first.getMethodName().equals(method);});}
        /** Only the actual native own restoration callback may retire this source and retain its returned history. */
        public boolean originalNormalSlotRestorationReturned(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness proof){
            if(!originalSharedCaller("applyNormalReturnDeviceSlotSelection")||proof==null||retired||revoked
                ||admission.loadedMaintenanceAuthorityIdentity()!=this||proof.originalLegIdentity()!=admission.legIdentity()
                ||proof.originalSlot()!=original.slot()||!proof.originalHistory().sameStoredObservation(originalHistory)
                ||!proof.retainedLineageCurrent()||!proof.rawProjectionCurrent()||proof.restoredHistory()==null
                ||!proof.restoredHistory().sameStoredObservation(call.expectedSelectionHistory())||!call.originalSelectionHistoryCurrent()
                ||!sceneStoredCurrent(true)||!call.cleanupSceneCurrent())return false;
            returnedRestoration=proof;retired=true;return retiredTickCustodyCurrent();
        }
        /** Actual same-slot native terminal proof; a matching raw slot never retires a device source. */
        public boolean originalNoLoanTerminalReturned(dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt proof){
            if(!originalSharedCaller("finishDeviceConsumed")||proof==null||retired||revoked||admission.originalSlot()!=admission.deviceSlot()
                ||admission.normalSlotLoanIdentity()!=null||proof!=admission.terminalCleanupIdentity()||proof.issue()!=admission.issueIdentity()
                ||proof.previousSlot()!=original.slot()||!originalHistory.current()||!call.originalSelectionHistoryCurrent()
                ||!proof.current()||!nativeCurrent()||!originalHistory.current())return false;
            noLoanTerminal=proof;retired=true;return retiredTickCustodyCurrent();
        }
        private boolean retiredFactsCurrent(){
            if(!retired||revoked||!liveEpisodeCurrent(true)||!sceneStoredCurrent(true)||!call.nativeSceneCurrent()||!call.originalSelectionHistoryCurrent()
                ||original.player().getInventory().getSelectedSlot()!=original.slot()||!originalWindowCurrent())return false;
            if(returnedRestoration!=null)return returnedRestoration.originalLegIdentity()==admission.legIdentity()
                &&returnedRestoration.retainedLineageCurrent()&&returnedRestoration.rawProjectionCurrent()
                &&returnedRestoration.restoredHistory().sameStoredObservation(call.expectedSelectionHistory())&&sceneStoredCurrent(true);
            return noLoanTerminal!=null&&noLoanTerminal==admission.terminalCleanupIdentity()&&noLoanTerminal.issue()==admission.issueIdentity()
                &&noLoanTerminal.previousSlot()==original.slot()&&originalHistory.current()
                &&originalHistory.sameStoredObservation(call.expectedSelectionHistory())&&sceneStoredCurrent(true);
        }
        public boolean retiredPublicationAffiliationCurrent(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication exact){
            return exact==original.originalPublication()&&retiredFactsCurrent();
        }
        private boolean retiredTickCustodyCurrent(){return retiredFactsCurrent()
            &&original.originalPublication().currentForLoadedReturnDeviceRetired(this)&&retiredFactsCurrent();}
        private boolean tickReturnCustodyCurrent(){return retired?retiredTickCustodyCurrent():nativeCurrent();}
    }
    /** Original selected-slot guard, extended only by the same private genuine restored-return device source. */
    private boolean originalReturnSelectedSlotCurrent(){
        var original=captured;if(original==null||client.player!=original.player())return false;
        var authority=selectedLoadedReturnDeviceAuthority;
        if(authority!=null&&authority.original==original&&authority.originalToken==token&&authority.restored==witness)
            return authority.tickReturnCustodyCurrent();
        return client.player.getInventory().getSelectedSlot()==original.slot();
    }
    private RetainedLoadoutOperation retainedOperation;
    private RetainedLoadoutLoan retainedLoadoutLoan;
    /** Native retained gear belongs to one original run, never a new admission or public gear witness. */
    public final class RetainedLoadoutLoan {
        private final FarmModule farm;private final Object player,world,connection;private final String profile;
        private final dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AppliedReceipt applied;
        private final String main,alt,target;private final Object origin;private boolean revoked;
        private final ModuleManager.LifecycleSnapshot farmRun;private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> originalMain;
        private RetainedLoadoutLoan(RetainedLoadoutOperation operation){farm=operation.original.farm();player=operation.original.player();world=operation.original.world();connection=operation.original.connection();profile=operation.original.profile();applied=operation.applied;main=operation.main;alt=operation.alt;target=operation.target;origin=operation.dispatch;farmRun=modules.lifecycleSnapshot();settings=operation.settings;originalMain=operation.originalMain;}
        public String retainedName(){return target;}
        private boolean sameRun(){String observedProfile=dev.veltraph.client.rewarp.mc.McFarmCycleCoordinator.profileId(farm);return profile.equals(observedProfile)&&sameStoredRun();}
        private boolean sameStoredRun(){return !revoked&&farm.isEnabled()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&modules.originalModuleLifecycleCurrent(farmRun,farm)&&settings!=null&&settings.current()&&applied.retainedGearCurrent();}
    }
    /** Refusal-only original MAIN identity check before reservation. No copied contract can mint a loan. */
    public boolean retainedInitialGearAllowed(FarmModule originalFarm,dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AncillaryContract main){thread();
        if(main==null||client.player==null||client.level==null)return false;
        var nativeMain=retainedLoadoutLoan==null?nativeRetainedGear():retainedLoadoutLoan.originalMain;
        if(retainedLoadoutLoan!=null&&(retainedLoadoutLoan.farm!=originalFarm||!retainedLoadoutLoan.sameRun()))return false;
        var encoded=new TreeMap<String,String>();nativeMain.forEach((name,identity)->encoded.put(name,identity.persistentCanonicalEncoding()));
        return encoded.equals(main.exactNativeGear());
    }
    /** Immutable native MAIN is retained at the authorized Root arm, never refreshed after callbacks. */
    public Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> originalRetainedMainAtArm(FarmModule farm,dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AncillaryContract main,dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.InitialGearHistory initial){
        if(initial==null||!initial.current()||!retainedInitialGearAllowed(farm,main))return null;
        var original=retainedLoadoutLoan==null?initial.gear():retainedLoadoutLoan.originalMain;
        var encoded=new TreeMap<String,String>();original.forEach((name,identity)->encoded.put(name,identity.persistentCanonicalEncoding()));
        return encoded.equals(main.exactNativeGear())&&initial.current()?original:null;
    }
    /** Read-only source choice; an unknown retained loan stops new dispatch instead of inventing MAIN. */
    public boolean retainedSelectionNeeded(FarmModule originalFarm,String target){thread();
        String main=config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_FARMING_LOADOUT),alt=config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_PEST_LOADOUT);
        if(target==null||main==null||alt==null||main.isBlank()||alt.isBlank()||(!target.equals(main)&&!target.equals(alt))||main.equals(alt))return false;
        if(retainedLoadoutLoan==null)return target.equals(alt);
        if(retainedLoadoutLoan.farm!=originalFarm||!retainedLoadoutLoan.sameRun()){retainedLoadoutLoan.revoked=true;return false;}
        return !target.equals(retainedLoadoutLoan.target);
    }
    /** Private operation source retirement only; this cannot waive native inventory or engine guards. */
    public boolean retainedOriginalFarmOwned(FarmModule farm,FarmMaintenanceLease.Token candidate,dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch){
        var operation=retainedOperation;return dispatch!=null&&captured!=null&&captured.farm()==farm&&token==candidate&&lease.owns(candidate)
            &&captured.helper() instanceof dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper&&helper.armedDispatch()==dispatch
            &&dispatch.originalFarm()==farm&&dispatch.originalInputPublication()==captured.originalPublication()
            &&retainedSavedMaintenancePause!=null&&retainedSavedMaintenancePause.original==captured
            &&retainedSavedMaintenancePause.originalToken==candidate&&retainedSavedMaintenancePause.completedOriginalPauseProof()
            &&(operation==null?captured.helper()==dev.veltraph.client.VeltraphBootstrap.earlyRetainedLoadout()&&farm.maintenanceOwnedBy(candidate)
                :operation.dispatch==dispatch&&operation.original==captured&&operation.issued==candidate&&!operation.revoked
                    &&(operation.transferRecorded?!farm.isSuspended():farm.maintenanceOwnedBy(candidate)));
    }
    /** Privately issued from the existing committed pair; only this exact native port/flow can consume it. */
    public final class RetainedLoadoutOperation {
        private final Capture original;private final FarmMaintenanceLease.Token issued;
        private final SavedMaintenanceEnableSource declaration=retainedSavedMaintenanceEnable;
        private final SavedMaintenancePauseCall pause=retainedSavedMaintenancePause;
        private final dev.veltraph.client.farming.module.FarmMaintenanceEngineWitness checkpoint=engineWitness;
        private final Thread ownerThread=Thread.currentThread();
        private final dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper;
        private final dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort port;
        private final dev.veltraph.client.garden.loadout.NamedLoadoutFlow flow;
        private final dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch;
        private final ModuleManager.LifecycleSnapshot activeLifecycle;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot control;
        private final Object location;private final String target,main,alt;private final Vec3 samePose;private final float sameYaw,samePitch;private final Pose samePosture;
        private final dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings frozenBindings;private final dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.InitialGearHistory initialGear;
        private final dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AncillaryContract contract;
        private final Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> originalMain;
        private dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
        private dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
        private long epoch,serial,applications;
        private boolean transferRecorded,rootClaimed,disableClaimed,inputClaimed,restoringKeys,restoreReturnedNormally,leaseCompletedNormally,closeObserverFailed;
        private int nextRestoreIndex;private RetainedMaintenanceRestoreKey restoreInstruction;
        private dev.veltraph.client.VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation rootCall;
        private ModuleManager.ActualDisableStart disabling;private ModuleManager.OwnDisableReceipt disabled;
        private ModuleManager.LifecycleSnapshot stopped;
        private McIssuedReturnInventory.RetainedMaintenanceSnapshot resources;
        private boolean capturingResources,resourcesClosed;
        private dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AppliedReceipt applied;
        private boolean finishing,finished,revoked;
        private RetainedLoadoutOperation(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper,
                dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch,
                dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort port,dev.veltraph.client.garden.loadout.NamedLoadoutFlow flow,String target){
            original=captured;issued=token;this.helper=helper;this.dispatch=dispatch;this.port=port;this.flow=flow;this.target=target;
            samePose=position();sameYaw=client.player.getYRot();samePitch=client.player.getXRot();samePosture=client.player.getPose();
            main=config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_FARMING_LOADOUT);alt=config.get(dev.veltraph.client.config.PestLoadoutConfig.PEST_PEST_LOADOUT);
            activeLifecycle=modules.lifecycleSnapshot();settings=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            control=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
            frozenBindings=helper.frozenBindings();contract=helper.frozenContract();
            originalMain=helper.originalMainAtArm();initialGear=helper.initialGearHistory();
            location=context instanceof dev.veltraph.client.game.mc.McGameContextService mc?mc.gameplaySignalsSnapshot():null;
        }
        public boolean currentFor(dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort exactPort,dev.veltraph.client.garden.loadout.NamedLoadoutFlow exactFlow,String exactTarget){
            return nativeCurrentFor(exactPort,exactFlow,exactTarget);
        }
        /** Final known-store/pair/native leaf; it invokes no arbitrary readiness or loadout supplier. */
        public boolean nativeCurrentFor(dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort exactPort,dev.veltraph.client.garden.loadout.NamedLoadoutFlow exactFlow,String exactTarget){
            if(revoked||finished||retainedOperation!=this||exactPort!=port||exactFlow!=flow||!target.equals(exactTarget)||!client.isSameThread()
                ||captured!=original||token!=issued||!lease.owns(issued)||lease.state()!=FarmMaintenanceLease.State.HELPER_ACTIVE||original.helper()!=helper
                ||modules!=dev.veltraph.client.VeltraphBootstrap.moduleManager()||config!=dev.veltraph.client.VeltraphBootstrap.config()
                ||policies.get(helper)!=original.policy()||original.farm().isEnabled()==false||(transferRecorded?original.farm().isSuspended():!original.farm().maintenanceOwnedBy(issued))
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.player==null||!client.player.isAlive()||client.player.getInventory().getSelectedSlot()!=original.slot()
                ||settings==null||!settings.current()||(!inputClaimed&&(control==null||!control.current()||!control.active()||!helper.id().equals(control.ownerModuleId())))
                ||(!inputClaimed&&!dispatch.continuationCurrent(FarmMaintenanceCoordinator.this,helper,original.farm()))
                ||declaration==null||retainedSavedMaintenanceEnable!=declaration||declaration.original!=original||!declaration.originalSettingsCurrent()
                ||pause==null||retainedSavedMaintenancePause!=pause||pause.original!=original||pause.originalToken!=issued||!pause.completedOriginalPauseProof()
                ||checkpoint==null||engineWitness!=checkpoint||!capturedBindingsCurrent(original)
                ||client.getUser()!=original.originalUser()||!original.originalAccount().equals(original.player().getUUID())
                ||!(location instanceof dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot signals)||!signals.current())return false;
            boolean lifecycleCurrent=!finishing?helper.isEnabled()&&modules.lifecycleCurrent(activeLifecycle)
                :disabled==null?helper.isEnabled()&&modules.lifecycleCurrent(activeLifecycle)
                    :disabled.closedCurrent()&&stopped!=null&&modules.lifecycleCurrent(stopped)
                    &&modules.ownDisableFromMatches(disabled,activeLifecycle,stopped,helper);
            // Root's installed final SystemClockService uses this exact live JDK wall clock. Sample AFTER source callbacks.
            if(!lifecycleCurrent)return false;long now=System.currentTimeMillis();
            if(!lease.deadlineCurrent(issued,now)){revoked=true;return false;}return true;
        }
        public boolean bindingsCurrentFor(dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort exactPort,dev.veltraph.client.garden.loadout.NamedLoadoutFlow exactFlow,String name,
                dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings settings,dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AncillaryContract contract){
            return exactPort==port&&exactFlow==flow&&target.equals(name)&&settings==frozenBindings&&contract==this.contract
                &&helper.retainedBindingsCurrent(exactPort,exactFlow,name,settings,contract)&&nativeCurrentFor(exactPort,exactFlow,name);}
        public boolean actionsAllowed(){return !finishing&&!finished&&!revoked&&nativeCurrentFor(port,flow,target);}
        public dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings settings(){return frozenBindings;}
        public dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.InitialGearHistory initialGearHistory(){return initialGear;}
        public dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AncillaryContract contract(){return contract;}
        /** Source-only terminal observation, accepted only by this operation's original pure lease. */
        public boolean terminalLeaseCurrent(FarmMaintenanceLease exact,FarmMaintenanceLease.Token candidate){return exact==lease&&candidate==issued&&transferRecorded&&finishing&&!finished
            &&restoreReturnedNormally&&inputClaimed&&nextRestoreIndex==9&&!restoringKeys&&resources!=null&&resources.currentFinal()
            &&nativeCurrentFor(port,flow,target)&&applied!=null&&applied.currentFor(this,flow,target)&&sameRetainedPose(this)
            &&hardware!=null&&hardware.mousePoseCurrent()&&installation!=null&&installation.current()&&epoch==dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)
            &&serial==dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)&&applications==dev.veltraph.client.service.mc.McOwnedRotation.frameApplications(client)
            &&!dev.veltraph.client.service.mc.McOwnedRotation.hasQueuedApplication(client)&&lease.deadlineCurrent(issued,System.currentTimeMillis());}
        /** Removal only after this exact original pure lease actually observes expiration. */
        public void retireObservedLeaseExpiry(FarmMaintenanceLease exact,FarmMaintenanceLease.Token candidate,long now){if(exact==lease&&candidate==issued&&retainedOperation==this&&!lease.deadlineCurrent(issued,now))revoked=true;}
        /** Actual original lease age, diagnostic only; no timing refresh or fabricated clock. */
        public long leaseElapsedMillis(){return lease.elapsedMillis(issued,System.currentTimeMillis());}
        private boolean sameNativeTerminalState(){return hardware!=null&&(inputClaimed?hardware.mousePoseCurrent():hardware.current())&&installation!=null&&installation.current()
            &&epoch==dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)&&serial==dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)
            &&applications==dev.veltraph.client.service.mc.McOwnedRotation.frameApplications(client)&&!dev.veltraph.client.service.mc.McOwnedRotation.hasQueuedApplication(client);}
        public String target(){return target;}

        public FarmMaintenanceCoordinator coordinatorIdentity(){return FarmMaintenanceCoordinator.this;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalPublication();}
        public SavedMaintenanceEnableSource originalEnableDeclaration(){return declaration;}
        public FarmModule farm(){return original.farm();}public Participant helperIdentity(){return helper;}
        public FarmMaintenanceLease.Token maintenanceToken(){return issued;}
        public int originalToolSlot(){return original.slot();}
        public dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch originalDispatchIdentity(){return dispatch;}
        public Object originalCheckpointIdentity(){return checkpoint;}public Object originalPauseIdentity(){return original;}
        public ModuleManager.LifecycleSnapshot originalEnabledLifecycle(){return activeLifecycle;}
        public boolean claimOriginalRetainedResume(dev.veltraph.client.VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation actual){
            if(rootClaimed||actual==null||!finishing||retainedOperation!=this||!actual.matchesRetained(this)||Thread.currentThread()!=ownerThread)return false;
            rootClaimed=true;rootCall=actual;return beforeNativeCurrent();
        }
        public boolean beforeNativeCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesRetained(this)&&!disableClaimed
            &&nativeCurrentFor(port,flow,target)&&applied!=null&&applied.currentFor(this,flow,target)&&sameRetainedPose(this)
            &&modules.lifecycleCurrent(activeLifecycle)&&original.farm().maintenanceEngineUnchanged(issued,checkpoint)
            &&lease.deadlineCurrent(issued,System.currentTimeMillis());}
        public boolean claimOriginalDisable(ModuleManager.ActualDisableStart actual){
            if(disableClaimed||actual==null||retainedOperation!=this||actual.registryIdentity()!=modules||actual.parent()!=helper
                ||Thread.currentThread()!=ownerThread)return false;
            boolean named=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=RetainedLoadoutOperation.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.VeltraphBootstrap.class
                    &&caller.getMethodName().equals("captureOriginalRootDisable");});
            if(!named)return false;disableClaimed=true;disabling=actual;return beforeDisableCurrent();
        }
        public boolean beforeDisableCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesRetained(this)&&disableClaimed&&disabling!=null
            &&completing&&disabling.startingCurrent()&&disabling.startedFrom(activeLifecycle)&&original.farm().maintenanceOwnedBy(issued)
            &&retainedOperation==this&&captured==original&&token==issued&&declaration.originalSettingsCurrent()&&sameRetainedPose(this)
            &&applied!=null&&applied.currentFor(this,flow,target)&&lease.deadlineCurrent(issued,System.currentTimeMillis());}
        public boolean matchesCompletedDisable(ModuleManager.OwnDisableReceipt receipt){
            if(!disableClaimed||disabling==null||receipt==null||receipt.actualStart()!=disabling||!receipt.closedCurrent()||disabled!=null
                ||retainedOperation!=this||captured!=original||token!=issued||Thread.currentThread()!=ownerThread)return false;
            var actualStopped=receipt.stoppedSnapshot();
            if(actualStopped==null||!modules.ownDisableFromMatches(receipt,activeLifecycle,actualStopped,helper))return false;
            disabled=receipt;stopped=actualStopped;return modules.lifecycleCurrent(stopped)&&lease.deadlineCurrent(issued,System.currentTimeMillis());
        }
        public boolean resumePublicationCurrent(){return rootClaimed&&rootCall!=null&&rootCall.matchesRetained(this)&&transferRecorded
            &&!restoreReturnedNormally&&retainedOperation==this&&captured==original&&token==issued&&lease.owns(issued)
            &&lease.state()==FarmMaintenanceLease.State.HELPER_ACTIVE&&disabled!=null&&disabled.closedCurrent()&&modules.lifecycleCurrent(stopped)
            &&declaration.originalSettingsCurrent()&&engineWitness==checkpoint&&original.farm().isEnabled()&&!original.farm().isSuspended()
            &&sameRetainedPose(this)&&resources!=null&&resources.currentFinal()&&applied!=null&&applied.currentFor(this,flow,target)
            &&lease.deadlineCurrent(issued,System.currentTimeMillis());}
        public boolean prepublicationCurrent(){return resumePublicationCurrent()&&original.originalPublication()!=null
            &&original.originalPublication().nativeHistoryCurrent()&&resumePublicationCurrent();}
        public boolean claimOriginalPublication(dev.veltraph.client.VeltraphBootstrap.RootOriginalInputInvocation actual){
            if(inputClaimed||actual==null||retainedOperation!=this||!actual.matchesRetainedMaintenanceResume(this))return false;
            inputClaimed=true;return prepublicationCurrent();
        }
        public boolean afterOriginalInputCurrent(){return inputClaimed&&resumePublicationCurrent();}
        public boolean restorationEpisodeCurrent(){return restoringKeys&&retainedOperation==this&&restoreInstruction!=null
            &&restoreInstruction.source==this&&restoreInstruction.index==nextRestoreIndex&&nextRestoreIndex>=0&&nextRestoreIndex<9
            &&rootClaimed&&inputClaimed&&transferRecorded&&!restoreReturnedNormally&&Thread.currentThread()==ownerThread
            &&captured==original&&token==issued&&engineWitness==checkpoint&&retainedSavedMaintenanceEnable==declaration;}
        public boolean resourceCaptureCurrent(){return capturingResources&&resources==null&&!transferRecorded&&!inputClaimed
            &&!restoringKeys&&!restoreReturnedNormally&&resourceSnapshotCurrent();}
        public boolean resourceSnapshotCurrent(){
            if(resourcesClosed||Thread.currentThread()!=ownerThread||rootCall==null||!rootClaimed||disabled==null||!disabled.closedCurrent()
                ||stopped==null||!modules.lifecycleCurrent(stopped)||!modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||client.getUser()!=original.originalUser()||!original.originalAccount().equals(original.player().getUUID()))return false;
            if(leaseCompletedNormally)return rootCall.belongsRetainedSource(this)&&restoreReturnedNormally&&inputClaimed&&transferRecorded&&nextRestoreIndex==9&&!restoringKeys
                &&lease.state()==FarmMaintenanceLease.State.RESUMED&&retainedOperation==this&&captured==original&&token==issued
                &&retainedSavedMaintenanceEnable==declaration&&engineWitness==checkpoint;
            return !resourcesClosed&&(capturingResources||resources!=null)&&retainedOperation==this
            &&rootClaimed&&rootCall!=null&&rootCall.matchesRetained(this)&&Thread.currentThread()==ownerThread
            &&captured==original&&token==issued&&lease.owns(issued)&&lease.state()==FarmMaintenanceLease.State.HELPER_ACTIVE
            &&disabled!=null&&disabled.closedCurrent()&&stopped!=null&&modules.lifecycleCurrent(stopped)
            &&modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())&&retainedSavedMaintenanceEnable==declaration
            &&declaration.original==original&&declaration.originalSettingsCurrent()&&engineWitness==checkpoint
            &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
            &&client.options==original.options()&&client.getUser()==original.originalUser()&&original.originalAccount().equals(original.player().getUUID())
            &&dev.veltraph.client.VeltraphBootstrap.farmMaintenance()==FarmMaintenanceCoordinator.this
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==modules&&dev.veltraph.client.VeltraphBootstrap.config()==config;}
        /** Negative genuine terminal observation only; this phase cannot issue loadout/native effects. */
        public boolean terminalAppliedObservationCurrent(dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort exactPort,
                dev.veltraph.client.garden.loadout.NamedLoadoutFlow exactFlow,String exactTarget,
                dev.veltraph.client.garden.loadout.NamedLoadoutFlow.Settings exactSettings,
                dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AncillaryContract exactContract){
            if(exactPort!=port||exactFlow!=flow||!target.equals(exactTarget)||exactSettings!=frozenBindings||exactContract!=contract
                ||!leaseCompletedNormally||!restoreReturnedNormally||!transferRecorded||!inputClaimed||finished||revoked
                ||retainedOperation!=this||captured!=original||token!=issued||Thread.currentThread()!=ownerThread
                ||lease.state()!=FarmMaintenanceLease.State.RESUMED||rootCall==null||!rootCall.belongsRetainedSource(this)
                ||retainedSavedMaintenanceEnable!=declaration||declaration.original!=original||engineWitness!=checkpoint
                ||!declaration.originalSettingsCurrent()||settings==null||!settings.current()||!capturedBindingsCurrent(original)
                ||disabled==null||!disabled.closedCurrent()||stopped==null||!modules.lifecycleCurrent(stopped)
                ||!modules.originalModuleLifecycleCurrent(original.beforeHelper(),original.farm())
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||client.getUser()!=original.originalUser()||!original.originalAccount().equals(original.player().getUUID())
                ||!helper.retainedBindingsCurrent(port,flow,target,frozenBindings,contract))return false;
            return retainedOperation==this&&captured==original&&token==issued&&leaseCompletedNormally&&!finished&&!revoked
                &&lease.completedRetainedDeadlineCurrent(issued,this,System.currentTimeMillis());
        }
        public boolean completedOriginalLeaseIdentity(FarmMaintenanceLease exact,FarmMaintenanceLease.Token candidate){return exact==lease&&candidate==issued
            &&leaseCompletedNormally&&restoreReturnedNormally&&!finished&&!revoked&&retainedOperation==this&&captured==original&&token==issued;}
        /** Final original terminal facts after native close and all late terminal receipt/pose callbacks. */
        private boolean finalStoredTerminalFactsCurrent(){
            if(closeObserverFailed||!rootClaimed||!inputClaimed||!restoreReturnedNormally||!leaseCompletedNormally||!transferRecorded
                ||nextRestoreIndex!=9||restoringKeys||finished||revoked||retainedOperation!=this||captured!=original||token!=issued
                ||Thread.currentThread()!=ownerThread||engineWitness!=checkpoint||retainedSavedMaintenanceEnable!=declaration
                ||resources==null||resourcesClosed||applied==null||rootCall==null||!rootCall.belongsRetainedSource(this)
                ||client.player!=original.player()||client.level!=original.world()||client.options!=original.options()
                ||settings==null||!settings.current()||stopped==null||!modules.lifecycleCurrent(stopped)
                ||helper.frozenBindings()!=frozenBindings||helper.frozenContract()!=contract||helper.armedDispatch()!=dispatch
                ||!storedOriginalBindingsCurrent(original)||lease.state()!=FarmMaintenanceLease.State.RESUMED)return false;
            if(!resources.finalStoredFactsCurrent()||!applied.originalWindowDeadlineCurrentFor(this,flow,target))return false;
            return retainedOperation==this&&captured==original&&token==issued&&engineWitness==checkpoint&&retainedSavedMaintenanceEnable==declaration
                &&client.player==original.player()&&client.level==original.world()&&client.options==original.options()&&!revoked&&!finished
                &&lease.completedRetainedDeadlineCurrent(issued,this,System.currentTimeMillis());
        }
        public boolean normalReturnedCurrent(){return !closeObserverFailed&&rootClaimed&&inputClaimed&&restoreReturnedNormally&&leaseCompletedNormally&&nextRestoreIndex==9
            &&!restoringKeys&&!finished&&Thread.currentThread()==ownerThread&&retainedOperation==this&&captured==original&&token==issued
            &&retainedSavedMaintenanceEnable==declaration&&engineWitness==checkpoint&&lease.state()==FarmMaintenanceLease.State.RESUMED
            &&disabled!=null&&disabled.closedCurrent()&&modules.lifecycleCurrent(stopped)&&resources!=null&&resources.currentFinal()
            &&applied!=null&&applied.terminalCurrentFor(this,flow,target)
            &&original.farm().isEnabled()&&!original.farm().isSuspended()&&sameRetainedPose(this)&&sameNativeTerminalState()
            &&lease.completedRetainedDeadlineCurrent(issued,this,System.currentTimeMillis());}

    }
    /** Called only by the exact committed registered early helper, retaining the Root-issued timing invocation. */
    public RetainedLoadoutOperation beginRetainedLoadout(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper,
            dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch,
            dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort port,dev.veltraph.client.garden.loadout.NamedLoadoutFlow flow,String target){
        thread();if(retainedOperation!=null||helper==null||dispatch==null||port==null||flow==null||captured==null||captured.helper()!=helper
            ||lease.state()!=FarmMaintenanceLease.State.HELPER_ACTIVE||!helper.isEnabled()||!dispatch.continuationCurrent(this,helper,captured.farm())
            ||!flow.usesExactPort(port)||!valid(token)||!retainedPauseSettled(helper,token,dispatch)||!protectedRestored())return null;
        if(retainedLoadoutLoan!=null&&!retainedLoadoutLoan.sameRun())return null;
        var inheritedLoan=retainedLoadoutLoan;var operation=new RetainedLoadoutOperation(helper,dispatch,port,flow,target);
        // All provider/profile/config observations precede this final original native gear lineage check.
        if(operation.originalMain==null||operation.frozenBindings==null||operation.contract==null||operation.target==null
            ||operation.main==null||operation.alt==null||operation.main.isBlank()||operation.alt.isBlank()||operation.main.equals(operation.alt)
            ||(!operation.target.equals(operation.main)&&!operation.target.equals(operation.alt))
            ||operation.initialGear==null||!operation.initialGear.current()||inheritedLoan!=retainedLoadoutLoan||inheritedLoan!=null&&!inheritedLoan.sameRun()||inheritedLoan==null&&!operation.originalMain.equals(nativeRetainedGear()))return null;
        retainedOperation=operation;
        if(!operation.bindingsCurrentFor(port,flow,target,operation.frozenBindings,operation.contract)||!operation.nativeCurrentFor(port,flow,target)||!operation.initialGear.current()||inheritedLoan!=null&&!inheritedLoan.sameStoredRun()||inheritedLoan==null&&!operation.originalMain.equals(nativeRetainedGear())){retainedOperation=null;return null;}return operation;
    }

    /** Existing actual SourceP pause/checkpoint settles naturally; no old OwnedPauseCall is created. */
    public boolean retainedPauseSettled(dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper,FarmMaintenanceLease.Token issued,
            dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch){
        thread();var original=captured;var declaration=retainedSavedMaintenanceEnable;var pause=retainedSavedMaintenancePause;
        return original!=null&&original.helper()==helper&&token==issued&&lease.state()==FarmMaintenanceLease.State.HELPER_ACTIVE
            &&declaration!=null&&declaration.original==original&&declaration.originalToken==issued&&declaration.originalSettingsCurrent()
            &&declaration.completedHelperLifecycle!=null&&modules.lifecycleCurrent(declaration.completedHelperLifecycle)
            &&pause!=null&&pause.original==original&&pause.originalToken==issued&&pause.completedOriginalPauseProof()
            &&original.originalPublication()==dispatch.originalInputPublication()&&engineWitness!=null&&preparationPose!=null&&preparationPose.settled()!=null
            &&client.player==original.player()&&client.level==original.world()&&client.getConnection()==original.connection()
            &&client.player!=null&&client.player.isAlive()&&client.player.onGround()&&client.gui.screen()==null
            &&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried()==ItemStack.EMPTY
            &&Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)<=.00001
            &&preparationPose.valid(issued,preparationObservation(false))&&original.farm().maintenanceEngineUnchanged(issued,engineWitness)
            &&dispatch.continuationCurrent(this,helper,original.farm())&&lease.deadlineCurrent(issued,System.currentTimeMillis());
    }
    /** Negative original lease fence used before an operation exists; it never creates or refreshes authority. */
    public boolean retainedOriginalDeadlineCurrent(FarmModule farm,dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule helper,
            FarmMaintenanceLease.Token issued,dev.veltraph.client.VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch){
        var original=captured;var declaration=retainedSavedMaintenanceEnable;var pause=retainedSavedMaintenancePause;
        if(dispatch==null||helper==null||original==null||original.farm()!=farm||original.helper()!=helper||token!=issued||issued==null
            ||!client.isSameThread()||!lease.owns(issued)||lease.state()!=FarmMaintenanceLease.State.HELPER_ACTIVE
            ||helper!=dev.veltraph.client.VeltraphBootstrap.earlyRetainedLoadout()||helper.armedDispatch()!=dispatch
            ||original.originalPublication()!=dispatch.originalInputPublication()||farm!=dispatch.originalFarm()
            ||declaration==null||declaration.original!=original||declaration.originalToken!=issued||!declaration.originalSettingsCurrent()
            ||pause==null||pause.original!=original||pause.originalToken!=issued||!pause.completedOriginalPauseProof()
            ||declaration.completedHelperLifecycle==null||!modules.originalModuleLifecycleCurrent(original.beforeHelper(),farm)
            ||engineWitness==null||engineWitness!=declaration.checkpoint
            ||policies.get(helper)!=original.policy()||!capturedBindingsCurrent(original)||client.player!=original.player()
            ||client.level!=original.world()||client.getConnection()!=original.connection()||client.options!=original.options()
            ||client.getUser()!=original.originalUser()||!original.originalAccount().equals(original.player().getUUID())
            ||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=this||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=modules
            ||dev.veltraph.client.VeltraphBootstrap.config()!=config)return false;
        var operation=retainedOperation;
        if(operation==null){if(!farm.maintenanceOwnedBy(issued)||!modules.originalModuleLifecycleCurrent(declaration.completedHelperLifecycle,helper))return false;}
        else if(operation.disabled==null){if(!helper.isEnabled()||!modules.originalModuleLifecycleCurrent(declaration.completedHelperLifecycle,helper))return false;}
        else if(!operation.disabled.closedCurrent()||operation.stopped==null||!modules.lifecycleCurrent(operation.stopped)
            ||!modules.ownDisableFromMatches(operation.disabled,operation.activeLifecycle,operation.stopped,helper))return false;
        if(operation!=null&&(operation.original!=original||operation.issued!=issued||operation.dispatch!=dispatch||operation.revoked
                ||(operation.transferRecorded?farm.isSuspended():!farm.maintenanceOwnedBy(issued))))return false;
        // No Root continuation callback here: Root consumes this final negative observation itself.
        return captured==original&&token==issued&&retainedSavedMaintenanceEnable==declaration&&retainedSavedMaintenancePause==pause
            &&lease.deadlineCurrent(issued,System.currentTimeMillis());
    }
    public boolean retainedOperationOutstanding(){thread();return retainedOperation!=null;}
    public String retainedLoadoutName(){thread();return retainedLoadoutLoan==null?"":retainedLoadoutLoan.target;}
    public boolean retainedBatchLoadoutBlocked(dev.veltraph.client.core.AbstractModule helper){thread();return retainedLoadoutLoan!=null;}
    private Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> nativeRetainedGear(){
        if(client.player==null||client.level==null)return Map.of();
        var observed=new LinkedHashMap<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot>();
        for(var slot:PROTECTED)observed.put(slot.name(),dev.veltraph.client.inventory.NativeStackIdentity.capture(client.player.getItemBySlot(slot),client.level.registryAccess()));
        return Map.copyOf(observed);
    }
    private boolean sameRetainedPose(RetainedLoadoutOperation operation){var exact=operation.original;
        return exact!=null&&client.player==exact.player()&&client.level==exact.world()&&client.getConnection()==exact.connection()
            &&client.player.isAlive()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu
            &&client.player.inventoryMenu.getCarried()==ItemStack.EMPTY&&client.player.onGround()
            &&Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)<=.00001
            &&client.player.getInventory().getSelectedSlot()==exact.slot()&&client.player.getPose()==operation.samePosture
            &&Double.doubleToLongBits(client.player.getX())==Double.doubleToLongBits(operation.samePose.x())
            &&Double.doubleToLongBits(client.player.getY())==Double.doubleToLongBits(operation.samePose.y())
            &&Double.doubleToLongBits(client.player.getZ())==Double.doubleToLongBits(operation.samePose.z())
            &&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(operation.sameYaw)
            &&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(operation.samePitch);
    }
    /** Actual HELPER_ACTIVE same-pose terminal; ordinary restoration/physical return remains unchanged. */
    public boolean finishRetainedLoadout(RetainedLoadoutOperation operation,dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.AppliedReceipt applied){
        thread();if(operation==null||operation!=retainedOperation||operation.finishing||applied==null
            ||!operation.nativeCurrentFor(operation.port,operation.flow,operation.target)||!applied.currentFor(operation,operation.flow,operation.target)
            ||!sameRetainedPose(operation)||engineWitness!=operation.checkpoint||!operation.original.farm().maintenanceEngineUnchanged(operation.issued,operation.checkpoint)
            ||selectedSavedMaintenanceResumeCall!=null||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource rootSource)
            ||!rootSource.affiliates(this))return false;
        if(operation.target.equals(operation.main)&&!applied.appliedGear().equals(operation.originalMain))return false;
        operation.hardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);
        operation.installation=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(client);
        operation.epoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        operation.serial=dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client);
        operation.applications=dev.veltraph.client.service.mc.McOwnedRotation.frameApplications(client);
        operation.applied=applied;operation.finishing=true;
        dev.veltraph.client.VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation actualRoot=null;
        boolean completed=false,rootCloseAttempted=false;
        try{
            actualRoot=rootSource.beginOriginalRetainedResume(operation);
            if(actualRoot==null)return false;
            completing=true;
            try{operation.helper.setEnabled(false);}finally{completing=false;}
            if(!operation.nativeCurrentFor(operation.port,operation.flow,operation.target)||!applied.currentFor(operation,operation.flow,operation.target)
                ||!sameRetainedPose(operation)||operation.disabled==null||!operation.disabled.closedCurrent()
                ||operation.hardware==null||!operation.hardware.current()||operation.installation==null||!operation.installation.current()
                ||operation.epoch!=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)
                ||operation.serial!=dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)
                ||operation.applications!=dev.veltraph.client.service.mc.McOwnedRotation.frameApplications(client)
                ||dev.veltraph.client.service.mc.McOwnedRotation.hasQueuedApplication(client))return false;
            // Genuine applied gear is verified first. The actual post-disable raw42 window freezes every
            // remaining stack/resource before rebind/resume/providers and the native restore9 continuation.
            operation.capturingResources=true;
            try{operation.resources=McIssuedReturnInventory.captureRetainedMaintenance(client,operation);}
            finally{operation.capturingResources=false;}
            if(operation.resources==null||!operation.resources.currentFinal()
                ||!operation.original.farm().maintenanceEngineUnchanged(operation.issued,operation.checkpoint)
                ||!operation.original.farm().rebindForMaintenance(operation.issued,operation.checkpoint)
                ||!operation.nativeCurrentFor(operation.port,operation.flow,operation.target)||!applied.currentFor(operation,operation.flow,operation.target)
                ||!operation.resources.currentFinal()||!lease.deadlineCurrent(operation.issued,System.currentTimeMillis()))return false;
            if(!operation.original.farm().resumeAfterMaintenance(operation.issued))return false;
            operation.transferRecorded=true; // The real original engine returned normally, once.
            if(!rootSource.publishOriginalRetainedResume(operation,actualRoot))return false;
            restoreKeysForRetainedResume(operation);
            if(!operation.resources.currentFinal()||!applied.currentFor(operation,operation.flow,operation.target)
                ||!operation.nativeCurrentFor(operation.port,operation.flow,operation.target)||!sameRetainedPose(operation)
                ||!lease.deadlineCurrent(operation.issued,System.currentTimeMillis()))return false;
            // Allocate/observe prospective loan before the pure lease commits; no getter after terminal publication.
            var nextLoan=operation.target.equals(operation.main)?null:new RetainedLoadoutLoan(operation);
            if(nextLoan!=null&&!nextLoan.sameStoredRun())return false;
            operation.restoreReturnedNormally=true;
            if(!lease.completeRetained(operation.issued,operation))return false;
            operation.leaseCompletedNormally=true;
            // Original tuple remains installed through genuine Root/native9 closure and raw42 final validation.
            rootCloseAttempted=true;rootSource.closeOriginalRetainedResume(actualRoot);
            if(!actualRoot.normalReturnedCurrent(client)||operation.resources==null||!operation.resources.currentFinal()
                ||!operation.normalReturnedCurrent()||!operation.finalStoredTerminalFactsCurrent())return false;
            actualRoot=null; // The genuine once-only native close has returned normally.
            retainedLoadoutLoan=nextLoan;operation.finished=true;retainedOperation=null;
            captured=null;token=null;witness=null;engineWitness=null;preparation=null;deferredFreshStart=null;pausedKeys=null;preparationPose=null;
            if(retainedSavedMaintenanceEnable==operation.declaration)retainedSavedMaintenanceEnable=null;
            if(retainedSavedMaintenancePause==operation.pause)retainedSavedMaintenancePause=null;
            pendingSavedMaintenanceEnable=null;selectedSavedMaintenanceInputCall=null;
            completed=true;return true;
        }finally{
            completing=false;
            try{if(actualRoot!=null&&!rootCloseAttempted){rootCloseAttempted=true;rootSource.closeOriginalRetainedResume(actualRoot);}}
            catch(RuntimeException|Error unavailable){operation.closeObserverFailed=true;}
            finally{
                try{if(operation.resources!=null)operation.resources.close();}
                catch(RuntimeException|Error unavailable){operation.closeObserverFailed=true;}
                finally{operation.capturingResources=false;operation.resourcesClosed=true;}
                if(!completed){operation.revoked=true;if(applied!=null)applied.revokeRetainedGear();
                    if(retainedOperation==operation&&captured==operation.original&&token==operation.issued)cancel(operation.issued,"Retained original native handoff refused");}
            }
        }
    }
    public RetainedLoadoutOperation pendingRetainedMaintenanceDisableForRoot(ModuleManager.ActualDisableStart actual){
        var operation=retainedOperation;return operation!=null&&operation.finishing&&completing&&actual!=null
            &&actual.registryIdentity()==modules&&actual.parent()==operation.helper?operation:null;
    }
    /** Real ordered nine-key instructions; setters must return original native confirmation for every key. */
    public final class RetainedMaintenanceRestoreKey {
        private final RetainedLoadoutOperation source;private final int index;private final net.minecraft.client.KeyMapping key;
        private final boolean held;private boolean claimed;private dev.veltraph.client.service.mc.McHeldKeys.RetainedMaintenanceKeyInvocation nativeCall;
        private RetainedMaintenanceRestoreKey(RetainedLoadoutOperation source,int index){this.source=source;this.index=index;
            key=source.original.keyMappings()[index];held=source.original.keys()[index];}
        public Minecraft client(){return FarmMaintenanceCoordinator.this.client;}public int index(){return index;}
        public net.minecraft.client.KeyMapping key(){return key;}public boolean desiredHeld(){return held;}public boolean directSetter(){return index<5;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return source.originalInputPublication();}
        public FarmModule farm(){return source.farm();}public FarmModule originalFarm(){return source.farm();}
        public RetainedLoadoutOperation sourceCall(){return source;}
        public dev.veltraph.client.VeltraphBootstrap.RootRetainedMaintenanceResumeInvocation rootResumeIdentity(){return source.rootCall;}
        public dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication originalPublicationIdentity(){return source.originalInputPublication();}
        public Object originalBindingHistory(){return source.original.keyBindingHistories()[index];}
        public boolean beforeNativeCurrent(){return nativeBeforeCurrent();}
        public boolean normalNativeCurrent(){return claimed&&nativeCall!=null&&nativeCall.matchesInstruction(this)&&nativeBeforeCurrent();}
        public boolean nativeBeforeCurrent(){return source.restorationEpisodeCurrent()&&source.restoreInstruction==this&&source.nextRestoreIndex==index
            &&source.rootCall!=null&&source.rootCall.matchesRetained(source)&&source.rootCall.beforeRestoreNativeCurrent(source.original.farm(),client)
            &&source.disabled!=null&&source.disabled.closedCurrent()&&modules.lifecycleCurrent(source.stopped)&&capturedBindingsCurrent(source.original)
            &&source.original.farm().isEnabled()&&!source.original.farm().isSuspended()&&source.sameNativeTerminalState()
            &&lease.deadlineCurrent(source.issued,System.currentTimeMillis());}
        public boolean finalNativeSourceCurrent(){
            var original=source.original;var root=source.rootCall;
            if(!claimed||nativeCall==null||!nativeCall.matchesInstruction(this)||root==null||!source.restorationEpisodeCurrent()
                ||source.restoreInstruction!=this||source.nextRestoreIndex!=index||index<0||index>=9||Thread.currentThread()!=source.ownerThread
                ||captured!=original||token!=source.issued||engineWitness!=source.checkpoint||retainedSavedMaintenanceEnable!=source.declaration
                ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection()
                ||client.options!=original.options()||key!=original.keyMappings()[index]||source.stopped==null||!modules.lifecycleCurrent(source.stopped)
                ||source.resources==null||!source.resources.currentFinal()||source.applied==null||!source.applied.currentFor(source,source.flow,source.target)
                ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||client.player.inventoryMenu.getCarried()!=ItemStack.EMPTY
                ||client.player.getInventory().getSelectedSlot()!=original.slot()||!root.finalRestoreNativeCurrent(source,client)
                ||!capturedBindingsCurrent(original)||!source.declaration.originalSettingsCurrent()||!sameRetainedPose(source))return false;
            // Source/provider/native getter work is complete. The same final stored veto is also
            // invoked by the native writer after its final getter and in-flight prefix checks.
            return finalStoredFieldFactsCurrent();
        }
        /** Negative final original facts only; no Root/provider/pose/current-gear callback or fresh snapshot. */
        public boolean finalStoredFieldFactsCurrent(){
            var original=source.original;
            if(!claimed||nativeCall==null||!source.rootClaimed||!source.inputClaimed||!source.transferRecorded
                ||source.restoreReturnedNormally||source.leaseCompletedNormally||source.finished||source.revoked
                ||!source.restoringKeys||retainedOperation!=source||source.restoreInstruction!=this||source.nextRestoreIndex!=index
                ||index<0||index>=9||Thread.currentThread()!=source.ownerThread||captured!=original||token!=source.issued
                ||engineWitness!=source.checkpoint||retainedSavedMaintenanceEnable!=source.declaration||source.declaration.original!=original
                ||source.declaration.checkpoint!=source.checkpoint||source.resources==null||source.resourcesClosed
                ||source.disabled==null||source.stopped==null||lease.state()!=FarmMaintenanceLease.State.HELPER_ACTIVE||!lease.owns(source.issued)
                ||client.player!=original.player()||client.level!=original.world()||client.options!=original.options()
                ||key!=original.keyMappings()[index]||policies.get(source.helper)!=original.policy()
                ||source.settings==null||!source.settings.current()||!modules.lifecycleCurrent(source.stopped)
                ||source.helper.frozenBindings()!=source.frozenBindings||source.helper.frozenContract()!=source.contract
                ||source.helper.armedDispatch()!=source.dispatch||!storedOriginalBindingsCurrent(original))return false;
            // These owned immutable/native observations perform no actor/provider/native getter callbacks.
            // Retained raw42 field identities, content graph and mutation journal are checked LAST.
            if(!source.resources.finalStoredFactsCurrent()||source.applied==null
                ||!source.applied.originalWindowDeadlineCurrentFor(source,source.flow,source.target))return false;
            return source.restoringKeys&&retainedOperation==source&&source.restoreInstruction==this&&source.nextRestoreIndex==index
                &&captured==original&&token==source.issued&&engineWitness==source.checkpoint&&retainedSavedMaintenanceEnable==source.declaration
                &&client.player==original.player()&&client.level==original.world()&&client.options==original.options()
                &&key==original.keyMappings()[index]&&!source.revoked&&!source.finished
                &&lease.deadlineCurrent(source.issued,System.currentTimeMillis());
        }
        public boolean finalNativeCurrent(dev.veltraph.client.service.mc.McHeldKeys.RetainedMaintenanceKeyInvocation exact){
            return claimed&&nativeCall!=null&&nativeCall==exact&&exact.matchesInstruction(this)&&finalNativeSourceCurrent();}
        public boolean claimNativeInstruction(dev.veltraph.client.service.mc.McHeldKeys.RetainedMaintenanceKeyInvocation actual){
            if(claimed||actual==null||source.restoreInstruction!=this||!actual.matchesInstruction(this))return false;
            boolean named=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
                var caller=frames.filter(f->f.getDeclaringClass()!=RetainedMaintenanceRestoreKey.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.service.mc.McHeldKeys.class
                    &&caller.getMethodName().equals("restoreRetainedMaintenanceKey");});
            if(!named)return false;claimed=true;nativeCall=actual;return nativeBeforeCurrent();
        }
    }
    /** Actual final native key classes and original passive binding journals; no policy/provider/key-mode query. */
    private boolean storedOriginalBindingsCurrent(Capture original){
        if(original==null||client.options!=original.options()||original.keyMappings().length!=9||original.keyBindingHistories().length!=9)return false;
        var actual=heldMappings();
        for(int i=0;i<9;i++){
            var mapping=actual[i];
            if(mapping==null||mapping!=original.keyMappings()[i]
                ||(mapping.getClass()!=net.minecraft.client.KeyMapping.class&&mapping.getClass()!=net.minecraft.client.ToggleKeyMapping.class)
                ||original.keyBindingHistories()[i]==null
                ||!dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(mapping,original.keyBindingHistories()[i]))return false;
        }
        return client.options==original.options();
    }
    private void restoreKeysForRetainedResume(RetainedLoadoutOperation operation){
        if(operation==null||retainedOperation!=operation||operation.restoringKeys||operation.nextRestoreIndex!=0)
            throw new IllegalStateException("Original retained native farm restoration unavailable");
        operation.restoringKeys=true;
        try{for(int i=0;i<9;i++){
            var instruction=new RetainedMaintenanceRestoreKey(operation,i);operation.restoreInstruction=instruction;
            var returned=dev.veltraph.client.service.mc.McHeldKeys.restoreRetainedMaintenanceKey(instruction);
            if(returned==null||returned.instruction()!=instruction||!returned.normallyReturned()||!returned.confirmed()||!instruction.nativeBeforeCurrent())
                throw new IllegalStateException("Original retained native farm restoration refused at "+i);
            operation.nextRestoreIndex=i+1;operation.restoreInstruction=null;
        }}finally{operation.restoringKeys=false;operation.restoreInstruction=null;}
    }

    /** Exact read-only movement lineage. The existing coordinator remains the sole restoration authority. */
    public record MovementAuthorityView(FarmModule farm,Participant helper,FarmMaintenanceLease.Token token,
            FarmReturnPoint capturedPose,FarmMaintenanceLease.State phase,Object captureIdentity,Object sourcePolicyIdentity,
            ModuleManager registryIdentity,Object restorationWitness,ConfigService configStore,Object rotationServiceIdentity,Object player,Object world,Object connection){}
    public MovementAuthorityView movementAuthorityView(Participant helper,FarmMaintenanceLease.Token candidate,
            dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose,Vec3 goal){
        thread();var capture=captured;var phase=lease.state();var restorationWitness=witness;
        if(capture==null||helper==null||capture.helper()!=helper||candidate==null||candidate!=token
                ||!lease.owns(candidate)||!helper.isEnabled()||helper.isPassiveObserver()||!helper.ownsGameplayInput()
                ||purpose==null||!dev.veltraph.client.navigation.SharedMovementPolicy.finite(goal)
                ||!identities(true)||externalControlOwned.getAsBoolean())return null;
        if(phase!=FarmMaintenanceLease.State.HELPER_ACTIVE){
            if((phase!=FarmMaintenanceLease.State.RESTORING&&phase!=FarmMaintenanceLease.State.RETURNING)
                    ||purpose!=dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.EXACT_RETURN
                    ||!goal.equals(capture.pose().position())||restorationWitness==null||!restoration(restorationWitness))return null;
        }
        // Witness callbacks are observed guards, not permission to invent a restored loadout.
        if(captured!=capture||token!=candidate||witness!=restorationWitness||lease.state()!=phase||!lease.owns(candidate)||!identities(true))return null;
        return new MovementAuthorityView(capture.farm(),helper,candidate,capture.pose(),phase,capture,capture.policy(),
            modules,restorationWitness,config,services.rotation(),admittedPlayer(),admittedWorld(),capture.connection());
    }
    private MaintenancePassiveContinuation pendingPassiveMaintenanceContinuation;
    /** One genuine Root passive transition, retaining coordinator/Journey/Source identities before callbacks. */
    public final class MaintenancePassiveContinuation {
        private final dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual;
        private final Capture original=captured;private final FarmMaintenanceLease.Token originalToken=token;
        private final Object restored=witness,checkpoint=engineWitness;
        private final FarmMaintenanceLease.State phase=lease.state();
        private final SavedMaintenanceEnableSource declaration=retainedSavedMaintenanceEnable;
        private final SavedReturnAlignmentSource alignmentPointer=selectedSavedReturnSource;
        private final SavedReturnAlignmentSource alignment=alignmentPointer!=null&&original!=null
            &&alignmentPointer.original==original&&alignmentPointer.originalToken==originalToken
            &&alignmentPointer.call.currentFinalNative()?alignmentPointer:null;
        private final ModuleManager.LifecycleSnapshot helperBefore=declaration==null?null:declaration.completedHelperLifecycle;
        private final ModuleManager.LifecycleSnapshot alignmentBefore=alignment==null?null:alignment.lifecycle;
        private final ModuleManager.LifecycleSnapshot preparedBefore=original!=null&&phase==FarmMaintenanceLease.State.PREPARED?original.beforeHelper():null;
        private final java.util.IdentityHashMap<Object,dev.veltraph.client.VeltraphBootstrap.RootOwnedControlPassiveContinuation> controls=new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<MovementGameplayAuthority,ModuleManager.LifecycleSnapshot> movements=new java.util.IdentityHashMap<>();
        private final Thread thread=Thread.currentThread();
        private McSavedAnchorRecovery.PassiveLifecycleContinuation journey;
        private ModuleManager.LifecycleSnapshot after;
        private boolean preparing,prepared,committed,closed,revoked;
        private MaintenancePassiveContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual){this.actual=actual;}
        private boolean rawIdentityCurrent(){return !revoked&&!closed&&!committed&&pendingPassiveMaintenanceContinuation==this
            &&Thread.currentThread()==thread&&captured==original&&token==originalToken&&witness==restored&&engineWitness==checkpoint
            &&lease.state()==phase&&retainedSavedMaintenanceEnable==declaration&&selectedSavedReturnSource==alignmentPointer
            &&(declaration==null||declaration.completedHelperLifecycle==helperBefore)
            &&(alignment==null||alignment.lifecycle==alignmentBefore)
            &&(preparedBefore==null||original.beforeHelperLineage.current==preparedBefore);}
        private boolean maintenanceSourceCurrent(){
            if(original==null)return declaration==null&&alignment==null&&originalToken==null;
            if(declaration==null||declaration.original!=original||original.originalPublication()!=actual.originalInputPublication()
                    ||!declaration.originalSettingsCurrent()||!original.farm().maintenanceOwnedBy(originalToken)
                    ||client.player!=original.player()||client.level!=original.world()||client.getConnection()!=original.connection())return false;
            if(preparedBefore!=null)return helperBefore==null&&witness==restored&&engineWitness==checkpoint
                &&lease.owns(originalToken)&&modules.lifecycleSameHistory(preparedBefore,actual.beforeLifecycle())
                &&declaration.witnessBound&&declaration.checkpoint!=null&&engineWitness==declaration.checkpoint;
            return helperBefore!=null&&modules.lifecycleSameHistory(helperBefore,actual.beforeLifecycle())
                &&declaration.nativePublicationAffiliationCurrent(actual.originalInputPublication());
        }
        private boolean addControl(Object control){
            if(controls.containsKey(control))return true;
            var proof=dev.veltraph.client.VeltraphBootstrap.captureOwnedControlPassiveContinuation(control,actual);
            if(proof==null)return false;controls.put(control,proof);return true;
        }
        /** Only an already admitted exact old movement source may join before preparation. */
        public boolean retainMovement(MovementGameplayAuthority source){
            if(preparing||prepared||!rawIdentityCurrent()||source==null||source.capture!=original
                    ||source.issuedToken!=originalToken||source.capture.originalPublication()!=actual.originalInputPublication()
                    ||!modules.lifecycleSameHistory(source.lifecycle,actual.beforeLifecycle())
                    ||!movementGameplayAuthorityStillCurrent(source,source.capture.helper(),source.issuedToken,source.purpose,source.goal)){
                revoked=true;return false;
            }
            if(movements.containsKey(source))return true;
            if(!addControl(source.controlSnapshot)){revoked=true;return false;}
            movements.put(source,source.lifecycle);return rawIdentityCurrent();
        }
        public boolean prepare(){
            if(preparing||prepared||!rawIdentityCurrent()){revoked=true;return false;}
            preparing=true;
            try{
                if(!actual.normalReturnedCurrent(client)||actual.registryIdentity()!=modules)return false;
                after=actual.normalReturnedLifecycle();
                if(after==null||!after.known()||!modules.lifecycleCurrent(after))return false;
                if(!maintenanceSourceCurrent())return false;
                if(journey==null||!journey.prepare())return false;
                if(alignment!=null&&(!alignment.originalLive(false)||!journey.prospectiveAlignmentCurrent(alignment.call)
                        ||!alignment.rawCurrentAtLifecycle(after,false)))return false;
                for(var entry:movements.entrySet()){
                    var movement=entry.getKey();if(movement.lifecycle!=entry.getValue()
                        ||!movementGameplayAuthorityAtLifecycle(movement,movement.capture.helper(),movement.issuedToken,movement.purpose,movement.goal,after))return false;
                }
                for(var proof:controls.values())if(!proof.prepare())return false;

                if(!actual.normalReturnedCurrent(client)||!rawIdentityCurrent())return false;
                prepared=true;return true;
            }finally{preparing=false;if(!prepared)revoked=true;}
        }
        /** Final private identities only; every prospective provider/native/source check preceded this method. */
        public boolean rawCurrent(){
            if(!rawIdentityCurrent())return false;
            for(var entry:movements.entrySet())if(entry.getKey().lifecycle!=entry.getValue())return false;
            for(var proof:controls.values())if(!proof.rawCurrent())return false;
            return journey==null||journey.rawCurrent();
        }
        public boolean commit(){
            if(!prepared||!actual.passiveDeclarationCommitWindowCurrent()||!rawCurrent())return false;
            for(var proof:controls.values())if(!proof.commit()){revoked=true;return false;}
            if(journey!=null&&!journey.commit()){revoked=true;return false;}
            if(preparedBefore!=null)original.beforeHelperLineage.current=after;
            if(declaration!=null&&helperBefore!=null)declaration.completedHelperLifecycle=after;
            if(alignment!=null)alignment.lifecycle=after;
            for(var movement:movements.keySet())movement.lifecycle=after;
            committed=true;return true;
        }
        public boolean commitWindowCurrent(){return !closed&&Thread.currentThread()==thread
            &&pendingPassiveMaintenanceContinuation==this&&actual.passiveDeclarationCommitWindowCurrent();}
        public void close(){
            if(Thread.currentThread()!=thread)return;closed=true;
            if(pendingPassiveMaintenanceContinuation==this)pendingPassiveMaintenanceContinuation=null;
        }
    }
    public MaintenancePassiveContinuation capturePassiveMaintenanceContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual){
        if(pendingPassiveMaintenanceContinuation!=null){pendingPassiveMaintenanceContinuation.revoked=true;return null;}
        if(actual==null||!client.isSameThread()||actual.registryIdentity()!=modules||!actual.sourceBeforeCurrent(client)
                ||dev.veltraph.client.VeltraphBootstrap.farmMaintenance()!=this)return null;
        var next=new MaintenancePassiveContinuation(actual);pendingPassiveMaintenanceContinuation=next;boolean accepted=false;
        try{
            if(!next.maintenanceSourceCurrent())return null;
            if(next.alignment!=null&&(!next.alignment.currentFinalNative()
                    ||!modules.lifecycleSameHistory(next.alignmentBefore,actual.beforeLifecycle())||!next.addControl(next.alignment.control)))return null;
            next.journey=returning.capturePassiveLifecycleContinuation(actual);
            if(next.journey==null||!next.rawCurrent()||!actual.sourceBeforeCurrent(client))return null;
            accepted=true;return next;
        }finally{if(!accepted)next.close();}
    }
    /** Privately minted GAMEPLAY source observation, distinct from cancellation lineage. */
    public final class MovementGameplayAuthority {
        private final Capture capture;private final FarmMaintenanceLease.Token issuedToken;
        private final Object restoredWitness;private final FarmMaintenanceLease.State phase;
        private final dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose;private final Vec3 goal;
        private final Object settingsSnapshot,controlSnapshot;private final BooleanSupplier externalSource;
        private final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot location;
        private ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.VeltraphBootstrap.RootPrivatePassiveSource passiveRoles;
        private MovementGameplayAuthority(Capture capture,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose,Vec3 goal,
                Object settingsSnapshot,Object controlSnapshot,dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot location,ModuleManager.LifecycleSnapshot lifecycle){
            this.capture=capture;issuedToken=token;restoredWitness=witness;phase=lease.state();this.purpose=purpose;this.goal=goal;
            this.settingsSnapshot=settingsSnapshot;this.controlSnapshot=controlSnapshot;this.location=location;this.lifecycle=lifecycle;externalSource=externalControlOwned;
            var installation=dev.veltraph.client.VeltraphBootstrap.originalNativeInstallationFor(client);
            passiveRoles=installation==null?null:installation.passiveRoleDeclaration();
        }
    }
    /** Read-only provenance classification only; no Boolean from this method admits native effects. */
    public static boolean hasKnownGameplaySettings(java.util.function.Supplier<String> settings){return settings instanceof dev.veltraph.client.garden.pests.mc.McPestMaintenanceModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.pests.mc.McPestPhilipMaintenanceModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.pests.mc.McPestTrapMaintenanceModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.pests.mc.McPestSprayonatorMaintenanceModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.visitor.mc.McVisitorCycleModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.inventory.selling.McFarmAutoSellModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.inventory.selling.CapturedGeorgeHelper.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.jacob.mc.McJacobRewardModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule.GameplaySettings
        ||settings instanceof dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule.GameplaySettings;}
    private Object knownGameplaySettingsSnapshot(Capture capture){Object settings=capture.policy().settings();var helper=capture.helper();
        if(!hasKnownGameplaySettings(capture.policy().settings()))return null;
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestMaintenanceModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestPhilipMaintenanceModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestTrapMaintenanceModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestSprayonatorMaintenanceModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.visitor.mc.McVisitorCycleModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.inventory.selling.McFarmAutoSellModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.inventory.selling.CapturedGeorgeHelper.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.jacob.mc.McJacobRewardModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        if(settings instanceof dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule.GameplaySettings actual)return actual.snapshot(helper,config,capture.policy());
        return null;
    }
    private boolean knownGameplaySettingsCurrent(Object settings,Object snapshot){
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestMaintenanceModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestTrackedMaintenanceModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestPhilipMaintenanceModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestTrapMaintenanceModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestSprayonatorMaintenanceModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.visitor.mc.McVisitorCycleModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.inventory.selling.McFarmAutoSellModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.inventory.selling.CapturedGeorgeHelper.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.jacob.mc.McJacobRewardModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.garden.pests.mc.McPestEarlyLoadoutModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        if(settings instanceof dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule.GameplaySettings actual)return actual.snapshotCurrent(snapshot);
        return false;
    }
    /** First qualified source branch: original HELPER_ACTIVE; arbitrary settings/loadout/external closures refuse. */
    public MovementGameplayAuthority movementGameplayAuthority(Participant helper,FarmMaintenanceLease.Token candidate,
            dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose,Vec3 goal){
        thread();var original=captured;var lifecycle=modules.lifecycleSnapshot();
        if(original==null||rollingBack||completing||activating||lease.state()!=FarmMaintenanceLease.State.HELPER_ACTIVE||original.helper()!=helper||candidate!=token
            ||!(externalControlOwned instanceof dev.veltraph.client.VeltraphBootstrap.RootMaintenanceControlSource source)||!source.affiliates(this)
            ||!(context instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext)||!lifecycle.known())return null;
        var settings=knownGameplaySettingsSnapshot(original);if(settings==null)return null;
        var control=dev.veltraph.client.VeltraphBootstrap.ownedUseControlSnapshot(this);var location=nativeContext.gameplaySignalsSnapshot();
        if(control==null||location==null)return null;
        var view=movementAuthorityView(helper,candidate,purpose,goal);
        if(view==null||view.captureIdentity()!=original||captured!=original||!modules.lifecycleCurrent(lifecycle))return null;
        var actual=new MovementGameplayAuthority(original,purpose,goal,settings,control,location,lifecycle);
        return movementGameplayAuthorityStillCurrent(actual,helper,candidate,purpose,goal)?actual:null;
    }
    /** Callback-free final original source/config/native identity; never invokes policy/settings/loadout/external callbacks. */
    public boolean movementGameplayAuthorityStillCurrent(MovementGameplayAuthority actual,Participant helper,FarmMaintenanceLease.Token candidate,
            dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose,Vec3 goal){
        return movementGameplayAuthorityAtLifecycle(actual,helper,candidate,purpose,goal,actual==null?null:actual.lifecycle)
            &&dev.veltraph.client.VeltraphBootstrap.ownedUseControlCurrent(actual.controlSnapshot)
            &&movementGameplayAuthorityAtLifecycle(actual,helper,candidate,purpose,goal,actual==null?null:actual.lifecycle);
    }
    private boolean movementGameplayAuthorityAtLifecycle(MovementGameplayAuthority actual,Participant helper,FarmMaintenanceLease.Token candidate,
            dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose,Vec3 goal,ModuleManager.LifecycleSnapshot projected){
        if(actual==null||rollingBack||completing||activating||!client.isSameThread()||captured!=actual.capture||token!=actual.issuedToken||candidate!=actual.issuedToken
            ||actual.capture.helper()!=helper||actual.purpose!=purpose||actual.goal!=goal||actual.restoredWitness!=witness||lease.state()!=actual.phase
            ||actual.phase!=FarmMaintenanceLease.State.HELPER_ACTIVE||!lease.owns(candidate)||policies.get(helper)!=actual.capture.policy()
            ||externalControlOwned!=actual.externalSource
            ||!modules.lifecycleCurrent(projected)||client.player!=actual.capture.player()||client.level!=actual.capture.world()
            ||client.getConnection()!=actual.capture.connection()||!actual.capture.farm().maintenanceOwnedBy(candidate))return false;
        for(var module:modules.all())if(!(module instanceof dev.veltraph.client.core.AbstractModule stored)
                ||stored.isEnabled()&&module!=actual.capture.farm()&&module!=actual.capture.helper()
                    &&(actual.passiveRoles==null||!actual.passiveRoles.declaredActor(module)
                        ||!actual.passiveRoles.originalRoleHistoryCurrent(modules)))return false;
        return knownGameplaySettingsCurrent(actual.capture.policy().settings(),actual.settingsSnapshot)
            &&actual.location.current()
            &&knownGameplaySettingsCurrent(actual.capture.policy().settings(),actual.settingsSnapshot)
            &&modules.lifecycleCurrent(projected)&&captured==actual.capture&&token==actual.issuedToken&&lease.state()==actual.phase;
    }
    /** Cleanup-only exact original source. Receipt journals may change; every policy and control history remains held. */
    public boolean greenhouseGuardRestorationSourceCurrent(MovementGameplayAuthority actual,
            dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule helper,FarmMaintenanceLease.Token candidate,Vec3 goal){
        var purpose=dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK;
        if(actual==null||rollingBack||completing||activating||!client.isSameThread()||captured!=actual.capture||token!=actual.issuedToken||candidate!=actual.issuedToken
            ||actual.capture.helper()!=helper||actual.purpose!=purpose||actual.goal!=goal||actual.restoredWitness!=witness||lease.state()!=actual.phase
            ||actual.phase!=FarmMaintenanceLease.State.HELPER_ACTIVE||!lease.owns(candidate)||policies.get(helper)!=actual.capture.policy()
            ||externalControlOwned!=actual.externalSource
            ||!modules.lifecycleCurrent(actual.lifecycle)||client.player!=actual.capture.player()||client.level!=actual.capture.world()
            ||client.getConnection()!=actual.capture.connection()||!actual.capture.farm().maintenanceOwnedBy(candidate))return false;
        for(var module:modules.all())if(!(module instanceof dev.veltraph.client.core.AbstractModule stored)
                ||stored.isEnabled()&&module!=actual.capture.farm()&&module!=actual.capture.helper()
                    &&(actual.passiveRoles==null||!actual.passiveRoles.declaredActor(module)
                        ||!actual.passiveRoles.originalRoleHistoryCurrent(modules)))return false;
        return knownGameplaySettingsCurrent(actual.capture.policy().settings(),actual.settingsSnapshot)
            &&actual.location.current()&&dev.veltraph.client.VeltraphBootstrap.greenhouseGuardCleanupControlCurrent(actual.controlSnapshot,this)
            &&knownGameplaySettingsCurrent(actual.capture.policy().settings(),actual.settingsSnapshot)
            &&modules.lifecycleCurrent(actual.lifecycle)&&captured==actual.capture&&token==actual.issuedToken&&lease.state()==actual.phase;
    }
    /** Cancellation-only view; no settings/gear/return predicates are promoted into action authority. */
    public MovementAuthorityView movementCancellationView(Participant helper,FarmMaintenanceLease.Token candidate){
        thread();var capture=captured;var phase=lease.state();
        if(capture==null||candidate==null||candidate!=token||capture.helper()!=helper||!lease.owns(candidate)
                ||phase==FarmMaintenanceLease.State.PREPARED||!helper.isEnabled()||helper.isPassiveObserver()||!helper.ownsGameplayInput()
                ||!capture.farm().isEnabled()||!capture.farm().maintenanceOwnedBy(candidate)
                ||client.player!=admittedPlayer()||client.level!=admittedWorld()||client.getConnection()!=capture.connection())return null;
        for(var controller:modules.enabledControllers())if(controller!=capture.farm()&&controller!=helper)return null;
        if(captured!=capture||token!=candidate||lease.state()!=phase||!lease.owns(candidate))return null;
        return new MovementAuthorityView(capture.farm(),helper,candidate,capture.pose(),phase,capture,capture.policy(),
            modules,witness,config,services.rotation(),admittedPlayer(),admittedWorld(),capture.connection());
    }
    /** Private-constructor receipt exists only in the original synchronous captured helper disable closure. */
    public final class MovementRetirementView {
        private final Capture capture;private final FarmMaintenanceLease.Token retiredToken;private final Object restoredWitness;
        private final Object player,world;private final ConfigService configStore;
        private MovementRetirementView(Capture capture,FarmMaintenanceLease.Token token,Object restoredWitness,Object player,Object world,ConfigService configStore){
            this.capture=capture;this.retiredToken=token;this.restoredWitness=restoredWitness;this.player=player;this.world=world;this.configStore=configStore;
        }
        public FarmModule farm(){return capture.farm();}public Participant helper(){return capture.helper();}
        public FarmMaintenanceLease.Token token(){return retiredToken;}public FarmReturnPoint capturedPose(){return capture.pose();}
        public Object captureIdentity(){return capture;}public Object sourcePolicyIdentity(){return capture.policy();}
        public ModuleManager registryIdentity(){return modules;}public Object restorationWitness(){return restoredWitness;}
        public Object player(){return player;}public Object world(){return world;}public Object connection(){return capture.connection();}
        public ConfigService configStore(){return configStore;}
        public Object rotationServiceIdentity(){return services.rotation();}
    }
    public MovementRetirementView movementRetirementView(Participant helper,FarmMaintenanceLease.Token candidate){
        thread();var closed=movementRetirement;
        return closed!=null&&closed.helper()==helper&&candidate!=null&&closed.token()==candidate
            &&!helper.isEnabled()&&captured==null&&token==null&&!lease.active()&&movementRetirement==closed?closed:null;
    }
    /** Callback-free final source identity fence, after camera/provider/controller observations. */
    public boolean movementCancellationStillCurrent(MovementAuthorityView expected){
        thread();var capture=captured;
        return expected!=null&&capture!=null&&capture==expected.captureIdentity()&&token==expected.token()
            &&lease.owns(token)&&lease.state()==expected.phase()&&capture.farm()==expected.farm()&&capture.helper()==expected.helper()
            &&capture.policy()==expected.sourcePolicyIdentity()&&capture.pose()==expected.capturedPose()
            &&modules==expected.registryIdentity()&&witness==expected.restorationWitness()
            &&config==expected.configStore()&&services.rotation()==expected.rotationServiceIdentity()
            &&admittedPlayer()==expected.player()&&admittedWorld()==expected.world()&&capture.connection()==expected.connection();
    }
    /** Does not invoke helper getters or reconstruct a historical closure. */
    public boolean movementRetirementStillCurrent(MovementRetirementView expected){
        thread();return expected!=null&&movementRetirement==expected&&captured==null&&token==null&&!lease.active();
    }
    public FarmMaintenanceLease.State state(){return lease.state();}
    public FarmMaintenanceLease.Token tokenFor(Participant helper){thread();return captured!=null&&captured.helper()==helper&&lease.active()?token:null;}
    public boolean isCompleting(Participant helper){thread();return completing&&captured!=null&&captured.helper()==helper;}
    public boolean consumeExpectedTeleport(Vec3 position,long now){thread();return captured!=null&&identities(true)&&returning.consumeExpectedTeleport(position,now);}
    public void onServerMessage(String message){thread();if(captured!=null&&identities(true))returning.onServerMessage(message);}
    public String status(){if(captureRetirement!=null&&captureRetirement.failure!=null)return "Maintenance FAILED (original loaded closing custody retained) | "+returning.status();return lease.status()+" | "+returning.status();}
    public FarmReturnPoint capturedPose(FarmMaintenanceLease.Token candidate){thread();return lease.owns(candidate)&&captured!=null?captured.pose():null;}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean[] keys(){var o=client.options;return new boolean[]{o.keyUp.isDown(),o.keyDown.isDown(),o.keyLeft.isDown(),o.keyRight.isDown(),o.keyJump.isDown(),o.keyShift.isDown(),o.keySprint.isDown(),o.keyAttack.isDown(),o.keyUse.isDown()};}
    private void restoreKeys(boolean[] keys){var o=client.options;o.keyUp.setDown(keys[0]);o.keyDown.setDown(keys[1]);o.keyLeft.setDown(keys[2]);o.keyRight.setDown(keys[3]);o.keyJump.setDown(keys[4]);McHeldKeys.setHeld(o.keyShift,keys[5]);McHeldKeys.setHeld(o.keySprint,keys[6]);McHeldKeys.setHeld(o.keyAttack,keys[7]);McHeldKeys.setHeld(o.keyUse,keys[8]);}
}
