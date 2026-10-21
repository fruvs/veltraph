package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.navigation.FlightController;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.navigation.LegacyTravelPublicationIdentity;
import dev.veltraph.client.navigation.mc.McFlightNavigator;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McRotationService;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McScopedGroundSharedMovementNavigator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Loaded current-plot source pest scanning, vacuum relocation, native aim/use hold, exact removal+bag proof. */
public final class McPestVacuumModule extends GardenActionModule {
    public static final String ID="garden_pest_cleaner";private PestBindings.Cleaner b;private Entity target;private int bag,removed;private boolean ack,held,flying;private long holdUntil;
    private final McFlightNavigator flight;private final McScopedGroundSharedMovementNavigator pestGround;private boolean pestWalking;private List<Entity> burst=List.of();
    private final LegacyTravelPublicationLease publications;private LegacyTravelPublicationLease.Leg flightLeg;private long flightJourney;
    private LegacyTravelRetirement.RetirementOutcome motionRetirement;private boolean retirementBlocked,handoffRefused;private String retirementDiagnostic="";
    private LegacyTravelPublicationIdentity.State capturedParentContext;
    private java.util.function.Supplier<LegacyTravelPublicationIdentity.State> retiredCleanupProof=()->null;
    private java.util.function.Supplier<LegacyTravelPublicationIdentity.State> retiredStopProof=()->null;
    private LegacyTravelPublicationLease.RetiredParentInteraction parentInteraction;
    private java.util.function.Supplier<LegacyTravelPublicationLease.RetiredParentInteraction> retiredInteraction=()->null;
    private PreparedStart preparedStart;private boolean preparedReady;private LegacyTravelPublicationLease.Leg initialAdmission;
    private LegacyTravelPublicationLease.CleanupTransaction normalCleanupTransaction;
    private LegacyTravelPublicationLease.KeyReleaseOutcome normalKeyOutcome;
    private LegacyTravelPublicationLease.CameraCleanupOutcome normalCameraOutcome;
    private PestInventoryLease.OwnedRestoreReceipt normalInventoryOutcome;
    private LegacyTravelPublicationLease.NativeParentCleanupToken normalCleanupToken;
    private dev.veltraph.client.core.ModuleManager.OwnEnableReceipt observedRestartEnable;
    private dev.veltraph.client.core.ModuleManager.OwnDisableReceipt actualStoppedReceipt;
    private long normalCleanupSourceVersion=-1;

    /** Only the actual COMPLETE source invocation can hand the existing native interaction to its issuer. */
    @Override protected LegacyTravelPublicationLease.RetiredParentInteraction childCompletionInteraction(){
        return parentInteraction;
    }
    /** Before Parent resets its source, retain read-only intent for this actual Core enable invocation. No effects or admission. */
    @Override protected void childRestartPreparation(dev.veltraph.client.core.ModuleManager.OwnEnableReceipt ownEnable){
        var exact=normalCleanupTransaction;if(exact==null)return;long version=sourceVersion();
        try{var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var token=exact.completionToken();
            if(ownEnable==null||ownEnable.parent()!=this||!ownEnable.invocationCurrent()||root==null||root.get(ID).orElse(null)!=this
                    ||normalCleanupTransaction!=exact||normalCleanupSourceVersion!=version||token==null||token.consumed()
                    ||retirementBlocked||handoffRefused||cleanupInProgress||helperCleanupFailed||ownedCleanup!=null)return;
            observedRestartEnable=ownEnable;normalCleanupToken=token;
            var intent=new PreparedStart(root,ownEnable,exact,token,version);
            if(intent.preparing()&&normalCleanupTransaction==exact&&normalCleanupSourceVersion==version&&sourceVersion()==version
                    &&observedRestartEnable==ownEnable&&normalCleanupToken==token&&ownEnable.invocationCurrent()){
                preparedStart=intent;preparedReady=false;
            }
        }catch(RuntimeException unavailable){/* Unknown intent cannot bypass the original Parent lifecycle gate. */}
    }
    /** Issuer itself owns each effect and its outcomes; no actor receipt list or Boolean grants cleanup. */
    @Override protected void childBeforeOwnedDisable(NormalCompletionReceipt completion){
        if(completion==null||normalCleanupTransaction!=null||cleanupInProgress||helperCleanupFailed||retirementBlocked)return;
        var exactInteraction=parentInteraction;var exactHandle=inventoryCleanupHandle;
        if(exactInteraction==null||exactHandle==null||completion.interaction()!=exactInteraction||!completion.current())return;
        var transaction=exactInteraction.beginCleanupTransaction(completion,exactHandle);if(transaction==null)return;
        normalCleanupTransaction=transaction;normalCleanupSourceVersion=sourceVersion();
        // Capture original strict gear permission while enabled. All effects wait for actual onDisable flag=false.
        retirementDiagnostic="NORMAL_CLEANUP_CAPTURED: "+transaction.diagnostic();
    }
    /** Actual managed post-disable return; no actor restoration, key, camera or packet effects run here. */
    @Override protected void childActualDisableReturned(dev.veltraph.client.core.ModuleManager.OwnDisableReceipt receipt){
        var exact=normalCleanupTransaction;long version=sourceVersion();
        if(exact==null||normalCleanupSourceVersion!=version||receipt==null||receipt.parent()!=this||!receipt.returnedCurrent())return;
        if(!exact.observeActualDisableReceipt(receipt))return;
        if(normalCleanupTransaction==exact&&normalCleanupSourceVersion==sourceVersion())actualStoppedReceipt=receipt;
        // Observe only. Neither a token object nor COMPLETE can exist until the entire real posthook confirms normal return.
    }
    /** Actual setEnabled(false) and complete Core hook returned normally; this callback publishes nothing. */
    @Override protected void childOwnedDisableNormalReturned(NormalCompletionReceipt completion){
        var exact=normalCleanupTransaction;
        if(exact==null||completion==null||completion.interaction()!=parentInteraction||normalCleanupSourceVersion!=sourceVersion()
                ||actualStoppedReceipt==null||actualStoppedReceipt.parent()!=this||!actualStoppedReceipt.current()||isEnabled())return;
        var token=exact.complete();boolean idleAttempted=false,idleRegistered=false;
        if(normalCleanupTransaction==exact&&normalCleanupSourceVersion==sourceVersion()){
            normalCleanupToken=token;
            // This real normal-return site precedes Root's causal lock-drop; never arm from a later enable or diagnostic poll.
            if(token!=null&&token.current()){idleAttempted=true;idleRegistered=token.armNormalIdleCancellation();}
        }
        retirementDiagnostic="NORMAL_CLEANUP_RETURNED: "+exact.diagnostic()+"; idleRegistration="
            +(idleRegistered?"ACTUAL_NORMAL_TOKEN_REGISTERED":idleAttempted?"UNAVAILABLE":"AWAITING_CONFIRMED_TOKEN");
    }
    /** Read-only completion attempt: the issuer still requires actual disabled STOPPED and full native proof. */
    private void observeStoppedCleanup(){var exact=normalCleanupTransaction;
        if(exact==null||normalCleanupSourceVersion!=sourceVersion()||isEnabled()||state()!=dev.veltraph.client.core.MacroState.STOPPED
                ||actualStoppedReceipt==null||!actualStoppedReceipt.current())return;
        var token=exact.complete();if(normalCleanupTransaction==exact&&normalCleanupSourceVersion==sourceVersion())normalCleanupToken=token;
        retirementDiagnostic="NORMAL_CLEANUP: "+exact.diagnostic();
    }
    /** Intent only: no Legacy admission, native actuator, control ticket or cleanup authority. */
    private final class PreparedStart {
        private final dev.veltraph.client.core.ModuleManager root;
        private final Object player,world,connection,store,rotation;
        private final dev.veltraph.client.core.ModuleManager.OwnEnableReceipt restartEnable;
        private final LegacyTravelPublicationLease.CleanupTransaction restartTransaction;
        private final LegacyTravelPublicationLease.NativeParentCleanupToken restartToken;
        private final long sourceBeforeEnable;
        private PreparedStart(dev.veltraph.client.core.ModuleManager root){this(root,null,null,null,-1);}
        private PreparedStart(dev.veltraph.client.core.ModuleManager root,dev.veltraph.client.core.ModuleManager.OwnEnableReceipt enable,
                LegacyTravelPublicationLease.CleanupTransaction transaction,LegacyTravelPublicationLease.NativeParentCleanupToken token,long source){
            this.root=root;restartEnable=enable;restartTransaction=transaction;restartToken=token;sourceBeforeEnable=source;
            player=client.player;world=client.level;connection=client.getConnection();
            store=dev.veltraph.client.VeltraphBootstrap.config();rotation=services.rotation();}
        /** Core observation authorizes only this no-effect intent, never old cleanup/current or a new Leg. */
        private boolean restartStateCurrent(boolean beforeReset){return restartEnable==null?!beforeReset:
                restartTransaction!=null&&restartToken!=null&&observedRestartEnable==restartEnable
                &&normalCleanupTransaction==restartTransaction&&normalCleanupToken==restartToken&&!restartToken.consumed()
                &&normalCleanupSourceVersion==sourceBeforeEnable&&sourceBeforeEnable>=0&&sourceBeforeEnable<Long.MAX_VALUE
                &&sourceVersion()==(beforeReset?sourceBeforeEnable:sourceBeforeEnable+1)
                &&!retirementBlocked&&!handoffRefused&&!cleanupInProgress&&!helperCleanupFailed&&ownedCleanup==null
                &&restartEnable.parent()==McPestVacuumModule.this
                &&(beforeReset?restartEnable.invocationCurrent():restartEnable.invocationCurrent()||restartEnable.current());}
        private boolean sameSource(){return client.isSameThread()&&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root
            &&root.get(ID).orElse(null)==McPestVacuumModule.this&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&dev.veltraph.client.VeltraphBootstrap.config()==store&&services.rotation()==rotation;}
        private boolean preparing(){return sceneCurrent(true);}
        private boolean current(){return sceneCurrent(false);}
        private boolean sceneCurrent(boolean beforeReset){try{
            var lifecycle=root.lifecycleSnapshot();if(lifecycle==null||!lifecycle.known()||!sameSource()||!isEnabled()||!restartStateCurrent(beforeReset))return false;
            boolean role=root.inputOwner().orElse(null)==McPestVacuumModule.this&&root.enabledControllers().size()==1;
            return role&&sameSource()&&restartStateCurrent(beforeReset)&&root.lifecycleCurrent(lifecycle);
        }catch(RuntimeException unavailable){return false;}}
    }
    private OwnedCleanupReceipt ownedCleanup;private PestInventoryLease.OwnedRestoreHandle inventoryCleanupHandle;private List<KeyCleanup> initialKeyCleanup=List.of();private boolean cleanupInProgress,helperCleanupFailed;
    private enum CleanupResult { NOT_REQUIRED,CONFIRMED_LOCAL,REFUSED,UNCONFIRMED,FAILED }
    private record KeyCleanup(net.minecraft.client.KeyMapping key,boolean before,CleanupResult result,dev.veltraph.client.service.mc.McHeldKeys.ScopedHeldReceipt nativeReceipt) {}
    /** Only this native effect producer can mint completion; it does not grant new admission. */
    private final class OwnedCleanupReceipt {
        private final LegacyTravelPublicationLease.RetiredParentInteraction interaction;
        private final java.util.function.BooleanSupplier scope;private final List<KeyCleanup> keys;
        private final McRotationService.DirectCancellationReceipt cancellation;
        private final PestInventoryLease.OwnedRestoreReceipt inventory;
        private final CleanupResult camera,menu,slot;private final boolean helperClean,finalFence;
        private OwnedCleanupReceipt(LegacyTravelPublicationLease.RetiredParentInteraction interaction,java.util.function.BooleanSupplier scope,
                List<KeyCleanup> keys,McRotationService.DirectCancellationReceipt cancellation,PestInventoryLease.OwnedRestoreReceipt inventory,
                CleanupResult camera,CleanupResult menu,CleanupResult slot,boolean helperClean,boolean finalFence){
            this.interaction=interaction;this.scope=scope;this.keys=List.copyOf(keys);this.cancellation=cancellation;this.inventory=inventory;
            this.camera=camera;this.menu=menu;this.slot=slot;this.helperClean=helperClean;this.finalFence=finalFence;
        }
        private boolean confirmed(CleanupResult result){return result==CleanupResult.NOT_REQUIRED||result==CleanupResult.CONFIRMED_LOCAL;}
        boolean localComplete(){return helperClean&&finalFence&&keys.size()==9&&keys.stream().allMatch(k->k.result()==CleanupResult.CONFIRMED_LOCAL)
            &&confirmed(camera)&&confirmed(menu)&&confirmed(slot)&&(inventory==null||inventory.localComplete());}
        boolean current(){return localComplete()&&cleanupFence(scope)&&keys.stream().allMatch(k->!k.key().isDown())
            &&(cancellation==null||cancellation.cleanupCurrent(!isEnabled()))&&(inventory==null||inventory.currentLocal(scope));}
        boolean restartConfirmed(){return current()&&(inventory==null||inventory.restartConfirmed());}
        @Override public String toString(){return "owned cleanup keys="+keys.stream().map(KeyCleanup::result).toList()+", camera="+camera
            +", menu="+menu+", slot="+slot+", inventory="+inventory+", helper="+helperClean+", final="+finalFence;}
    }
    public McPestVacuumModule(Minecraft c,FarmServices s,ConfigService cfg){super(ID,"Vacuum loaded Garden pests",c,s,cfg);flight=new McFlightNavigator(c,s.input(),cfg);pestGround=new McScopedGroundSharedMovementNavigator(c,s,cfg);publications=new LegacyTravelPublicationLease(c,s,cfg,()->cfg.revision(PestConfig.PEST_CLEANER_BINDINGS).orElse(-1));}
    protected boolean optedIn(){return config.get(PestConfig.PEST_CLEANER_ENABLED);}
    protected Optional<String> ready(){b=PestBindings.read(config.get(PestConfig.PEST_CLEANER_BINDINGS),PestBindings.Cleaner.class);
        if(client.gui.screen()!=null||PestItems.uniqueSlot(client,b.vacuum())<0||PestItems.counter(client,b.vacuum(),b.bagPrefix()).isEmpty())return Optional.of("Closed menus and one identified vacuum with readable bag count required");
        var selected=candidates();return selected.isEmpty()?Optional.of("No exact loaded pest models inside selected plot"):selected.size()>b.maxPests()?Optional.of("Selected pest model count exceeds the bounded batch budget"):Optional.empty();}
    protected boolean screenAllowed(String title){return false;}
    private List<Entity> candidates(){var result=new ArrayList<Entity>();for(var e:client.level.entitiesForRendering())if(PestItems.entityMatches(e,b.pest())&&GardenPlotBounds.contains(plot(),e.getX(),e.getZ())&&e.distanceToSqr(client.player)<32*32)result.add(e);
        result.sort(Comparator.comparingDouble(e->e.distanceToSqr(client.player)));return result;}
    protected void begin(){if(preparedStart!=null){preparedReady=true;return;}if(!freshJourneyAllowed()||!stopFlight(false)||!ensureParentInteraction())return;bag=PestItems.counter(client,b.vacuum(),b.bagPrefix()).orElseThrow();removed=0;ack=held=flying=false;target=null;phase=Phase.RELOCATE;
        int slot=config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT);if(!parentInteraction.current())return;
        boolean begun=lease.begin(b.vacuum(),slot);inventoryCleanupHandle=lease.cleanupHandle();if(!parentInteraction.current())return;
        if(!begun)finish(Phase.FAILED,"Vacuum relocation refused");}
    protected void advance(){
        if(retirementBlocked||handoffRefused)return;
        if(client.gui.screen()!=null){finish(Phase.CANCELLED,"Screen opened while cleaning pests");return;}
        if(phase==Phase.RELOCATE){if(!ensureParentInteraction())return;boolean ready=lease.readyOwned(inventoryCleanupHandle,parentInteraction::current);if(!parentInteraction.current())return;if(ready)phase=Phase.AIM;return;}
        if(phase==Phase.WAIT_ACK){
            if(!lease.activeTool()){if(!useOwned(false))return;finish(Phase.CANCELLED,"Selected vacuum tool changed during use hold");return;}
            int current=PestItems.counter(client,b.vacuum(),b.bagPrefix()).orElse(-1);boolean gone=client.level.getEntity(target.getId())!=target||!target.isAlive();
            if(gone){if(!useOwned(false))return;held=false;}
            else if(!PestItems.entityMatches(target,b.pest())||owner.distanceTo(target)>b.reach()||!owner.hasLineOfSight(target)){
                if(!useOwned(false))return;held=false;finish(Phase.CANCELLED,"Pest identity/range/line of sight changed during use hold");return;
            }else{if(!useOwned(aim(target)))return;}
            int vanished=(int)burst.stream().filter(e->client.level.getEntity(e.getId())!=e||!e.isAlive()).count();
            if(current>bag+b.maxPests()-removed||current<bag){finish(Phase.FAILED,"Vacuum bag changed by unexpected amount");return;}
            if(ack&&gone&&vanished>0&&current==bag+vanished){if(!useOwned(false))return;held=false;bag=current;removed+=vanished;target=null;burst=List.of();phase=Phase.AIM;
                if(removed>=b.maxPests()||candidates().isEmpty())finish(Phase.COMPLETE,"Verified "+removed+" pest removals and exact bag increases");return;}
            if(now()>=holdUntil){if(!useOwned(false))return;held=false;finish(Phase.FAILED,"Pest capture/effect acknowledgment withheld; stopped bounded use hold");}return;
        }
        if(target==null){var options=candidates();if(options.isEmpty()){finish(Phase.COMPLETE,"All selected loaded pests removed: "+removed);return;}target=options.getFirst();}
        if(!PestItems.entityMatches(target,b.pest())||client.level.getEntity(target.getId())!=target){finish(Phase.CANCELLED,"Selected pest identity changed before use");return;}
        if(owner.distanceTo(target)>b.reach()||!owner.hasLineOfSight(target)){
            if(owner.getAbilities().mayfly){if(!flying){var p=target.position();flying=startFlight(new dev.veltraph.client.world.Vec3(p.x-1,p.y,p.z));if(!flying){finish(Phase.FAILED,"No permitted loaded flight route to pest");return;}}
                var state=flight.tick();if(state==FlightController.State.BLOCKED){finish(Phase.FAILED,"Pest flight blocked");return;}if(state==FlightController.State.ARRIVED){if(!stopFlight())return;}return;}
            approach(target);return;
        }
        if(!stopFlight()||!handoffGround())return;if(!aim(target))return;
        if((client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit&&entityHit.getEntity()!=target)
                ||(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit&&client.level.getBlockEntity(blockHit.getBlockPos())!=null)){
            finish(Phase.FAILED,"Use ray intersects an unrelated entity or interactive block; move to an open pest approach");return;
        }
        ack=false;burst=List.copyOf(candidates());if(burst.size()>b.maxPests()-removed||!lease.activeTool()){finish(Phase.FAILED,"Vacuum batch identity/budget changed before source hold");return;}
        phase=Phase.WAIT_ACK;holdUntil=now()+b.holdMs();
        if(!useOwned(true))return;held=true;reason="Bounded source right-click hold; awaiting same pest removal and bag increase";
    }
    @Override public void onServerMessage(String line){super.onServerMessage(line);if(isEnabled()&&phase==Phase.WAIT_ACK&&b.captureAck().equals(dev.veltraph.client.game.LocationText.stripColorCodes(line)))ack=true;}
    private boolean stopFlight(){return stopFlight(true);}
    private boolean stopFlight(boolean handoff){var old=flightLeg;RuntimeException stopFailure=null;boolean retireFailure=false;
        try{flight.stop();}catch(RuntimeException failed){stopFailure=failed;}
        finally{try{if(old!=null){try{motionRetirement=old.retireOwned();}catch(RuntimeException failed){retireFailure=true;motionRetirement=old.retirementOutcome();
                    if(motionRetirement==null)motionRetirement=LegacyTravelRetirement.notRetired(true,true);retirementBlocked=true;}}}
            finally{if(old!=null){retiredInteraction=old::retiredParentInteraction;parentInteraction=null;}if(old!=null)retiredCleanupProof=old::retiredCleanupParentContext;if(old!=null)retiredStopProof=old::postRetirementOwnParentContext;if(flightLeg==old){flightLeg=null;flying=false;}}}
        if(stopFailure!=null||retireFailure||motionRetirement!=null&&!motionRetirement.cleanSuccess()){
            retirementBlocked=true;retirementDiagnostic="MISMATCH: flight retirement "+motionRetirement;
            phase=Phase.FAILED;reason=retirementDiagnostic;
            if(old!=null&&old.postRetirementOwnParentContext()!=null)throw new IllegalStateException(retirementDiagnostic);
        }
        if(!retirementBlocked&&handoff&&old!=null&&old.retirementContinuationContext()==null){
            handoffRefused=true;retirementDiagnostic="REVOKED: CLEAN flight retirement cannot authorize this handoff";phase=Phase.FAILED;reason=retirementDiagnostic;
            if(retiredCleanupProof.get()==null){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed flight Parent scope superseded";phase=Phase.FAILED;reason=retirementDiagnostic;}
        }
        return !retirementBlocked&&(!handoff||!handoffRefused);}
    private boolean freshJourneyAllowed(){if(retirementBlocked||ownedCleanup!=null||cleanupInProgress||preparedStart!=null)return false;
        if(!sameCapturedParentCurrent()){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed flight Parent changed before fresh journey";phase=Phase.FAILED;reason=retirementDiagnostic;return false;}
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
    private boolean startFlight(dev.veltraph.client.world.Vec3 destination){if(normalCleanupTransaction!=null||ownedCleanup!=null||cleanupInProgress)return false;if(!stopFlight()||!stopGround())return false;
        if(!isEnabled()||!ownsGameplayInput()||client.player!=owner||client.level!=level)return false;
        flightLeg=publications.beginExpected(++flightJourney,this);if(flightLeg==null)return false;if(capturedParentContext==null)capturedParentContext=flightLeg.commandContext();parentInteraction=null;
        boolean started=flight.start(destination,java.util.concurrent.ThreadLocalRandom.current().nextLong(),flightLeg);
        if(!started||!flightLeg.current()){stopFlight();return false;}return true;}
    @Override public void onDisable(){
        if(cleanupInProgress)return;
        var pending=preparedStart;long disableSourceVersion=sourceVersion();
        if(pending!=null&&initialAdmission==null&&flightLeg==null&&!pestGround.hasLeg()&&(motionRetirement==null||normalCleanupTransaction!=null&&normalCleanupSourceVersion!=sourceVersion())){
            // This intent never admitted or published: no effect cleanup and no owned receipt is invented.
            if(preparedStart==pending){preparedStart=null;preparedReady=false;target=null;burst=List.of();held=flying=pestWalking=false;
                if(owner==pending.player&&level==pending.world){owner=null;level=null;ownedMenu=null;menus.clear();}}
            if(phase!=Phase.COMPLETE&&phase!=Phase.FAILED&&phase!=Phase.CANCELLED){phase=Phase.CANCELLED;reason="Manual stop before owned admission";}
            return;
        }
        cleanupInProgress=true;
        try{var initial=initialAdmission;
            if(initial!=null)try{motionRetirement=initial.retireOwned();if(!motionRetirement.cleanSuccess())retirementBlocked=true;}
                catch(RuntimeException failure){helperCleanupFailed=true;retirementBlocked=true;}
                finally{retiredCleanupProof=initial::retiredCleanupParentContext;retiredStopProof=initial::postRetirementOwnParentContext;retiredInteraction=initial::retiredParentInteraction;if(initialAdmission==initial)initialAdmission=null;}
            try{stopFlight(false);}catch(RuntimeException failure){helperCleanupFailed=true;}
            finally{try{stopGround();}catch(RuntimeException failure){helperCleanupFailed=true;}}
            super.onDisable();
        }finally{cleanupInProgress=false;if(sourceVersion()==disableSourceVersion){
            if(preparedStart==pending){preparedStart=null;preparedReady=false;}target=null;burst=List.of();held=false;if(flightLeg==null)flying=false;pestWalking=false;
        }}
    }
    /** Enable prepares the exact intended Parent/scene. Root owns lock acquisition before its ordinary tick. */
    @Override protected boolean childInitializationHandled(){
        if(normalCleanupTransaction!=null){
            var intent=preparedStart;
            if(intent!=null&&intent.restartTransaction==normalCleanupTransaction&&intent.restartEnable==observedRestartEnable
                    &&intent.restartToken==normalCleanupToken&&intent.current())return true;
            handoffRefused=true;retirementDiagnostic="RESTART_UNKNOWN: exact pre-reset own-enable intent unavailable";return true;
        }
        if(normalCleanupTransaction!=null||ownedCleanup!=null||cleanupInProgress||parentInteraction!=null||motionRetirement!=null||preparedStart!=null){
            handoffRefused=true;retirementDiagnostic="RESTART_UNCONFIRMED: prior intent needs actual owned successor";return true;
        }
        try{
            var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
            if(lifecycle==null||!lifecycle.known()||root.get(ID).orElse(null)!=this){retirementBlocked=true;retirementDiagnostic="MISMATCH: prepared Parent unavailable";return true;}
            var intent=new PreparedStart(root);
            if(!intent.current()||!root.lifecycleCurrent(lifecycle)){retirementBlocked=true;retirementDiagnostic="MISMATCH: prepared Parent changed during read";return true;}
            preparedStart=intent;preparedReady=false;
        }catch(RuntimeException unavailable){retirementBlocked=true;retirementDiagnostic="MISMATCH: Parent preparation refused";}
        return true;
    }
    /** Original deadline/config/PWC/plot/contest guards have already passed before this callback. */
    @Override protected boolean childTickPrepared(){
        var pending=preparedStart;if(pending==null)return true;
        if(retirementBlocked||handoffRefused)return false;
        if(!preparedReady||!pending.current())return false;
        if(!activatePreparedStart())return false;
        if(preparedStart!=pending)return false;
        preparedStart=null;preparedReady=false;begin();
        return isEnabled()&&!retirementBlocked&&!handoffRefused&&ownedCleanup==null;
    }
    /** First native admission requires Root's real lock through unchanged beginExpected. */
    private boolean activatePreparedStart(){
        if(normalCleanupTransaction!=null){
            var enable=observedRestartEnable;var token=normalCleanupToken;
            if(enable==null||!enable.current()||token==null){handoffRefused=true;retirementDiagnostic="RESTART_UNKNOWN: stopped native cleanup/token not confirmed";return false;}
            var successor=publications.beginAfterParentCleanup(++flightJourney,this,token,enable);
            if(successor==null){handoffRefused=true;retirementDiagnostic="RESTART_UNKNOWN: actual producer denied native reentry policy/control";return false;}
            // Do not adopt raw current context. A future approved producer must return its real admitted Leg.
            initialAdmission=successor;
            if(!token.consumed()||!successor.current()){retirementBlocked=true;retirementDiagnostic="MISMATCH: actual successor not consumed/current";return false;}
            capturedParentContext=successor.commandContext();
            try{motionRetirement=successor.retireOwned();}
            finally{retiredCleanupProof=successor::retiredCleanupParentContext;retiredStopProof=successor::postRetirementOwnParentContext;retiredInteraction=successor::retiredParentInteraction;
                if(successor.retirementOutcome()!=null&&initialAdmission==successor)initialAdmission=null;}
            if(motionRetirement==null||!motionRetirement.cleanSuccess()){retirementBlocked=true;retirementDiagnostic="MISMATCH: successor initial retirement";return false;}
            parentInteraction=retiredInteraction.get();if(parentInteraction==null||!parentInteraction.current()){retirementBlocked=true;retirementDiagnostic="MISMATCH: successor actual interaction unavailable";return false;}
            // This branch is unreachable while the actual G7 source explicitly refuses reentry.
            normalCleanupTransaction=null;normalCleanupToken=null;observedRestartEnable=null;actualStoppedReceipt=null;normalCleanupSourceVersion=-1;
            normalKeyOutcome=null;normalCameraOutcome=null;normalInventoryOutcome=null;handoffRefused=false;
        }
        if(normalCleanupTransaction!=null||ownedCleanup!=null||cleanupInProgress){handoffRefused=true;retirementDiagnostic="RESTART_UNKNOWN: actual G7 native reentry proof is not yet available";return false;}
        if(parentInteraction==null&&motionRetirement==null){
            var first=publications.beginExpected(++flightJourney,this);
            if(first==null){retirementBlocked=true;retirementDiagnostic="MISMATCH: initial Parent admission refused";return false;}
            initialAdmission=first;capturedParentContext=first.commandContext();
            try{motionRetirement=first.retireOwned();}
            finally{retiredCleanupProof=first::retiredCleanupParentContext;retiredStopProof=first::postRetirementOwnParentContext;retiredInteraction=first::retiredParentInteraction;
                if(first.retirementOutcome()!=null&&initialAdmission==first)initialAdmission=null;}
            if(motionRetirement==null||!motionRetirement.cleanSuccess()){retirementBlocked=true;retirementDiagnostic="MISMATCH: initial Parent retirement refused";return false;}
            parentInteraction=retiredInteraction.get();
        }
        if(parentInteraction==null||!parentInteraction.current()){retirementBlocked=true;retirementDiagnostic="MISMATCH: initial Parent proof unavailable";return false;}
        var initialKeys=new ArrayList<KeyCleanup>();var exact=parentInteraction;
        if(client.options!=null)for(var key:new net.minecraft.client.KeyMapping[]{client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,
                client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse})initialKeys.add(releaseKeyOwned(key,exact::cleanupCurrent));
        initialKeyCleanup=List.copyOf(initialKeys);
        if(initialKeyCleanup.size()!=9||initialKeyCleanup.stream().anyMatch(k->k.result()!=CleanupResult.CONFIRMED_LOCAL)||!exact.current()){
            retirementBlocked=true;retirementDiagnostic="MISMATCH: initial owned key cleanup incomplete";
        }
        return !retirementBlocked&&parentInteraction!=null&&parentInteraction.current();
    }
    /** Handles both success and refusal. The Parent must never execute broad cleanup afterward. */
    @Override protected boolean childCleanupHandled(){
        var normal=normalCleanupTransaction;
        if(normal!=null){if(normalCleanupSourceVersion!=sourceVersion())return true;var oldOwner=owner;var oldLevel=level;var oldMenu=ownedMenu;var oldHandle=inventoryCleanupHandle;long version=sourceVersion();
            try{
                if(isEnabled()){handoffRefused=true;retirementDiagnostic="UNKNOWN: normal cleanup requires actual disabled parent";return true;}
                normalKeyOutcome=normal.releaseKeysOwned();
                normalCameraOutcome=normal.cancelCameraOwned();
                normalInventoryOutcome=normal.restoreInventoryOwned();
                // C9 captured actual NULL input/camera roles before and after its real disabled cleanup issue.
                if(normalKeyOutcome==null||!normalKeyOutcome.confirmed()||normalCameraOutcome==null||!normalCameraOutcome.confirmed()||normalInventoryOutcome==null){
                    handoffRefused=true;retirementDiagnostic="MISMATCH: "+normal.diagnostic();
                }else retirementDiagnostic="NORMAL_CLEANUP_PENDING: "+normal.diagnostic();
            }catch(RuntimeException failed){helperCleanupFailed=true;handoffRefused=true;normal.abort(LegacyTravelPublicationLease.CleanupAbort.UNKNOWN);
                retirementDiagnostic="UNKNOWN: "+normal.diagnostic();}
            finally{if(normalCleanupTransaction==normal&&sourceVersion()==version){
                if(inventoryCleanupHandle==oldHandle)inventoryCleanupHandle=null;
                if(owner==oldOwner&&level==oldLevel&&ownedMenu==oldMenu){owner=null;level=null;ownedMenu=null;menus.clear();}
            }}
            return true; // All native effects belong to the concrete transaction, including partial/refused outcomes.
        }
        if(ownedCleanup!=null)return true;
        var exactInteraction=parentInteraction;var exactInventoryHandle=inventoryCleanupHandle;
        var exactRetiredCleanup=retiredCleanupProof;
        java.util.function.BooleanSupplier scope=exactInteraction!=null?exactInteraction::cleanupCurrent:()->exactRetiredCleanup.get()!=null;
        var keyResults=new ArrayList<KeyCleanup>();McRotationService.DirectCancellationReceipt cancellation=null;
        PestInventoryLease.OwnedRestoreReceipt inventory=null;
        CleanupResult camera=CleanupResult.REFUSED,menu=ownedMenu==null?CleanupResult.NOT_REQUIRED:CleanupResult.REFUSED;
        CleanupResult slot=originalSlot<0?CleanupResult.NOT_REQUIRED:CleanupResult.REFUSED;
        boolean finalFence=false;var oldOwner=owner;var oldLevel=level;var oldMenu=ownedMenu;long cleanupSourceVersion=sourceVersion();
        boolean helperClean=!helperCleanupFailed&&!retirementBlocked&&(motionRetirement==null||motionRetirement.cleanSuccess());
        try{
            if(client.options!=null){
                for(var key:new net.minecraft.client.KeyMapping[]{client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,
                        client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse})
                    keyResults.add(releaseKeyOwned(key,scope));
            }
            if(cleanupFence(scope)){
                if(exactInteraction!=null&&exactInteraction.ownPublicationReceipt()!=null){
                    cancellation=exactInteraction.cancelOwnedCamera(!isEnabled());
                    camera=cancellation!=null&&cleanupFence(scope)?CleanupResult.CONFIRMED_LOCAL:CleanupResult.UNCONFIRMED;
                }else if(helperClean&&cleanupFence(scope))camera=CleanupResult.NOT_REQUIRED; // Fresh actual no-effect or clean helper retirement.
            }
            if(exactInventoryHandle!=null)inventory=services.rotation() instanceof McRotationService rotation
                ?lease.restoreOwned(exactInventoryHandle,scope,this,rotation):lease.restoreOwned(exactInventoryHandle,scope); // Refusal still retires only this exact old logical lease.
            if(cleanupFence(scope)&&oldMenu!=null&&oldOwner!=null&&client.player==oldOwner&&client.level==oldLevel&&oldOwner.containerMenu==oldMenu){
                oldOwner.closeContainer();
                // A GUI/provider transition has no opaque owned transition certificate in this stage.
                menu=CleanupResult.UNCONFIRMED;
            }
            if(originalSlot>=0&&cleanupFence(scope)&&client.player==oldOwner&&client.level==oldLevel&&oldOwner!=null
                    &&oldOwner.getInventory().getSelectedSlot()==originalSlot){
                slot=CleanupResult.CONFIRMED_LOCAL; // Exact inventory receipt restored it; no duplicate broad setter.
            }
            finalFence=cleanupFence(scope);
        }catch(RuntimeException failure){retirementDiagnostic="MISMATCH: owned Parent cleanup threw";}
        finally{
            var produced=new OwnedCleanupReceipt(exactInteraction,scope,keyResults,cancellation,inventory,camera,menu,slot,helperClean,finalFence);
            if(sourceVersion()==cleanupSourceVersion)ownedCleanup=produced;
            if(sourceVersion()==cleanupSourceVersion&&inventoryCleanupHandle==exactInventoryHandle)inventoryCleanupHandle=null;
            if(sourceVersion()==cleanupSourceVersion&&owner==oldOwner&&level==oldLevel&&ownedMenu==oldMenu){owner=null;level=null;ownedMenu=null;menus.clear();}
            if(sourceVersion()!=cleanupSourceVersion)retirementDiagnostic="UNKNOWN: original cleanup logical scope superseded";
            else if(!produced.localComplete())retirementDiagnostic="MISMATCH: "+produced;
            else if(!produced.restartConfirmed())retirementDiagnostic="UNCONFIRMED: "+produced;
            else retirementDiagnostic="CLEAN_LOCAL: "+produced+"; native successor admission not yet consumed";
            if(sourceVersion()==cleanupSourceVersion&&phase!=Phase.COMPLETE&&phase!=Phase.FAILED&&phase!=Phase.CANCELLED){phase=Phase.CANCELLED;reason="Manual stop";}
        }
        return true;
    }
    private boolean cleanupFence(java.util.function.BooleanSupplier scope){try{return client.isSameThread()&&scope!=null&&scope.getAsBoolean();}catch(RuntimeException failure){return false;}}
    /** Shared producer fences every native key/toggle effect after its existing observer callback. */
    private KeyCleanup releaseKeyOwned(net.minecraft.client.KeyMapping key,java.util.function.BooleanSupplier scope){
        boolean before=key!=null&&key.isDown();if(key==null||!cleanupFence(scope))return new KeyCleanup(key,before,CleanupResult.REFUSED,null);
        dev.veltraph.client.service.mc.McHeldKeys.ScopedHeldReceipt nativeReceipt=null;
        try{
            nativeReceipt=dev.veltraph.client.service.mc.McHeldKeys.setHeldScoped(key,false,scope);
            CleanupResult result=nativeReceipt!=null&&nativeReceipt.confirmed()&&cleanupFence(scope)&&!key.isDown()?CleanupResult.CONFIRMED_LOCAL:CleanupResult.UNCONFIRMED;
            return new KeyCleanup(key,before,result,nativeReceipt);
        }catch(RuntimeException failure){return new KeyCleanup(key,before,CleanupResult.FAILED,nativeReceipt);}
    }
    /** Closed evidence is read-only; every later Parent stop rechecks the actual Root/admission fence. */
    @Override protected boolean lifecycleStopAllowed(){try{if(ownedCleanup!=null||cleanupInProgress)return false;if(preparedStart!=null&&initialAdmission==null&&flightLeg==null&&!pestGround.hasLeg()&&(motionRetirement==null||normalCleanupTransaction!=null&&normalCleanupSourceVersion!=sourceVersion()))return preparedStart.current();if(initialAdmission!=null)return initialAdmission.cleanupCurrent();if(flightLeg!=null)return flightLeg.cleanupCurrent();
            if(pestGround.hasLeg())return pestGround.cleanupCurrent();
            if(retirementBlocked)return retiredStopProof.get()!=null;
            if(parentInteraction!=null)return parentInteraction.cleanupCurrent();
            if(motionRetirement!=null)return retiredCleanupProof.get()!=null;
            return !handoffRefused&&sameCapturedParentCurrent();}catch(RuntimeException unavailable){return false;}}
    @Override protected boolean lifecycleCleanupAllowed(){try{if(flightLeg!=null)return flightLeg.cleanupCurrent();
            if(pestGround.hasLeg())return pestGround.cleanupCurrent();
            if(parentInteraction!=null)return parentInteraction.cleanupCurrent();
            if(motionRetirement!=null)return retiredCleanupProof.get()!=null;
            return !retirementBlocked&&!handoffRefused&&sameCapturedParentCurrent();}catch(RuntimeException unavailable){return false;}}
    /** The first Parent view receives a real no-effect admission; later views consume the exact retired flight. */
    private boolean ensureParentInteraction(){
        if(normalCleanupTransaction!=null||retirementBlocked||handoffRefused||ownedCleanup!=null||cleanupInProgress||flightLeg!=null||pestGround.hasLeg())return false;
        if(parentInteraction!=null)return parentInteraction.current();
        if(motionRetirement!=null){parentInteraction=retiredInteraction.get();return parentInteraction!=null&&parentInteraction.current();}
        if(!isEnabled()||!ownsGameplayInput()||client.player!=owner||client.level!=level||!sameCapturedParentCurrent())return false;
        var initial=publications.beginExpected(++flightJourney,this);if(initial==null)return false;
        var context=initial.commandContext();if(capturedParentContext==null)capturedParentContext=context;
        try{motionRetirement=initial.retireOwned();}
        finally{retiredCleanupProof=initial::retiredCleanupParentContext;retiredStopProof=initial::postRetirementOwnParentContext;retiredInteraction=initial::retiredParentInteraction;}
        if(!motionRetirement.cleanSuccess()){retirementBlocked=true;retirementDiagnostic="MISMATCH: initial Parent interaction retirement "+motionRetirement;phase=Phase.FAILED;reason=retirementDiagnostic;
            if(initial.postRetirementOwnParentContext()!=null)throw new IllegalStateException(retirementDiagnostic);return false;}
        parentInteraction=retiredInteraction.get();return parentInteraction!=null&&parentInteraction.current();
    }
    /** Original cardinal ground approach with its bounded varied ground policy and exact Parent. */
    @Override protected boolean approach(Entity selected){
        if(selected==null||client.level.getEntity(selected.getId())!=selected||!selected.isAlive()||client.gui.screen()!=null)return false;
        if(owner.distanceTo(selected)>2.8||!owner.hasLineOfSight(selected)){
            if(!pestWalking){
                if(!ensureParentInteraction())return false;
                var block=selected.blockPosition();
                for(int[] off:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    if(!parentInteraction.current())return false;
                    var goal=pestGround.position(new dev.veltraph.client.world.BlockPos(block.getX()+off[0],block.getY(),block.getZ()+off[1]));
                    if(!parentInteraction.current())return false;
                    if(pestGround.startExpected(goal,2500,32,.35,SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL,this)){
                        pestWalking=true;parentInteraction=null;break;
                    }
                    if(pestGround.retirementRefused()){stopGround();return false;}
                    // The failed cardinal candidate's actual clean Leg supplies the next view scope.
                    if(!handoffGround())return false;
                }
                if(!pestWalking){finish(Phase.FAILED,"No loaded safe path to Garden entity");return false;}
            }
            var status=pestGround.tick();
            if(status==dev.veltraph.client.navigation.mc.McTravelNavigator.State.FAILED){stopGround();finish(Phase.FAILED,"Garden approach path blocked");return false;}
            if(status==dev.veltraph.client.navigation.mc.McTravelNavigator.State.ARRIVED)pestWalking=false;
            return false;
        }
        if(!handoffGround())return false;pestWalking=false;return aim(selected);
    }
    private boolean stopGround(){if(!pestGround.hasLeg())return !retirementBlocked;try{pestGround.stop();}
        finally{pestWalking=false;var outcome=pestGround.groundRetirementOutcome();if(outcome!=null){motionRetirement=outcome;
            retiredCleanupProof=pestGround::retiredCleanupParentContext;retiredStopProof=pestGround::postRetirementOwnParentContext;parentInteraction=null;}}
        if(pestGround.retirementRefused()){retirementBlocked=true;retirementDiagnostic="MISMATCH: scoped pest ground retirement "+motionRetirement;phase=Phase.FAILED;reason=retirementDiagnostic;return false;}
        return !retirementBlocked;
    }
    private boolean handoffGround(){if(!pestGround.hasLeg())return !retirementBlocked&&!handoffRefused;
        if(!pestGround.retireForHandoff()){stopGround();handoffRefused=true;retirementDiagnostic="REVOKED: scoped ground handoff unavailable";return false;}
        var actual=pestGround.retiredContinuationLeg();if(actual==null){handoffRefused=true;return false;}
        motionRetirement=actual.retirementOutcome();retiredCleanupProof=actual::retiredCleanupParentContext;retiredStopProof=actual::postRetirementOwnParentContext;retiredInteraction=actual::retiredParentInteraction;
        parentInteraction=retiredInteraction.get();pestWalking=false;return parentInteraction!=null&&parentInteraction.current();
    }
    /** Original Parent geometry, native tracking smoother, same-tick behavior and alignment bounds. */
    @Override protected boolean aim(Entity selected){if(!ensureParentInteraction()||!(services.rotation() instanceof McRotationService rotation))return false;
        var delta=selected.getBoundingBox().getCenter().subtract(owner.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        if(!parentInteraction.current())return false;
        var receipt=rotation.trackWithReceipt(yaw,pitch,parentInteraction.publicationFence());
        if(!parentInteraction.advance(receipt)||!parentInteraction.current()){retirementDiagnostic="REVOKED: Parent tracking publication refused";return false;}
        return Math.abs(WalkController.wrap(owner.getYRot()-yaw))<2&&Math.abs(owner.getXRot()-pitch)<2;
    }
    /** Refused false writes cannot release a newer owner's held use. No cleanup is inferred from a stale CLEAN outcome. */
    private boolean useOwned(boolean down){var own=parentInteraction;
        try{if(own==null||!(down?own.current():own.cleanupCurrent())){retirementDiagnostic="REVOKED: vacuum use custody unavailable";return false;}
            var receipt=dev.veltraph.client.service.mc.McHeldKeys.setHeldScoped(client.options.keyUse,down,down?own::current:own::cleanupCurrent);
            if(receipt==null||!receipt.confirmed()||!(down?own.current():own.cleanupCurrent())){retirementDiagnostic="REVOKED: vacuum use custody changed during native setter";return false;}return true;
        }catch(RuntimeException unavailable){retirementDiagnostic="MISMATCH: vacuum use setter refused";return false;}}

    @Override public String diagnostics(){return super.diagnostics()+" | "+retirementDiagnostic;}
}
