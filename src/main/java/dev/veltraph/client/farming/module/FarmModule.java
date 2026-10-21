package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.rewarp.RewarpService;
import dev.veltraph.client.rewarp.RewarpStartGate;
import dev.veltraph.client.service.FarmServices;

import java.util.List;
import java.util.Optional;

/**
 * Base class for farming modules. Holds the shared services, config, and layout registry, and
 * enforces the safety contract that disabling a module releases all of its inputs.
 */
public abstract class FarmModule extends AbstractModule {

    protected final FarmServices services;
    protected final ConfigService config;
    protected final FarmLayoutRegistry registry;
    private dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding farmTargetGuard;
    private record GuardStop(dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding original){}
    private GuardStop guardStop;

    protected FarmModule(String id,
                         String displayName,
                         FarmServices services,
                         ConfigService config,
                         FarmLayoutRegistry registry) {
        super(id, displayName, ModuleCategory.FARMING);
        this.services = services;
        this.config = config;
        this.registry = registry;
    }

    /** The layout profile currently selected for this card. */
    public abstract FarmLayoutProfile activeProfile();

    /** Short state string for HUD / debug panel. */
    public abstract String debugState();

    /**
     * All farming controllers self-declare {@code AreaType.GARDEN} (CONFIRMED for Pumpkin/FarmBuilder/
     * Analyzer/ReturnToLocation; INFERRED-strong for the rest of the family). See
     * {@code docs/LOCATION_GATES.md}.
     */
    @Override
    public final Optional<GameArea> requiredArea() {
        return Optional.of(GameArea.GARDEN);
    }

    @Override
    public final boolean requiresSkyBlock() {
        return true;
    }

    /** The hotbar tool name fragments this module's crop needs (e.g. {@code "hoe"}, {@code "dicer"}). */
    protected abstract List<String> toolKeywords();

    /** Validate this selected farm's scalar/configuration values before any start-time side effects. */
    protected Optional<String> configurationReadinessError(){return Optional.empty();}

    /** Hotbar slot chosen by the last successful {@link #preStartCheck()}; -1 if none. */
    private int chosenToolSlot = -1;
    private String chosenToolName;
    private dev.veltraph.client.service.mc.McFarmToolReadiness nativeFarmingTool;
    private int admittedToolConfiguration;
    private String admittedFarmProfile;

    /**
     * Set while the automatic rewarp runtime (Batch 2; {@code AutoRewarpRuntime}) has paused this module
     * to send the rewarp command and wait for the Garden. While suspended the engine is not ticked and
     * inputs are held released — but the module stays <i>enabled</i>, so the activation guard is not
     * re-run and the macro is not re-started. Cleared by {@link #resumeAfterRewarp()}.
     */
    private boolean suspendedForRewarp;
    private boolean suspendedForCycleReset;
    /** Distinct failed-start rollback gate. It does not impersonate maintenance suspension or issue native authority. */
    private SavedRollbackQuiescence savedRollbackQuiescence;
    public static final class SavedRollbackQuiescence {
        private final FarmModule farm;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceFailedPauseQuiescence failedPause;
        private final Object token;private final Thread thread=Thread.currentThread();private boolean released;
        private SavedRollbackQuiescence(FarmModule farm,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source,Object token){
            this.farm=farm;this.source=source;failedPause=null;this.token=token;
        }
        private SavedRollbackQuiescence(FarmModule farm,dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceFailedPauseQuiescence failedPause){
            this.farm=farm;source=null;this.failedPause=failedPause;token=failedPause.maintenanceToken();
        }
    }
    /** Negative-only failed pause gate. A partial pause never impersonates a completed rollback/resume. */
    public final SavedRollbackQuiescence beginFailedPauseQuiescence(
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceFailedPauseQuiescence source){
        if(savedRollbackQuiescence!=null||source==null||source.farm()!=this||!source.claimCurrent())return null;
        var gate=new SavedRollbackQuiescence(this,source);savedRollbackQuiescence=gate;
        if(!source.claim(gate))return null;return gate;
    }
    public final boolean failedPauseQuiescenceCurrent(SavedRollbackQuiescence gate,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceFailedPauseQuiescence source){
        return gate!=null&&savedRollbackQuiescence==gate&&!gate.released&&gate.farm==this&&gate.source==null&&gate.failedPause==source
            &&source!=null&&gate.token==source.maintenanceToken()&&Thread.currentThread()==gate.thread;
    }
    /** Only the selected original rollback source can claim the old farm's independent engine gate. */
    public final SavedRollbackQuiescence beginSavedRollbackQuiescence(
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source){
        if(savedRollbackQuiescence!=null||source==null||source.farm()!=this||!source.claimQuiescenceCurrent(this)
                ||!maintenanceOwnedBy(source.maintenanceToken()))return null;
        var gate=new SavedRollbackQuiescence(this,source,source.maintenanceToken());savedRollbackQuiescence=gate;
        if(!source.claimQuiescence(gate)){return null;}return gate;
    }
    /** Exact retained token only; this read cannot mint a resume/publication grant. */
    public final boolean savedRollbackQuiescenceCurrent(SavedRollbackQuiescence gate,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source){
        return gate!=null&&savedRollbackQuiescence==gate&&!gate.released&&gate.farm==this&&gate.source==source
                &&gate.token==source.maintenanceToken()&&Thread.currentThread()==gate.thread;
    }
    /** Called only after whole native completion, final old resources and lease retirement were accepted. */
    public final boolean completeSavedRollbackQuiescence(SavedRollbackQuiescence gate,
            dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.SavedMaintenanceRollbackCall source){
        if(!savedRollbackQuiescenceCurrent(gate,source)||!source.releaseQuiescenceCurrent(gate))return false;
        gate.released=true;savedRollbackQuiescence=null;return true;
    }
    /** A foreign disable/re-enable is a new run; the old gate never blocks that run or clears its inputs. */
    private boolean rollbackEngineQuiescent(){
        var gate=savedRollbackQuiescence;
        if(gate==null)return false;
        if(gate.source!=null?gate.source.originalFarmLifecycleCurrent():gate.failedPause!=null&&gate.failedPause.originalFarmLifecycleCurrent())return true;
        if(savedRollbackQuiescence==gate){gate.released=true;savedRollbackQuiescence=null;}return false;
    }
    private Object maintenanceToken;
    private long maintenanceSuspension;
    private FarmMaintenanceEngineWitness maintenanceWitness;
    private IssuedReturnCheckpoint issuedReturnCheckpoint;
    private dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall activeIssuedPause;
    private boolean maintenanceRebindAttempted;
    private dev.veltraph.client.farming.movement.FarmRouteDriver.MaintenanceBinding maintenanceReboundRoute;
    private dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding maintenanceReboundGuard;

    /**
     * Shared return-to-location service (Target 2). Null when not wired (e.g. pure unit tests that
     * don't exercise the rewarp gate), in which case the {@code REWARP_REQUIRED} gate is skipped.
     */
    private RewarpService rewarpService;

    /** Inject the shared {@link RewarpService} (set once at registration; enables the rewarp gate). */
    public final void setRewarpService(RewarpService rewarpService) {
        this.rewarpService = rewarpService;
    }

    protected final RewarpService rewarpService() {
        return rewarpService;
    }

    /**
     * Select the crop tool on start: from the configured slot or by name-scanning the hotbar
     * ({@link CropToolSelector}). Also enforces the {@code REWARP_REQUIRED} gate first. Blocks the
     * start with a chat-ready error if a rewarp point is required-but-missing, or the tool is missing.
     */
    @Override
    public final Optional<String> preStartCheck() {
        if(isEnabled()&&usesNativeFarmingInventory())return Optional.of("Enabled farming readiness belongs to its issued cycle/recovery, not a fresh start");
        var configurationError=configurationReadinessError();
        if(configurationError.isPresent())return configurationError;
        boolean rewarpRequired = config.get(FarmingConfig.REWARP_REQUIRED);
        boolean hasPoints = rewarpService != null && rewarpService.hasPoints();
        Optional<String> rewarpBlock = RewarpStartGate.check(rewarpRequired, hasPoints, displayName());
        if (rewarpBlock.isPresent()) {
            dev.veltraph.client.log.VeltraphLog.basic(displayName(),
                    "blocked start: REWARP_REQUIRED but no saved rewarp point");
            return rewarpBlock;
        }
        ToolSelection sel = CropToolSelector.select(services.inventory(),
                config.get(FarmingConfig.FARMING_TOOL_SLOT), toolKeywords(), displayName());
        if (!sel.ok()) {
            return Optional.of(sel.error());
        }
        chosenToolSlot = sel.slot();
        chosenToolName = services.inventory().hotbarItemNames().get(chosenToolSlot);
        nativeFarmingTool=null;
        if(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService nativeInventory) {
            nativeFarmingTool=nativeInventory.captureFarmingTool(this,chosenToolSlot,chosenToolName);
            if(nativeFarmingTool==null||!nativeFarmingTool.selectIfCurrent(this,nativeInventory,chosenToolSlot,true)) {
                chosenToolSlot=-1;chosenToolName=null;nativeFarmingTool=null;
                return Optional.of("Veltraph: farming start item or native slot custody changed; start was not admitted.");
            }
        } else services.inventory().selectSlot(chosenToolSlot);
        admittedToolConfiguration=config.get(FarmingConfig.FARMING_TOOL_SLOT);
        admittedFarmProfile=dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this);
        dev.veltraph.client.log.VeltraphLog.details(displayName(),
                "selected tool slot " + (chosenToolSlot + 1) + " for " + activeProfile().displayName());
        return Optional.empty();
    }

    /** Prove the original intended item before reselecting; no invalid slot becomes a new tool. */
    protected final boolean toolReady() {
        if(maintenanceToken!=null||isSuspended())return false;
        if(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService nativeInventory)
            return nativeFarmingTool!=null&&nativeFarmingTool.selectIfCurrent(this,nativeInventory,chosenToolSlot,false);
        // Existing unbound pure planner fixtures have no native publication capability.
        if(chosenToolSlot<0)return true;
        var names=services.inventory().hotbarItemNames();
        if(chosenToolSlot>=names.size()||chosenToolName==null||chosenToolName.isEmpty()
                ||!chosenToolName.equals(names.get(chosenToolSlot)))return false;
        if(services.inventory().selectedSlot()!=chosenToolSlot)services.inventory().selectSlot(chosenToolSlot);
        names=services.inventory().hotbarItemNames();
        return chosenToolSlot<names.size()&&chosenToolName.equals(names.get(chosenToolSlot))
                &&services.inventory().selectedSlot()==chosenToolSlot;
    }
    /** Actual service type only; this read is not native permission. */
    public final boolean usesNativeFarmingInventory() {
        return services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService;
    }
    /** Fresh item fact only. Native effect producers retain their own original control/PWC custody. */
    public final boolean nativeSelectedFarmingToolCurrent(net.minecraft.client.Minecraft actualClient) {
        return maintenanceToken==null&&nativeFarmingTool!=null
                &&services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService nativeInventory
                &&nativeFarmingTool.selectedCurrent(this,nativeInventory,actualClient,chosenToolSlot);
    }

    protected final int chosenToolSlot() {
        return chosenToolSlot;
    }

    /**
     * Revalidate the actual start tool and selected slot for recovery and normal-cycle settling.
     * A removed/replaced tool keeps farming suspended instead of treating its old slot as proof.
     */
    public final boolean toolReadyForRewarp() {
        if(maintenanceToken!=null)return false; // The helper owns loadout/held-slot choices.
        // An old slot number alone cannot prove that the tool survived the warp. Require the
        // selected start tool before reasserting the slot; a later preStartCheck may select anew.
        if(chosenToolSlot<0)return false;
        if(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService nativeInventory)
            return nativeFarmingTool!=null&&nativeFarmingTool.selectForRecovery(this,nativeInventory,chosenToolSlot);
        var names=services.inventory().hotbarItemNames();
        if(chosenToolSlot>=names.size()||chosenToolName==null||chosenToolName.isEmpty()
                ||!chosenToolName.equals(names.get(chosenToolSlot)))return false;
        services.inventory().selectSlot(chosenToolSlot);
        return services.inventory().selectedSlot()==chosenToolSlot;
    }

    /** Read-only retained-tool/config/profile readiness; only the actual coordinator admits END -> START. */
    public final boolean retainedCycleToolReady() {
        if(!isEnabled()||!suspendedForCycleReset||suspendedForRewarp||maintenanceToken!=null
                ||chosenToolSlot<0||admittedFarmProfile==null)return false;
        if(config.get(FarmingConfig.FARMING_TOOL_SLOT)!=admittedToolConfiguration
                ||!admittedFarmProfile.equals(dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this)))return false;
        var error=configurationReadinessError();
        if(RewarpStartGate.check(config.get(FarmingConfig.REWARP_REQUIRED),rewarpService!=null&&rewarpService.hasPoints(),displayName()).isPresent())return false;
        return error.isEmpty()&&isEnabled()&&suspendedForCycleReset&&!suspendedForRewarp&&maintenanceToken==null
                &&config.get(FarmingConfig.FARMING_TOOL_SLOT)==admittedToolConfiguration
                &&admittedFarmProfile.equals(dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this))&&selectedFarmingToolReady();
    }

    /** Cache the exact actual source guard handout; a later same-owner replacement is never adopted. */
    protected final void installFarmTargetGuard(java.util.function.Predicate<dev.veltraph.client.world.BlockPos> allowed) {
        services.breaking().installTargetGuard(this,allowed);
        if(dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.activeFor(this)) {
            var actual=dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.installedTargetGuard(this);
            if(actual==null)throw new IllegalStateException("Issued resume did not publish its exact source crop guard");
            farmTargetGuard=actual;
        } else farmTargetGuard=services.breaking().targetGuardBinding(this).orElse(null);
    }
    protected final void clearFarmTargetGuard() {
        if(services.breaking() instanceof dev.veltraph.client.service.mc.McBlockBreakingService nativeBreaking) {
            var stop=guardStop;var exact=stop==null?farmTargetGuard:stop.original();
            if(exact!=null&&nativeBreaking.clearTargetGuardOwned(this,exact)&&farmTargetGuard==exact)farmTargetGuard=null;
            return;
        }
        services.breaking().clearTargetGuard(this);
    }
    /** Release every movement and break input. Always called on disable (safety). */
    protected final void releaseInputs() {
        services.input().releaseAllMovement();
        services.breaking().setBreaking(false);
    }

    /** Negative-only native refusal route; the adapters can release only this farm's original histories. */
    public final void releaseRefusedFarmingInputs() {
        dev.veltraph.client.service.mc.McFarmOwnedCleanup.runToolRefusal(this,this::releaseInputs);
    }

    /**
     * Per-tick update while enabled. Made {@code final} so the suspend-for-rewarp gate is enforced for
     * every farming module in one place: while {@link #suspendedForRewarp} is set the engine is not
     * ticked and inputs are re-released each tick (idempotent), so a running engine cannot re-assert
     * movement/breaking during a rewarp. Subclasses implement their engine tick in {@link #onTickFarming()}.
     */
    @Override
    public final void onTick() {
        if(rollbackEngineQuiescent()||maintenanceToken!=null||issuedReturnCheckpoint!=null)return; // Never erase the original helper/issued return driver inputs.
        if (isSuspended()) {
            releaseInputs();
            return;
        }
        if(!toolReady()) {
            // A refusal may release only this farm's retained native histories, never raw foreign keys.
            dev.veltraph.client.service.mc.McFarmOwnedCleanup.runToolRefusal(this,this::releaseInputs);
            return;
        }
        dev.veltraph.client.service.mc.McFarmOwnedCleanup.runFarmingTick(this,this::onTickFarming);
    }

    /** Engine update for this farming module (runs only while not suspended for rewarp). */
    protected abstract void onTickFarming();

    /**
     * Pause this module for an automatic rewarp: release all inputs/breaking and stop ticking the
     * engine, <b>without</b> disabling the module (so the activation guard is not re-run and the macro
     * is not re-started). Idempotent. Used by {@code AutoRewarpRuntime}.
     */
    public final void suspendForRewarp() {
        if(activeIssuedPause!=null&&!genuineIssuedPauseSuspensionCaller())
            throw new IllegalStateException("Original issued pause cannot be borrowed by a callback");
        if(rollbackEngineQuiescent())throw new IllegalStateException("Original rollback owns farming quiescence");
        if(issuedReturnCheckpoint!=null)throw new IllegalStateException("Issued return owns farming suspension");
        if(suspendedForCycleReset||maintenanceToken!=null)throw new IllegalStateException("Another owner holds farming suspension");
        onBeforeFarmSuspension();
        if(!isEnabled()||suspendedForCycleReset||maintenanceToken!=null)return;
        suspendedForRewarp = true;
        releaseInputs();
    }

    private boolean genuineIssuedPauseSuspensionCaller(){
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var caller=stream.filter(frame->!(frame.getDeclaringClass()==FarmModule.class
                    &&(frame.getMethodName().equals("genuineIssuedPauseSuspensionCaller")
                        ||frame.getMethodName().startsWith("lambda$genuineIssuedPauseSuspensionCaller")
                        ||frame.getMethodName().equals("suspendForRewarp")))).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==FarmModule.class&&caller.getMethodName().equals("pauseForIssuedReturn");
        });
    }

    /** Resume after an automatic rewarp: re-arm so the engine ticks again from the next tick. */
    public final void resumeAfterRewarp() {
        if (rollbackEngineQuiescent()||issuedReturnCheckpoint!=null||!suspendedForRewarp) return;
        onResumeAfterRewarp();
        suspendedForRewarp = false;
    }

    /** Drop geometry tied to the pre-warp position. Existing engines may retain their own state. */
    protected void onResumeAfterRewarp() {}

    public final boolean isSuspendedForRewarp() {
        return suspendedForRewarp;
    }

    /** Explicit normal END -> START role; never arms interrupted-pose recovery. */
    public boolean cycleEndReady() { return true; }

    public final boolean suspendForCycleReset() {
        if(rollbackEngineQuiescent())return false;
        if(!isEnabled()||suspendedForRewarp||suspendedForCycleReset||maintenanceToken!=null||!cycleEndReady())return false;
        onBeforeFarmSuspension();
        if(!isEnabled()||suspendedForRewarp||suspendedForCycleReset||maintenanceToken!=null||!cycleEndReady())return false;
        suspendedForCycleReset=true;releaseInputs();return true;
    }
    public final boolean isSuspendedForCycleReset(){return suspendedForCycleReset;}
    public final boolean isSuspended(){return suspendedForRewarp||suspendedForCycleReset||maintenanceToken!=null;}
    public final boolean isSuspendedForMaintenance(){return maintenanceToken!=null;}
    public final boolean maintenanceOwnedBy(Object token){return token!=null&&maintenanceToken==token&&isEnabled();}
    public final int validatedFarmingToolSlot(){return chosenToolSlot;}
    /** Read-only admission check; unlike recovery readiness it never selects a slot. */
    public final boolean selectedFarmingToolReady(){
        var names=services.inventory().hotbarItemNames();
        return maintenanceToken==null&&(!(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService)
                ||nativeSelectedFarmingToolCurrent(net.minecraft.client.Minecraft.getInstance()))
                &&chosenToolSlot>=0&&chosenToolSlot<names.size()
                &&services.inventory().selectedSlot()==chosenToolSlot&&chosenToolName!=null&&!chosenToolName.isEmpty()
                &&chosenToolName.equals(names.get(chosenToolSlot));
    }
    /** Commit once after deferred helper activation; engine state and enable lifecycle stay intact. */
    public final boolean suspendForMaintenance(Object token){
        if(rollbackEngineQuiescent())return false;
        if(token==null||!isEnabled()||isSuspended())return false;
        onBeforeFarmSuspension();
        if(!isEnabled()||isSuspended())return false;
        maintenanceToken=token;maintenanceSuspension++;maintenanceWitness=null;maintenanceReboundRoute=null;maintenanceRebindAttempted=false;maintenanceReboundGuard=null;services.rotation().cancelRotation();releaseInputs();return true;
    }
    /** The coordinator has independently proved loadout, return, view, posture and settle. No restart hook. */
    public final boolean resumeAfterMaintenance(Object token){
        var gate=savedRollbackQuiescence;
        if(gate!=null&&(gate.source==null||!savedRollbackQuiescenceCurrent(gate,gate.source)||gate.token!=token
                ||!gate.source.originalFarmResumeCurrent(gate)))return false;
        if(!maintenanceOwnedBy(token)||suspendedForRewarp||suspendedForCycleReset||maintenanceResumeReadinessError().isPresent())return false;
        if(maintenanceWitness!=null&&(!maintenanceRebindAttempted||maintenanceReboundGuard==null
                ||!maintenanceEngineUnchanged(token,maintenanceWitness)||!maintenanceRouteCurrent(maintenanceWitness)
                ||!services.breaking().targetGuardBoundToCurrentContext(this,maintenanceReboundGuard)
                ||services.breaking().isBreaking()
                ||services.breaking().pendingBlockChanges(this).filter(java.util.Set::isEmpty).isEmpty()))return false;
        if(gate!=null&&(!savedRollbackQuiescenceCurrent(gate,gate.source)||!gate.source.originalFarmResumeFinalCurrent(gate)))return false;
        maintenanceToken=null;maintenanceWitness=null;maintenanceReboundGuard=null;maintenanceReboundRoute=null;return true;
    }
    /** Called while the farm still owns inputs, before a helper/recovery suspension transfers custody. */
    protected void onBeforeFarmSuspension() {}
    /** Exact family checkpoint support; unknown family engines cannot obtain a maintenance lease. */
    protected Optional<FarmMaintenanceState> maintenanceProgress(){return Optional.empty();}
    protected Optional<String> maintenanceProgressReadinessError(){return Optional.empty();}
    /** Family-private refusal only, including legacy resume without an issued witness. No progress is restored here. */
    protected Optional<String> maintenanceResumeReadinessError(){return Optional.empty();}
    /** Only an active concrete farm drop supplies this port. Legacy route ports stay unavailable. */
    protected dev.veltraph.client.farming.movement.FarmRouteDriver maintenanceRouteDriver(){return null;}
    private String maintenanceRouteProfile(){return dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this);}
    private boolean maintenanceRouteUnchanged(FarmMaintenanceEngineWitness witness){
        var driver=maintenanceRouteDriver();
        return driver==witness.routeDriver&&(driver==null||driver.maintenanceBindingUnchanged(
                maintenanceReboundRoute==null?witness.routeBinding:maintenanceReboundRoute,maintenanceRouteProfile()));
    }
    private boolean maintenanceRouteCurrent(FarmMaintenanceEngineWitness witness){
        return witness.routeDriver==null||maintenanceReboundRoute!=null&&witness.routeDriver.maintenanceBoundToCurrentContext(maintenanceReboundRoute,maintenanceRouteProfile());
    }
    private Optional<FarmMaintenanceState> fullMaintenanceProgress(){
        return maintenanceProgress().map(progress->FarmMaintenanceState.of(this,
                "family",progress,"macroState",state(),"chosenToolSlot",chosenToolSlot,"chosenToolName",chosenToolName,"nativeFarmingTool",nativeFarmingTool,"admittedToolConfiguration",admittedToolConfiguration,"admittedFarmProfile",admittedFarmProfile,
                "suspendedForRewarp",suspendedForRewarp,"suspendedForCycleReset",suspendedForCycleReset));
    }
    /** Read-only lease admission. Known pending predictions may drain after the committed pause. */
    public final Optional<String> maintenanceEngineReadinessError(){
        if(!isEnabled()||suspendedForRewarp||suspendedForCycleReset)return Optional.of("Farm recovery owns the engine");
        if(maintenanceProgress().isEmpty())return Optional.of("Farm family has no exact maintenance checkpoint");
        var familyError=maintenanceProgressReadinessError();if(familyError.isPresent())return familyError;
        var binding=services.breaking().targetGuardBinding(this);
        if(binding.isEmpty()||!services.breaking().targetGuardBoundToCurrentContext(this,binding.get()))
            return Optional.of("Farm native crop guard is unavailable or stale");
        var pending=services.breaking().pendingBlockChanges(this);
        return pending.isEmpty()?Optional.of("Farm native damage acknowledgments are unavailable"):Optional.empty();
    }
    /** The committed pause drains ordinary native acknowledgments before any helper travel. */
    public final Optional<String> maintenanceWitnessReadinessError(Object token){
        if(!maintenanceOwnedBy(token))return Optional.of("Maintenance token does not own this suspension");
        var readiness=maintenanceEngineReadinessError();if(readiness.isPresent())return readiness;
        if(services.breaking().isBreaking())return Optional.of("Farm attack has not been released");
        var pending=services.breaking().pendingBlockChanges(this);
        return pending.isEmpty()||!pending.get().isEmpty()?Optional.of("Farm native damage acknowledgments are unavailable or pending"):Optional.empty();
    }
    /** Capture only the exact committed suspension, after inputs release and before departure. */
    public final Optional<FarmMaintenanceEngineWitness> captureMaintenanceEngineWitness(Object token){
        if(!maintenanceOwnedBy(token)||maintenanceWitness!=null||maintenanceWitnessReadinessError(token).isPresent())return Optional.empty();
        var progress=fullMaintenanceProgress();var binding=services.breaking().targetGuardBinding(this);
        if(progress.isEmpty()||binding.isEmpty())return Optional.empty();
        var driver=maintenanceRouteDriver();
        var route=driver==null?Optional.<dev.veltraph.client.farming.movement.FarmRouteDriver.MaintenanceBinding>empty():driver.captureMaintenanceBinding(maintenanceRouteProfile());
        if(driver!=null&&route.isEmpty())return Optional.empty();
        maintenanceWitness=new FarmMaintenanceEngineWitness(this,token,maintenanceSuspension,progress.get(),binding.get(),driver,route.orElse(null));
        return Optional.of(maintenanceWitness);
    }
    /** Pure logical preservation test; native references deliberately have a separate receipt. */
    public final boolean maintenanceEngineUnchanged(Object token,FarmMaintenanceEngineWitness witness){
        return witness!=null&&witness==maintenanceWitness&&witness.owner==this&&witness.token==token
                &&witness.suspension==maintenanceSuspension&&maintenanceOwnedBy(token)
                &&fullMaintenanceProgress().filter(witness.progress()::equals).isPresent()&&maintenanceRouteUnchanged(witness);
    }
    /** Read-only original suspension handout. Public callers cannot invent the private captured witness. */
    public final dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding originalMaintenanceGuard(
            Object token,dev.veltraph.client.service.mc.McBlockBreakingService actual){
        var witness=maintenanceWitness;
        if(actual==null||services.breaking()!=actual||config!=dev.veltraph.client.VeltraphBootstrap.config()
            ||witness==null||maintenanceRebindAttempted||maintenanceReboundGuard!=null||maintenanceReboundRoute!=null
            ||farmTargetGuard!=witness.binding||!maintenanceEngineUnchanged(token,witness))return null;
        return farmTargetGuard==witness.binding&&maintenanceWitness==witness&&maintenanceOwnedBy(token)?witness.binding:null;
    }
    /** Root alone admits the arrival. Refresh only the cached native guard, never planners or aim. */
    public final boolean rebindForMaintenance(Object token,FarmMaintenanceEngineWitness witness){
        if(maintenanceRebindAttempted||!maintenanceEngineUnchanged(token,witness)
                ||services.breaking().isBreaking()||maintenanceProgressReadinessError().isPresent())return false;
        if(witness.routeDriver!=null&&!witness.routeDriver.canRebindMaintenance(witness.routeBinding,maintenanceRouteProfile()))return false;
        maintenanceRebindAttempted=true;
        var rebound=services.breaking().rebindTargetGuard(this,witness.binding);
        if(rebound.isEmpty())return false;
        maintenanceReboundGuard=rebound.get();farmTargetGuard=rebound.get();
        if(witness.routeDriver!=null){
            var route=witness.routeDriver.rebindMaintenance(witness.routeBinding,maintenanceRouteProfile());
            if(route.isEmpty())return false;
            maintenanceReboundRoute=route.get();
        }
        var pending=services.breaking().pendingBlockChanges(this);
        return maintenanceEngineUnchanged(token,witness)
                &&services.breaking().targetGuardBoundToCurrentContext(this,maintenanceReboundGuard)
                &&pending.isPresent()&&pending.get().isEmpty()&&!services.breaking().isBreaking();
    }
    /** Scheduled-rest compatibility retains its legacy recovery behavior until its port adopts witnesses. */
    public final boolean rebindForScheduledRest(Object token){
        if(issuedReturnCheckpoint!=null||!maintenanceOwnedBy(token))return false;
        onResumeAfterRewarp();
        return maintenanceOwnedBy(token);
    }
    @Override public final boolean ownsGameplayInput(){return maintenanceToken==null
            &&(issuedReturnCheckpoint==null||issuedReturnCheckpoint.resuming);}
    /** Immutable source checkpoint. Only this exact actual native coordinator pause can create it. */
    public static final class IssuedReturnCheckpoint {
        private final FarmModule farm;
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall original;
        private final long serial;
        private final String profile,toolName;
        private final int toolSlot;
        private final dev.veltraph.client.service.mc.McFarmToolReadiness nativeTool;
        private final int admittedToolConfiguration;
        private final String admittedFarmProfile;
        private final FarmMaintenanceState progress;
        private final FarmIssuedResumeState semantics;
        private final java.util.Map<String,Object> engineIdentities;
        private final dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding guard;
        private final dev.veltraph.client.farming.movement.FarmRouteDriver route;
        private final FarmMaintenanceState routeState;
        private boolean resuming,consumed;
        private dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall activeResume;
        private IssuedReturnCheckpoint(FarmModule farm,
                dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall original,
                FarmMaintenanceState progress,FarmIssuedResumeState semantics,java.util.Map<String,Object> engineIdentities,
                dev.veltraph.client.farming.movement.FarmRouteDriver route,FarmMaintenanceState routeState) {
            this.farm=farm;this.original=original;serial=original.serial();profile=original.profile();
            toolSlot=farm.chosenToolSlot;toolName=farm.chosenToolName;nativeTool=farm.nativeFarmingTool;
            admittedToolConfiguration=farm.admittedToolConfiguration;admittedFarmProfile=farm.admittedFarmProfile;
            this.progress=progress;this.semantics=semantics;
            this.engineIdentities=java.util.Map.copyOf(engineIdentities);guard=farm.farmTargetGuard;
            this.route=route;this.routeState=routeState;
        }
        /** Current S74 start-tool fields are conserved through the real issued hook; this is a negative leaf only. */
        private boolean originalStartToolFieldsCurrent(){return farm.nativeFarmingTool==nativeTool&&farm.chosenToolSlot==toolSlot
            &&java.util.Objects.equals(farm.chosenToolName,toolName)&&farm.admittedToolConfiguration==admittedToolConfiguration
            &&java.util.Objects.equals(farm.admittedFarmProfile,admittedFarmProfile);}
        public dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding originalTargetGuardBinding(){return guard;}
        public dev.veltraph.client.farming.movement.FarmRouteDriver originalRouteDriver(){return route;}
        public FarmMaintenanceState originalRouteState(){return routeState;}
        public long serial(){return serial;}
        public String profile(){return profile;}
    }
    /** Unsupported families cannot obtain an issued resume checkpoint. Native authority stays in the typed issuer. */
    protected Optional<FarmIssuedResumeState> captureIssuedReturnSemantics(){return Optional.empty();}
    /** Refusal-only provider evidence. It cannot replace the original closed native arrival/source leaf. */
    protected Optional<String> issuedReturnArrivalReadinessError(){return Optional.of("Issued arrival continuation unsupported");}
    /** Family-specific issued hook preserves the saved engine/direction/progress; ordinary legacy resume is separate. */
    protected void onResumeIssuedReturn(){throw new IllegalStateException("Issued resume semantics unsupported");}
    /** Unlike maintenance admission, resume retirement captures even an inactive native route. */
    protected dev.veltraph.client.farming.movement.FarmRouteDriver issuedReturnRouteDriver(){return maintenanceRouteDriver();}
    private Optional<FarmMaintenanceState> issuedReturnProgress(){
        return maintenanceProgress().map(p->FarmMaintenanceState.of(this,"family",p,"macroState",state(),
                "chosenToolSlot",chosenToolSlot,"chosenToolName",chosenToolName,
                "nativeFarmingTool",nativeFarmingTool,"admittedToolConfiguration",admittedToolConfiguration,"admittedFarmProfile",admittedFarmProfile,
                "suspendedForRewarp",suspendedForRewarp,"suspendedForCycleReset",suspendedForCycleReset,
                "maintenanceToken",FarmMaintenanceState.identity(maintenanceToken)));
    }
    private static java.util.Map<String,Object> issuedEngineIdentities(FarmMaintenanceState progress){
        var found=new java.util.LinkedHashMap<String,Object>();collectIssuedEngineIdentities("",progress,found);return found;
    }
    private static void collectIssuedEngineIdentities(String path,Object value,java.util.Map<String,Object> found){
        // The issued hook retains aim and row engines. Only original native route/coverage custody
        // is retired by the independently fenced actual resume producer.
        if(path.endsWith(".routeDriver")||path.endsWith(".reachCompletion"))return;
        if(value instanceof FarmMaintenanceState state){
            found.put(path,FarmMaintenanceState.identity(state.engineIdentity()));state.fields().forEach((key,nested)->collectIssuedEngineIdentities(path+"."+key,nested,found));
        }else if(value instanceof java.util.Map<?,?> map){
            map.forEach((key,nested)->collectIssuedEngineIdentities(path+"."+key,nested,found));
        }
    }
    /** Read-only refusal before the coordinator creates a workflow or captures timer custody. */
    public final Optional<String> issuedReturnReadinessError(){
        if(!isEnabled()||isSuspended())return Optional.of("Original farm is not an active productive owner");
        try{return captureIssuedReturnSemantics().isPresent()?Optional.empty():Optional.of("Finish the original farm transfer before an issued return");}
        catch(RuntimeException unavailable){return Optional.of("Original issued-return semantics unavailable");}
    }

    /** The coordinator itself pauses; a public raw suspended flag cannot supply checkpoint custody. */
    public final Optional<IssuedReturnCheckpoint> pauseForIssuedReturn(
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall original){
        if(rollbackEngineQuiescent()||original==null||activeIssuedPause!=null||issuedReturnCheckpoint!=null||!isEnabled()||isSuspended()
                ||original.farm()!=this||!original.current(this)||original.serial()<=0
                ||!java.util.Objects.equals(original.profile(),dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this)))return Optional.empty();
        activeIssuedPause=original;
        try {
        var semantics=captureIssuedReturnSemantics();
        if(semantics.isEmpty()||!original.current(this))return Optional.empty();
        suspendForRewarp();
        if(!isEnabled()||!suspendedForRewarp||maintenanceToken!=null||suspendedForCycleReset||!original.current(this))return Optional.empty();
        var progress=issuedReturnProgress();var route=issuedReturnRouteDriver();
        var routeState=route==null?null:route.maintenanceState().orElse(null);
        if(progress.isEmpty()||farmTargetGuard==null||route!=null&&routeState==null
                ||captureIssuedReturnSemantics().filter(semantics.get()::equals).isEmpty()||!original.current(this))return Optional.empty();
        var checkpoint=new IssuedReturnCheckpoint(this,original,progress.get(),semantics.get(),issuedEngineIdentities(progress.get()),route,routeState);
        issuedReturnCheckpoint=checkpoint;
        return issuedReturnPausedCurrent(checkpoint)?Optional.of(checkpoint):Optional.empty();
        } finally {if(activeIssuedPause==original)activeIssuedPause=null;}
    }
    /** Only the actual original pause body can identify its intentional suspension transition. */
    public final boolean issuedReturnPausingUnder(dev.veltraph.client.VeltraphBootstrap.RootFarmReturnAdmission exact){
        var original=activeIssuedPause;
        return exact!=null&&original!=null&&original.farm()==this&&original.admissionIdentity()==exact
                &&isEnabled()&&maintenanceToken==null&&!suspendedForCycleReset;
    }
    /** Final packet/resume veto reads stored fields only; semantic family state is checked earlier. */
    public final boolean issuedReturnPausedBound(IssuedReturnCheckpoint exact,
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.PauseCall original){
        return exact!=null&&exact==issuedReturnCheckpoint&&exact.farm==this&&exact.original==original
                &&original!=null&&exact.serial==original.serial()&&!exact.consumed&&!exact.resuming&&exact.originalStartToolFieldsCurrent()
                &&isEnabled()&&suspendedForRewarp&&!suspendedForCycleReset&&maintenanceToken==null;
    }
    /** Pure source continuity is separate from the issuer's new native transport/landing proof. */
    public final boolean issuedReturnPausedCurrent(IssuedReturnCheckpoint exact){
        if(exact==null||exact!=issuedReturnCheckpoint||exact.farm!=this||exact.consumed||exact.resuming||!exact.originalStartToolFieldsCurrent()
                ||!isEnabled()||!suspendedForRewarp||suspendedForCycleReset||maintenanceToken!=null
                ||chosenToolSlot!=exact.toolSlot||!java.util.Objects.equals(chosenToolName,exact.toolName)
                ||!java.util.Objects.equals(exact.profile,dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this))
                ||!exact.original.sourceHeader().current())return false;
        return captureIssuedReturnSemantics().filter(exact.semantics::equals).isPresent()
                &&issuedReturnProgress().filter(exact.progress::equals).isPresent()
                &&issuedReturnRouteDriver()==exact.route&&exact.original.sourceHeader().current();
    }
    /** Private stored invocation identity only. This observation grants no action or replay. */
    public final boolean issuedReturnResumingBy(IssuedReturnCheckpoint exact,
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall original){
        return exact!=null&&exact==issuedReturnCheckpoint&&exact.farm==this&&exact.resuming&&!exact.consumed
                &&original!=null&&exact.activeResume==original&&exact.originalStartToolFieldsCurrent();
    }
    /** Commit one original resume hook under the actual closed native arrival invocation. */
    public final boolean resumeIssuedReturn(IssuedReturnCheckpoint exact,
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.ResumeCall original){
        if(rollbackEngineQuiescent()||!issuedReturnPausedCurrent(exact)||original==null||original.farm()!=this||original.serial()!=exact.serial
                ||!java.util.Objects.equals(original.profile(),exact.profile)||!original.current(this,exact)
                ||!original.nativeCurrent())return false;
        // Family geometry/providers run before rechecking the original frozen semantic/native source.
        if(issuedReturnArrivalReadinessError().isPresent()||!issuedReturnPausedCurrent(exact)
                ||!original.current(this,exact)||!original.nativeCurrent())return false;
        exact.activeResume=original;exact.resuming=true;
        boolean refreshed=false;
        try {
            var registration=dev.veltraph.client.VeltraphBootstrap.farmReturnRegistrationFor(
                    dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator());
            if(registration==null||!registration.beginResumeOwner(original)||!original.nativeCurrent())return false;
            refreshed=dev.veltraph.client.service.mc.McIssuedFarmResumeGuard.run(this,exact,original,this::onResumeIssuedReturn);
            var after=issuedReturnProgress();
            if(!refreshed||issuedReturnCheckpoint!=exact||!exact.originalStartToolFieldsCurrent()||!isEnabled()||!suspendedForRewarp
                ||maintenanceToken!=null||suspendedForCycleReset||after.isEmpty()
                ||captureIssuedReturnSemantics().filter(exact.semantics::equals).isEmpty()
                ||!exact.engineIdentities.equals(issuedEngineIdentities(after.get()))
                ||!java.util.Objects.equals(exact.profile,dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(this))
                ||!original.nativeCurrent())return false;
            exact.consumed=true;suspendedForRewarp=false;issuedReturnCheckpoint=null;return true;
        } finally {
            if(issuedReturnCheckpoint==exact){exact.resuming=false;exact.activeResume=null;}
        }
    }
    /** Caller must independently observe/settle/aim at the explicit START before invoking this. */
    public final boolean restartAfterCycleReset() {
        if(rollbackEngineQuiescent())return false;
        if(issuedReturnCheckpoint!=null||!isEnabled()||!suspendedForCycleReset||suspendedForRewarp)return false;
        onCycleRestart();
        if(!isEnabled())return false;
        suspendedForCycleReset=false;return true;
    }
    protected void onCycleRestart(){onEnable();}

    @Override
    public final void onDisable() {
        var previous=guardStop;guardStop=new GuardStop(farmTargetGuard);
        try{dev.veltraph.client.service.mc.McFarmOwnedCleanup.run(this, this::onDisableFarming);}
        finally{guardStop=previous;}
    }

    /** Concrete family retirement runs inside the original farm cleanup scope. */
    protected void onDisableFarming() {
        services.rotation().cancelRotation();
        suspendedForRewarp = false;
        suspendedForCycleReset = false;
        maintenanceToken=null;maintenanceWitness=null;maintenanceReboundGuard=null;maintenanceReboundRoute=null;
        issuedReturnCheckpoint=null;
        releaseInputs();
    }
}
