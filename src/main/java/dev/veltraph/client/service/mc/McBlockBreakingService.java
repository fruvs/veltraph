package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.BlockBreakingService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.world.BlockPos;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

/**
 * Minecraft-backed {@link BlockBreakingService}. Holds/releases the vanilla attack key so the client's
 * normal block-breaking path runs while the row engine walks the crop row.
 */
public final class McBlockBreakingService implements BlockBreakingService {

    private record TargetGuard(McBlockBreakingService service, Object owner, Minecraft client,
            ClientLevel level, LocalPlayer player, MultiPlayerGameMode gameMode, ClientPacketListener connection,
            Predicate<BlockPos> allowedTarget, java.util.UUID playerUuid) implements TargetGuardBinding {}

    private static final AtomicReference<TargetGuard> TARGET_GUARD = new AtomicReference<>();
    private static final GuardCustodyStore<TargetGuard> GUARD_HISTORY = new GuardCustodyStore<>(TARGET_GUARD);

    private final Minecraft client;
    private boolean breaking;
    private long ownedBreakingGeneration;
    private OwnedBreakingReceipt ownedBreakingReceipt;
    private ImmediateHeldAttempt immediateHeldAttempt;

    public McBlockBreakingService(Minecraft client) {
        this.client = client;
    }

    @Override
    public void installTargetGuard(Object owner, Predicate<BlockPos> allowedTarget) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(allowedTarget, "allowedTarget");
        if(McIssuedFarmResumeGuard.installCrop(this,owner,allowedTarget))return;
        GUARD_HISTORY.set(new TargetGuard(this, owner, client, client.level, client.player,
                client.gameMode, client.getConnection(), allowedTarget,client.player==null?null:client.player.getUUID()));
    }

    @Override
    public void clearTargetGuard(Object owner) {
        if(McIssuedFarmResumeGuard.active())return;
        TargetGuard guard = TARGET_GUARD.get();
        if (guard != null && guard.service() == this && guard.owner() == owner) {
            if(McFarmOwnedCleanup.stopping())return; // Only the original source-owned binding may retire during farm stop.
            if(GUARD_HISTORY.compareAndSet(guard, null)){
                var exact=farmBreaking;
                if(exact!=null&&exact.guard==guard&&owner==exact.scope.farm)exact.guardCleared=true;
            }
        }
    }

    final class ResumeBreakingRetirement {
        final Object originalFarm=farmBreaking,originalOwned=ownedBreakingReceipt,originalImmediate=immediateHeldAttempt;
        final long originalGeneration=ownedBreakingGeneration;
        final boolean originalBreaking=breaking;
        final Object farm;
        boolean retired;
        ResumeBreakingRetirement(Object farm){this.farm=farm;}
        boolean current(){
            if(!McInputService.canonicalFarmBreakingCurrent(McBlockBreakingService.this,client))return false;
            if(retired)return !breaking&&farmBreaking==null&&ownedBreakingReceipt==null&&immediateHeldAttempt==null
                    &&ownedBreakingGeneration==originalGeneration+1;
            return breaking==originalBreaking&&farmBreaking==originalFarm&&ownedBreakingReceipt==originalOwned
                &&immediateHeldAttempt==originalImmediate&&ownedBreakingGeneration==originalGeneration
                &&(originalFarm==null||originalFarm instanceof FarmBreaking actual&&actual.scope.farm==farm&&actual.generation==originalGeneration)
                &&(originalOwned==null||originalOwned instanceof OwnedBreakingReceipt actual&&actual.guard.owner()==farm)
                &&(originalImmediate==null||originalImmediate instanceof ImmediateHeldAttempt actual&&actual.parent==farm)
                &&(!originalBreaking||originalFarm!=null);
        }
        boolean retire(java.util.function.BooleanSupplier source){
            if(retired)return current()&&source.getAsBoolean()&&current();
            if(originalGeneration==Long.MAX_VALUE||!current()||!source.getAsBoolean()||!current())return false;
            breaking=false;revokeOwnedBreaking();retired=true;
            return current()&&source.getAsBoolean()&&current();
        }
    }
    ResumeBreakingRetirement captureIssuedResumeBreaking(Object farm){var actual=new ResumeBreakingRetirement(farm);return actual.current()?actual:null;}
    boolean issuedResumeGuardCurrent(Object owner,TargetGuardBinding exact){
        return exact instanceof TargetGuard guard&&guard.service()==this&&guard.owner()==owner&&TARGET_GUARD.get()==guard;
    }
    /** Private exact predecessor plus one actual CAS; no raw same-owner adoption. */
    TargetGuardBinding installIssuedResumeGuard(Object owner,TargetGuardBinding exact,Predicate<BlockPos> allowed,java.util.function.BooleanSupplier source){
        if(!(exact instanceof TargetGuard old)||old.service()!=this||old.owner()!=owner||TARGET_GUARD.get()!=old
                ||!source.getAsBoolean()||TARGET_GUARD.get()!=old||!McIssuedFarmResumeGuard.current())return null;
        var replacement=new TargetGuard(this,owner,client,client.level,client.player,client.gameMode,client.getConnection(),allowed,
                client.player==null?null:client.player.getUUID());
        return McIssuedFarmResumeGuard.current()&&TARGET_GUARD.compareAndSet(old,replacement)?replacement:null;
    }
    @Override public java.util.Optional<TargetGuardBinding> targetGuardBinding(Object owner){
        var guard=TARGET_GUARD.get();
        return guard!=null&&guard.service()==this&&guard.owner()==owner?java.util.Optional.of(guard):java.util.Optional.empty();
    }
    @Override public boolean targetGuardBoundToCurrentContext(Object owner,TargetGuardBinding binding){
        return binding instanceof TargetGuard guard&&TARGET_GUARD.get()==guard&&guard.service()==this
                &&guard.owner()==owner&&guard.client()==client&&nativeMaintenanceContext()
                &&guard.player()==client.player&&guard.level()==client.level
                &&guard.gameMode()==client.gameMode&&guard.connection()==client.getConnection()
                &&guard.playerUuid()!=null&&guard.playerUuid().equals(client.player.getUUID());
    }
    private boolean nativeMaintenanceContext(){
        return client==Minecraft.getInstance()&&client.isSameThread()&&client.player!=null&&client.level!=null
                &&client.gameMode!=null&&client.getConnection()!=null&&client.player.isAlive()
                &&client.player.level()==client.level&&client.gui.screen()==null
                &&client.player.containerMenu==client.player.inventoryMenu
                &&client.player.containerMenu.getCarried().isEmpty();
    }
    private boolean nativePredictionsEmpty(){
        if(!(client.level instanceof dev.veltraph.client.mixin.ClientLevelPredictionAccessor levelAccess))return false;
        var handler=levelAccess.veltraph$blockStatePredictionHandler();
        return handler instanceof dev.veltraph.client.mixin.BlockStatePredictionHandlerAccessor accessor
                &&accessor.veltraph$serverVerifiedStates()!=null&&accessor.veltraph$serverVerifiedStates().isEmpty();
    }
    @Override public java.util.Optional<TargetGuardBinding> rebindTargetGuard(Object owner,TargetGuardBinding expected){
        try {
        if(!(expected instanceof TargetGuard old)||owner==null||old.service()!=this||old.owner()!=owner
                ||old.client()!=client||TARGET_GUARD.get()!=old||old.player()==null||!nativeMaintenanceContext()
                ||breaking||client.options==null||client.options.keyAttack.isDown()||old.playerUuid()==null||!old.playerUuid().equals(client.player.getUUID())
                ||!nativePredictionsEmpty())return java.util.Optional.empty();
        var replacement=new TargetGuard(this,owner,client,client.level,client.player,client.gameMode,
                client.getConnection(),old.allowedTarget(),old.playerUuid());
        if(!GUARD_HISTORY.compareAndSet(old,replacement))return java.util.Optional.empty();
        return targetGuardBoundToCurrentContext(owner,replacement)?java.util.Optional.of(replacement):java.util.Optional.empty();
        } catch(Throwable unavailable){return java.util.Optional.empty();}
    }

    private GreenhouseGuardCustody greenhouseGuardCustody;
    private dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule attemptedGuardParent;
    private Predicate<BlockPos> attemptedGreenhouseGuard;
    /** Exact original farm guard -> exact private helper guard -> same original farm guard. */
    public final class GreenhouseGuardCustody {
        private final dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent;
        private final dev.veltraph.client.farming.module.FarmModule farm;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator coordinator;
        private final dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token;
        private final dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.MovementGameplayAuthority source;
        private final dev.veltraph.client.world.Vec3 goal;
        private final TargetGuard original,helper;
        private final Object options;private final KeyMapping attack,use;
        private final dev.veltraph.client.config.MapConfigService.StoredKeysSnapshot farmSettings;
        private final GuardCustodySwap<TargetGuard> swap;
        private GreenhouseGuardCustody(dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent,
                dev.veltraph.client.farming.module.FarmModule farm,
                dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator coordinator,
                dev.veltraph.client.rewarp.FarmMaintenanceLease.Token token,
                dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.MovementGameplayAuthority source,
                dev.veltraph.client.world.Vec3 goal,TargetGuard original,TargetGuard helper,
                dev.veltraph.client.config.MapConfigService.StoredKeysSnapshot farmSettings,long originalGeneration){
            this.parent=parent;this.farm=farm;this.coordinator=coordinator;this.token=token;this.source=source;
            this.goal=goal;this.original=original;this.helper=helper;this.farmSettings=farmSettings;
            options=client.options;attack=client.options.keyAttack;use=client.options.keyUse;
            swap=new GuardCustodySwap<>(GUARD_HISTORY,original,helper,originalGeneration);
        }
        private boolean sourceCurrent(){return sourceCurrent(false);}
        private boolean restorationCurrent(){return sourceCurrent(true);}
        private boolean sourceCurrent(boolean restoring){
            return greenhouseGuardCustody==this&&client.isSameThread()&&client==Minecraft.getInstance()
                &&McInputService.canonicalFarmBreakingCurrent(McBlockBreakingService.this,client)
                &&coordinator==dev.veltraph.client.VeltraphBootstrap.farmMaintenance()
                &&parent.cultivationGuardPredicateCurrent(helper.allowedTarget())
                &&farmSettings.current()&&farm.originalMaintenanceGuard(token,McBlockBreakingService.this)==original
                &&(restoring?coordinator.greenhouseGuardRestorationSourceCurrent(source,parent,token,goal):
                    coordinator.movementGameplayAuthorityStillCurrent(source,parent,token,
                        dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK,goal))
                &&farmSettings.current();
        }
        private boolean nativeIdle(){
            return nativeMaintenanceContext()&&ownedNativeContext(original,attack,options)
                &&original.playerUuid()!=null&&original.playerUuid().equals(client.player.getUUID())
                &&client.options.keyUse==use&&!attack.isDown()&&!use.isDown()&&!breaking
                &&ownedBreakingReceipt==null&&immediateHeldAttempt==null&&farmBreaking==null&&nativePredictionsEmpty();
        }
        /** The handle exists only after the genuine farm-to-helper CAS normally returns. */
        public TargetGuardBinding guardBinding(){return swap.installed()?helper:null;}
        /** One attempt only. A refusal/throw leaves the current guard untouched and cannot be retried. */
        public boolean restoreOriginal(){return swap.restore(this::restorationCurrent,this::nativeIdle);}
    }
    public GreenhouseGuardCustody installGreenhouseMaintenanceGuard(
            dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent,Predicate<BlockPos> allowedTarget){
        try {
            if(parent==null||allowedTarget==null||!parent.cultivationGuardPredicateCurrent(allowedTarget)||!nativeMaintenanceContext()||client.options==null
                ||!McInputService.canonicalFarmBreakingCurrent(this,client)||breaking
                ||client.options.keyAttack.isDown()||client.options.keyUse.isDown()||!nativePredictionsEmpty())return null;
            if(attemptedGuardParent==parent&&attemptedGreenhouseGuard==allowedTarget)return null;
            // One original private predicate installation attempt; no retry can adopt later source/history.
            attemptedGuardParent=parent;attemptedGreenhouseGuard=allowedTarget;
            var guardSnapshot=GUARD_HISTORY.snapshot();var original=guardSnapshot.value();var originalGeneration=guardSnapshot.generation();
            var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
            var coordinator=dev.veltraph.client.VeltraphBootstrap.farmMaintenance();
            var config=dev.veltraph.client.VeltraphBootstrap.config();
            if(root==null||coordinator==null||root.get(parent.id()).orElse(null)!=parent)return null;
            var keys=new java.util.ArrayList<dev.veltraph.client.config.ConfigKey<?>>();
            for(var field:dev.veltraph.client.config.FarmingConfig.class.getFields())
                if(field.get(null) instanceof dev.veltraph.client.config.ConfigKey<?> key)keys.add(key);
            var farmSettings=config instanceof dev.veltraph.client.config.MapConfigService map?map.storedKeysSnapshot(keys):
                config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedKeysSnapshot(keys):null;
            if(farmSettings==null)return null;
            var token=coordinator.tokenFor(parent);if(token==null)return null;
            var goal=new dev.veltraph.client.world.Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
            var view=coordinator.movementAuthorityView(parent,token,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK,goal);
            if(view==null||view.registryIdentity()!=root||view.configStore()!=config)return null;
            var farm=view.farm();
            if(original==null||farm.originalMaintenanceGuard(token,this)!=original||!targetGuardBoundToCurrentContext(farm,original))return null;
            var source=coordinator.movementGameplayAuthority(parent,token,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.CROP_WORK,goal);
            if(source==null)return null;
            var helper=new TargetGuard(this,parent,client,original.level(),original.player(),original.gameMode(),
                original.connection(),allowedTarget,original.playerUuid());
            var result=new GreenhouseGuardCustody(parent,farm,coordinator,token,source,goal,original,helper,farmSettings,originalGeneration);
            // Retain the private pending destination before callbacks; final native/source leaves precede the sole CAS.
            var previous=greenhouseGuardCustody;greenhouseGuardCustody=result;
            if(!result.swap.install(result::sourceCurrent,result::nativeIdle)){
                if(greenhouseGuardCustody==result)greenhouseGuardCustody=previous;return null;}
            return result;
        }catch(Throwable unavailable){return null;}
    }

    /** Called at the vanilla interaction boundary, independently of attack-key/tick state. */
    public static boolean allowsDestroyBlock(MultiPlayerGameMode gameMode,
            net.minecraft.core.BlockPos actualTarget) {
        TargetGuard guard = TARGET_GUARD.get();
        if (guard == null) return true;
        try {
            Minecraft mc = guard.client();
            if (mc == null || mc != Minecraft.getInstance() || actualTarget == null
                    || guard.player() == null || guard.level() == null || guard.gameMode() == null
                    || mc.player != guard.player() || mc.level != guard.level()
                    || mc.gameMode != guard.gameMode() || gameMode != guard.gameMode()
                    || guard.connection() == null || mc.getConnection() != guard.connection()
                    || mc.player.level() != mc.level || !mc.player.isAlive()
                    || mc.gui.screen() != null || mc.level.isOutsideBuildHeight(actualTarget)
                    || !mc.level.getChunkSource().hasChunk(actualTarget.getX() >> 4, actualTarget.getZ() >> 4)) {
                return false;
            }
            var farm=guard.owner() instanceof dev.veltraph.client.farming.module.FarmModule actual?actual:null;
            var originalFarm=farm==null?null:McFarmOwnedCleanup.NativeScope.capture(mc,farm);
            if(farm!=null&&(originalFarm==null||!McInputService.canonicalFarmBreakingCurrent(guard.service(),mc)))return false;
            boolean allowed=guard.allowedTarget().test(new BlockPos(actualTarget.getX(),actualTarget.getY(),actualTarget.getZ()));
            // Predicate callbacks cannot refresh/adopt a different guard, owner or intended item.
            return allowed&&(farm==null||TARGET_GUARD.get()==guard
                    &&McInputService.canonicalFarmBreakingCurrent(guard.service(),mc)&&originalFarm.activeCurrent()
                    &&originalFarm.root.inputOwner().orElse(null)==farm&&McOwnedRotation.authority(mc)==farm
                    &&TARGET_GUARD.get()==guard&&McInputService.canonicalFarmBreakingCurrent(guard.service(),mc)
                    &&originalFarm.activeCurrent());
        } catch (Throwable failure) {
            // A stale/invalid predicate cannot fall through into a vanilla destroy attempt.
            return false;
        }
    }

    @Override
    public java.util.Optional<java.util.Set<BlockPos>> pendingBlockChanges(Object owner) {
        TargetGuard guard=TARGET_GUARD.get();
        try {
            if(guard==null||owner==null||guard.service()!=this||guard.owner()!=owner||guard.client()!=client
                    ||client!=Minecraft.getInstance()||!client.isSameThread()||guard.player()==null||guard.level()==null
                    ||guard.gameMode()==null||client.player!=guard.player()||client.level!=guard.level()
                    ||client.gameMode!=guard.gameMode()||client.player.level()!=client.level||!client.player.isAlive()
                    ||guard.connection()==null||client.getConnection()!=guard.connection()||client.gui.screen()!=null)return java.util.Optional.empty();
            if(!(client.level instanceof dev.veltraph.client.mixin.ClientLevelPredictionAccessor levelAccess))return java.util.Optional.empty();
            var handler=levelAccess.veltraph$blockStatePredictionHandler();
            if(!(handler instanceof dev.veltraph.client.mixin.BlockStatePredictionHandlerAccessor accessor))return java.util.Optional.empty();
            var nativePending=accessor.veltraph$serverVerifiedStates();
            if(nativePending==null)return java.util.Optional.empty();
            var copy=new java.util.HashSet<BlockPos>();
            for(long packed:nativePending.keySet().toLongArray()){
                var p=net.minecraft.core.BlockPos.of(packed);copy.add(new BlockPos(p.getX(),p.getY(),p.getZ()));
            }
            // A destroyed client prediction is AIR while its original coordinate is still pending.
            // These keys are copied unchanged; no state, sequence, map or acknowledgment is written.
            return java.util.Optional.of(java.util.Set.copyOf(copy));
        }catch(Throwable unavailable){return java.util.Optional.empty();}
    }

    public record FloorGuardObservation(boolean present,boolean ownerMatches,boolean nativeMatches,boolean pendingKnown,java.util.Set<net.minecraft.core.BlockPos> pending){
        public String cell(net.minecraft.core.BlockPos pos){return "guardPresent="+present+",guardOwnerMatches="+ownerMatches+",guardNativeMatches="+nativeMatches+",pendingKnown="+pendingKnown+",targetPending="+pending.contains(pos)+",confirmedHarvest=unavailable";}
    }
    /** At most36 membership reads. No full prediction-map copy, callback, mutation or ACK consumption. */
    public static FloorGuardObservation floorObservationGuard(Object owner,java.util.Collection<net.minecraft.core.BlockPos> sampled){
        if(sampled==null||sampled.size()>36)throw new IllegalArgumentException("Floor query exceeds36cells");
        var guard=TARGET_GUARD.get();if(guard==null)return new FloorGuardObservation(false,false,false,false,java.util.Set.of());
        var c=guard.client();boolean matches=guard.owner()==owner;boolean exact=matches&&c!=null&&c.isSameThread()&&guard.player()!=null&&guard.level()!=null&&guard.player()==c.player&&guard.level()==c.level&&guard.connection()==c.getConnection()&&guard.gameMode()==c.gameMode;
        if(!exact||!(c.level instanceof dev.veltraph.client.mixin.ClientLevelPredictionAccessor levelAccess))return new FloorGuardObservation(true,matches,exact,false,java.util.Set.of());
        var handler=levelAccess.veltraph$blockStatePredictionHandler();
        if(!(handler instanceof dev.veltraph.client.mixin.BlockStatePredictionHandlerAccessor accessor)||accessor.veltraph$serverVerifiedStates()==null)return new FloorGuardObservation(true,matches,exact,false,java.util.Set.of());
        var nativePending=accessor.veltraph$serverVerifiedStates();var present=new java.util.HashSet<net.minecraft.core.BlockPos>();
        for(var position:sampled)if(nativePending.containsKey(position.asLong()))present.add(position.immutable());
        return new FloorGuardObservation(true,matches,true,true,java.util.Set.copyOf(present));
    }

    @Override
    public void setBreaking(boolean breaking){
        RootNativeInputEpisode observation=null;
        try{observation=RootNativeInputEpisode.enter(this,client,client.options==null?null:client.options.keyAttack,breaking);}catch(RuntimeException|Error unavailable){}
        try{setBreakingOriginal(breaking);}finally{if(observation!=null)try{observation.close();}catch(RuntimeException|Error unavailable){}}
    }
    private void setBreakingOriginal(boolean breaking) {
        if(McIssuedFarmResumeGuard.releaseBreaking(this,breaking))return;
        if(farmBreakingCall(breaking))return;
        farmBreaking=null;
        revokeOwnedBreaking();
        this.breaking = breaking;
        if (client.options != null) {
            KeyMapping attack = client.options.keyAttack;
            if (attack != null) {
                McHeldKeys.setHeld(attack, breaking);
                if (VeltraphLog.shouldLog(dev.veltraph.client.log.DebugLogMode.VERBOSE)) {
                    VeltraphLog.verbose("McBlockBreakingService",
                            "attack key " + (breaking ? "HELD" : "released"));
                }
            }
        }
    }

    private final class FarmBreaking {
        final McFarmOwnedCleanup.NativeScope scope;final TargetGuard guard;
        final McHeldKeys.ScopedHeldReceipt held;final long generation;boolean guardCleared;
        FarmBreaking(McFarmOwnedCleanup.NativeScope scope,TargetGuard guard,McHeldKeys.ScopedHeldReceipt held,long generation) {
            this.scope=scope;this.guard=guard;this.held=held;this.generation=generation;
        }
        boolean storedCurrent() {
            var actualGuard=TARGET_GUARD.get();
            return farmBreaking==this&&ownedBreakingGeneration==generation
                &&McInputService.canonicalFarmBreakingCurrent(McBlockBreakingService.this,client)
                &&(actualGuard==guard||guardCleared&&actualGuard==null)
                &&held.key()==client.options.keyAttack&&held.normalIdleHistoryCurrent();
        }
    }
    /** Private actual canonical farm attack normal return; never a supplied receipt observer. */
    static final class KeyNormalReturn {
        private final McBlockBreakingService service;private final McFarmOwnedCleanup.NativeScope scope;private final RootNativeInputEpisode issuing;
        final McHeldKeys.ScopedHeldReceipt receipt;
        private KeyNormalReturn(McBlockBreakingService service,McFarmOwnedCleanup.NativeScope scope,McHeldKeys.ScopedHeldReceipt receipt){this.service=service;this.scope=scope;this.receipt=receipt;issuing=RootNativeInputEpisode.peek();}
        dev.veltraph.client.core.Module originalModule(){return scope.farm;}
        boolean nativeSourceCurrent(){return issuing!=null&&issuing.matches(service,scope,receipt)&&McInputService.canonicalFarmBreakingCurrent(service,service.client)&&(scope.activeReleaseCurrent()||scope.stopCurrent());}
    }
    private void observeOriginalBreakingReturn(McFarmOwnedCleanup.NativeScope scope,McHeldKeys.ScopedHeldReceipt actual){
        try {RootNativeHistory.normalBreakingReturned(new KeyNormalReturn(this,scope,actual));}
        catch(RuntimeException|Error unavailable){/* Passive observation cannot change original publication semantics. */}
    }
    static final class GuiBreakingRetirement {
        private final McBlockBreakingService service;private final RootNativeHistory.GuiTicket ticket;
        private final FarmBreaking exact;private final long generation;
        private GuiBreakingRetirement(McBlockBreakingService service,RootNativeHistory.GuiTicket ticket){this.service=service;this.ticket=ticket;exact=service.farmBreaking;generation=service.ownedBreakingGeneration;}
    }
    static GuiBreakingRetirement captureGuiBreakingRetirement(RootNativeHistory.GuiTicket ticket,Minecraft client){
        var service=McInputService.canonicalFarmBreaking(client);
        if(service==null||!RootNativeHistory.guiCaptureCurrent(ticket,client))return null;
        var exact=service.farmBreaking;
        if(exact!=null&&(!RootNativeHistory.guiOriginalCurrent(ticket,exact.scope.farm)||!exact.storedCurrent()
                ||!exact.held.confirmed()||!exact.held.held()||RootNativeHistory.guiBeforeKey(ticket,exact.held.key())==null))return null;
        return new GuiBreakingRetirement(service,ticket);
    }
    static boolean retireGuiClearedOwnedBreaking(GuiBreakingRetirement capture,RootNativeHistory.GuiTicket ticket,
            dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage.NormalTransition transition){
        if(capture==null||capture.ticket!=ticket||!McInputService.canonicalFarmBreakingCurrent(capture.service,capture.service.client)
                ||!RootNativeHistory.guiRetirementCurrent(ticket,transition,capture.service.client)
                ||capture.service.farmBreaking!=capture.exact||capture.service.ownedBreakingGeneration!=capture.generation)return false;
        if(capture.exact==null)return true;
        var key=capture.exact.held.key();if(transition.held(key)||!transition.advances(key,RootNativeHistory.guiBeforeKey(ticket,key)))return false;
        // Guard custody stays separate. No setter/cancel runs, and no new attack receipt is minted.
        capture.service.breaking=false;capture.service.revokeOwnedBreaking();return true;
    }
    static boolean adoptRetainedResumeOwnedBreaking(RootNativeHistory.RetainedResumeCustody actual){
        var service=McInputService.canonicalFarmBreaking(actual.client());
        if(service==null)return false;
        var beforeFarm=service.farmBreaking;var beforeOwned=service.ownedBreakingReceipt;var beforeImmediate=service.immediateHeldAttempt;
        long beforeGeneration=service.ownedBreakingGeneration;var beforeGuard=TARGET_GUARD.get();
        if(!actual.current())return false;
        var key=service.client.options.keyAttack;var receipt=actual.receipt(key);
        if(receipt==null||!receipt.confirmed()||receipt.key()!=key||!receipt.normalIdleHistoryCurrent())return false;
        if(!receipt.held())return true;
        var owned=receipt;
        if(!receipt.normallyReturnedOwnedHeldAttempt()){
            var old=beforeFarm;
            if(old==null||old.scope.farm!=actual.farm||!old.held.normallyReturnedOwnedHeldAttempt()||!old.held.normalIdleHistoryCurrent())return true;
            owned=old.held;
        }
        var guard=beforeGuard;
        if(guard==null||guard.service()!=service||guard.owner()!=actual.farm||beforeGeneration==Long.MAX_VALUE
                ||!actual.current()||!owned.normalIdleHistoryCurrent())return false;
        if(TARGET_GUARD.get()!=beforeGuard||service.farmBreaking!=beforeFarm||service.ownedBreakingReceipt!=beforeOwned
                ||service.immediateHeldAttempt!=beforeImmediate||service.ownedBreakingGeneration!=beforeGeneration)return false;
        // Exact normally returned true ownership only; no callbacks after final private ledger checks.
        service.revokeOwnedBreaking();long generation=service.ownedBreakingGeneration;
        service.farmBreaking=service.new FarmBreaking(actual.scope,guard,owned,generation);service.breaking=true;return true;
    }
    static boolean adoptSavedResumeOwnedBreaking(RootNativeHistory.SavedResumeCustody actual){
        var service=McInputService.canonicalFarmBreaking(actual.client());
        if(service==null)return false;
        var beforeFarm=service.farmBreaking;var beforeOwned=service.ownedBreakingReceipt;var beforeImmediate=service.immediateHeldAttempt;
        long beforeGeneration=service.ownedBreakingGeneration;var beforeGuard=TARGET_GUARD.get();
        if(!actual.current())return false;
        var key=service.client.options.keyAttack;var receipt=actual.receipt(key);
        if(receipt==null||!receipt.confirmed()||receipt.key()!=key||!receipt.normalIdleHistoryCurrent())return false;
        if(!receipt.held())return true;
        var owned=receipt;
        if(!receipt.normallyReturnedOwnedHeldAttempt()){
            var old=beforeFarm;
            if(old==null||old.scope.farm!=actual.farm||!old.held.normallyReturnedOwnedHeldAttempt()||!old.held.normalIdleHistoryCurrent())return true;
            owned=old.held;
        }
        var guard=beforeGuard;
        if(guard==null||guard.service()!=service||guard.owner()!=actual.farm||beforeGeneration==Long.MAX_VALUE
                ||!actual.current()||!owned.normalIdleHistoryCurrent())return false;
        if(TARGET_GUARD.get()!=beforeGuard||service.farmBreaking!=beforeFarm||service.ownedBreakingReceipt!=beforeOwned
                ||service.immediateHeldAttempt!=beforeImmediate||service.ownedBreakingGeneration!=beforeGeneration)return false;
        // Exact normally returned true ownership only; no callbacks after final private ledger checks.
        service.revokeOwnedBreaking();long generation=service.ownedBreakingGeneration;
        service.farmBreaking=service.new FarmBreaking(actual.scope,guard,owned,generation);service.breaking=true;return true;
    }
    static boolean adoptSavedRollbackOwnedBreaking(RootNativeHistory.SavedRollbackCustody actual){
        var service=McInputService.canonicalFarmBreaking(actual.client());
        if(service==null)return false;
        var beforeFarm=service.farmBreaking;var beforeOwned=service.ownedBreakingReceipt;var beforeImmediate=service.immediateHeldAttempt;
        long beforeGeneration=service.ownedBreakingGeneration;var beforeGuard=TARGET_GUARD.get();
        if(!actual.current())return false;
        var key=service.client.options.keyAttack;var receipt=actual.receipt(key);
        if(receipt==null||!receipt.confirmed()||receipt.key()!=key||!receipt.normalIdleHistoryCurrent())return false;
        if(!receipt.held())return true;
        var owned=receipt;
        if(!receipt.normallyReturnedOwnedHeldAttempt()){
            var old=beforeFarm;
            if(old==null||old.scope.farm!=actual.farm||!old.held.normallyReturnedOwnedHeldAttempt()||!old.held.normalIdleHistoryCurrent())return true;
            owned=old.held;
        }
        var guard=beforeGuard;
        if(guard==null||guard.service()!=service||guard.owner()!=actual.farm||beforeGeneration==Long.MAX_VALUE
                ||!actual.current()||!owned.normalIdleHistoryCurrent())return false;
        if(TARGET_GUARD.get()!=beforeGuard||service.farmBreaking!=beforeFarm||service.ownedBreakingReceipt!=beforeOwned
                ||service.immediateHeldAttempt!=beforeImmediate||service.ownedBreakingGeneration!=beforeGeneration)return false;
        // Exact normally returned true ownership only; no callbacks after final private ledger checks.
        service.revokeOwnedBreaking();long generation=service.ownedBreakingGeneration;
        service.farmBreaking=service.new FarmBreaking(actual.scope,guard,owned,generation);service.breaking=true;return true;
    }
    static final class PassiveBreakingContinuation {
        private final McBlockBreakingService service;
        private final RootNativeHistory.PassiveTransitionObservation source;
        private final FarmBreaking before;
        private final TargetGuard guard;
        private final OwnedBreakingReceipt owned;
        private final ImmediateHeldAttempt immediate;
        private final long generation;
        private final boolean breaking,guardCleared;
        private FarmBreaking next;
        private PassiveBreakingContinuation(McBlockBreakingService service,RootNativeHistory.PassiveTransitionObservation source){
            this.service=service;this.source=source;before=service.farmBreaking;guard=TARGET_GUARD.get();
            owned=service.ownedBreakingReceipt;immediate=service.immediateHeldAttempt;generation=service.ownedBreakingGeneration;
            breaking=service.breaking;guardCleared=before!=null&&before.guardCleared;
        }
    }
    static PassiveBreakingContinuation capturePassiveBreakingContinuation(RootNativeHistory.PassiveTransitionObservation source){
        var service=McInputService.canonicalFarmBreaking(source.client());if(service==null)return null;
        var capture=new PassiveBreakingContinuation(service,source);var old=capture.before;
        if(old!=null&&(!old.storedCurrent()||!old.held.normallyReturnedOwnedHeldAttempt()||!source.beforeScopeCurrent(old.scope)))return null;
        return passiveBreakingCommitCurrent(capture)?capture:null;
    }
    static PassiveBreakingContinuation preparePassiveBreakingContinuation(PassiveBreakingContinuation capture,RootNativeHistory.PassiveTransitionObservation source){
        if(capture==null||capture.source!=source||!source.normalCurrent())return null;
        var old=capture.before;
        if(old!=null){
            if(!source.normalScopeCurrent(old.scope)||!old.held.normallyReturnedOwnedHeldAttempt())return null;
            var scope=old.scope.continueAfterPassive(source.actual());if(scope==null)return null;
            capture.next=capture.service.new FarmBreaking(scope,old.guard,old.held,old.generation);capture.next.guardCleared=capture.guardCleared;
        }
        return source.normalCurrent()&&passiveBreakingCommitCurrent(capture)?capture:null;
    }
    /** Raw private custody checks only, after every callback-bearing source/native read. */
    static boolean passiveBreakingCommitCurrent(PassiveBreakingContinuation capture){
        var service=capture.service;return service.farmBreaking==capture.before&&TARGET_GUARD.get()==capture.guard
            &&service.ownedBreakingReceipt==capture.owned&&service.immediateHeldAttempt==capture.immediate
            &&service.ownedBreakingGeneration==capture.generation&&service.breaking==capture.breaking
            &&(capture.before==null||capture.before.guardCleared==capture.guardCleared);
    }
    static boolean commitPassiveBreakingContinuation(PassiveBreakingContinuation capture){
        if(!capture.source.commitWindowCurrent()||!passiveBreakingCommitCurrent(capture)||(capture.before!=null&&capture.next==null))return false;
        if(capture.before!=null)capture.service.farmBreaking=capture.next;
        return true;
    }
    private FarmBreaking farmBreaking;
    private boolean farmBreakingCall(boolean requested) {
        var prior=farmBreaking;
        var refused=McFarmOwnedCleanup.toolRefusalFarm();
        if(refused!=null&&!McFarmOwnedCleanup.stopping()) {
            if(requested||prior==null||prior.scope.farm!=refused
                    ||!prior.scope.activeReleaseProvidersCurrent()||!prior.storedCurrent())return true;
            var released=McHeldKeys.releaseOwnedFromObservedScoped(prior.held,
                    ()->McFarmOwnedCleanup.toolRefusalFarm()==refused&&prior.scope.activeReleaseCurrent()&&prior.storedCurrent());
            observeOriginalBreakingReturn(prior.scope,released);
            if(released.confirmed()||released.normallyReturnedOwnedReleaseAttempt()) {
                if(farmBreaking==prior&&ownedBreakingGeneration==prior.generation&&prior.scope.activeReleaseCurrent()) {
                    breaking=false;farmBreaking=null;revokeOwnedBreaking();
                }
            }
            return true;
        }
        if(McFarmOwnedCleanup.stopping()) {
            if(requested||prior==null||prior.scope.farm!=McFarmOwnedCleanup.stoppingFarm()
                ||!prior.scope.cleanupProvidersCurrent()||!prior.storedCurrent())return true;
            var released=McHeldKeys.releaseOwnedFromObservedScoped(prior.held,
                ()->prior.scope.stopCurrent()&&prior.storedCurrent());
            observeOriginalBreakingReturn(prior.scope,released);
            if(released.confirmed()||released.normallyReturnedOwnedReleaseAttempt()) {
                if(farmBreaking==prior&&ownedBreakingGeneration==prior.generation&&prior.scope.stopCurrent()) {
                    breaking=false;farmBreaking=null;revokeOwnedBreaking();
                }
            }
            return true;
        }
        // Suspension commits its flag before releasing attack. Retain the original publication rather than minting a new active scope.
        if(!requested&&prior!=null&&prior.scope.activeReleaseProvidersCurrent()&&prior.storedCurrent()) {
            var released=McHeldKeys.releaseOwnedFromObservedScoped(prior.held,
                ()->prior.scope.activeReleaseCurrent()&&prior.storedCurrent());
            observeOriginalBreakingReturn(prior.scope,released);
            if(released.confirmed()||released.normallyReturnedOwnedReleaseAttempt()) {
                if(farmBreaking==prior&&ownedBreakingGeneration==prior.generation&&prior.scope.activeReleaseCurrent()) {
                    breaking=false;farmBreaking=null;revokeOwnedBreaking();
                }
            }
            return true;
        }
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        var active=root==null?null:root.inputOwner().orElse(null);
        if(!(active instanceof dev.veltraph.client.farming.module.FarmModule farm)
            ||!McInputService.canonicalFarmBreakingCurrent(this,client))return McFarmOwnedCleanup.farmingTickFarm()!=null;
        if(McFarmOwnedCleanup.farmingTickFarm()!=null&&McFarmOwnedCleanup.farmingTickFarm()!=farm)return true;
        var scope=McFarmOwnedCleanup.NativeScope.capture(client,farm);
        if(scope==null)return true;
        var guard=TARGET_GUARD.get();
        if(guard==null||guard.service()!=this||guard.owner()!=farm||!targetGuardBoundToCurrentContext(farm,guard))return true;
        if(!requested) {
            if(prior!=null&&prior.scope.farm==farm&&prior.scope.activeReleaseCurrent()&&prior.storedCurrent()) {
                var released=McHeldKeys.releaseOwnedFromObservedScoped(prior.held,
                    ()->prior.scope.activeReleaseCurrent()&&prior.storedCurrent());
                observeOriginalBreakingReturn(prior.scope,released);
                if(released.confirmed()||released.normallyReturnedOwnedReleaseAttempt()) {
                    if(farmBreaking==prior&&ownedBreakingGeneration==prior.generation&&prior.scope.activeReleaseCurrent()) {
                        breaking=false;farmBreaking=null;revokeOwnedBreaking();
                    }
                }
            }
            return true;
        }
        if(prior!=null&&prior.scope.farm==farm&&prior.guard==guard&&prior.storedCurrent()
            &&prior.scope.sameNativeProducer(scope)&&scope.activeCurrent()) {
            // This exact private own key history survives a real same-producer no-op, never a foreign/raw write.
            var observed=McHeldKeys.setHeldFromObservedScoped(client.options.keyAttack,true,
                ()->prior.storedCurrent()&&scope.activeCurrent());
            observeOriginalBreakingReturn(scope,observed);
            if(observed.confirmed()&&prior.storedCurrent()&&scope.activeCurrent())
                farmBreaking=new FarmBreaking(scope,guard,prior.held,prior.generation);
            return true;
        }
        if(ownedBreakingGeneration==Long.MAX_VALUE||client.options.keyAttack.isDown())return true;
        revokeOwnedBreaking();long generation=ownedBreakingGeneration;
        var published=McHeldKeys.setHeldFromObservedScoped(client.options.keyAttack,true,
            ()->ownedBreakingGeneration==generation&&TARGET_GUARD.get()==guard&&scope.activeCurrent());
        observeOriginalBreakingReturn(scope,published);
        if(published.normallyReturnedOwnedHeldAttempt()&&ownedBreakingGeneration==generation
            &&TARGET_GUARD.get()==guard&&scope.activeCurrent()) {
            farmBreaking=new FarmBreaking(scope,guard,published,generation);breaking=true;
        }
        return true;
    }

    /** A real target-bound native held publication; public values cannot construct this receipt. */
    public final class OwnedBreakingReceipt {
        private final TargetGuard guard;private final KeyMapping key;private final Object options,config;
        private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private final McHeldKeys.ScopedHeldReceipt held;private final long generation;
        private OwnedBreakingReceipt(TargetGuard guard,KeyMapping key,McHeldKeys.ScopedHeldReceipt held,
                dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,
                dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,Object config,long generation){
            this.guard=guard;this.key=key;this.held=held;this.root=root;this.lifecycle=lifecycle;this.input=input;
            this.config=config;this.generation=generation;options=client.options;
        }
        public boolean current(){return ownedBreakingActionCurrent(this);}
    }
    /** Normal legacy invocations revoke scoped custody even if vanilla sees a no-op held value. */
    private void revokeOwnedBreaking(){farmBreaking=null;ownedBreakingReceipt=null;immediateHeldAttempt=null;if(ownedBreakingGeneration<Long.MAX_VALUE)ownedBreakingGeneration++;}
    private boolean exactGuard(Object owner,TargetGuardBinding exact){
        return exact instanceof TargetGuard guard&&TARGET_GUARD.get()==guard&&guard.service()==this&&guard.owner()==owner&&guard.client()==client;
    }
    private boolean ownedNativeContext(TargetGuard guard,KeyMapping key,Object options){
        return client==Minecraft.getInstance()&&client.isSameThread()&&client.options==options&&client.options!=null
            &&client.options.keyAttack==key&&client.player==guard.player()&&client.level==guard.level()
            &&client.gameMode==guard.gameMode()&&client.getConnection()==guard.connection()
            &&client.player!=null&&client.level!=null&&client.getConnection()!=null;
    }
    private boolean ownedBreakingCurrent(OwnedBreakingReceipt exact){
        if(exact==null||exact!=ownedBreakingReceipt||exact.generation!=ownedBreakingGeneration||!breaking
            ||!exactGuard(exact.guard.owner(),exact.guard)||!ownedNativeContext(exact.guard,exact.key,exact.options)
            ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=exact.root
            ||dev.veltraph.client.VeltraphBootstrap.config()!=exact.config
            ||!(exact.guard.owner() instanceof dev.veltraph.client.core.Module parent)
            ||exact.root.get(parent.id()).orElse(null)!=parent
            ||!exact.root.ownCleanupLifecycleCurrent(exact.lifecycle,parent)||!exact.input.current()
            ||!exact.input.active()||!parent.id().equals(exact.input.ownerModuleId()))return false;
        // Actual private key history is checked last; foreign native ABA cannot restore custody.
        return exact.held.confirmed()&&exact.held.held()&&exact.held.key()==exact.key&&exact.held.normalIdleHistoryCurrent();
    }
    private boolean ownedBreakingActionCurrent(OwnedBreakingReceipt exact){
        return ownedBreakingCurrent(exact)&&exact.root.lifecycleCurrent(exact.lifecycle)&&nativeMaintenanceContext();
    }
    /** Private actual attempt flow. It cannot be constructed by a caller or used as gameplay authority. */
    final class ImmediateHeldAttempt {
        private final dev.veltraph.client.core.Module parent;private final TargetGuard guard;private final KeyMapping key;
        private final Object options,config;private final dev.veltraph.client.core.ModuleManager root;
        private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;private long generation;
        private McHeldKeys.ScopedHeldReceipt held;
        private ImmediateHeldAttempt(dev.veltraph.client.core.Module parent,TargetGuard guard,KeyMapping key,Object options,Object config,
                dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.LifecycleSnapshot lifecycle,
                dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input,long generation){
            this.parent=parent;this.guard=guard;this.key=key;this.options=options;this.config=config;this.root=root;this.lifecycle=lifecycle;this.input=input;this.generation=generation;
        }
        /** Stored identities only, called immediately after normally returned privately claimed TRUE. */
        void cache(McHeldKeys.ScopedHeldReceipt actual){
            if(contextCurrent()&&root.lifecycleCurrent(lifecycle)&&actual!=null&&actual.key()==key&&actual.normallyReturnedOwnedHeldAttempt())held=actual;
        }
        private boolean contextCurrent(){
            return immediateHeldAttempt==this&&ownedBreakingGeneration==generation&&client.isSameThread()
                &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&dev.veltraph.client.VeltraphBootstrap.config()==config
                &&McInputService.canonicalFarmBreakingCurrent(McBlockBreakingService.this,client)
                &&root.get(parent.id()).orElse(null)==parent&&root.ownCleanupLifecycleCurrent(lifecycle,parent)
                &&input.current()&&input.active()&&parent.id().equals(input.ownerModuleId())
                &&exactGuard(parent,guard)&&ownedNativeContext(guard,key,options);
        }
    }
    /** Releases only this exact service's cached native TRUE; return is cleanup accounting, never action/ACK. */
    public boolean releaseBreakingAttemptedOwned(Object owner,TargetGuardBinding exactGuard){
        var attempt=immediateHeldAttempt;
        if(attempt==null||attempt.parent!=owner||attempt.guard!=exactGuard||!attempt.contextCurrent()
            ||attempt.held==null||!attempt.held.normallyReturnedOwnedHeldAttempt())return false;
        var released=McHeldKeys.releaseAttemptedOwnedScoped(attempt.held,attempt::contextCurrent);
        if(!released.normallyReturnedOwnedReleaseAttempt()||!attempt.contextCurrent())return false;
        // No source callback after the final leaf and before retirement. Never revives an action receipt.
        breaking=false;revokeOwnedBreaking();return true;
    }
    /** Exact source target guard and original active Root role are required before the first native effect. */
    public OwnedBreakingReceipt setBreakingOwned(Object owner,TargetGuardBinding exactGuard,java.util.function.BooleanSupplier refusalOnlyFence){
        Objects.requireNonNull(refusalOnlyFence);
        if(!(owner instanceof dev.veltraph.client.core.Module parent)||!(exactGuard instanceof TargetGuard guard)
            ||!exactGuard(owner,exactGuard)||!nativeMaintenanceContext()||ownedBreakingGeneration==Long.MAX_VALUE)return null;
        var prior=ownedBreakingReceipt;
        if(prior!=null&&prior.guard==guard&&ownedBreakingActionCurrent(prior)){
            if(!refusalOnlyFence.getAsBoolean()||!ownedBreakingActionCurrent(prior))return null;
            return prior; // Retain only a genuine current own publication, never claim a new setter.
        }
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var config=dev.veltraph.client.VeltraphBootstrap.config();
        var lifecycle=root==null?null:root.lifecycleSnapshot();var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        var options=client.options;var attack=options==null?null:options.keyAttack;long originalGeneration=ownedBreakingGeneration;
        if(root==null||lifecycle==null||!lifecycle.known()||input==null||!input.active()||!parent.id().equals(input.ownerModuleId())
            ||root.get(parent.id()).orElse(null)!=parent||root.inputOwner().orElse(null)!=parent||!parent.isEnabled()
            ||attack==null||attack.isDown()||breaking)return null;
        java.util.function.BooleanSupplier cleanupLeaf=()->client.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root
            &&dev.veltraph.client.VeltraphBootstrap.config()==config&&root.ownCleanupLifecycleCurrent(lifecycle,parent)&&input.current()
            &&exactGuard(owner,guard)
            &&ownedNativeContext(guard,attack,options)&&ownedBreakingGeneration==originalGeneration;
        java.util.function.BooleanSupplier nativeLeaf=()->cleanupLeaf.getAsBoolean()&&root.lifecycleCurrent(lifecycle)&&nativeMaintenanceContext();
        java.util.function.BooleanSupplier fence=()->refusalOnlyFence.getAsBoolean()&&nativeLeaf.getAsBoolean();
        // Key history is captured inside this call before the first observer/source callback.
        var published=McHeldKeys.setHeldFromObservedScoped(attack,true,fence);
        if(!OwnedBreakingCommitFence.current(published.confirmed()&&published.held(),published::normalIdleHistoryCurrent,
                refusalOnlyFence,nativeLeaf)){
            // A policy refusal after a normally returned own setter grants cleanup only, never a successful action.
            // Foreign native/key/service history or actor replacement refuses this exact release.
            McHeldKeys.releaseAttemptedOwnedScoped(published,cleanupLeaf);return null;
        }
        // Sole stored service-state commit follows the actual normally returned native publication.
        breaking=true;ownedBreakingGeneration++;
        var result=new OwnedBreakingReceipt(guard,attack,published,root,lifecycle,input,config,ownedBreakingGeneration);
        ownedBreakingReceipt=result;return ownedBreakingActionCurrent(result)?result:null;
    }
    /** Greenhouse-only immediate cleanup handoff; ordinary generic publication remains byte exact. */
    public OwnedBreakingReceipt setBreakingOwnedImmediate(Object owner,TargetGuardBinding exactGuard,java.util.function.BooleanSupplier refusalOnlyFence){
        Objects.requireNonNull(refusalOnlyFence);
        if(!(owner instanceof dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule parent)||!(exactGuard instanceof TargetGuard guard)
            ||!exactGuard(owner,exactGuard)||!nativeMaintenanceContext()||ownedBreakingGeneration==Long.MAX_VALUE)return null;
        var prior=ownedBreakingReceipt;
        if(prior!=null&&prior.guard==guard&&ownedBreakingActionCurrent(prior)){
            if(!refusalOnlyFence.getAsBoolean()||!ownedBreakingActionCurrent(prior))return null;
            return prior; // Retain only a genuine current own publication, never claim a new setter.
        }
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var config=dev.veltraph.client.VeltraphBootstrap.config();
        var lifecycle=root==null?null:root.lifecycleSnapshot();var input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        var options=client.options;var attack=options==null?null:options.keyAttack;long originalGeneration=ownedBreakingGeneration;
        if(root==null||lifecycle==null||!lifecycle.known()||input==null||!input.active()||!parent.id().equals(input.ownerModuleId())
            ||root.get(parent.id()).orElse(null)!=parent||root.inputOwner().orElse(null)!=parent||!parent.isEnabled()
            ||attack==null||attack.isDown()||breaking)return null;
        java.util.function.BooleanSupplier cleanupLeaf=()->client.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root
            &&dev.veltraph.client.VeltraphBootstrap.config()==config&&root.ownCleanupLifecycleCurrent(lifecycle,parent)&&input.current()
            &&exactGuard(owner,guard)
            &&ownedNativeContext(guard,attack,options)&&ownedBreakingGeneration==originalGeneration;
        java.util.function.BooleanSupplier nativeLeaf=()->cleanupLeaf.getAsBoolean()&&root.lifecycleCurrent(lifecycle)&&nativeMaintenanceContext();
        java.util.function.BooleanSupplier fence=()->refusalOnlyFence.getAsBoolean()&&nativeLeaf.getAsBoolean();
        // Store concrete private in-flight destination before native publication. McHeldKeys caches only real returned TRUE.
        if(!McInputService.canonicalFarmBreakingCurrent(this,client))return null;
        var attempt=new ImmediateHeldAttempt(parent,guard,attack,options,config,root,lifecycle,input,originalGeneration);
        immediateHeldAttempt=attempt;
        // Key history is captured inside this call before the first observer/source callback.
        var published=McHeldKeys.setHeldFromObservedScoped(attack,true,fence,attempt);
        if(!OwnedBreakingCommitFence.current(published.confirmed()&&published.held(),published::normalIdleHistoryCurrent,
                refusalOnlyFence,nativeLeaf)){
            // A policy refusal after a normally returned own setter grants cleanup only, never a successful action.
            // Foreign native/key/service history or actor replacement refuses this exact release.
            releaseBreakingAttemptedOwned(owner,guard);return null;
        }
        // Original source validation cannot resurrect a cache released/replaced by nested cleanup.
        if(!attempt.contextCurrent()||attempt.held==null||!attempt.held.normallyReturnedOwnedHeldAttempt())return null;
        // Sole stored service-state commit follows the actual normally returned native publication.
        breaking=true;ownedBreakingGeneration++;attempt.generation=ownedBreakingGeneration;
        var result=new OwnedBreakingReceipt(guard,attack,published,root,lifecycle,input,config,ownedBreakingGeneration);
        ownedBreakingReceipt=result;return ownedBreakingActionCurrent(result)?result:null;
    }
    /** Exact original key/service/native history; changed context or replacement publication refuses. */
    public boolean releaseBreakingOwned(OwnedBreakingReceipt exact,java.util.function.BooleanSupplier refusalOnlyFence){
        Objects.requireNonNull(refusalOnlyFence);
        if(!ownedBreakingCurrent(exact)||!refusalOnlyFence.getAsBoolean()||!ownedBreakingCurrent(exact))return false;
        // The private scoped key receipt accounts for only this release's native transitions.
        var released=McHeldKeys.releaseOwnedFromObservedScoped(exact.held,()->refusalOnlyFence.getAsBoolean()&&ownedBreakingReleaseContext(exact));
        if(!OwnedBreakingCommitFence.current(released.confirmed()&&!released.held(),released::normalIdleHistoryCurrent,
                refusalOnlyFence,()->ownedBreakingReleaseContext(exact))){
            // A normally returned own false setter retires local state even when a later source callback refuses/throws.
            // This logical cleanup is not reported as transaction success and cannot overwrite a foreign service claim.
            if(released.normallyReturnedOwnedReleaseAttempt()&&ownedBreakingReleaseContext(exact)){breaking=false;revokeOwnedBreaking();}
            return false;
        }
        breaking=false;revokeOwnedBreaking();return true;
    }
    private boolean ownedBreakingReleaseContext(OwnedBreakingReceipt exact){
        return exact!=null&&exact==ownedBreakingReceipt&&exact.generation==ownedBreakingGeneration&&breaking
            &&exactGuard(exact.guard.owner(),exact.guard)&&ownedNativeContext(exact.guard,exact.key,exact.options)
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==exact.root&&dev.veltraph.client.VeltraphBootstrap.config()==exact.config
            &&exact.guard.owner() instanceof dev.veltraph.client.core.Module parent&&exact.root.get(parent.id()).orElse(null)==parent
            &&exact.root.ownCleanupLifecycleCurrent(exact.lifecycle,parent)&&exact.input.current()&&exact.input.active()
            &&parent.id().equals(exact.input.ownerModuleId());
    }
    public boolean clearTargetGuardOwned(Object owner,TargetGuardBinding exact){
        if(McIssuedFarmResumeGuard.active())return false;
        if(!(exact instanceof TargetGuard guard)||!exactGuard(owner,exact))return false;
        if(McFarmOwnedCleanup.stopping()) {
            if(!(owner instanceof dev.veltraph.client.farming.module.FarmModule farm)
                ||!McFarmOwnedCleanup.stopRouteCurrent(farm)||!McInputService.canonicalFarmBreakingCurrent(this,client))return false;
            var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
            var pending=root==null?null:root.pendingActualOwnDisable(farm).orElse(null);
            if(pending==null||pending.registryIdentity()!=root||!pending.current(farm)
                ||!ownedNativeContext(guard,client.options==null?null:client.options.keyAttack,client.options)
                ||!McFarmOwnedCleanup.stopRouteCurrent(farm)||!pending.current(farm)||!exactGuard(owner,exact))return false;
        }
        if(!GUARD_HISTORY.compareAndSet(guard,null))return false;
        var own=farmBreaking;if(own!=null&&own.guard==guard&&own.scope.farm==owner)own.guardCleared=true;
        return true;
    }

    @Override
    public java.util.Optional<dev.veltraph.client.world.BlockPos> targetBlockPos() {
        if(client.level==null || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return java.util.Optional.empty();
        var p=hit.getBlockPos();
        if(!client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4))return java.util.Optional.empty();
        return java.util.Optional.of(new dev.veltraph.client.world.BlockPos(p.getX(),p.getY(),p.getZ()));
    }

    @Override
    public String targetBlockId() {
        if(client.level==null || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return "";
        var pos=hit.getBlockPos();
        if(!client.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4))return "";
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(client.level.getBlockState(pos).getBlock()).toString();
    }

    @Override
    public java.util.Optional<dev.veltraph.client.world.BlockPos> freshTargetBlockPos() {
        var player=client.player;var level=client.level;
        if(player==null||level==null||player.level()!=level||!player.isAlive()
                ||client.getConnection()==null||client.gui.screen()!=null)return java.util.Optional.empty();
        double range=player.blockInteractionRange();
        if(!Double.isFinite(range)||range<=0||range>64)return java.util.Optional.empty();
        var picked=player.pick(range,1f,false);
        if(!(picked instanceof net.minecraft.world.phys.BlockHitResult hit)
                ||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return java.util.Optional.empty();
        var pos=hit.getBlockPos();
        if(level.isOutsideBuildHeight(pos)||!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4))return java.util.Optional.empty();
        return java.util.Optional.of(new dev.veltraph.client.world.BlockPos(pos.getX(),pos.getY(),pos.getZ()));
    }

    @Override
    public boolean isBreaking() {
        return breaking;
    }
}
