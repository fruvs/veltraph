package dev.veltraph.client.greenhouse;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Explicit current plan -> original farm lease -> existing native cultivation -> same-engine return. */
public final class GreenhouseMaintenanceModule extends GreenhouseModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="greenhouse_maintenance";
    private static final List<ConfigKey<?>> POLICY_KEYS=List.of(GreenhouseConfig.GREENHOUSE_ENABLED,GreenhouseConfig.GREENHOUSE_PROFILE,
        GreenhouseConfig.GREENHOUSE_PLOT,GreenhouseConfig.GREENHOUSE_PLAN,GreenhouseConfig.GREENHOUSE_OBSERVED_BINDINGS,
        GreenhouseConfig.GREENHOUSE_MAX_ACTIONS,GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS,
        GreenhouseConfig.GREENHOUSE_ACTION_TIMEOUT_MS,GreenhouseConfig.GREENHOUSE_RETURN_TIMEOUT_MS,
        RotationConfig.ROTATION_SPEED_MULTIPLIER,TravelConfig.TRAVEL_APPROACH_MODE);
    public enum Phase { IDLE, PREPARED, CARE, RETURNING, COMPLETE, CANCELLED }
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final ModuleManager modules;private final FarmMaintenanceCoordinator maintenance;
    private FarmModule farm;private Object player,world,connection;private ModuleManager.LifecycleSnapshot offeredLifecycle;
    private MapConfigService.StoredKeysSnapshot frozen;private dev.veltraph.client.navigation.DynamicRouteConfigStamp routePolicy;private FarmMaintenanceLease.Token preparedToken,token;
    private boolean arming,pending,starting,careStarted,cancelling;private Phase phase=Phase.IDLE;
    private NativeReceipt receipt;private BooleanSupplier restoreProof;private String cachedParentOutcome="NOT_STARTED";
    private dev.veltraph.client.world.Vec3 aimTarget;private dev.veltraph.client.service.mc.McRotationService.QueuedRequestHandle ownedCamera;
    /** Queue actual token-owned camera requests, retaining only the genuine producer handout. */
    @Override protected void aimCultivationPoint(dev.veltraph.client.world.Vec3 target){
        if(!owned()||!closed())return;
        var eye=client.player.getEyePosition();double dx=target.x()-eye.x,dy=target.y()-eye.y,dz=target.z()-eye.z;
        queueOwnedView((float)Math.toDegrees(Math.atan2(dz,dx))-90,(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))),target);
    }
    @Override protected void aimCultivationView(float yaw,float pitch){queueOwnedView(yaw,pitch,null);}
    private float aimYaw,aimPitch;
    private void queueOwnedView(float yaw,float pitch,dev.veltraph.client.world.Vec3 target){
        if(!owned()||!closed()||!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        if(Objects.equals(target,aimTarget)&&Float.floatToIntBits(yaw)==Float.floatToIntBits(aimYaw)&&Float.floatToIntBits(pitch)==Float.floatToIntBits(aimPitch)&&ownedCamera!=null){
            if(ownedCamera.current()&&ownedCamera.nativeCurrent())return;}
        if(ownedCamera!=null&&!rotation.cancelOwnedHandle(ownedCamera,()->same()))throw new IllegalStateException("Original Greenhouse queued camera changed");
        if(!owned())return;
        var at=currentCultivationSnapshot();if(at==null)throw new IllegalStateException("Current cultivation position unavailable");
        var point=new dev.veltraph.client.world.Vec3(at.position().x(),at.position().y(),at.position().z());
        var binding=dev.veltraph.client.navigation.mc.McFarmMaintenanceAuthorityBridge.capture(client,this,config,routePolicy,
            dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK,point).orElse(null);
        if(binding==null||!owned())throw new IllegalStateException("Genuine original maintenance camera source unavailable");
        var actual=rotation.rotateToMaintenanceOwned(yaw,pitch,null,binding,()->same()&&frozenCurrent()&&phase==Phase.CARE);
        // Producer returns immediately after replacement with no post-publication callback.
        // Retain its private cleanup handle before any further original/native query.
        if(actual!=null)ownedCamera=actual;
        if(actual==null||!same()||!frozenCurrent())throw new IllegalStateException("Genuine token-owned camera request unavailable");
        aimTarget=target;aimYaw=yaw;aimPitch=pitch;
    }
    @Override protected void beforeDiagnosticIssue(){
        var original=ownedCamera;if(original==null)return;
        if(!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation)||!rotation.cancelOwnedHandle(original,()->same()))throw new IllegalStateException("Original queued camera retirement refused before diagnostic effect");
        if(ownedCamera!=original)throw new IllegalStateException("Queued camera replaced during diagnostic retirement");ownedCamera=null;aimTarget=null;
    }
    private void releaseOwnedCamera(){if(ownedCamera!=null&&services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation)rotation.cancelOwnedHandle(ownedCamera,()->same());ownedCamera=null;aimTarget=null;}
    private dev.veltraph.client.service.mc.McInventoryService.GreenhouseSlotCustody ownSelection;
    private dev.veltraph.client.service.mc.McInventoryService.GreenhouseSelectionStart selectionStart;
    @Override protected void captureCultivationSelection(int originalSlot){
        if(!(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService actual)||!owned())throw new IllegalStateException("Original Greenhouse slot custody unavailable");
        ownSelection=actual.captureGreenhouseSlotCustody(this,originalSlot,selectionStart);
        if(ownSelection==null)throw new IllegalStateException("Genuine original Greenhouse selection history unavailable");
    }
    @Override protected boolean selectCultivationSlot(int slot){
        var exact=ownSelection;if(exact==null||!exact.select(slot,this::owned))throw new IllegalStateException("Owned Greenhouse selection changed");return true;
    }
    @Override protected boolean restoreOriginalCultivationSlot(int originalSlot){
        var exact=ownSelection;if(exact==null)return false;
        return exact.restoreOriginal();
    }
    private dev.veltraph.client.service.mc.McBlockBreakingService.GreenhouseGuardCustody guardCustody;
    @Override protected void installCultivationGuard(java.util.function.Predicate<dev.veltraph.client.world.BlockPos> target){
        if(!(services.breaking() instanceof dev.veltraph.client.service.mc.McBlockBreakingService actual))throw new IllegalStateException("Native original farm guard custody unavailable");
        var original=actual.installGreenhouseMaintenanceGuard(this,target);
        if(original!=null)guardCustody=original;
        if(original==null)throw new IllegalStateException("Original witnessed farm crop guard could not be borrowed");
    }
    private dev.veltraph.client.service.BlockBreakingService.TargetGuardBinding ownGuard;
    private dev.veltraph.client.service.mc.McBlockBreakingService.OwnedBreakingReceipt ownAttack;
    @Override protected void installedCultivationGuard(){ownGuard=services.breaking() instanceof dev.veltraph.client.service.mc.McBlockBreakingService actual?actual.targetGuardBinding(this).orElse(null):null;}
    @Override protected void setCultivationBreaking(boolean held){
        if(!(services.breaking() instanceof dev.veltraph.client.service.mc.McBlockBreakingService actual))throw new IllegalStateException("Native owned Greenhouse breaking unavailable");
        if(held){if(!owned()||ownGuard==null)throw new IllegalStateException("Current original Greenhouse harvest guard unavailable");ownAttack=actual.setBreakingOwnedImmediate(this,ownGuard,this::owned);if(ownAttack==null)throw new IllegalStateException("Genuine Greenhouse harvest publication refused");}
        else {if(ownAttack!=null)actual.releaseBreakingOwned(ownAttack,()->same());
            if(ownGuard!=null)actual.releaseBreakingAttemptedOwned(this,ownGuard);ownAttack=null;}
    }
    /** Ordinary useItemOn is a one-shot native call; this module never owns a held use key. */
    @Override protected void releaseCultivationUse(){}
    @Override protected void clearCultivationGuard(){
        var original=guardCustody;if(original==null){if(ownGuard!=null)throw new IllegalStateException("Original borrowed crop guard custody missing");return;}
        if(!original.restoreOriginal())throw new IllegalStateException("Exact original farm crop guard restoration refused");
        if(guardCustody!=original)throw new IllegalStateException("Borrowed crop guard replaced during restoration");
        ownGuard=null;guardCustody=null;
    }
    @Override protected void cleanupInput(){stopCultivationTravel();setCultivationBreaking(false);releaseOwnedCamera();}


    private CompletedOwnDisable completedOwnDisable;
    private boolean cleanupQuiescent(){return completedCultivationCleanupQuiescent()&&guardCustody==null&&ownAttack==null&&ownGuard==null&&ownedCamera==null;}
    /** Passive original arrival binding; only the real Root invocation can complete it. Never permits native cleanup. */
    public final class CompletedOwnDisable {
        private final Object arrival;private final ModuleManager.LifecycleSnapshot before;
        private final Object p,w,c;private final FarmModule originalFarm;private final FarmMaintenanceLease.Token originalToken;
        private final NativeReceipt originalReceipt;private final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
        private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot camera;
        private final long epoch,serial,applications;
        private ModuleManager.PendingOwnDisable pendingCall;private ModuleManager.OwnDisableReceipt returned;
        private boolean bodyReturned,revoked;
        private CompletedOwnDisable(Object arrival){
            this.arrival=arrival;before=modules.lifecycleSnapshot();p=player;w=world;c=connection;
            originalFarm=farm;originalToken=token;originalReceipt=receipt;
            hardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);
            camera=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(client);
            epoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
            serial=dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client);
            applications=dev.veltraph.client.service.mc.McOwnedRotation.frameApplications(client);
        }
        private boolean contextCurrent(){return !revoked&&completedOwnDisable==this&&client.isSameThread()
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==modules&&dev.veltraph.client.VeltraphBootstrap.config()==config
            &&modules.get(ID).orElse(null)==GreenhouseMaintenanceModule.this&&player==p&&world==w&&connection==c&&same()
            &&farm==originalFarm&&farm!=null&&farm.isEnabled()&&token==originalToken&&receipt==originalReceipt&&receipt!=null
            &&farm.maintenanceOwnedBy(originalToken)&&frozenCurrent()&&phase==Phase.COMPLETE&&cleanupQuiescent();}
        private boolean publicationCurrent(){return hardware!=null&&hardware.current()&&camera!=null&&camera.current()
            &&epoch>=0&&epoch<Long.MAX_VALUE&&serial>=0&&serial<Long.MAX_VALUE&&applications>=0&&applications<Long.MAX_VALUE
            &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==epoch
            &&dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)==serial
            &&dev.veltraph.client.service.mc.McOwnedRotation.frameApplications(client)==applications
            &&!dev.veltraph.client.service.mc.McOwnedRotation.hasQueuedApplication(client);}
        private void entering(){
            if(!contextCurrent()||!publicationCurrent()||!maintenance.isCompleting(GreenhouseMaintenanceModule.this)) {revoked=true;return;}
            pendingCall=modules.pendingActualOwnDisable(GreenhouseMaintenanceModule.this).orElse(null);
            if(pendingCall==null||pendingCall.registryIdentity()!=modules||!pendingCall.current(GreenhouseMaintenanceModule.this)
                ||!modules.pendingOwnDisableFromMatches(before,GreenhouseMaintenanceModule.this))revoked=true;
        }
        private void bodyReturned(){
            bodyReturned=!revoked&&pendingCall!=null&&pendingCall.current(GreenhouseMaintenanceModule.this)
                &&modules.pendingOwnDisableFromMatches(before,GreenhouseMaintenanceModule.this)&&contextCurrent()&&publicationCurrent();
            if(!bodyReturned)revoked=true;
        }
        private void returned(ModuleManager.OwnDisableReceipt actual){
            if(!bodyReturned||!contextCurrent()||!publicationCurrent()||actual==null
                ||actual.parent()!=GreenhouseMaintenanceModule.this||!actual.returnedCurrent()) {revoked=true;return;}
            returned=actual;
        }
        /** Exact private arrival identity and genuine normally finished Root disable; unchanged native histories only. */
        public boolean current(Object exactArrival,ModuleManager exactRoot,ModuleManager.LifecycleSnapshot exactBefore){
            var actual=returned;
            return exactArrival==arrival&&exactRoot==modules&&exactBefore!=null
                &&bodyReturned&&actual!=null&&contextCurrent()&&publicationCurrent()
                &&modules.ownDisableFromMatches(actual,before,actual.stoppedSnapshot(),GreenhouseMaintenanceModule.this)
                &&modules.ownDisableFromMatches(actual,exactBefore,actual.stoppedSnapshot(),GreenhouseMaintenanceModule.this);
        }
    }
    public CompletedOwnDisable captureCompletedOwnDisable(Object originalArrivalIdentity){
        if(originalArrivalIdentity==null||!client.isSameThread()||!owned()||phase!=Phase.COMPLETE
            ||maintenance.state()!=FarmMaintenanceLease.State.READY||!careStarted||pending||starting||cancelling
            ||!closed()||!cleanupQuiescent()||receipt==null)return null;
        var original=new CompletedOwnDisable(originalArrivalIdentity);completedOwnDisable=original;
        if(!modules.lifecycleCurrent(original.before)||!original.contextCurrent()||!original.publicationCurrent()){
            original.revoked=true;completedOwnDisable=null;return null;}
        return original;
    }
    @Override protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt actual){
        var original=completedOwnDisable;if(original!=null)original.returned(actual);
    }

    /** Can only be minted after this original runtime reaches COMPLETE with newer native receipts and physical return. */
    public final class NativeReceipt {
        private final Snapshot after;private long lastClock;private boolean clockInvalid;private final List<net.minecraft.world.item.ItemStack> slots;private final FarmMaintenanceLease.Token lease;private final Object p,w,c;private final FarmModule original;
        private NativeReceipt(Snapshot after){this.after=after;lastClock=services.clock().nowMillis();var rows=new ArrayList<net.minecraft.world.item.ItemStack>();for(int i=0;i<client.player.getInventory().getContainerSize();i++)rows.add(client.player.getInventory().getItem(i).copy());slots=List.copyOf(rows);lease=token;p=player;w=world;c=connection;original=farm;}
        public Context context(){return after.context();}public long revision(){return after.revision();}
    }
    public GreenhouseMaintenanceModule(Minecraft client,ConfigService config,FarmServices services,ModuleManager modules,FarmMaintenanceCoordinator maintenance){
        super(ID,"Selected Greenhouse farm maintenance",client,config,services);this.client=client;this.config=config;this.services=services;this.modules=modules;this.maintenance=maintenance;
    }
    private MapConfigService.StoredKeysSnapshot captureSettings(){return config instanceof MapConfigService m?m.storedKeysSnapshot(POLICY_KEYS):config instanceof JsonConfigService j?j.storedKeysSnapshot(POLICY_KEYS):null;}
    private boolean closed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean same(){return client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive();}
    private boolean frozenCurrent(){return frozen!=null&&routePolicy!=null&&frozen.current();}
    /** Explicit dispatch only; no default interval, archive or published minimum can initiate a transaction. */
    public boolean dispatch(BooleanSupplier guardedStart){
        if(isEnabled()||maintenance.active()||guardedStart==null)return false;
        var owners=modules.enabledControllers();if(owners.size()!=1||!(owners.getFirst() instanceof FarmModule selected)||selected.isSuspended())return false;
        farm=selected;player=client.player;world=client.level;connection=client.getConnection();offeredLifecycle=modules.lifecycleSnapshot();frozen=captureSettings();routePolicy=dev.veltraph.client.navigation.DynamicRouteConfigStamp.capture(config).orElse(null);
        arming=true;try{return preReservationReadiness().isEmpty()&&maintenance.startEligibility(this).isEmpty()&&guardedStart.getAsBoolean();}
        finally{arming=false;if(!isEnabled()&&maintenance.tokenFor(this)==null)clearOffer();}
    }
    private void clearOffer(){farm=null;player=world=connection=null;offeredLifecycle=null;frozen=null;routePolicy=null;preparedToken=null;}
    @Override public Optional<String> preReservationReadiness(){
        // The standard guard invokes this again during the coordinator's deferred activation.
        // Initial arming has ended and the original farm is intentionally suspended at that point.
        // Only the exact still-current issued PREPARED token admits that second check.
        if(preparedToken!=null){
            if(!same()||!closed()||!frozenCurrent()||!maintenance.preparedActivationValid(this,preparedToken))return Optional.of("Original prepared Greenhouse lease changed");
            if(diagnosticOnlySelected()||preparedPlan().isEmpty())return Optional.of("Explicit selected cultivation plan required");
            return super.preStartCheck();
        }
        if(!arming||farm==null||!farm.isEnabled()||farm.isSuspended()||!modules.all().contains(farm)||!same()||!closed()||!frozenCurrent()||!modules.lifecycleCurrent(offeredLifecycle))return Optional.of("Explicit fresh current Greenhouse plan and one original active Garden farm required");
        if(diagnosticOnlySelected()||preparedPlan().isEmpty())return Optional.of("Select an explicit bounded cultivation plan; an inspection alone is not parent care");
        return super.preStartCheck();
    }
    @Override public Optional<String> preStartCheck(){
        if(diagnosticOnlySelected()||preparedPlan().isEmpty())return Optional.of("Explicit selected cultivation plan required");
        if(starting)return super.preStartCheck();
        if(preparedToken!=null&&same()&&closed()&&frozenCurrent()&&maintenance.preparedActivationValid(this,preparedToken))return super.preStartCheck();
        return preReservationReadiness();
    }
    @Override public boolean prepareActivation(FarmMaintenanceLease.Token candidate){
        if(!same()||!closed()||!frozenCurrent()||super.preStartCheck().isPresent()||!maintenance.preparedActivationValid(this,candidate))return false;
        preparedToken=candidate;return true;
    }
    @Override public boolean activatePrepared(FarmMaintenanceLease.Token candidate,BooleanSupplier activation){
        if(candidate==null||candidate!=preparedToken||!same()||!frozenCurrent()||!maintenance.preparedActivationValid(this,candidate))return false;
        return activation.getAsBoolean();
    }
    @Override public void cancelPrepared(FarmMaintenanceLease.Token candidate){if(candidate==preparedToken){preparedToken=null;clearOffer();}}
    /** No native request, input, selection, camera or cultivation is issued during lease preparation. */
    @Override public void onEnable(){
        // Mint before original source callbacks; public raw onEnable cannot obtain the actual Root enable receipt.
        var start=services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService actual?actual.beginGreenhouseSelection(this):null;
        if(start==null)throw new IllegalStateException("Actual Greenhouse enable selection start unavailable");selectionStart=start;
        completedOwnDisable=null;cachedParentOutcome="PREPARED";pending=true;careStarted=false;starting=false;token=null;receipt=null;restoreProof=null;aimTarget=null;ownedCamera=null;phase=Phase.PREPARED;}
    public void committed(FarmMaintenanceLease.Token candidate){
        if(!isEnabled()||!pending||candidate==null||candidate!=preparedToken||maintenance.tokenFor(this)!=candidate||!same()||!frozenCurrent())throw new IllegalStateException("Greenhouse farm lease commit changed");
        token=candidate;preparedToken=null;
    }
    private boolean owned(){return isEnabled()&&token!=null&&same()&&frozenCurrent()&&farm!=null&&farm.isEnabled()&&farm.maintenanceOwnedBy(token)&&maintenance.valid(token);}
    @Override public void onTick(){
        if(!owned()){cancelOwned("Greenhouse native context, selected policy or original lease changed");return;}
        if(pending){pending=false;starting=true;careStarted=true;phase=Phase.CARE;try{super.onEnable();}finally{starting=false;}return;}
        if(phase==Phase.RETURNING){
            if(!restored()){cancelOwned("Greenhouse final receipt changed before parent return");return;}
            var result=maintenance.tickReturn(token);
            if(result==FarmMaintenanceCoordinator.ReturnStatus.FAILED){cancelOwned("Original Garden return failed");return;}
            if(result==FarmMaintenanceCoordinator.ReturnStatus.READY){phase=Phase.COMPLETE;if(!maintenance.resume(token,restoreProof)){phase=Phase.CANCELLED;cancelOwned("Original farm checkpoint resume refused");}}return;
        }
        super.onTick();
    }
    @Override protected void cultivationTerminal(){
        if(runtime().state()!=GreenhouseRuntime.State.COMPLETE||!owned()){cancelOwned("Selected cultivation did not finish with known receipts");return;}
        var after=currentCultivationSnapshot();
        if(after==null||after.knowledge(services.clock().nowMillis(),config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS))!=Knowledge.KNOWN||!after.screenFree()||!runtime().unresolvedActions().isEmpty()){cancelOwned("Fresh final Greenhouse receipt unavailable");return;}
        receipt=new NativeReceipt(after);restoreProof=this::restored;
        if(!restored()||!maintenance.beginRestore(token)||!maintenance.beginReturn(token,restoreProof)){cancelOwned("Original farm restoration refused");return;}
        phase=Phase.RETURNING;
    }
    private Map<String,Integer> inventory(){if(client.player==null)return Map.of();var result=new HashMap<String,Integer>();for(int i=0;i<client.player.getInventory().getContainerSize();i++){var stack=client.player.getInventory().getItem(i);if(!stack.isEmpty())result.merge(GreenhouseModule.stackIdentity(stack),stack.getCount(),Math::addExact);}return Map.copyOf(result);}
    private boolean restored(){
        var actual=receipt;if(actual==null||actual.lease!=token||actual.original!=farm||actual.p!=player||actual.w!=world||actual.c!=connection||!same()||!frozenCurrent()||!closed()||runtime().state()!=GreenhouseRuntime.State.COMPLETE||!runtime().unresolvedActions().isEmpty())return false;
        long now=services.clock().nowMillis();if(now<actual.lastClock)actual.clockInvalid=true;actual.lastClock=Math.max(actual.lastClock,now);if(actual.clockInvalid)return false;if(actual.after.knowledge(now,config.get(GreenhouseConfig.GREENHOUSE_OBSERVATION_MAX_AGE_MS))!=Knowledge.KNOWN)return false;
        var current=currentCultivationSnapshot();if(current==null||!current.context().equals(actual.after.context())||current.revision()!=actual.after.revision()||current.observedAt()!=actual.after.observedAt()||!current.plants().equals(actual.after.plants())||!inventory().equals(actual.after.inventory()))return false;
        if(client.player.getInventory().getContainerSize()!=actual.slots.size()||client.player.getInventory().getSelectedSlot()!=actual.after.selectedSlot())return false;
        for(int i=0;i<actual.slots.size();i++)if(!net.minecraft.world.item.ItemStack.matches(actual.slots.get(i),client.player.getInventory().getItem(i)))return false;
        for(var entry:actual.after.plants().entrySet()){var cell=entry.getKey();if(!McGreenhouseLoadedCell.readable(client.level,cell)||!client.level.getBlockState(new net.minecraft.core.BlockPos(cell.x(),cell.y(),cell.z())).toString().equals(entry.getValue().fingerprint()))return false;}
        return same()&&frozenCurrent();
    }
    private void cancelOwned(String reason){if(cancelling)return;cachedParentOutcome="CANCEL: "+reason;cancelling=true;phase=Phase.CANCELLED;try{maintenance.cancel(token==null?maintenance.tokenFor(this):token,reason);if(isEnabled())setEnabled(false);}finally{cancelling=false;}}
    @Override public void onDisable(){
        var original=completedOwnDisable;if(original!=null)original.entering();
        pending=false;
        try{if(careStarted)super.onDisable();}
        finally{
            // A refused guard restore must not skip retirement of our exact own selected-slot custody.
            // Manual replacement/ABA is independently refused by that private native history.
            try{var selection=ownSelection;if(selection!=null)selection.restoreOriginal();}
            catch(RuntimeException cleanupRefused){/* Preserve the original cleanup failure; no new ownership is inferred. */}
            finally{careStarted=false;}
        }
        if(original!=null)original.bodyReturned();
        if(maintenance.isCompleting(this))return;cancelOwned("Greenhouse parent interrupted; no automatic resume");
    }
    @Override public void onServerMessage(String text){maintenance.onServerMessage(text);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return phase==Phase.RETURNING?isEnabled()&&maintenance.consumeExpectedTeleport(position,now):super.consumeExpectedTeleport(position,now);}
    public String cachedParentOutcome(){return cachedParentOutcome;}
    public Phase maintenancePhase(){return phase;}public Optional<NativeReceipt> nativeReceipt(){return Optional.ofNullable(receipt);}
    /** Actual policy provenance and selected-key ABA history, without invoking arbitrary settings callbacks. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private FarmMaintenanceCoordinator.Policy policy;
        private GameplaySettings(){}
        @Override public String get(){var values=new ArrayList<String>();for(var key:POLICY_KEYS)values.add(key.name()+"="+config.get(key)+"@"+config.revision(key));return values.toString();}
        private final class Snapshot {final GameplaySettings source=GameplaySettings.this;final Object issued=policy;final MapConfigService.StoredKeysSnapshot keys;Snapshot(MapConfigService.StoredKeysSnapshot keys){this.keys=keys;}}
        public Object snapshot(Object expected,ConfigService store,Object exactPolicy){if(expected!=GreenhouseMaintenanceModule.this||store!=config||exactPolicy!=policy||policy==null||!client.isSameThread())return null;var keys=captureSettings();if(keys==null)return null;var result=new Snapshot(keys);return snapshotCurrent(result)?result:null;}
        public boolean snapshotCurrent(Object exact){return exact instanceof GreenhouseMaintenanceModule.GameplaySettings.Snapshot s&&s.source==this&&s.issued==policy&&client.isSameThread()&&s.keys.current();}
    }
    private record GearBinding(Object player,Object world,Object connection,Object gear){}
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){
        var settings=new GameplaySettings();var policy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),settings,new FarmMaintenanceCoordinator.LoadoutProof(){
            public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new GearBinding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
            public boolean restored(Object binding,Object witness){return binding instanceof GearBinding b&&witness==restoreProof&&witness instanceof BooleanSupplier proof&&client.player==b.player()&&client.level==b.world()&&client.getConnection()==b.connection()&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
        },600_000);settings.policy=policy;return policy;
    }
    @Override public String diagnostics(){return phase+" | "+super.diagnostics()+" | "+maintenance.status();}
}
