package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.config.*;
import dev.veltraph.client.combat.grind.*;
import dev.veltraph.client.combat.heal.SkyBlockHealth;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/** Dedicated Ghost route/search/combat/loot/heal/return workflow, using ordinary client actions. */
public final class GhostGrindModule extends AbstractModule {
    public static final String ID="ghost_grind";
    private static final List<ConfigKey<?>> SOURCE_PARENT_DECLARED_KEYS=List.copyOf(PersistableKeys.collect(
            List.of(GhostGrindConfig.class,GhostConfig.class,RotationConfig.class,TravelConfig.class)));
    private record Target(LivingEntity entity,String name,boolean protectedTarget) {}
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final LocalRouteStore store;
    private final java.util.function.BooleanSupplier externalHealOwnership;
    private McTravelNavigator routeTravel;private McNavigator walk;
    private final CombatScheduledPause scheduledPause;
    private final CombatAim aim=new CombatAim();private final CombatMovement movement=new CombatMovement();
    private final AngularRateLimiter yawLimit=new AngularRateLimiter(),pitchLimit=new AngularRateLimiter();
    private final TravelHeading healPitch=new TravelHeading();private final GhostHealReturn recovery=new GhostHealReturn();
    private boolean capturedHudRequiredAtStart;
    private dev.veltraph.client.combat.heal.HealHealthObservationBasis healHealthBasis;
    private final GhostHealDirectReturn healReturnDriver=new GhostHealDirectReturn();
    private Map<dev.veltraph.client.config.ConfigKey<?>,Object> healReturnConfig=Map.of();
    private static final List<dev.veltraph.client.config.ConfigKey<?>> HEAL_RETURN_KEYS=List.of(GhostGrindConfig.GHOST_GRIND_HEAL_SLOT,GhostGrindConfig.GHOST_GRIND_HEAL_TIMEOUT_MS,GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS,RotationConfig.ROTATION_SPEED_MULTIPLIER,GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW,GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS,GhostConfig.GHOST_CAPTURED_HUD_REQUIRED,GhostGrindConfig.GHOST_GRIND_HEAL);
    private final SkyBlockHealth health=new SkyBlockHealth();private GhostLedger ledger=new GhostLedger();
    private final Map<UUID,Long> blacklist=new HashMap<>();private final Map<Integer,Long> ignoredDrops=new HashMap<>();
    private String preflightBinding="";private GhostPreflightProfile preflightProfile;private GhostNativePreflight preflight;private long lastBuff=Long.MIN_VALUE/2;private boolean grindStarted;
    private List<LocalRoute> routes=List.of();private LocalRoute route;private int checkpoint,originalSlot,dropId=-1,fallbackCheckpoint=-1;
    private LivingEntity target;private Object owner,world;private ItemStack healItem=ItemStack.EMPTY;
    private boolean routeActive,walkActive,settling,inventoryReturn,lifecycleOwned;private Vec3 walkGoal;private long nextPath,dropDeadline,lastHeal,lastAttack;
    private int heals,returns,routeArrivals;private String dropName,status="Configure a local Ghost route";
    // Source base constructor explicitly initializes its nullable area property to null.
    private GhostSourceParentAreaResource sourceMappedArea;
    private final GhostSourceOwnedParentArea sourceParentArea=new GhostSourceOwnedParentArea(this);
    private final GhostNativeSearchPhase sourceSearchChild=new GhostNativeSearchPhase(this);
    private McGhostSourceSearchJumpBoost sourceJumpBoostHelper;
    // Registered actual issued work remains independent of committed selection/SEARCH receipt.
    private final McGhostSourcePressureLifecycle sourcePressureLifecycle;
    private GhostSourceWalkJob sourceIssuedWalkJob;private GhostSourceIssuedSearchJob.Receipt sourceIssuedWalkReceipt;
    private McGhostSourceOwnedWalkCursor sourceInstalledWalkCursor;
    private GhostSourceWalkSteering sourceWalkSteering=new GhostSourceWalkSteering();
    private final GhostConstrainedMeleeStandoff sourceMeleeStandoff=new GhostConstrainedMeleeStandoff();
    public record StandoffBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){}
    public record StandoffFootCorner(int blockX,int blockY,int blockZ,double cornerX,double cornerZ,boolean loaded,
        String nativeBlock,boolean fluid,java.util.List<StandoffBox> localCollisionBoxes,boolean boxesTruncated,Double observedTop,boolean contact){
        public StandoffFootCorner{localCollisionBoxes=java.util.List.copyOf(localCollisionBoxes);}}
    public record StandoffHit(String type,boolean evaluated,boolean selectedTarget,Double distance){}
    public record StandoffDecisionDiagnostics(long serial,long tick,java.util.UUID targetUuid,
        GhostConstrainedMeleeStandoff.Frame frame,GhostConstrainedMeleeStandoff.Observation policyObservation,GhostConstrainedMeleeStandoff.Evidence result,
        boolean targetOnGround,boolean targetNoAi,StandoffBox targetBox,float targetHealth,java.util.List<StandoffFootCorner> targetFootCorners,
        boolean leftBodyClear,Boolean leftSupportClear,boolean rightBodyClear,Boolean rightSupportClear,boolean standingBodyClear,Boolean standingSupportClear,
        StandoffHit currentHit,StandoffHit freshHit,int playerHurtTime,Integer targetSwell,Boolean targetIgnited,
        boolean inputOwnerIsParent,boolean cameraOwnerIsParent){}
    public record StandoffAttackDiagnostics(long decisionSerial,long tick,java.util.UUID targetUuid,float beforeClientHealth,float afterClientHealth,
        Vec3 rawMotionBefore,Vec3 rawMotionAfter,int targetHurtTime){}
    private StandoffDecisionDiagnostics sourceStandoffDecision;
    private StandoffAttackDiagnostics sourceStandoffAttack;
    private long sourceStandoffSerial;
    private GhostOpenWalkVariationRun sourceOpenVariation;
    private final GhostSourceFallbackLifetime sourceFallbackLifetime=new GhostSourceFallbackLifetime();
    private GhostSourceLeavingPolicyLifetime sourceLeavingPolicy;
    private McGhostSourceFallbackSearch sourceFallbackSearch;private McGhostSourceFallbackSearch.Pair sourceCapturedFallback;
    private McGhostSourceCoordinateCursor sourceFallbackCursor;private McGhostSourceLeavingChild sourceLeavingChild;
    private McGhostSourceLeavingEtherBridge sourceLeavingEther;
    // SEARCH retry clears the source helper flag, not the independently installed global walk.
    private final GhostSourceRetryMovement<McGhostSourceFallbackSearch,McGhostSourceCoordinateCursor> sourceRetryMovement=new GhostSourceRetryMovement<>();
    private boolean sourceRetainedFallbackActuated;
    private GhostSourceWalkSteering sourceRetainedFallbackSteering;
    private void sourceClearRetainedFallback(){var retained=sourceRetryMovement.clear();sourceRetainedFallbackActuated=false;
        if(retained!=null)retained.producer().revoke();
        if(sourceRetainedFallbackSteering!=null)sourceRetainedFallbackSteering.reset();sourceRetainedFallbackSteering=null;}
    private void sourceSearchReleaseMovement(){if(!sourceRetainedFallbackActuated)services.input().releaseAllMovement();}
    private void sourceRetainedFallbackTick(){
        var retained=sourceRetryMovement.retained();if(retained==null)return;
        // The real old producer still checks its original parent/child/PWC/camera/config scope;
        // current cursor also checks immutable terrain epochs and fresh next-body geometry.
        if(!retained.cursor().current(client)){sourceStop("Retained fallback original custody revoked");return;}
        if(sourceCallbacks.pending()||sourceViewAcquisition.pending()){services.input().releaseAllMovement();return;}
        var steering=retained.cursor().observe(client);
        if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.CONTINUE){sourceCoordinateSteering(steering.point(),steering.jump(),sourceRetainedFallbackSteering);sourceRetainedFallbackActuated=isEnabled();}
        else if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.AT_DESTINATION){sourceClearRetainedFallback();services.input().releaseAllMovement();sourceWalkSteering.reset();}
        else sourceStop("Retained fallback remaining geometry revoked");
    }
    private List<Creeper> sourceReadyNoPath=List.of();
    private void sourceClearRecovery(){sourceClearRetainedFallback();var fallback=sourceFallbackSearch;sourceFallbackSearch=null;sourceCapturedFallback=null;sourceFallbackCursor=null;
        var ether=sourceLeavingEther;sourceLeavingEther=null;var leaving=sourceLeavingChild;sourceLeavingChild=null;
        if(fallback!=null)fallback.revoke();if(ether!=null)ether.revoke();if(leaving!=null)leaving.revoke();sourceReadyNoPath=List.of();sourceWalkSteering.reset();}
    private McGhostLootNativeProvider.Session sourceLootSession;private McGhostLootCoordinateWalk sourceLootWalk;
    private int sourceLootPostedUnits;private final Map<Integer,Long> sourceIgnoredLoot=new HashMap<>();
    private void sourceClearLoot(){var walk=sourceLootWalk;sourceLootWalk=null;if(walk!=null)walk.revoke();
        if(sourceLootSession!=null)sourceLootSession.revoke();sourceLootSession=null;sourceLootPostedUnits=0;sourceWalkSteering.reset();}
    private boolean sourceLootTick(long now){
        if(sourceLootSession==null){
            if(!config.get(GhostGrindConfig.GHOST_GRIND_COLLECT_DROPS))return false;
            sourceIgnoredLoot.entrySet().removeIf(e->now>=e.getValue());
            var observed=McGhostLootNativeProvider.observe(client,this);var candidate=observed.nearest();
            if(candidate==null||sourceIgnoredLoot.containsKey(candidate.entityId()))return false;
            if(fullInventory()){sourceStop("Inventory full; source loot collection paused for explicit restock");return true;}
            services.input().releaseAllMovement();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;
            if(Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)>=.001||!client.player.onGround()){
                status="Settling native motion before owned item-coordinate capture";return true;
            }
            observed=McGhostLootNativeProvider.observe(client,this);candidate=observed.nearest();if(candidate==null)return false;
            sourceLootSession=observed.begin(candidate,now,config.get(GhostGrindConfig.GHOST_SOURCE_LOOT_TIMEOUT_MS));sourceLootPostedUnits=0;
            var goal=sourceLootSession.observeCoordinateGoal();if(goal==null){sourceClearLoot();return true;}
            sourceLootWalk=McGhostLootCoordinateWalk.issue(client,this,sourceLootSession,goal,()->sourceLootWalk);sourceLootWalk.begin();
        }
        var session=sourceLootSession;var state=session.poll(now);int confirmed=session.creditedUnits();
        if(confirmed>sourceLootPostedUnits){ledger.confirmedPickupUnits(session.serverId(),confirmed-sourceLootPostedUnits);sourceLootPostedUnits=confirmed;}
        if(state==GhostLootPickupTransaction.State.COMPLETE){services.input().releaseAllMovement();sourceClearLoot();status="Owned Ghost drop delivered to inventory";return true;}
        if(state==GhostLootPickupTransaction.State.REVOKED){sourceStop("Loot parent/PWC/camera custody revoked");return true;}
        if(state==GhostLootPickupTransaction.State.TIMED_OUT){sourceIgnoredLoot.put(session.chosenId(),now+30000);services.input().releaseAllMovement();sourceClearLoot();status="Loot delivery not confirmed; source combat resumed";return true;}
        if(sourceLootWalk==null||sourceLootWalk.state()==McGhostLootCoordinateWalk.State.REVOKED){
            // Actual item coordinates can settle/move; a fresh private goal is required, never old coordinate input.
            var goal=session.observeCoordinateGoal();if(goal==null){services.input().releaseAllMovement();status="Waiting for native pickup packet and exact inventory receipt";return true;}
            sourceLootWalk=McGhostLootCoordinateWalk.issue(client,this,session,goal,()->sourceLootWalk);sourceLootWalk.begin();sourceWalkSteering.reset();
        }
        var child=sourceLootWalk;child.pump();
        if(child.state()==McGhostLootCoordinateWalk.State.NO_PATH){sourceIgnoredLoot.put(session.chosenId(),now+30000);services.input().releaseAllMovement();sourceClearLoot();status="No owned item-coordinate path; source combat resumed";return true;}
        if(child.state()==McGhostLootCoordinateWalk.State.WALKING){
            var steering=child.observe();
            if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.CONTINUE){
                if(!child.current()||session.poll(now)==GhostLootPickupTransaction.State.REVOKED){sourceStop("Loot action boundary revoked");return true;}
                sourceCoordinateSteering(steering.point(),steering.jump());status="Owned Ghost loot coordinate cursor";
            }else{services.input().releaseAllMovement();if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.STALE){child.revoke();sourceLootWalk=null;}else status="Waiting for confirmed inventory delivery";}
        }else{services.input().releaseAllMovement();status=child.state()==McGhostLootCoordinateWalk.State.ARRIVED?"Waiting for native pickup/inventory receipts":"Capturing native item-coordinate path";}
        return true;
    }
    private void sourceCoordinateSteering(GhostSourceSampledRay.Point point,boolean jump){sourceCoordinateSteering(point,jump,sourceWalkSteering);}
    private void sourceCoordinateSteering(GhostSourceSampledRay.Point point,boolean jump,GhostSourceWalkSteering actualSteering){
        var p=client.player;var d=p.getDeltaMovement();var intent=actualSteering.advance(new GhostSourceWalkSteering.Frame(p.tickCount,
                new GhostSourceSampledRay.Point(p.getX(),p.getY(),p.getZ()),p.getYRot(),p.getXRot(),d.x,d.z,p.onGround(),point,jump,p.getXRot()));
        if(intent.outcome()==GhostSourceWalkSteering.Outcome.REJECTED){services.input().releaseAllMovement();return;}
        if(sourceParentAreaStamp(this).isEmpty()){sourceStop("Coordinate child parent authority revoked");return;}
        sourceApplyKeys(intent.forward(),intent.back(),intent.left(),intent.right(),intent.jump());services.rotation().aim(intent.yaw(),intent.pitch());
    }
    private final GhostSourceSearchRetryCounter sourceRetry=new GhostSourceSearchRetryCounter();
    private final GhostSourceCallbackQueue<GhostSourceWalkJob> sourceCallbacks=new GhostSourceCallbackQueue<>();
    private McGhostSourcePathResultSelection.Observation sourceCompletionObservation;
    private final GhostViewAcquisition<GhostSourcePathJobTransaction.Result> sourceViewAcquisition=new GhostViewAcquisition<>();
    private final GhostFarTargetView sourceFarView=new GhostFarTargetView();
    private GhostSourceUnreachableActorLedger.CallbackToken sourceUnreachableToken;
    private GhostSourcePathJobTransaction sourceTransactions;
    private GhostSourcePathJobTransaction.IssuedJob sourceTransactionJob;
    private GhostMovementWorkflow sourceWorkflowBinding=GhostMovementWorkflow.LEGACY_AUTHORED_ROUTES;
    private long sourceRetryAtTick=Long.MIN_VALUE;private boolean sourceAtDestination;
    private Set<GhostSourceWalkFrontier.Cell> sourceInputForbiddenCells=Set.of();
    private void invalidateSourceIssuedJob(){
        var prior=sourceIssuedWalkJob;sourceIssuedWalkJob=null;sourceIssuedWalkReceipt=null;sourceInstalledWalkCursor=null;
        sourceAtDestination=false;if(sourceOpenVariation!=null)sourceOpenVariation.revoke();sourceOpenVariation=null;sourceCallbacks.cancel();sourceCompletionObservation=null;sourceViewAcquisition.clear();sourceFarView.reset();sourceMeleeStandoff.reset();sourceStandoffDecision=null;sourceStandoffAttack=null;sourceReceivedHitDecision=null;if(sourceUnreachableToken!=null)sourceUnreachableToken.revoke();sourceUnreachableToken=null;sourceWalkSteering.reset();
        if(sourceTransactionJob!=null){sourceTransactionJob.revoke();sourceTransactionJob=null;}
        if(prior!=null)prior.revoke();
    }
    McGhostSourceOwnedWalkCursor sourceNativeInstalledWalkCursor(){return sourceInstalledWalkCursor;}
    public record SourceRuntimeEvidence(GhostMovementWorkflow workflow,Object job,GhostSourceWalkJob.State jobState,
            Object cursor,boolean melee,int retryAttempts,long retryAtTick,Object fallback,McGhostSourceFallbackSearch.State fallbackState,
            Object fallbackCursor,boolean fallbackActive,Object leaving,McGhostSourceLeavingChild.State leavingState,
            GhostSourceLeavingPhaseObservation.Snapshot leavingPreparation,McGhostSourceLeavingEtherBridge.State etherState,
            Object lootSession,GhostLootPickupTransaction.State lootState,int lootUnits,Object lootChild,McGhostLootCoordinateWalk.Evidence lootCapture,
            int variedEdges) {}
    /** Independent native fixture/HUD diagnostics only. Reads cached fields, never grants or mutates native authority. */
    public SourceRuntimeEvidence sourceRuntimeEvidence(){return new SourceRuntimeEvidence(sourceWorkflowBinding,sourceIssuedWalkJob,
            sourceIssuedWalkJob==null?null:sourceIssuedWalkJob.state(),sourceInstalledWalkCursor,sourceAtDestination,sourceRetry.attempts(),sourceRetryAtTick,
            sourceFallbackSearch,sourceFallbackSearch==null?null:sourceFallbackSearch.state(),sourceFallbackCursor,sourceFallbackLifetime.active(),
            sourceLeavingChild,sourceLeavingChild==null?null:sourceLeavingChild.state(),sourceLeavingChild==null?null:sourceLeavingChild.cachedPreparationEvidence(),
            sourceLeavingEther==null?null:sourceLeavingEther.state(),sourceLootSession,sourceLootSession==null?null:sourceLootSession.state(),
            sourceLootSession==null?0:sourceLootSession.creditedUnits(),sourceLootWalk,sourceLootWalk==null?null:sourceLootWalk.evidence(),
            sourceOpenVariation==null?0:sourceOpenVariation.cachedEdgeCount());}
    /** Called by the actual source base-event handler only; no ordinary tick wiring is enabled in Q. */
    GhostSourceCrowdPressureCounter.Observation sourcePressureAfterSuccessfulBaseEvent(){return sourcePressureLifecycle.reachedOwnedBaseEvent(this);}
    GhostSourceCrowdPressureCounter.Observation sourcePressureObservation(){return sourcePressureLifecycle.snapshot();}
    GhostSourceWalkJob sourceRegisteredWalkJob(){return sourceIssuedWalkJob;}
    GhostSourceIssuedSearchJob.Receipt sourceRegisterWalkJob(GhostSourceWalkJob actualJob){
        if(actualJob==null||actualJob.parent()!=this||sourceParentAreaStamp(this).isEmpty()
                ||!actualJob.issuedCohort().current()||actualJob.sourceChildIdentity()!=sourceSearchChild)
            throw new IllegalStateException("Actual current typed Ghost worker required");
        invalidateSourceIssuedJob();sourceIssuedWalkJob=actualJob;
        try{sourceIssuedWalkReceipt=GhostSourceIssuedSearchJob.observeIssuedJob(client,this,actualJob);
            if(!sourceIssuedWalkReceipt.scopeCurrent())throw new IllegalStateException("Issued job owner changed");
            return sourceIssuedWalkReceipt;
        }catch(RuntimeException invalid){invalidateSourceIssuedJob();throw invalid;}
    }
    Optional<GhostSourceIssuedSearchJob.Receipt> sourceIssuedWalkReceipt(GhostSourceWalkJob actualJob){
        return actualJob==sourceIssuedWalkJob&&sourceIssuedWalkReceipt!=null&&sourceIssuedWalkReceipt.scopeCurrent()
                ?Optional.of(sourceIssuedWalkReceipt):Optional.empty();
    }
    private final GhostSourcePlayerProximity sourcePlayerProximity=new GhostSourcePlayerProximity();
    private Object sourceProximityPlayer,sourceProximityWorld,sourceProximityConnection;
    private int sourceProximityTick=Integer.MIN_VALUE;
    private boolean sourceAreaConstructorObserved;
    private long sourceParentGeneration,sourceParentSettingsRevision;
    private String sourceParentSettingsSignature="";
    private GhostSourceSettingsObservation sourceParentSettingsObservation;
    // Reuse immutable value storage only after every unchanged fresh native guard succeeds.
    private final GhostSourceStampValues sourceStampValues=new GhostSourceStampValues();
    private Object sourceParentConnection;
    private String sourceParentJumpBoost="UNKNOWN";
    public GhostGrindModule(Minecraft client,FarmServices services,ConfigService config,LocalRouteStore store){
        this(client,services,config,store,()->false);
    }
    public GhostGrindModule(Minecraft client,FarmServices services,ConfigService config,LocalRouteStore store,java.util.function.BooleanSupplier externalHealOwnership){
        super(ID,"Ghost Grind",ModuleCategory.COMBAT);this.client=client;this.services=services;this.config=config;this.store=store;
        this.externalHealOwnership=java.util.Objects.requireNonNull(externalHealOwnership);
        dev.veltraph.client.combat.heal.mc.McCapturedHudVitals.install(client);
        dev.veltraph.client.combat.heal.mc.McReceivedHealth.install(client);
        sourcePressureLifecycle=new McGhostSourcePressureLifecycle(client);
        sourceTransactions=new GhostSourcePathJobTransaction(this,new SourceNativeJobAdapter(),GhostSourcePathFailureSuppression.runtime());
        sourceLeavingPolicy=new GhostSourceLeavingPolicyLifetime(()->java.util.concurrent.ThreadLocalRandom.current().nextDouble());
        sourceMappedArea=null;sourceAreaConstructorObserved=true; // Actual mapped constructor observation, not a feature-label default.
        routeTravel=new McTravelNavigator(client,services,config);walk=new McNavigator(client,services.input(),config);
        scheduledPause=new CombatScheduledPause(client,this::isEnabled,this::scheduledReadiness,this::scheduledSignature,
                this::releaseForScheduledPause,this::rebindForScheduledPause,()->status="Ghost rest complete; route/combat resumed",()->setEnabled(false));
    }
    public GhostSourceDestination sourceDestination(Object actualOwner){
        if(sourceParentAreaStamp(actualOwner).isEmpty())throw new IllegalStateException("Actual Root-owned destination context required");
        return java.util.Objects.requireNonNull(config.get(GhostGrindConfig.GHOST_SOURCE_DESTINATION),"Source destination unavailable");
    }
    public record SourceProximityObservation(boolean randomized,long revision) {}
    /** Explicit observed-on-client-tick adapter for the readable source updater; JNI callback cadence is qualified. */
    public SourceProximityObservation sourceObservePlayerProximity(Object actualOwner){
        if(sourceParentAreaStamp(actualOwner).isEmpty())throw new IllegalStateException("Actual Root-owned player observation required");
        if(client.player!=sourceProximityPlayer||client.level!=sourceProximityWorld||client.getConnection()!=sourceProximityConnection||client.player.tickCount!=sourceProximityTick){
            var players=client.level.players().stream().map(p->new GhostSourcePlayerProximity.PlayerPoint(p,
                    client.player.position().distanceTo(p.position()))).toList();
            sourcePlayerProximity.observe(players);sourceProximityPlayer=client.player;sourceProximityWorld=client.level;
            sourceProximityConnection=client.getConnection();sourceProximityTick=client.player.tickCount;
        }
        return new SourceProximityObservation(sourcePlayerProximity.randomized(),sourcePlayerProximity.revision());
    }
    public long sourcePlayerProximityRevision(Object actualOwner){
        if(sourceParentAreaStamp(actualOwner).isEmpty())return -1;return sourcePlayerProximity.revision();
    }
    public Object sourceNativeChildIdentity(Object actualOwner){
        if(sourceParentAreaStamp(actualOwner).isEmpty())return null;
        return sourceChildFieldOnly();
    }
    private Object sourceChildFieldOnly(){return sourceJumpBoostHelper!=null?sourceJumpBoostHelper:sourceLeavingChild!=null?sourceLeavingChild:sourceLootWalk!=null?sourceLootWalk:sourceSearchChild;}
    /** Field-only read immediately after the actual full parent validation; no expiry or action callback. */
    Object sourceChildFromValidatedStamp(GhostSourceOwnedParentArea.Stamp actual){
        if(actual==null||actual.parent()!=this||actual.generation()!=sourceParentGeneration||actual.settingsRevision()!=sourceParentSettingsRevision
            ||actual.player()!=owner||actual.world()!=world||actual.connection()!=sourceParentConnection)return null;
        return sourceChildFieldOnly();
    }
    /** Source helper-abort identity suppression only; not protected chat, attack history or path-failure UUID. */
    public void sourceObserveIgnoredActor(Object actualOwner,net.minecraft.world.entity.Entity actualEntity){
        if(sourceParentAreaStamp(actualOwner).isEmpty()||actualEntity==null||actualEntity.level()!=client.level||client.level.getEntity(actualEntity.getId())!=actualEntity)
            throw new IllegalStateException("Actual owned helper-abort body required");
        GhostSourceIdentityIgnore.runtime().observeIgnored(actualEntity,System.currentTimeMillis());
    }
    public GhostSourceJumpBoostGate.Result sourceJumpBoostBeforeSelection(Object actualOwner){
        if(sourceJumpBoostHelper!=null)return actualOwner==this&&sourceJumpBoostHelper.current()?GhostSourceJumpBoostGate.Result.MODIFY_REQUIRED:GhostSourceJumpBoostGate.Result.UNKNOWN;
        if(sourceParentAreaStamp(actualOwner).isEmpty())return GhostSourceJumpBoostGate.Result.UNKNOWN;
        boolean modify=config.get(GhostGrindConfig.GHOST_SOURCE_MODIFY_JUMP_BOOST);boolean present=client.player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST);
        if(!modify||!present)return GhostSourceJumpBoostGate.Result.NOT_REQUIRED;
        var access=McGhostSourceCookieAccess.observe(client);
        var result=GhostSourceJumpBoostGate.evaluate(GhostSourceJumpBoostGate.Observed.YES,access,modify,System.currentTimeMillis(),GhostSourceJumpBoostClock.runtime().lastEntry());
        // Recovered local availability does not invent still-bound native menu identities.
        return result==GhostSourceJumpBoostGate.Result.MODIFY_REQUIRED&&preflightProfile==null?GhostSourceJumpBoostGate.Result.UNKNOWN:result;
    }
    McGhostSourceSearchJumpBoost sourceActiveJumpBoostHelper(){return sourceJumpBoostHelper;}
    private boolean sourceJumpBoostAuthorityCurrent(){
        if(sourceParentAuthorityCurrent())return true;
        var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return manager!=null&&manager.get(ID).orElse(null)==this&&manager.inputOwner().orElse(null)==this&&manager.enabledControllers().size()==1&&isEnabled()
            &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==null&&client.gui.screen()!=null&&sourceJumpBoostHelper!=null&&sourceJumpBoostHelper.ownsNativeViewPause();
    }
    boolean sourceJumpBoostScopeCurrent(GhostSourceJumpBoostContinuation.Scope scope){return scope!=null&&scope.parent()==this&&scope.child()==sourceSearchChild
        &&client.isSameThread()&&sourceJumpBoostAuthorityCurrent()&&isEnabled()&&grindStarted&&!scheduledPause.paused()&&!externalHealOwnership.getAsBoolean()
        &&sourceWorkflowBinding==GhostMovementWorkflow.SOURCE_WALK&&scope.generation()==sourceParentGeneration&&scope.settingsRevision()==sourceParentSettingsRevision
        &&client.player==scope.player()&&client.level==scope.world()&&client.getConnection()==scope.connection()&&client.player==owner&&client.level==world
        &&client.player!=null&&!client.player.isDeadOrDying()&&sourceParentSettingsObservation!=null&&sourceParentSettingsObservation.current(config,sourceMappedArea)
        &&sourceLeavingChild==null&&sourceLeavingEther==null&&sourceLootSession==null&&sourceLootWalk==null;}
    private void sourceBeginJumpBoostHelper(){
        sourceEnterSearch("Source Jump Boost helper entering");services.rotation().cancelRotation();
        var stamp=sourceParentAreaStamp(this).orElseThrow(()->new IllegalStateException("Actual source helper entry revoked"));
        var scope=new GhostSourceJumpBoostContinuation.Scope(this,sourceSearchChild,stamp.generation(),stamp.settingsRevision(),client.player,client.level,client.getConnection(),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
        if(preflightProfile==null||!preflightBinding.equals(config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE)))throw new IllegalStateException("Actual source helper profile required");
        sourceJumpBoostHelper=McGhostSourceSearchJumpBoost.issue(client,services,this,scope,preflightProfile,preflightBinding);status="Owned source Jump Boost menu helper";
    }
    private void sourceTickJumpBoostHelper(){
        var helper=sourceJumpBoostHelper;if(helper==null)return;helper.tick();status=helper.status();
        if(helper.failed()){sourceStop("Source Jump Boost helper custody revoked: "+status);return;}
        if(helper.completed()){
            if(helper.continuationChild()!=sourceSearchChild||!helper.consumeActualClose()){sourceStop("Source Jump Boost actual close/same-child receipt rejected");return;}
            sourceJumpBoostHelper=null;observeSourceParentSetup();sourceEnterSearch("Actual source Jump Boost close resumed the same SEARCH child");
        }
    }
    public record SourceJumpBoostEvidence(boolean active,Object continuationChild,long lastActualEntry,String status,GhostSourceJumpBoostContinuation.Scope issuedScope) {}
    public SourceJumpBoostEvidence sourceJumpBoostEvidence(){return new SourceJumpBoostEvidence(sourceJumpBoostHelper!=null,sourceJumpBoostHelper==null?null:sourceJumpBoostHelper.continuationChild(),GhostSourceJumpBoostClock.runtime().lastEntry(),sourceJumpBoostHelper==null?status:sourceJumpBoostHelper.status(),sourceJumpBoostHelper==null?null:sourceJumpBoostHelper.issuedScope());}
    public McGhostSourceGuardSnapshot sourceSearchGuardObservation(Object actualOwner){return McGhostSourceGuardSnapshot.observe(client,this,actualOwner);}
    public Optional<McGhostSourceNativeCohortSnapshot> sourceSearchCohortObservation(Object actualOwner){
        var guard=sourceSearchGuardObservation(actualOwner);
        if(guard.beforeSearch()!=GhostSourceParentGuardDispatcher.Result.UNHANDLED
                ||sourceJumpBoostBeforeSelection(actualOwner)!=GhostSourceJumpBoostGate.Result.NOT_REQUIRED)return Optional.empty();
        return Optional.of(McGhostSourceNativeCohortSnapshot.observe(client,this,actualOwner));
    }
    public record SourceRecoveryEvidence(Object actualFallbackPair,GhostSourceCoordinateGraphEvidence selectedFallbackGraph,
            List<GhostSourceCoordinateGraphEvidence> attemptedFallbackGraphs,McGhostSourceLeavingChild.CachedEvidence leaving) {}
    /** Cache-only actual worker/child evidence. No capture, permission lookup, pump, callback or phase mutation. */
    public SourceRecoveryEvidence sourceRecoveryEvidence(){return new SourceRecoveryEvidence(sourceCapturedFallback,
            sourceCapturedFallback==null?null:sourceCapturedFallback.cachedGraphEvidence(),
            sourceFallbackSearch==null?List.of():sourceFallbackSearch.cachedCandidateGraphEvidence(),
            sourceLeavingChild==null?null:sourceLeavingChild.cachedEvidence());}
    private String sourceParentSettingsSignature(){
        var values=new TreeMap<String,String>();
        for(var key:SOURCE_PARENT_DECLARED_KEYS)
            values.put(key.name(),String.valueOf(config.get(key)));
        return values+"|sourceArea="+(sourceMappedArea==null?"ABSENT":sourceMappedArea.name()+":"+sourceMappedArea.sha256()+":"+sourceMappedArea.origin());
    }
    private String sourceJumpBoostObservation(){
        var effect=client.player==null?null:client.player.getEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST);
        return effect==null?"ABSENT":"PRESENT:"+effect.getAmplifier();
    }
    private void observeSourceParentSetup(){
        sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();sourceParentGeneration++;
        if(!sourceAreaConstructorObserved||client.player==null||client.level==null||client.getConnection()==null)return;
        sourceParentConnection=client.getConnection();sourceParentJumpBoost=sourceJumpBoostObservation();
        sourceParentSettingsSignature=sourceParentSettingsSignature();
        sourceParentSettingsObservation=GhostSourceSettingsObservation.capture(config,SOURCE_PARENT_DECLARED_KEYS,sourceMappedArea);sourceParentSettingsRevision++;
        var stamp=new GhostSourceOwnedParentArea.Stamp(this,sourceParentGeneration,sourceParentSettingsRevision,client.player,client.level,sourceParentConnection);
        if(sourceMappedArea==null)sourceParentArea.observeOwnedConstructorAbsence(stamp);
        // PRESENT requires actual verified resource bytes; setup never converts an unavailable resource into ABSENT.
    }
    private boolean sourceParentAuthorityCurrent(){
        var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return manager!=null&&GhostSourceParentAuthority.owned(this,manager.get(ID).orElse(null),manager.inputOwner().orElse(null),
                manager.enabledControllerCount(),isEnabled(),dev.veltraph.client.service.mc.McOwnedRotation.authority(client));
    }
    public Optional<GhostSourceOwnedParentArea.Stamp> sourceParentAreaStamp(Object actualNativeInputOwner){
        long diagnosticStart=GhostSourceScopeCosts.begin(GhostSourceScopeCosts.Part.PARENT_STAMP_ALL);try{
        if(actualNativeInputOwner!=this||!client.isSameThread())return Optional.empty();
        if(!GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.ROOT_AUTHORITY,this::sourceParentAuthorityCurrent)||!isEnabled()||!grindStarted||scheduledPause.paused()||externalHealOwnership.getAsBoolean()
                ||client.player==null||client.level==null||client.getConnection()==null){
            sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();return Optional.empty();
        }
        if(client.player!=owner||client.level!=world||client.getConnection()!=sourceParentConnection||client.player.isDeadOrDying()
                ||client.gui.screen()!=null||(sourceParentSettingsObservation==null||!GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.SETTINGS_VALUES,()->sourceParentSettingsObservation.current(config,sourceMappedArea)))
                ||!sourceParentJumpBoost.equals(GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.JUMP_OBSERVATION,this::sourceJumpBoostObservation))){
            sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();sourceParentSettingsRevision++;return Optional.empty();
        }
        return GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.STAMP_ALLOCATION,()->sourceStampValues.observed(this,sourceParentGeneration,sourceParentSettingsRevision,owner,world,sourceParentConnection));
        }finally{GhostSourceScopeCosts.end(GhostSourceScopeCosts.Part.PARENT_STAMP_ALL,diagnosticStart);}
    }
    public Optional<GhostSourceOwnedParentArea.Receipt> sourceParentAreaReceipt(Object actualNativeInputOwner){
        return sourceParentAreaStamp(actualNativeInputOwner).map(sourceParentArea::capture)
                .filter(r->r.observation()!=GhostSourceOrderedStandingProvider.ParentAreaObservation.UNKNOWN);
    }
    public LivingEntity sourceNativeSelectedTarget(Object actualNativeInputOwner){return actualNativeInputOwner==this&&client.isSameThread()&&sourceParentAuthorityCurrent()&&isEnabled()?target:null;}
    public Optional<GhostNativeSearchPhase.Receipt> sourceNativeSearchReceipt(Object actualNativeInputOwner){
        return sourceParentAreaStamp(actualNativeInputOwner).map(stamp->sourceSearchChild.capture(stamp,target));
    }
    /** Read-only source foundation remains dormant: no current handler calls sourceSearchChild.observeSelected. */
    public GhostNativeSearchPhase.State sourceNativeSearchState(){return sourceSearchChild.state();}
    public void observeVerifiedSourceParentArea(Object actualNativeInputOwner,GhostSourceParentAreaResource observedArea,byte[] observedBytes,int maximumValues){
        if(actualNativeInputOwner!=this||!client.isSameThread()||!sourceParentAuthorityCurrent()||!isEnabled()||!grindStarted||scheduledPause.paused()||externalHealOwnership.getAsBoolean()
                ||observedArea==null||client.player==null||client.player.isDeadOrDying()||client.player!=owner||client.level!=world
                ||client.getConnection()==null||client.gui.screen()!=null)
            throw new IllegalStateException("Actual owned enabled source parent resource binding required");
        sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();sourceMappedArea=observedArea;sourceParentGeneration++;
        sourceParentSettingsRevision++;sourceParentSettingsSignature=sourceParentSettingsSignature();
        sourceParentSettingsObservation=GhostSourceSettingsObservation.capture(config,SOURCE_PARENT_DECLARED_KEYS,sourceMappedArea);
        sourceParentConnection=client.getConnection();sourceParentJumpBoost=sourceJumpBoostObservation();
        sourceParentArea.observePresentResource(new GhostSourceOwnedParentArea.Stamp(this,sourceParentGeneration,sourceParentSettingsRevision,
                owner,world,sourceParentConnection),observedBytes,observedArea,maximumValues);
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return !config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE).isBlank()&&(!isEnabled()||!grindStarted)?Optional.empty():Optional.of(GameArea.DWARVEN_MINES);}
    @Override public boolean hasTravelSetupPhase(){return !config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE).isBlank()&&!grindStarted;}
    @Override public boolean allowsScreen(String title){if(isEnabled()&&sourceJumpBoostHelper!=null)return sourceJumpBoostHelper.allowsScreen(title);return isEnabled()&&preflight!=null&&!grindStarted&&preflightBinding.equals(config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE))&&preflight.allowsScreen(title);}
    public Optional<GhostWarpPermit> pendingNativeWarp(){return isEnabled()&&preflight!=null?preflight.pendingNativeWarp():Optional.empty();}
    public Optional<GhostTransportLoop.Attempt> pendingTransportAttempt(){return isEnabled()&&preflight!=null&&preflightBinding.equals(config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE))&&!externalHealOwnership.getAsBoolean()?preflight.pendingTransportAttempt():Optional.empty();}
    public int transportAttempts(){return preflight==null?0:preflight.transportAttempts();}
    /** Exact Root owner only; advances source transport attempts, never buffs/grind/rebind/activation. */
    public boolean tickIssuedTransport(Object nativeInputOwner){
        if(nativeInputOwner!=this||!isEnabled()||preflight==null||grindStarted)return false;
        if(externalHealOwnership.getAsBoolean()||!preflightBinding.equals(config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE))){status="Ghost transport priority/frozen-profile revoked";setEnabled(false);return false;}
        preflight.advanceIssuedTransport();status=preflight.status();
        if(preflight.state()==GhostNativePreflight.State.FAILED){setEnabled(false);return false;}
        return preflight.pendingTransportAttempt().isPresent();
    }
    public boolean commitNativeWarp(GhostWarpPermit permit,Object nativeInputOwner){
        if(nativeInputOwner!=this||!isEnabled()||externalHealOwnership.getAsBoolean()||!preflightBinding.equals(config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE))||preflight==null||pendingNativeWarp().filter(p->p==permit).isEmpty()||!preflight.commitNativeWarp(permit))return false;
        rebindForScheduledPause();return true;
    }
    public boolean preflightComplete(){return grindStarted;}
    /** Cached modern constrained-melee decision only; never evaluates or grants authority. */
    public GhostConstrainedMeleeStandoff.Evidence sourceMeleeStandoffEvidence(){return sourceMeleeStandoff.cachedEvidence();}
    /** Cached actual source inputs/decision only: no physics/guards/observe/clock or action invocation. */
    public StandoffDecisionDiagnostics sourceMeleeStandoffDiagnostics(){return sourceStandoffDecision;}
    public StandoffAttackDiagnostics sourceMeleeStandoffAttackDiagnostics(){return sourceStandoffAttack;}
    public Optional<GhostNativePreflight.MenuDiagnostics> preflightMenuDiagnostics(){return preflight==null?Optional.empty():Optional.of(preflight.menuDiagnostics());}
    public String preflightOutcome(){return preflight==null?"Source preflight unbound":preflight.status();}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){if(sourceLeavingEther!=null&&sourceLeavingEther.consumeExpectedTeleport(position,now))return true;return !scheduledPause.paused()&&(preflight!=null&&!grindStarted?preflight.consumeExpectedTeleport(position,now):routeTravel.consumeExpectedTeleport(position,now));}
    /** Root's global generic heal assist must yield while this dedicated lifecycle owns healing. */
    public boolean ownsHealControl(){return isEnabled()&&!scheduledPause.paused();}
    @Override public boolean ownsGameplayInput(){return !scheduledPause.paused();}
    public dev.veltraph.client.scheduling.ScheduledPausePort scheduledPausePort(){return scheduledPause;}
    public Optional<String> schedulingReadiness(){return scheduledPause.paused()?scheduledPause.arrivalReadiness():scheduledReadiness();}
    public Optional<String> scheduledReadiness(){
        if(!grindStarted)return Optional.of("Ghost source ingress/buff preflight must finish before rest");
        if(sourceJumpBoostHelper!=null)return Optional.of("Owned source Jump Boost close must finish before rest");
        if(sourceLeavingChild!=null||sourceLeavingEther!=null||sourceLootSession!=null)return Optional.of("Owned source leaving/loot receipt must settle before rest");
        if(!isEnabled()||scheduledPause.paused()||client.player!=owner||client.level!=world||!weaponReady(weapon())||services.inventory().selectedSlot()!=weapon())return Optional.of("Exact enabled Ghost weapon owner required");
        if(externalHealOwnership.getAsBoolean()||settling||inventoryReturn||dropId>=0||client.player.isUsingItem()
                ||recovery.state()!=GhostHealReturn.State.IDLE&&recovery.state()!=GhostHealReturn.State.COMPLETE
                ||hp()<=config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT))return Optional.of("Ghost heal/drop/ability work or low health must settle before rest");
        if(routeActive&&route.nodes().get(checkpoint).kind()!=LocalRoute.Kind.WALK&&fallbackCheckpoint!=checkpoint)return Optional.of("Ghost ability travel must finish before rest");
        return Optional.empty();
    }
    public String scheduledSignature(){
        var values=new TreeMap<String,String>();for(var key:PersistableKeys.collect(List.of(GhostGrindConfig.class,GhostConfig.class,RotationConfig.class,TravelConfig.class)))values.put(key.name(),String.valueOf(config.get(key)));
        try{return values+"|"+GhostRouteCatalog.load(store,config.get(GhostGrindConfig.GHOST_GRIND_ROUTES),routes.isEmpty()?"":routes.getFirst().dimension());}
        catch(Exception failure){return values+"|unavailable route proof";}
    }
    private void releaseForScheduledPause(){if(sourceJumpBoostHelper!=null){sourceJumpBoostHelper.cancel();sourceJumpBoostHelper=null;}sourceClearRecovery();sourceClearLoot();sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();stopTravel();services.rotation().cancelRotation();services.breaking().setBreaking(false);CombatScheduledPause.releaseKeys(client);invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;ledger.clearNativeContext();status="Ghost engine paused for owned scheduled rest";}
    private void rebindForScheduledPause(){sourceClearRecovery();sourceClearLoot();sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();owner=client.player;world=client.level;routeTravel=new McTravelNavigator(client,services,config);walk=new McNavigator(client,services.input(),config);invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;ledger.clearNativeContext();health.reset();blacklist.clear();ignoredDrops.clear();walkGoal=null;nextPath=0;aim.reset();movement.reset();yawLimit.reset();pitchLimit.reset();healPitch.reset();}
    private int weapon(){return config.get(GhostGrindConfig.GHOST_GRIND_WEAPON_SLOT)-1;}
    private int healSlot(){return config.get(GhostGrindConfig.GHOST_GRIND_HEAL_SLOT)-1;}
    private boolean weaponReady(int slot){return client.player!=null&&slot>=0&&slot<9&&!client.player.getInventory().getItem(slot).isEmpty()
            &&(client.player.getInventory().getItem(slot).is(ItemTags.SWORDS)||client.player.getInventory().getItem(slot).is(ItemTags.AXES));}
    private boolean healReady(){
        if(!weaponReady(healSlot()))return false;var stack=client.player.getInventory().getItem(healSlot());var lore=stack.get(DataComponents.LORE);
        String wanted=config.get(GhostConfig.GHOST_HEAL_WEAPON_NAME).strip().toLowerCase(Locale.ROOT);
        return !wanted.isBlank()&&stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(wanted)
                &&lore!=null&&lore.lines().stream().anyMatch(l->l.getString().replaceAll("§.","").contains("Wither Impact"));
    }
    private boolean sourceWorkflowConfigured(){return config.get(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW)==GhostMovementWorkflow.SOURCE_WALK;}
    @Override public Optional<String> preStartCheck(){
        if(externalHealOwnership.getAsBoolean())return Optional.of("Global auto-healer still owns saved camera/slot; wait for restoration before Ghost activation");
        if(client.level==null||client.player==null)return Optional.of("A living loaded world is required");
        if(!weaponReady(weapon()))return Optional.of("Configure Ghost Weapon Slot to a sword or axe");
        if(config.get(GhostGrindConfig.GHOST_GRIND_HEAL)&&!healReady())return Optional.of("Configure a heal sword with the Wither Impact ability");
        try{preflightBinding=config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE);preflightProfile=preflightBinding.isBlank()?null:GhostPreflightProfile.parse(preflightBinding);}
        catch(RuntimeException invalid){return Optional.of("Ghost preflight: "+invalid.getMessage());}
        sourceWorkflowBinding=config.get(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW);
        if(sourceWorkflowConfigured()){
            try{GhostSourceWalkForbiddenCells.fromActualConfiguredJson(config.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS)).requireObserved();}
            catch(RuntimeException unavailable){return Optional.of("Source WALK requires an explicit modern forbidden-cell policy: "+unavailable.getMessage());}
            routes=List.of();return Optional.empty();
        }
        try{routes=GhostRouteCatalog.load(store,config.get(GhostGrindConfig.GHOST_GRIND_ROUTES),client.level.dimension().identifier().toString());}
        catch(Exception e){return Optional.of("Ghost route: "+e.getMessage());}
        return Optional.empty();
    }
    @Override public void onEnable(){
        if(externalHealOwnership.getAsBoolean()){status="Global auto-healer owns control; Ghost activation refused";setEnabled(false);return;}
        sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();sourceParentGeneration++;
        sourceWorkflowBinding=config.get(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW);sourceClearRecovery();sourceClearLoot();sourceRetry.resetFromMappedHandlerReset();sourceRetryAtTick=Long.MIN_VALUE;sourceFallbackLifetime.sourceSearchEntry();
        capturedHudRequiredAtStart=config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED);
        grindStarted=false;preflight=null;lifecycleOwned=true;owner=client.player;world=client.level;originalSlot=services.inventory().selectedSlot();ledger=new GhostLedger();health.reset();blacklist.clear();ignoredDrops.clear();
        recovery.reset();healReturnDriver.reset();healReturnConfig=Map.of();settling=routeActive=walkActive=inventoryReturn=false;invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;dropId=-1;heals=returns=routeArrivals=0;lastHeal=Long.MIN_VALUE/2;lastAttack=nextPath=0;
        aim.reset();movement.reset();yawLimit.reset();pitchLimit.reset();healPitch.reset();services.rotation().cancelRotation();
        if(preflightProfile!=null){preflight=new GhostNativePreflight(client,services,preflightProfile,lastBuff);status="Ghost source ingress preflight";}
        else beginGrind();
    }
    private void beginGrind(){
        if(!weaponReady(weapon())){status="Ghost weapon missing after preflight";setEnabled(false);return;}
        services.inventory().selectSlot(weapon());if(sourceWorkflowBinding==GhostMovementWorkflow.LEGACY_AUTHORED_ROUTES)chooseRoute();
        else sourceInputForbiddenCells=GhostSourceWalkForbiddenCells.fromActualConfiguredJson(config.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS)).requireObserved();
        grindStarted=true;observeSourceParentSetup();status=preflight==null?"Ghost grind-only: source preflight bindings are blank":"Finding Ghost area after source preflight: "+preflight.status();
    }
    private void chooseRoute(){route=routes.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(routes.size()));checkpoint=0;routeActive=false;}
    @Override public void onDisable(){
        if(sourceJumpBoostHelper!=null){sourceJumpBoostHelper.cancel();sourceJumpBoostHelper=null;}
        sourceClearRecovery();sourceClearLoot();sourceParentArea.invalidate();sourceStampValues.clear();invalidateSourceIssuedJob();sourceSearchChild.invalidate();sourceParentGeneration++;
        scheduledPause.invalidate();
        if(!lifecycleOwned)return;
        lifecycleOwned=false;
        if(preflight!=null)preflight.cancel();
        stopTravel();services.rotation().cancelRotation();services.breaking().setBreaking(false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);restoreHealSlot();
        if(client.player==owner&&client.level==world&&services.inventory().selectedSlot()==weapon())services.inventory().selectSlot(originalSlot);settling=false;recovery.reset();healHealthBasis=null;healReturnDriver.reset();healReturnConfig=Map.of();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;ledger.abandonPickup();
    }
    public int kills(){return ledger.kills();}public int collectedSorrows(){return ledger.collected("Sorrow");}public int sorrowNotices(){return ledger.sorrowNotices();}
    public int heals(){return heals;}public int physicalReturns(){return returns;}public int routeArrivals(){return routeArrivals;}
    public GhostHealReturn.Pose healPose(){return recovery.pose();}
    public GhostHealReturn.State healState(){return recovery.state();}
    @Override public void onActionBar(String text){if(!scheduledPause.paused())health.accept(text,System.currentTimeMillis());}
    @Override public void onServerMessage(String text){
        if(!isEnabled()||scheduledPause.paused()||text==null)return;if(sourceJumpBoostHelper!=null){sourceJumpBoostHelper.message(text);return;}if(preflight!=null&&!grindStarted){preflight.message(text);return;}String line=text.replaceAll("§.","");ledger.onMessage(line);
        if(line.contains("You were killed by Ghost")){status="Ghost death; inputs released, explicit restart required";setEnabled(false);return;}
        if(line.contains("is currently Hunting this monster")||line.contains("Another player is hunting this monster")){if(target!=null)blacklist.put(target.getUUID(),System.currentTimeMillis()+60000);invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;stopTravel();status="Protected Ghost blacklisted";}
        if(line.contains("New wave of creepers spawned")){ledger.wave();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;stopTravel();if(sourceWorkflowBinding==GhostMovementWorkflow.LEGACY_AUTHORED_ROUTES)chooseRoute();status="New Ghost wave; route/search restarted";}
        if(line.equals("You haven't unlocked this fast travel destination!")){status="Dwarven fast travel unavailable; stopped";setEnabled(false);}
    }
    private double hp(){long now=System.currentTimeMillis();return dev.veltraph.client.combat.heal.CapturedHudVitals.preferredPercent(
            dev.veltraph.client.combat.heal.mc.McCapturedHudVitals.current(client,now),config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED),
            health.percent(now),client.player.getHealth()/Math.max(1,client.player.getMaxHealth())*100).orElse(Double.NaN);}
    @Override public void onTick(){
        if(scheduledPause.paused())return;
        if(preflight!=null&&!grindStarted){
            if(externalHealOwnership.getAsBoolean()){status="Higher-priority healer took Ghost preflight control; stopped";setEnabled(false);return;}
            if(!preflightBinding.equals(config.get(GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE))){status="Ghost frozen preflight bindings changed; stopped";setEnabled(false);return;}
            preflight.tick();status=preflight.status();if(preflight.buffStartedAt()!=Long.MIN_VALUE)lastBuff=preflight.buffStartedAt();
            if(preflight.state()==GhostNativePreflight.State.FAILED){setEnabled(false);return;}
            if(preflight.complete()){owner=client.player;world=client.level;routeTravel=new McTravelNavigator(client,services,config);walk=new McNavigator(client,services.input(),config);if(preflight.buffStartedAt()!=Long.MIN_VALUE)lastBuff=preflight.buffStartedAt();beginGrind();}
            return;
        }
        if(sourceJumpBoostHelper!=null){sourceTickJumpBoostHelper();return;}
        var p=client.player;if(p==null||client.level!=world||p!=owner||client.gameMode==null||p.isDeadOrDying()||client.gui.screen()!=null){status="Death/world/GUI boundary; stopped";setEnabled(false);return;}
        if(config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED)!=capturedHudRequiredAtStart){status="Health input policy changed; explicit restart required";setEnabled(false);return;}
        if(capturedHudRequiredAtStart&&dev.veltraph.client.combat.heal.mc.McCapturedHudVitals.current(client,System.currentTimeMillis()).isEmpty()){
            status="Fresh received health unavailable; inputs released";setEnabled(false);return;
        }
        if(config.get(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW)!=sourceWorkflowBinding){status="Ghost workflow changed while active; explicit restart required";setEnabled(false);return;}
        if(sourceWorkflowBinding==GhostMovementWorkflow.SOURCE_WALK){sourceWorkflowTick();return;}
        float yaw=p.getYRot(),pitch=p.getXRot();tick();
        if(isEnabled()){double speed=config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);dev.veltraph.client.service.mc.McOwnedRotation.plan(client,yawLimit.apply(yaw,dev.veltraph.client.service.mc.McOwnedRotation.requestedYaw(client),speed),pitchLimit.apply(pitch,dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client),speed));}
    }
    private void sourceWorkflowTick(){
        sourceRetainedFallbackActuated=false;
        long now=System.currentTimeMillis();var p=client.player;
        blacklist.entrySet().removeIf(e->now>=e.getValue());ignoredDrops.entrySet().removeIf(e->now>=e.getValue());
        sourceObserveLedger(now);
        try{
            if(sourceParentAreaStamp(this).isEmpty()){sourceStop("Source parent/PWC/settings/camera context revoked");return;}
            sourcePressureAfterSuccessfulBaseEvent();
            if(sourceYieldForHealing(now))return;
            if(sourceLeavingChild==null&&sourceRetryAtTick!=Long.MIN_VALUE&&p.tickCount>=sourceRetryAtTick){sourceRetryAtTick=Long.MIN_VALUE;sourceEnterScheduledSearch();}
            if(!isEnabled())return;
            GhostSourceParentGuardDispatcher.Result retainedGate=null;
            GhostSourceJumpBoostGate.Result retainedJumpGate=null;
            if(sourceRetryMovement.active()){
                if(!weaponReady(weapon())||services.inventory().selectedSlot()!=weapon()){sourceStop("Retained source Ghost weapon ownership changed");return;}
                retainedGate=sourceSearchGuardObservation(this).beforeSearch();
                if(retainedGate!=GhostSourceParentGuardDispatcher.Result.UNHANDLED){sourceClearRetainedFallback();services.input().releaseAllMovement();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;status="Source parent guard owns retained search; "+retainedGate;return;}
                retainedJumpGate=sourceJumpBoostBeforeSelection(this);
                if(retainedJumpGate==GhostSourceJumpBoostGate.Result.MODIFY_REQUIRED){sourceBeginJumpBoostHelper();return;}
                if(retainedJumpGate==GhostSourceJumpBoostGate.Result.UNKNOWN){sourceClearRetainedFallback();services.input().releaseAllMovement();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;status="Retained source Jump Boost availability is unobserved";return;}
                sourceRetainedFallbackTick();if(!isEnabled())return;
            }
            if(sourceRecoveryTick(now))return;
            if(!sourceRetryMovement.active()&&!sourceCallbacks.pending()&&sourceLootTick(now))return;
            if(!weaponReady(weapon())||services.inventory().selectedSlot()!=weapon()){sourceStop("Source Ghost weapon ownership changed");return;}
            var gate=retainedGate==null?sourceSearchGuardObservation(this).beforeSearch():retainedGate;
            if(gate!=GhostSourceParentGuardDispatcher.Result.UNHANDLED){
                services.input().releaseAllMovement();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;status="Source parent guard owns selection; "+gate;return;
            }
            var jumpGate=retainedJumpGate==null?sourceJumpBoostBeforeSelection(this):retainedJumpGate;
            if(jumpGate==GhostSourceJumpBoostGate.Result.MODIFY_REQUIRED){sourceBeginJumpBoostHelper();return;}
            if(jumpGate==GhostSourceJumpBoostGate.Result.UNKNOWN){services.input().releaseAllMovement();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;status="Source Jump Boost availability is unobserved";return;}
            if(sourceRetryAtTick!=Long.MIN_VALUE&&p.tickCount<sourceRetryAtTick){services.input().releaseAllMovement();status="Source failed-path retry waits20 native ticks; captured fallback finished";return;}
            sourceRetryAtTick=Long.MIN_VALUE;
            if(sourceIssuedWalkJob!=null&&!sourceIssuedWalkJob.issuedReceipt().scopeCurrent()){sourceStop("Source issued job camera epoch/parent generation revoked");return;}
            if(sourceViewAcquisition.pending()){sourceAcquireViewTick(now);return;}
            if(sourceInstalledWalkCursor!=null&&!sourceAtDestination){sourceCursorTick(now);return;}
            if(sourceAtDestination){sourceMeleeTick(now);return;}
            if(sourceIssuedWalkJob==null){
                sourceSearchReleaseMovement();sourceSearchChild.invalidate();target=null;
                var cohort=sourceSearchCohortObservation(this).orElseThrow(()->new IllegalStateException("Actual source cohort unavailable"));
                sourceIssuedWalkJob=GhostSourceWalkJob.issue(this,cohort);
                sourceTransactionJob=sourceTransactions.issueCurrent(sourceIssuedWalkJob.candidateBodies().stream()
                        .map(v->new GhostSourcePathJobTransaction.Target(v.entity(),v.entity().getUUID())).toList());
                sourceUnreachableToken=GhostSourceUnreachableActorLedger.runtime().issue(sourceIssuedWalkJob.issuedReceipt().scope(),
                        sourceIssuedWalkJob.candidateBodies().stream().map(v->v.entity()).toList());
                GhostSourceUnreachableActorLedger.runtime().cleanupAtNonemptyWrapperInvocation(sourceIssuedWalkJob.candidateBodies().size(),System::currentTimeMillis);
                sourceIssuedWalkJob.beginNativeCapture(client);status="Source SEARCH: actual ranked candidate paths capturing";
            }
            var job=sourceIssuedWalkJob;
            if(job.state()==GhostSourceWalkJob.State.CAPTURING){job.pumpNativeCapture(client);sourceSearchReleaseMovement();status="Source SEARCH: native capture/pure6000 worker";}
            if(job.state()==GhostSourceWalkJob.State.REVOKED){sourceStop("Source native capture unavailable/revoked; no failed-target suppression");return;}
            if(job.state()==GhostSourceWalkJob.State.RESULTS_READY&&!sourceCallbacks.pending()){
                if(job.generatedPaths().isEmpty()){
                    sourceReadyNoPath=job.readyNoPathTargets();
                    job.finalizeFromMappedSelection(new GhostSourcePathResultScoring.Selection(true,List.of(),null));
                    final McGhostSourceFallbackSearch[] issued=new McGhostSourceFallbackSearch[1];
                    issued[0]=McGhostSourceFallbackSearch.issue(client,job,sourceFallbackLifetime,()->sourceFallbackSearch==issued[0]||sourceRetryMovement.registered(issued[0]));
                    sourceFallbackSearch=issued[0];sourceFallbackSearch.begin();sourceSearchReleaseMovement();
                    status="Source no-path: computing captured21-anchor400 fallback before completion callback";return;
                }
                sourceCallbacks.enqueue(job,job,p.tickCount);sourceSearchReleaseMovement();status="Actual produced geometry ready; owned completion callback queued for next tick";
            }
            var callback=sourceCallbacks.take(job,p.tickCount);
            if(callback.isPresent()){
                boolean emptyFallbackReady=job.state()==GhostSourceWalkJob.State.TERMINAL&&job.completedPath()==null
                        &&sourceFallbackSearch!=null&&sourceFallbackSearch.state()==McGhostSourceFallbackSearch.State.READY;
                if(callback.orElseThrow()!=job||(!emptyFallbackReady&&job.state()!=GhostSourceWalkJob.State.RESULTS_READY)||!job.issuedReceipt().scopeCurrent()){
                    sourceStop("Actual queued source job is no longer current");return;
                }
                var failures=(emptyFallbackReady?sourceReadyNoPath:job.readyNoPathTargets()).stream().filter(actor->actor.level()==client.level&&client.level.getEntity(actor.getId())==actor).map(actor->new GhostSourceUnreachableActorLedger.Failure(actor,
                        GhostSourceUnreachableActorLedger.SourceFailurePredicate.WRITE_LEDGER)).toList(); // Source Ghost lambda$findTarget$4 is literalfalse.
                GhostSourceUnreachableActorLedger.runtime().recordReadyNoPath(sourceUnreachableToken,job.issuedReceipt().scope(),
                        GhostSourceUnreachableActorLedger.NativeOutcome.READY_NO_PATH,failures,System::currentTimeMillis);
                if(!emptyFallbackReady){sourceCompletionObservation=McGhostSourcePathResultSelection.observe(client,job);
                    var selected=sourceCompletionObservation.selection();job.finalizeFromMappedSelection(selected);}
                var path=job.completedPath();var actualTarget=path==null?null:new GhostSourcePathJobTransaction.Target(path.target(),path.target().getUUID());
                var result=sourceTransactionJob.resultFromMappedWorker(actualTarget,path==null?null:path.path());
                if(path!=null){
                    if(sourceCompletionObservation==null||!sourceCompletionObservation.current()){sourceStop("Mapped selection frame changed before view acquisition");return;}
                    sourceViewAcquisition.hold(job.issuedReceipt().scope(),actualTarget,result);
                    sourceCompletionObservation=null;sourceFarView.reset();services.input().releaseAllMovement();status="Mapped Ghost result retained; view acquisition begins next ordinary tick";return;
                }
                var outcome=sourceTransactionJob.consume(result,now);sourceCompletionObservation=null;
                if(outcome==GhostSourcePathJobTransaction.Outcome.COMMITTED){status="Source target/cursor/SEARCH receipt committed";sourceCursorTick(now);}
                else if(outcome==GhostSourcePathJobTransaction.Outcome.EMPTY_FAILURE||outcome==GhostSourcePathJobTransaction.Outcome.FAILED_TARGET_SUPPRESSED){
                    // Actual retry helper already scheduled/switches through SourceNativeJobAdapter.
                    invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;services.input().releaseAllMovement();
                }else sourceStop("Source completion rejected: "+outcome);
            }
        }catch(RuntimeException invalid){sourceStop("Source WALK stopped: "+invalid.getMessage());}
    }
    /** Modern acquisition: retain the mapped result; publish no selected target/cursor until actual view is inside comfort. */
    private void sourceAcquireViewTick(long now){
        services.input().releaseAllMovement();var job=sourceIssuedWalkJob;
        if(job==null||job.completedPath()==null){sourceStop("Pending mapped acquisition lost its private path");return;}
        var path=job.completedPath();var mob=path.target();
        var body=new GhostSourcePathJobTransaction.Target(mob,mob.getUUID());
        if(!sourceFreshBody(mob)||!sourceViewAcquisition.current(job.issuedReceipt().scopeCurrent()?job.issuedReceipt().scope():null,body,
                job.remainingGeometryCurrent(client,mob,path.path()))){sourceEnterSearch("Pending target/geometry changed before view acquisition");return;}
        var view=sourceFarViewFrame(mob);
        if(sourceViewAcquisition.observe(job.issuedReceipt().scope(),body,true,view)){
            var result=sourceViewAcquisition.resultForCommit();
            var outcome=sourceTransactionJob.consume(result,now);sourceViewAcquisition.clear();
            if(outcome==GhostSourcePathJobTransaction.Outcome.COMMITTED){status="Source mapped selection committed after actual view acquisition";sourceCursorTick(now);}
            else sourceStop("Source aligned completion rejected: "+outcome);
            return;
        }
        var plan=sourceFarView.advance(view);
        if(!plan.observed()){sourceStop("Pending target view frame unavailable");return;}
        if(!sourceFreshBody(mob)||!job.remainingGeometryCurrent(client,mob,path.path())||!job.issuedReceipt().scopeCurrent()
                ||!sourceFarViewFrame(mob).equals(view)){
            sourceEnterSearch("Pending view authority or geometry changed before camera plan");return;
        }
        services.rotation().aim(plan.yaw(),plan.pitch());status="Turning naturally toward pending mapped Ghost; selection not published";
    }
    private GhostFarTargetView.Frame sourceFarViewFrame(Creeper mob){
        var p=client.player;var eye=p.getEyePosition();var center=mob.getBoundingBox().getCenter();
        return new GhostFarTargetView.Frame(p.tickCount,new GhostSourceSampledRay.Point(eye.x,eye.y,eye.z),
                new GhostSourceSampledRay.Point(center.x,center.y,center.z),p.getYRot(),p.getXRot());
    }
    /** Fallback400 is captured before callback; first5 and sixth install exactly the private returned pair. */
    private boolean sourceRecoveryTick(long now){
        long recoveryReadStart=System.nanoTime();
        // Sixth child enters before captured fallback movement; preparation and its clock continue concurrently.
        // The fallback remains the sole movement/camera writer until its own cursor has completed.
        var turn=GhostSourceRecoveryConcurrency.turn(sourceFallbackCursor!=null,sourceLeavingChild!=null);
        if(turn.prepareLeaving()&&turn.writer()==GhostSourceRecoveryConcurrency.Writer.FALLBACK){
            var child=sourceLeavingChild;long budget=Math.max(0,4_000_000L-(System.nanoTime()-recoveryReadStart));
            child.pump(budget);
            if(!child.current()||child.state()==McGhostSourceLeavingChild.State.REVOKED){sourceStop("Concurrent leaving preparation custody revoked");return true;}
            if(child.state()==McGhostSourceLeavingChild.State.FINISH_PARENT_UNRECOVERED){sourceStop("Concurrent leaving preparation requested unavailable parent transfer");return true;}
            if(child.etherSlotDue()){int slot=child.findActualEtherSlot();if(slot<0){sourceStop("Concurrent leaving Ether tool disappeared");return true;}
                services.inventory().selectSlot(slot);child.observedEtherSlotSelection(slot);}
            if(!sourceLeavingEventTick(child,turn.writer()==GhostSourceRecoveryConcurrency.Writer.LEAVING))return true;
        }
        if(sourceFallbackSearch!=null&&sourceFallbackSearch.state()==McGhostSourceFallbackSearch.State.CAPTURING){
            sourceFallbackSearch.pump();sourceSearchReleaseMovement();
            if(sourceFallbackSearch.state()==McGhostSourceFallbackSearch.State.REVOKED){sourceStop("Source fallback native capture revoked");return true;}
            if(sourceFallbackSearch.state()==McGhostSourceFallbackSearch.State.READY){
                sourceCapturedFallback=sourceFallbackSearch.capturedPair();var job=sourceIssuedWalkJob;
                if(job==null||job.state()!=GhostSourceWalkJob.State.TERMINAL||job.completedPath()!=null||!job.issuedReceipt().scopeCurrent()){
                    sourceStop("Captured fallback source job lost");return true;}
                sourceCallbacks.enqueue(job,job,client.player.tickCount);status="Private400 fallback captured; source failed callback queued";
            }return true;
        }
        if(sourceFallbackCursor!=null){
            var steering=sourceFallbackCursor.observe(client);
            if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.CONTINUE){sourceCoordinateSteering(steering.point(),steering.jump());status="Source captured400 fallback cursor";return true;}
            services.input().releaseAllMovement();
            if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.AT_DESTINATION){sourceFallbackCursor=null;sourceWalkSteering.reset();status="Source fallback reached captured endpoint";}
            else{sourceStop("Source fallback scope/remaining geometry revoked");return true;}
        }
        if(sourceLeavingChild!=null){
            var child=sourceLeavingChild;
            if(sourceLeavingEther!=null){
                if(child.current()&&!sourceLeavingEventTick(child,true))return true;
                var state=sourceLeavingEther.tick();
                if(state==McGhostSourceLeavingEtherBridge.State.ARRIVED){sourceStop("Source leaving Ether arrival confirmed; final parent transfer body unavailable");}
                else if(state==McGhostSourceLeavingEtherBridge.State.UNKNOWN_USE){sourceStop("Issued Ether use outcome unknown; no automatic retry");}
                else if(state==McGhostSourceLeavingEtherBridge.State.REVOKED){sourceStop("Source leaving Ether ownership revoked");}
                return true;
            }
            child.pump(Math.max(0,4_000_000L-(System.nanoTime()-recoveryReadStart)));
            if(child.current()&&!sourceLeavingEventTick(child,true))return true;
            if(child.state()==McGhostSourceLeavingChild.State.REVOKED){sourceStop("Source sixth-failure child context/geometry revoked");return true;}
            if(child.state()==McGhostSourceLeavingChild.State.FINISH_PARENT_UNRECOVERED){sourceStop("Source leaving child requested unreadable parent transfer; inputs released");return true;}
            if(child.etherSlotDue()){int slot=child.findActualEtherSlot();if(slot<0){sourceStop("Source leaving Ether item disappeared");return true;}
                services.inventory().selectSlot(slot);child.observedEtherSlotSelection(slot);}
            if(child.state()==McGhostSourceLeavingChild.State.WALKING){
                var steering=child.observeWalk();if(steering.outcome()==McGhostSourceCoordinateCursor.Outcome.CONTINUE){sourceCoordinateSteering(steering.point(),steering.jump());status="Source separately owned121/2000 leaving cursor";}
                else services.input().releaseAllMovement();return true;
            }
            if(child.state()==McGhostSourceLeavingChild.State.ETHERWARP_READY){
                services.input().releaseAllMovement();
                // The delayed tool action is independent of early path completion; R3 preserves its sampled5..24 deadline.
                if(child.etherSlotDue()){int slot=child.findActualEtherSlot();if(slot<0){sourceStop("Leaving Ether item unavailable");return true;}
                    services.inventory().selectSlot(slot);child.observedEtherSlotSelection(slot);}
                if(services.inventory().selectedSlot()!=child.findActualEtherSlot()){status="Waiting for sampled source Ether slot action";return true;}
                sourceLeavingEther=new McGhostSourceLeavingEtherBridge(client,child,services,config);
                if(!sourceLeavingEther.begin())sourceStop("Source guarded leaving Ether dispatch refused");return true;
            }
            services.input().releaseAllMovement();status="Source sixth-failure121 scan/private2000 path capture";return true;
        }
        return false;
    }
    /** Exact readable source leaving actions. Qualified native event cadence is once per owned modern tick. */
    private boolean sourceLeavingEventTick(McGhostSourceLeavingChild child,boolean ownsNativeActuation){
        boolean queued=services.rotation().sourceBusy();
        boolean pathActive=sourceFallbackCursor!=null||child.state()==McGhostSourceLeavingChild.State.WALKING;
        var event=child.observeActualEvent(queued,pathActive);
        if(!child.current()){sourceStop("Source leaving event custody revoked");return false;}
        if(!ownsNativeActuation){
            // Readable path-active event returns before counter/random actions. It may observe Ether aim,
            // but cannot overwrite the currently installed fallback's camera/keys or dispatch ability use.
            if(event.action()==GhostSourceLeavingLifecycle.Action.FINISH_PARENT){sourceStop("Concurrent leaving event requested unavailable transfer");return false;}
            return true;
        }
        switch(event.action()){
            case SELECT_SLOT_EIGHT -> services.inventory().selectSlot(8);
            case GATED_JUMP -> {if(child.actualJumpGate()==McGhostSourceLeavingChild.EventGate.ALLOWED&&sourceNativeBodyClear(0,0,true))services.input().setJumpHeld(true);}
            case GATED_LEFT_CLICK -> {
                var click=child.emitActualLeftClick();
                status="Source leaving gated native click: "+click;
            }
            case SNEAK -> services.input().setSneakHeld(true);
            case AIM_ETHER -> {
                var endpoint=child.actualLeavingEndpoint();if(endpoint!=null){
                    var eye=client.player.getEyePosition();double dx=endpoint.x()+.5-eye.x,dy=endpoint.y()-eye.y,dz=endpoint.z()+.5-eye.z;
                    float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
                    // The admitted Ether leg certifies its own bounded camera publication.
                    if(sourceLeavingEther!=null&&sourceLeavingEther.state()==McGhostSourceLeavingEtherBridge.State.ACTIVE){
                        if(!sourceLeavingEther.trackSourceAim(yaw,pitch)){sourceStop("Source leaving Ether aim publication revoked");return false;}
                    }else services.rotation().track(yaw,pitch);
                }
            }
            case FINISH_PARENT -> {sourceStop("Source leaving event requested unavailable delegated transfer");return false;}
            case NONE -> {}
        }return child.current();
    }
    private void sourceObserveLedger(long now){
        if(client.level==null)return;
        for(var e:client.level.entitiesForRendering())if(e instanceof Creeper mob&&mob.isPowered())
            ledger.observe(mob.getUUID(),mob.getHealth(),mob.isAlive(),ZealotRangedModule.protectedName(mob.getName().getString()),now);
        if(target instanceof Creeper mob)ledger.observe(mob.getUUID(),mob.getHealth(),mob.isAlive()&&!mob.isRemoved(),blacklist.containsKey(mob.getUUID()),now);
        // Source-specific loot/path continuation is separate pending work. Never run authored patrol navigation here.
    }
    private boolean sourceYieldForHealing(long now){
        if(recovery.state()!=GhostHealReturn.State.IDLE&&recovery.state()!=GhostHealReturn.State.COMPLETE){heal(now);status="Owned heal helper: "+status;return true;}
        boolean low=hp()<=config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT);
        if(low&&(now-lastHeal>=config.get(GhostConfig.GHOST_HEAL_COOLDOWN_MS)||hp()<=config.get(GhostConfig.GHOST_EMERGENCY_HEALTH_PCT))){
            if(!config.get(GhostGrindConfig.GHOST_GRIND_HEAL)||!healReady()){sourceStop("Low health; owned heal helper unavailable");return true;}
            var stack=client.player.getInventory().getItem(healSlot());
            var type=dev.veltraph.client.combat.heal.HealItemType.classify(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getHoverName().getString(),stack.isEmpty());
            if(type!=null&&!dev.veltraph.client.combat.heal.SharedHealUseLedger.cooling(type,now)){
                sourceClearRecovery();sourceClearLoot();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;stopTravel();services.input().releaseAllMovement();
                services.rotation().cancelRotation();settling=true;
            }
        }
        if(settling){services.input().releaseAllMovement();
            if(!client.player.onGround()||Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)>.02){status="Settling for owned Ghost heal helper";return true;}
            beginObservedHeal(now);return true;
        }return false;
    }
    private boolean sourceFreshBody(Creeper mob){
        if(mob==null||client.player==null||client.level==null||mob.level()!=client.level||client.level.getEntity(mob.getId())!=mob
                ||blacklist.containsKey(mob.getUUID())||GhostSourceIdentityIgnore.runtime().contains(mob))return false;
        var body=McGhostSourceBodyEligibilitySnapshot.observe(client,mob,GhostSourceEntityGraceWindow.runtime(),GhostSourcePathFailureSuppression.runtime());
        return body.current()&&body.sourceEligible()&&body.defensiveBodySafe()&&body.current();
    }
    private void sourceCursorTick(long now){
        var cursor=sourceInstalledWalkCursor;var job=sourceIssuedWalkJob;
        if(cursor==null||job==null||!(target instanceof Creeper mob)||!job.issuedReceipt().scopeCurrent()){sourceStop("Source cursor parent/camera authority revoked");return;}
        if(!sourceFreshBody(mob)){sourceEnterSearch("Selected body ended during cursor; source SEARCH resumed");return;}
        if(!job.ownsCompletedResult(mob,job.completedPath().path())||!job.remainingGeometryCurrent(client,mob,job.completedPath().path())){
            sourceEnterSearch("Remaining native geometry changed; source SEARCH resumed");return;}
        var steering=cursor.observe(client);
        switch(steering.outcome()){
            case STALE -> {
                if(job.issuedReceipt().scopeCurrent()&&sourceFreshBody(mob)&&sourceNativeSearchReceipt(this)
                        .map(r->r.current(job.issuedReceipt().scope().parent(),mob)).orElse(false))
                    sourceEnterSearch("Protected actor/ordinary obstruction changed; fresh source path requested");
                else sourceStop("Source cursor custody/unsafe body lost; no old steering emitted");
            }
            case RESEARCH -> sourceEnterSearch("At destination without source target visibility; searching again");
            case AT_DESTINATION -> {sourceAtDestination=true;services.input().releaseAllMovement();status="Source cursor completed; owned melee child";sourceMeleeTick(now);}
            case CONTINUE -> {
                var p=client.player;var motion=p.getDeltaMovement();var selectedPoint=steering.point();
                McGhostOpenWalkClearance variationClearance=null;boolean varied=false;
                if(config.get(GhostGrindConfig.GHOST_SOURCE_OPEN_WALK_VARIATION)&&sourceOpenVariation!=null){
                    var edge=cursor.cachedCurrentEdgeEvidence().orElse(null);
                    if(edge!=null){variationClearance=McGhostOpenWalkClearance.observe(client,this,job,cursor,mob);
                        var alternative=sourceOpenVariation.adapt(cursor,edge.identity(),edge.from(),edge.to(),
                                new GhostSourceSampledRay.Point(p.getX(),p.getY(),p.getZ()),steering.point(),p.onGround(),steering.jump(),variationClearance);
                        if(alternative.outcome()==GhostOpenWalkVariation.Outcome.REVOKED){services.input().releaseAllMovement();sourceEnterSearch("Open corridor changed; safe source path requested");return;}
                        if(alternative.outcome()==GhostOpenWalkVariation.Outcome.VARIED){selectedPoint=alternative.point();varied=true;}
                    }
                }
                var intent=sourceWalkSteering.advance(new GhostSourceWalkSteering.Frame(p.tickCount,
                        new GhostSourceSampledRay.Point(p.getX(),p.getY(),p.getZ()),p.getYRot(),p.getXRot(),motion.x,motion.z,p.onGround(),selectedPoint,steering.jump(),p.getXRot()));
                if(intent.outcome()==GhostSourceWalkSteering.Outcome.REJECTED){sourceStop("Source steering frame rejected");return;}
                if(!sourceFreshBody(mob)||!job.remainingGeometryCurrent(client,mob,job.completedPath().path())||!job.issuedReceipt().scopeCurrent()){sourceStop("Source action boundary revoked");return;}
                if(varied&&(variationClearance==null||!variationClearance.keysClear(intent))){services.input().releaseAllMovement();sourceEnterSearch("Actual varied key sweep blocked; safe path requested");return;}
                var viewFrame=sourceFarViewFrame(mob);var view=sourceFarView.advance(viewFrame);
                if(!view.observed()){sourceStop("Selected Ghost view frame rejected");return;}
                // Path keys use the CURRENT actual yaw. The viewing envelope has no path-planning authority.
                sourceApplyKeys(intent.forward(),intent.back(),intent.left(),intent.right(),intent.jump());
                if(!sourceFreshBody(mob)||!job.remainingGeometryCurrent(client,mob,job.completedPath().path())||!job.issuedReceipt().scopeCurrent()
                        ||!sourceFarViewFrame(mob).equals(viewFrame)){
                    services.input().releaseAllMovement();sourceStop("Source owner/body/geometry/view changed before camera plan");return;}
                services.rotation().aim(view.yaw(),view.pitch());status="Source WALK cursor with loose target view: "+intent.outcome();
            }
        }
    }
    public record ReceivedHitDecision(GhostReceivedHitEnvelope.Frame frame,boolean allowed,boolean finalFrameCurrent,GhostReceivedHitEnvelope.Motion motion) {
        public ReceivedHitDecision(GhostReceivedHitEnvelope.Frame frame,boolean allowed,boolean finalFrameCurrent){this(frame,allowed,finalFrameCurrent,null);}
    }
    private ReceivedHitDecision sourceReceivedHitDecision;
    /** Cached geometric veto evidence only; never evaluates or grants attack authority. */
    public ReceivedHitDecision receivedHitDecision(){return sourceReceivedHitDecision;}
    private GhostReceivedHitEnvelope.Frame sourceReceivedHitFrame(Creeper mob,GhostSourceWalkJob job){
        var p=client.player;if(!client.isSameThread()||p==null||client.level==null||client.getConnection()==null||mob==null
                ||target!=mob||mob.level()!=client.level||client.level.getEntity(mob.getId())!=mob||!isEnabled()
                ||dev.veltraph.client.VeltraphBootstrap.moduleManager().inputOwner().orElse(null)!=this
                ||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=this
                ||services.inventory().selectedSlot()!=weapon()||!weaponReady(weapon()))return null;
        // Native spawn and applied position packets initialize/update this exact actor's codec base.
        return new GhostReceivedHitEnvelope.Frame(p,client.level,client.getConnection(),mob,mob.getUUID(),job,
                dev.veltraph.client.service.mc.McOwnedRotation.epoch(client),p.tickCount,services.inventory().selectedSlot(),p.getEyePosition(),p.getLookAngle(),
                mob.position(),mob.getPositionCodec().getBase(),mob.getClientPosition(),mob.getBoundingBox(),mob.getPickRadius());
    }
    private void sourceMeleeTick(long now){
        var job=sourceIssuedWalkJob;var p=client.player;
        if(job==null||!(target instanceof Creeper mob)||!sourceFreshBody(mob)||!job.issuedReceipt().scopeCurrent()
                ||!sourceNativeSearchReceipt(this).map(r->r.current(job.issuedReceipt().scope().parent(),mob)).orElse(false)
                ||!job.remainingGeometryCurrent(client,mob,job.completedPath().path())){sourceEnterSearch("Selected native body no longer eligible; searching");return;}
        if(p.distanceTo(mob)>3.6||!p.hasLineOfSight(mob)){sourceEnterSearch("Moving Ghost left melee volume; actual source paths requested");return;}
        var eye=p.getEyePosition();var point=mob.getBoundingBox().getCenter();double dx=point.x-eye.x,dy=point.y-eye.y,dz=point.z-eye.z;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        var angles=aim.step(p.getYRot(),p.getXRot(),yaw,pitch,2,3,config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER));
        services.rotation().aim(angles.yaw(),angles.pitch());
        var here=position(p);var boss=position(mob);double radialX=here.x()-boss.x(),radialZ=here.z()-boss.z(),radialLength=Math.max(.01,Math.hypot(radialX,radialZ));
        double tangentX=-radialZ/radialLength*.8,tangentZ=radialX/radialLength*.8;
        boolean leftBody=sourceNativeBodyClear(tangentX,tangentZ,false);
        Boolean leftSupport=leftBody?walk.standingClear(new Vec3(here.x()+tangentX,here.y(),here.z()+tangentZ)):null;
        boolean leftSafe=leftBody&&Boolean.TRUE.equals(leftSupport);
        boolean rightBody=sourceNativeBodyClear(-tangentX,-tangentZ,false);
        Boolean rightSupport=rightBody?walk.standingClear(new Vec3(here.x()-tangentX,here.y(),here.z()-tangentZ)):null;
        boolean rightSafe=rightBody&&Boolean.TRUE.equals(rightSupport);
        var observedCurrentHit=client.hitResult;
        boolean exactHit=observedCurrentHit instanceof EntityHitResult current&&current.getEntity()==mob&&current.getLocation().distanceTo(eye)<=3;
        net.minecraft.world.phys.HitResult observedFreshHit=null;
        if(exactHit){observedFreshHit=p.raycastHitResult(1,p);exactHit=observedFreshHit instanceof EntityHitResult freshHit&&freshHit.getEntity()==mob&&freshHit.getLocation().distanceTo(eye)<=3;}
        var targetMotion=mob.getDeltaMovement();
        var scope=new GhostConstrainedMeleeStandoff.Scope(this,p,client.level,client.getConnection(),job,mob,dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
        boolean playerGrounded=p.onGround();
        boolean standingBody=sourceNativeBodyClear(0,0,false);Boolean standingSupport=standingBody?walk.standingClear(here):null;
        int playerHurt=p.hurtTime;Integer evaluatedSwell=null;Boolean evaluatedIgnited=null;
        boolean hazard=playerHurt>0;
        if(!hazard){evaluatedSwell=mob.getSwellDir();hazard=evaluatedSwell>0;if(!hazard){evaluatedIgnited=mob.isIgnited();hazard=evaluatedIgnited;}}
        var standoffFrame=new GhostConstrainedMeleeStandoff.Frame(scope,p.tickCount,here,boss,
            new Vec3(targetMotion.x,targetMotion.y,targetMotion.z),2.25,exactHit,playerGrounded,standingBody&&Boolean.TRUE.equals(standingSupport),leftSafe,rightSafe,hazard);
        var stayForecast=McGhostMeleeStayForecast.observe(client,this,job,mob,standoffFrame,walk).orElse(null);
        var standoff=sourceMeleeStandoff.observe(standoffFrame,stayForecast);
        var box=mob.getBoundingBox();
        sourceStandoffDecision=new StandoffDecisionDiagnostics(++sourceStandoffSerial,p.tickCount,mob.getUUID(),standoffFrame,sourceMeleeStandoff.cachedObservation(),standoff,
            mob.onGround(),mob.isNoAi(),new StandoffBox(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ),mob.getHealth(),standoffFootDiagnostic(box),
            leftBody,leftSupport,rightBody,rightSupport,standingBody,standingSupport,
            standoffHitDiagnostic(observedCurrentHit,mob,eye),standoffHitDiagnostic(observedFreshHit,mob,eye),playerHurt,evaluatedSwell,evaluatedIgnited,
            dev.veltraph.client.VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==this,dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==this);
        if(standoff.outcome()==GhostConstrainedMeleeStandoff.Outcome.REJECTED){sourceStop("Modern constrained melee frame rejected");return;}
        var keys=movement.choose(here,boss,p.getYRot(),2.25,false,standoff.outcome()==GhostConstrainedMeleeStandoff.Outcome.HOLD,
            next->sourceNativeBodyClear(next.x()-p.getX(),next.z()-p.getZ(),false),List.of());
        if(!sourceFreshBody(mob)||!job.issuedReceipt().scopeCurrent()||!job.remainingGeometryCurrent(client,mob,job.completedPath().path())){sourceStop("Source melee body/owner/geometry revoked");return;}
        if(standoff.outcome()==GhostConstrainedMeleeStandoff.Outcome.HOLD&&stayForecast!=null&&!stayForecast.revalidate()){
            sourceStop("Qualified melee stay forecast revoked before input");return;
        }
        sourceApplyKeys(keys.forward(),keys.back(),keys.left(),keys.right(),false);status="Source melee: native hit-volume tracking";
        if(client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==mob&&hit.getLocation().distanceTo(eye)<=3
                &&p.raycastHitResult(1,p) instanceof EntityHitResult fresh&&fresh.getEntity()==mob&&fresh.getLocation().distanceTo(eye)<=3
                &&p.getAttackStrengthScale(0)>=.95&&now-lastAttack>=100&&sourceFreshBody(mob)&&job.issuedReceipt().scopeCurrent()
                &&job.remainingGeometryCurrent(client,mob,job.completedPath().path())){
            var receivedHit=sourceReceivedHitFrame(mob,job);
            var receivedMotion=new GhostReceivedHitEnvelope.Motion(mob.onGround(),mob.getDeltaMovement());
            boolean receivedAllowed=GhostReceivedHitEnvelope.motionAllows(receivedHit,receivedMotion);
            sourceReceivedHitDecision=new ReceivedHitDecision(receivedHit,receivedAllowed,false,receivedMotion);
            if(!receivedAllowed)return; // Continue ordinary aim/movement; do not consume attack cooldown or invent a landing pose.
            if(!sourceFreshBody(mob)||!job.issuedReceipt().scopeCurrent()||!job.remainingGeometryCurrent(client,mob,job.completedPath().path())
                    ||!GhostReceivedHitEnvelope.same(receivedHit,sourceReceivedHitFrame(mob,job))
                    ||!GhostReceivedHitEnvelope.sameMotion(receivedMotion,new GhostReceivedHitEnvelope.Motion(mob.onGround(),mob.getDeltaMovement())))return;
            sourceReceivedHitDecision=new ReceivedHitDecision(receivedHit,true,true,receivedMotion);
            float beforeHealth=mob.getHealth();var beforeMotion=mob.getDeltaMovement();
            ledger.attack(mob.getUUID(),beforeHealth,now);client.gameMode.attack(p,mob);
            p.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
            p.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);lastAttack=now;
            var afterMotion=mob.getDeltaMovement();sourceStandoffAttack=new StandoffAttackDiagnostics(sourceStandoffDecision==null?0:sourceStandoffDecision.serial(),p.tickCount,mob.getUUID(),beforeHealth,mob.getHealth(),
                new Vec3(beforeMotion.x,beforeMotion.y,beforeMotion.z),new Vec3(afterMotion.x,afterMotion.y,afterMotion.z),mob.hurtTime);
        }
    }
    /** Diagnostic read at the actual decision boundary; never used as control/support permission. */
    private java.util.List<StandoffFootCorner> standoffFootDiagnostic(net.minecraft.world.phys.AABB box){
        var values=new java.util.ArrayList<StandoffFootCorner>();
        for(double x:new double[]{box.minX+.0001,box.maxX-.0001})for(double z:new double[]{box.minZ+.0001,box.maxZ-.0001}){
            int bx=net.minecraft.util.Mth.floor(x),by=net.minecraft.util.Mth.floor(box.minY-.001),bz=net.minecraft.util.Mth.floor(z);
            var pos=new net.minecraft.core.BlockPos(bx,by,bz);
            if(!client.level.getChunkSource().hasChunk(bx>>4,bz>>4)){values.add(new StandoffFootCorner(bx,by,bz,x,z,false,null,false,java.util.List.of(),false,null,false));continue;}
            var state=client.level.getBlockState(pos);var boxes=new java.util.ArrayList<StandoffBox>();Double[] observedTop={null};int[] visited={0};
            // Avoid materializing every AABB. Native VoxelShape traversal still runs in full;
            // only the first16 immutable descriptors are retained, and truncation is explicit.
            state.getCollisionShape(client.level,pos).forAllBoxes((minX,minY,minZ,maxX,maxY,maxZ)->{
                if(++visited[0]>16)return;boxes.add(new StandoffBox(minX,minY,minZ,maxX,maxY,maxZ));
                double localX=x-bx,localZ=z-bz;if(localX>=minX&&localX<=maxX&&localZ>=minZ&&localZ<=maxZ)observedTop[0]=observedTop[0]==null?by+maxY:Math.max(observedTop[0],by+maxY);
            });
            boolean truncated=visited[0]>16;Double top=observedTop[0];values.add(new StandoffFootCorner(bx,by,bz,x,z,true,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                !state.getFluidState().isEmpty(),boxes,truncated,top,!truncated&&top!=null&&Math.abs(top-box.minY)<.02));
        }
        return java.util.List.copyOf(values);
    }
    private static StandoffHit standoffHitDiagnostic(net.minecraft.world.phys.HitResult hit,Creeper target,net.minecraft.world.phys.Vec3 eye){
        return hit==null?new StandoffHit(null,false,false,null):new StandoffHit(hit.getType().name(),true,hit instanceof EntityHitResult entity&&entity.getEntity()==target,hit.getLocation().distanceTo(eye));
    }
    private void sourceApplyKeys(boolean forward,boolean back,boolean left,boolean right,boolean jump){
        var p=client.player;double yaw=Math.toRadians(p.getYRot());int f=(forward?1:0)-(back?1:0),l=(left?1:0)-(right?1:0);
        double n=Math.hypot(f,l),dx=n==0?0:(-Math.sin(yaw)*f+Math.cos(yaw)*l)/n*.35,dz=n==0?0:(Math.cos(yaw)*f+Math.sin(yaw)*l)/n*.35;
        boolean clear=n==0||sourceNativeBodyClear(dx,dz,false);
        boolean canJump=jump&&p.onGround()&&sourceNativeBodyClear(0,0,true);
        services.input().setForwardHeld(clear&&forward);services.input().setBackHeld(clear&&back);
        services.input().setLeftHeld(clear&&left);services.input().setRightHeld(clear&&right);
        services.input().setSneakHeld(false);services.input().setSprintHeld(false);services.input().setJumpHeld(canJump);
    }
    /** Native modern forecast uses the existing loaded/body/F23/actor veto, without emitting keys. */
    boolean sourceForecastActorStepClear(double dx,double dz){return client.isSameThread()&&sourceNativeBodyClear(dx,dz,false);}
    private boolean sourceNativeBodyClear(double dx,double dz,boolean jump){
        var p=client.player;if(p==null||client.level==null||!Double.isFinite(dx)||!Double.isFinite(dz))return false;
        var box=p.getBoundingBox().move(dx,jump?.42:0,dz);
        for(int x=net.minecraft.util.Mth.floor(box.minX);x<=net.minecraft.util.Mth.floor(box.maxX);x++)
            for(int y=net.minecraft.util.Mth.floor(box.minY);y<=net.minecraft.util.Mth.floor(box.maxY);y++)
                for(int z=net.minecraft.util.Mth.floor(box.minZ);z<=net.minecraft.util.Mth.floor(box.maxZ);z++){
                    var pos=new net.minecraft.core.BlockPos(x,y,z);if(!client.level.isLoaded(pos))return false;
                if(y==net.minecraft.util.Mth.floor(box.minY)&&sourceInputForbiddenCells.contains(new GhostSourceWalkFrontier.Cell(x,y,z)))return false;
                    var state=client.level.getBlockState(pos);if(state.is(net.minecraft.world.level.block.Blocks.LAVA))return false;
                }
        if(!client.level.noCollision(p,box))return false;
        for(var e:client.level.entitiesForRendering())if(e!=p&&e!=target&&e instanceof LivingEntity living&&living.isAlive()&&!e.isInvisible()
                &&e.getBoundingBox().intersects(box))return false;
        return sourceParentAreaStamp(this).isPresent();
    }
    private void sourceEnterScheduledSearch(){
        // Keep the exact old pair; a newly issued SEARCH is not authority to rebuild old custody.
        var producer=sourceFallbackSearch;var cursor=sourceFallbackCursor;
        if(producer==null||cursor==null){sourceEnterSearch("Scheduled source20-tick SEARCH entry");return;}
        if(!cursor.current(client)){sourceStop("Scheduled SEARCH old fallback custody revoked");return;}
        sourceFallbackSearch=null;sourceCapturedFallback=null;sourceFallbackCursor=null;
        var retainedSteering=sourceWalkSteering;sourceWalkSteering=new GhostSourceWalkSteering();
        sourceEnterSearch("Scheduled source20-tick SEARCH entry with retained global fallback");
        sourceRetryMovement.retain(producer,cursor);sourceRetainedFallbackSteering=retainedSteering;
        if(!cursor.current(client)){sourceStop("Scheduled SEARCH retained construction custody changed");}
    }
    private void sourceEnterSearch(String reason){
        sourceClearRecovery();sourceClearLoot();sourceFallbackLifetime.sourceSearchEntry();
        services.input().releaseAllMovement();invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;
        sourceAtDestination=false;aim.reset();movement.reset();status=reason;
    }
    private void sourceStop(String reason){sourceClearRecovery();sourceClearLoot();status=reason;services.input().releaseAllMovement();setEnabled(false);}
    private final class SourceNativeJobAdapter implements GhostSourcePathJobTransaction.RuntimeParentAdapter {
        public GhostSourcePathJobTransaction.Scope captureActualCurrentJob(){return sourceIssuedWalkJob!=null&&sourceIssuedWalkReceipt!=null
                &&sourceIssuedWalkReceipt.scopeCurrent()?sourceIssuedWalkReceipt.scope():null;}
        public GhostSourcePathJobTransaction.Target findActualCurrentTarget(UUID uuid){
            if(sourceIssuedWalkJob==null||client.level==null)return null;
            for(var body:sourceIssuedWalkJob.candidateBodies())if(body.entity().getUUID().equals(uuid)&&body.entity().level()==client.level&&client.level.getEntity(body.entity().getId())==body.entity())
                return new GhostSourcePathJobTransaction.Target(body.entity(),uuid);return null;
        }
        public GhostSourcePathJobTransaction.CommittedSelection commitTargetAndCursor(GhostSourcePathJobTransaction.Scope expected,
                GhostSourcePathJobTransaction.Target nativeTarget,GhostSourcePathJobTransaction.WalkPath path){
            var job=sourceIssuedWalkJob;var actual=captureActualCurrentJob();
            if(job==null||actual==null||!expected.same(actual)||!sourceViewAcquisition.ready()
                    ||!(nativeTarget.nativeIdentity() instanceof Creeper mob)
                    ||!mob.getUUID().equals(nativeTarget.nativeUuid())||!job.ownsCompletedResult(mob,path)||!sourceFreshBody(mob)
                    ||!job.remainingGeometryCurrent(client,mob,path))return null;
            var cursor=McGhostSourceOwnedWalkCursor.pending(job);
            if(!expected.same(captureActualCurrentJob())||!sourceViewAcquisition.observe(expected,nativeTarget,true,sourceFarViewFrame(mob))
                    ||!sourceFreshBody(mob)||!job.remainingGeometryCurrent(client,mob,path)
                    ||!expected.same(captureActualCurrentJob())||!GhostFarTargetView.acquisitionReady(sourceFarViewFrame(mob)))return null;
            // One actual client-thread commit. No target field is written during issue, capture, solving or scoring.
            sourceClearRetainedFallback();
            target=mob;sourceInstalledWalkCursor=cursor;sourceAtDestination=false;
            sourceSearchChild.observeSelected(expected.parent(),mob);
            sourceOpenVariation=new GhostOpenWalkVariationRun(cursor,java.util.concurrent.ThreadLocalRandom.current().nextLong());sourceWalkSteering.reset();aim.reset();movement.reset();
            if(!cursor.current(client)||sourceNativeSelectedTarget(GhostGrindModule.this)!=mob
                    ||sourceNativeSearchReceipt(GhostGrindModule.this).filter(r->r.current(expected.parent(),mob)).isEmpty()){
                sourceEnterSearch("Atomic source cursor publication validation failed");return null;
            }
            var stamp=sourceParentAreaStamp(GhostGrindModule.this).orElseThrow();
            return new GhostSourcePathJobTransaction.CommittedSelection(GhostGrindModule.this,sourceNativeChildIdentity(GhostGrindModule.this),sourceInstalledWalkCursor,
                    stamp.generation(),stamp.settingsRevision(),client.player,client.level,client.getConnection(),
                    new GhostSourcePathJobTransaction.Target(target,target.getUUID()),sourceInstalledWalkCursor.sourcePathIdentity());
        }
        public boolean dispatchFailedPathContinuation(GhostSourcePathJobTransaction.Scope expected,GhostSourcePathJobTransaction.Target failedTarget){
            if(!expected.same(captureActualCurrentJob()))return false;
            if(sourceFallbackSearch==null||sourceFallbackSearch.state()!=McGhostSourceFallbackSearch.State.READY)return false;
            var retry=sourceRetry.recordIssuedFailure();sourceClearRetainedFallback();services.input().releaseAllMovement();
            if(retry.next()==GhostSourceSearchRetryCounter.Next.SWITCH_PARENT_RECOVERY_CHILD){
                final McGhostSourceLeavingChild[] issued=new McGhostSourceLeavingChild[1];
                issued[0]=McGhostSourceLeavingChild.issue(client,GhostGrindModule.this,sourceLeavingPolicy.policy(),()->sourceLeavingChild==issued[0]);
                if(sourceCapturedFallback!=null&&!sourceFallbackSearch.authorizeSixthChildHandoff(sourceCapturedFallback,issued[0]))return false;
                sourceLeavingChild=issued[0];sourceRetryAtTick=Long.MIN_VALUE;
            }else sourceRetryAtTick=(long)client.player.tickCount+retry.delayTicks();
            if(sourceCapturedFallback!=null){sourceFallbackCursor=sourceCapturedFallback.cursor();sourceFallbackLifetime.installed(sourceCapturedFallback.anchor());sourceWalkSteering.reset();}
            status=sourceLeavingChild==null?"Source retry20 scheduled and captured400 fallback installed":"Source sixth child installed with exact captured400 fallback";
            return true;
        }
    }
    private void tick(){
        var p=client.player;long now=System.currentTimeMillis();blacklist.entrySet().removeIf(e->now>=e.getValue());ignoredDrops.entrySet().removeIf(e->now>=e.getValue());
        var candidates=targets();for(var t:candidates)ledger.observe(t.entity().getUUID(),t.entity().getHealth(),t.entity().isAlive(),t.protectedTarget(),now);
        if(recovery.state()!=GhostHealReturn.State.IDLE&&recovery.state()!=GhostHealReturn.State.COMPLETE){heal(now);return;}
        boolean low=hp()<=config.get(GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT);
        if(low&&(now-lastHeal>=config.get(GhostConfig.GHOST_HEAL_COOLDOWN_MS)||hp()<=config.get(GhostConfig.GHOST_EMERGENCY_HEALTH_PCT))){
            if(!config.get(GhostGrindConfig.GHOST_GRIND_HEAL)||!healReady()){status="Low health; heal disabled/missing, stopped";setEnabled(false);return;}
            var healStack=p.getInventory().getItem(healSlot());
            var type=dev.veltraph.client.combat.heal.HealItemType.classify(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(healStack.getItem()).toString(),
                    healStack.getHoverName().getString(),healStack.isEmpty());
            // Source readiness false returns to the parent rather than disabling it.
            settling=type!=null&&!dev.veltraph.client.combat.heal.SharedHealUseLedger.cooling(type,now);
        }
        if(settling){
            stopTravel();services.input().releaseAllMovement();status="Settling before owned heal snapshot";
            if(!p.onGround()||Math.hypot(p.getDeltaMovement().x,p.getDeltaMovement().z)>.02)return;
            beginObservedHeal(now);return;
        }
        if(dropId>=0&&ledger.pickup(dropName,inventoryCount(dropName))){dropId=-1;walk.stop();walkActive=false;status="Loot pickup acknowledged by inventory";return;}
        ItemEntity drop=null;if(config.get(GhostGrindConfig.GHOST_GRIND_COLLECT_DROPS))for(var e:client.level.entitiesForRendering())
            if(e instanceof ItemEntity i&&i.isAlive()&&!ignoredDrops.containsKey(i.getId())&&GhostLedger.DROPS.contains(clean(i.getItem().getHoverName().getString()))&&i.distanceToSqr(p)<24*24
                    &&(i.getOwner()==null||i.getOwner()==p)&&(drop==null||i.distanceToSqr(p)<drop.distanceToSqr(p)))drop=i;
        if(drop!=null){
            routeTravel.stop();routeActive=false;if(fullInventory()){inventoryReturn=true;dropId=-1;ledger.abandonPickup();}
            else {
                if(dropId!=drop.getId()){dropId=drop.getId();dropName=clean(drop.getItem().getHoverName().getString());ledger.beginPickup(dropName,inventoryCount(dropName));dropDeadline=now+12000;walkActive=false;}
                if(now>dropDeadline){ignoredDrops.put(dropId,now+60000);dropId=-1;ledger.abandonPickup();walk.stop();walkActive=false;}
                else{approach(position(drop),now);status="Collecting "+dropName+"; awaiting inventory acknowledgment";return;}
            }
        }
        if(dropId>=0){services.input().releaseAllMovement();if(now<dropDeadline){status="Drop disappeared; awaiting inventory acknowledgment";return;}ignoredDrops.put(dropId,now+60000);dropId=-1;ledger.abandonPickup();}
        if(inventoryReturn){if(!walkActive)walkActive=walk.start(route.nodes().get(checkpoint).position());if(walkActive&&walk.tick()==WalkController.State.WALKING){status="Inventory full; returning to route before restock stop";return;}status="Inventory full at route; sell/restock explicitly before restart";setEnabled(false);return;}
        var valid=candidates.stream().filter(t->t.entity().isAlive()&&!t.protectedTarget()&&!blacklist.containsKey(t.entity().getUUID())).toList();
        Target selected=valid.stream().filter(t->t.entity()==target).findFirst().orElseGet(()->valid.stream().min(Comparator.comparingDouble(t->t.entity().distanceToSqr(p))).orElse(null));
        if(selected==null){invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=null;walk.stop();walkActive=false;patrol();return;}
        if(target!=selected.entity()){invalidateSourceIssuedJob();sourceSearchChild.invalidate();target=selected.entity();aim.reset();movement.reset();}
        routeTravel.stop();routeActive=false;combat(target,now);
    }
    private void combat(LivingEntity mob,long now){
        var p=client.player;if(!weaponReady(weapon())){status="Ghost weapon missing";setEnabled(false);return;}
        if(services.inventory().selectedSlot()!=weapon()){status="Ghost weapon slot ownership changed";setEnabled(false);return;}
        Vec3 here=position(p),goal=position(mob);double distance=here.horizontalDistanceTo(goal);
        if(!p.hasLineOfSight(mob)&&!TravelPath.walkClear(walk,here,goal)){approach(goal,now);status="Approaching Ghost around obstacle";return;}
        if(walkActive){walk.stop();walkActive=false;}
        var point=mob.getBoundingBox().getCenter();var eye=p.getEyePosition();double dx=point.x-eye.x,dy=point.y-eye.y,dz=point.z-eye.z;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        var angles=aim.step(p.getYRot(),p.getXRot(),yaw,pitch,distance<3?2:10,distance<3?3:12,config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER));services.rotation().aim(angles.yaw(),angles.pitch());
        var keys=movement.choose(here,goal,angles.yaw(),2.25,false,false,next->TravelPath.walkClear(walk,here,next),List.of());
        services.input().releaseAllMovement();services.input().setForwardHeld(keys.forward());services.input().setBackHeld(keys.back());services.input().setLeftHeld(keys.left());services.input().setRightHeld(keys.right());
        status="Ghost approach/attack";
        if(client.hitResult instanceof EntityHitResult hit&&hit.getEntity()==mob&&hit.getLocation().distanceTo(eye)<=3&&p.hasLineOfSight(mob)&&p.getAttackStrengthScale(0)>=.95&&now-lastAttack>=100
                &&p.raycastHitResult(1,p) instanceof EntityHitResult currentHit&&currentHit.getEntity()==mob&&currentHit.getLocation().distanceTo(eye)<=3){
            int slot=weapon();int daed=config.get(GhostGrindConfig.GHOST_GRIND_DAEDALUS_SLOT)-1;
            if(daed>=0&&weaponReady(daed)&&clean(p.getInventory().getItem(daed).getHoverName().getString()).toLowerCase(Locale.ROOT).contains("daedalus")&&mob.getHealth()/Math.max(1,mob.getMaxHealth())*100<=config.get(GhostGrindConfig.GHOST_GRIND_DAEDALUS_HEALTH_PCT))services.inventory().selectSlot(daed);
            try{ledger.attack(mob.getUUID(),mob.getHealth(),now);client.gameMode.attack(p,mob);p.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);p.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);lastAttack=now;}
            finally{if(services.inventory().selectedSlot()==daed)services.inventory().selectSlot(slot);}
        }
    }
    private java.util.Optional<dev.veltraph.client.combat.heal.HealthObservation> recoveryHealth(long now){
        var captured=dev.veltraph.client.combat.heal.mc.McCapturedHudVitals.current(client,now);
        if(captured.isPresent())return captured.map(dev.veltraph.client.combat.heal.HealthObservation::captured);
        return config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED)?java.util.Optional.empty():dev.veltraph.client.combat.heal.mc.McReceivedHealth.current(client,now);
    }
    /** Read time belongs to this provider boundary; the caller's FSM deadline stays unchanged. */
    static java.util.Optional<dev.veltraph.client.combat.heal.HealthObservation> readRecoveryHealthAtCurrentClock(
            java.util.function.LongSupplier clock,java.util.function.LongFunction<java.util.Optional<dev.veltraph.client.combat.heal.HealthObservation>> provider){
        return provider.apply(clock.getAsLong());
    }
    /** Both source and authored-route heal starts use exactly the same raw received comparison basis. */
    private boolean beginObservedHeal(long now){
        var observed=readRecoveryHealthAtCurrentClock(System::currentTimeMillis,this::recoveryHealth);if(observed.isEmpty()){status="Fresh recovery health unavailable; no heal use";setEnabled(false);return false;}
        var p=client.player;healHealthBasis=dev.veltraph.client.combat.heal.HealHealthObservationBasis.begin(
                recovery,position(p),p.getYRot(),p.getXRot(),services.inventory().selectedSlot(),observed.get(),now);
        healItem=p.getInventory().getItem(healSlot()).copy();settling=false;healPitch.reset();
        var values=new LinkedHashMap<dev.veltraph.client.config.ConfigKey<?>,Object>();for(var key:HEAL_RETURN_KEYS)values.put(key,config.get(key));healReturnConfig=Map.copyOf(values);
        healReturnDriver.start(new GhostHealDirectReturn.Scope(this,p,client.level,client.getConnection(),recovery.pose()));return true;
    }
    private void heal(long now){
        var p=client.player;if(recovery.state()!=GhostHealReturn.State.RETURN||healReturnDriver.mode()!=GhostHealDirectReturn.Mode.DIRECT)services.input().releaseAllMovement();
        if(recovery.state()==GhostHealReturn.State.AIM&&services.inventory().selectedSlot()!=recovery.pose().slot()){status="Heal start slot ownership changed";setEnabled(false);return;}
        int expectedHealSlot=recovery.castRefused()?recovery.pose().slot():healSlot();
        if(recovery.state()!=GhostHealReturn.State.AIM&&services.inventory().selectedSlot()!=expectedHealSlot){status="Heal temporary-slot ownership changed";setEnabled(false);return;}
        var pos=p.blockPosition().above(2);boolean clear=client.level.getBlockState(pos).getCollisionShape(client.level,pos).isEmpty();
        var observed=recoveryHealth(now);
        double observedHealth=healHealthBasis==null?recovery.pose().health():healHealthBasis.health(observed);
        float observedAbsorption=healHealthBasis==null?recovery.pose().absorption():healHealthBasis.absorption(observed);
        var action=recovery.tick(position(p),p.getYRot(),p.getXRot(),observedHealth,observedAbsorption,clear,p.onGround(),Math.hypot(p.getDeltaMovement().x,p.getDeltaMovement().z),now,config.get(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS),config.get(GhostGrindConfig.GHOST_GRIND_HEAL_TIMEOUT_MS));status=recovery.status();
        switch(action){
            case AIM_DOWN -> services.rotation().aim(p.getYRot(),healPitch.stepLinear(p.getXRot(),80,config.get(RotationConfig.ROTATION_SPEED_MULTIPLIER)));
            case CAST -> {if(!healReady()||!ItemStack.matches(healItem,p.getInventory().getItem(healSlot()))){status="Heal sword changed before use";setEnabled(false);return;}var type=dev.veltraph.client.combat.heal.HealItemType.classify(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(healItem.getItem()).toString(),
                    healItem.getHoverName().getString(),healItem.isEmpty());
                if(type==null){status="Heal item is outside the source classifier; restoring";recovery.refuseCast();return;}
                if(dev.veltraph.client.combat.heal.SharedHealUseLedger.cooling(type,now)){
                    status="Shared heal type is cooling; restoring without use";recovery.refuseCast();return;
                }
                if(!dev.veltraph.client.combat.heal.CapturedHudVitals.mayEmitHeal(
                        dev.veltraph.client.combat.heal.mc.McCapturedHudVitals.current(client,now),config.get(GhostConfig.GHOST_CAPTURED_HUD_REQUIRED),type)){
                    status="Received mana does not permit heal use; restoring";recovery.refuseCast();return;
                }
                services.inventory().selectSlot(healSlot());
                // Source common helper records before ordinary use, never on mana/health prediction.
                dev.veltraph.client.combat.heal.SharedHealUseLedger.recordEmittedUse(type,now);
                client.gameMode.useItem(p,InteractionHand.MAIN_HAND);lastHeal=now;
                if(healHealthBasis!=null){long emittedAt=System.currentTimeMillis();healHealthBasis.issued(recoveryHealth(emittedAt),emittedAt);}}
            case WALK_RETURN -> {
                if(!p.onGround()){services.input().releaseAllMovement();status="Heal acknowledged; settling native knockback before physical return";return;}
                var scope=healReturnDriver.scope();if(!healReturnNativeCurrent(scope)){status="Heal direct-return custody revoked";setEnabled(false);return;}
                var clearance=new McGhostHealReturnClearance(client,this,scope);var motion=p.getDeltaMovement();
                var result=healReturnDriver.advance(new GhostHealDirectReturn.Frame(scope,p.tickCount,position(p),p.getYRot(),p.getXRot(),motion.x,motion.z,p.onGround()),clearance);
                if(result.outcome()==GhostHealDirectReturn.Outcome.REJECTED){status=result.reason();setEnabled(false);return;}
                if(result.outcome()==GhostHealDirectReturn.Outcome.PENDING){if(!clearance.current()){status="Heal pending-return custody changed before release";setEnabled(false);return;}services.input().releaseAllMovement();status=result.reason();return;}
                if(result.outcome()==GhostHealDirectReturn.Outcome.PATH){
                    if(!clearance.current()){status="Heal path-return custody changed during direct admission";setEnabled(false);return;}
                    if(!walkActive)walkActive=walk.start(recovery.pose().position(),2500,32,GhostHealReturn.RETURN_POSITION_RADIUS);
                    if(!clearance.current()){services.input().releaseAllMovement();status="Heal path-return custody changed before path tick";setEnabled(false);return;}
                    if(!walkActive||walk.tick()==WalkController.State.BLOCKED){status="Failed physical heal return: "+walk.status();setEnabled(false);return;}
                }else{
                    if(!clearance.current()){status="Heal direct-return frame changed before keys";setEnabled(false);return;}
                    var intent=result.intent();services.input().setForwardHeld(intent.forward());services.input().setBackHeld(intent.back());services.input().setLeftHeld(intent.left());services.input().setRightHeld(intent.right());services.input().setJumpHeld(false);services.input().setSneakHeld(false);services.input().setSprintHeld(false);
                    if(!clearance.current()){services.input().releaseAllMovement();status="Heal direct-return custody changed before camera";setEnabled(false);return;}
                    // Current-yaw ordinary keys restore the position while the existing
                    // bounded camera service restores the saved view concurrently.
                    services.rotation().track(recovery.pose().yaw(),recovery.pose().pitch());status=result.reason();
                }
            }
            case RESTORE_VIEW -> {walk.stop();walkActive=false;services.rotation().track(recovery.pose().yaw(),recovery.pose().pitch());}
            case COMPLETE -> {walk.stop();walkActive=false;restoreHealSlot();if(!recovery.castRefused()){heals++;returns++;}aim.reset();yawLimit.reset();pitchLimit.reset();}
            case FAILED -> {status=recovery.status();setEnabled(false);}
            case NONE -> {}
        }
    }
    /** Same paused-heal parent only; this is not a source cursor/job or a new ACK. */
    boolean healReturnNativeCurrent(GhostHealDirectReturn.Scope scope){
        var p=client.player;var manager=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        if(scope==null||scope!=healReturnDriver.scope()||scope.parent()!=this||scope.pose()!=recovery.pose()||recovery.state()!=GhostHealReturn.State.RETURN
            ||healReturnConfig.size()!=HEAL_RETURN_KEYS.size()||!client.isSameThread()||!isEnabled()||p==null||p!=owner||p!=scope.player()||client.level!=world||client.level!=scope.world()||client.getConnection()==null||client.getConnection()!=scope.connection()
            ||client.gameMode==null||!p.isAlive()||p.level()!=client.level||client.isPaused()||client.gui.screen()!=null||client.getCameraEntity()!=p||p.isScoping()
            ||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty()||p.getAbilities().flying||p.isInWater()||p.isInLava()||p.isSprinting()||p.isSwimming()
            ||manager.get(ID).orElse(null)!=this||manager.inputOwner().orElse(null)!=this||manager.enabledControllerCount()!=1||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=this)return false;
        if(services.inventory().selectedSlot()!=(recovery.castRefused()?recovery.pose().slot():healSlot())||!ItemStack.matches(healItem,p.getInventory().getItem(healSlot())))return false;
        for(var entry:healReturnConfig.entrySet())if(!java.util.Objects.equals(config.get(entry.getKey()),entry.getValue()))return false;
        return true;
    }
    boolean healReturnForbidden(int x,int y,int z){return sourceWorkflowBinding==GhostMovementWorkflow.SOURCE_WALK&&sourceInputForbiddenCells.contains(new GhostSourceWalkFrontier.Cell(x,y,z));}
    private void restoreHealSlot(){if(recovery.pose()!=null&&client.player==owner&&client.level==world&&services.inventory().selectedSlot()==healSlot())services.inventory().selectSlot(recovery.pose().slot());}
    private void patrol(){
        var node=route.nodes().get(checkpoint);if(!routeActive){routeActive=routeTravel.start(node);if(!routeActive&&node.kind()==LocalRoute.Kind.ETHERWARP&&config.get(GhostGrindConfig.GHOST_GRIND_WALK_FALLBACK)){fallbackCheckpoint=checkpoint;routeActive=routeTravel.start(new LocalRoute.Node(node.position(),node.yaw(),node.pitch()));}
            if(!routeActive){status="Ghost route failed: "+routeTravel.status();setEnabled(false);return;}}
        var state=routeTravel.tick();status="Ghost route "+(checkpoint+1)+"/"+route.nodes().size()+": "+routeTravel.status();
        if(state==McTravelNavigator.State.ARRIVED){routeActive=false;fallbackCheckpoint=-1;routeArrivals++;checkpoint=(checkpoint+1)%route.nodes().size();}
        else if(state==McTravelNavigator.State.FAILED){if(node.kind()==LocalRoute.Kind.ETHERWARP&&fallbackCheckpoint!=checkpoint&&config.get(GhostGrindConfig.GHOST_GRIND_WALK_FALLBACK)){fallbackCheckpoint=checkpoint;routeActive=routeTravel.start(new LocalRoute.Node(node.position(),node.yaw(),node.pitch()));if(routeActive)return;}setEnabled(false);}
    }
    private void approach(Vec3 goal,long now){if(!walkActive||now>=nextPath&&(walkGoal==null||walkGoal.distanceTo(goal)>1)){walkGoal=goal;nextPath=now+700;walkActive=walk.start(goal);}if(walkActive&&walk.tick()!=WalkController.State.WALKING)walkActive=false;}
    private void stopTravel(){sourceClearRetainedFallback();routeTravel.stop();walk.stop();routeActive=walkActive=false;}
    private List<Target> targets(){
        double radius=Math.max(4,Math.min(48,config.get(GhostGrindConfig.GHOST_GRIND_RADIUS)));List<LivingEntity> mobs=new ArrayList<>();List<ArmorStand> plates=new ArrayList<>();
        for(var e:client.level.entitiesForRendering())if(e!=client.player&&e.distanceToSqr(client.player)<(radius+3)*(radius+3)){if(e instanceof ArmorStand a&&a.hasCustomName())plates.add(a);else if(e instanceof LivingEntity l&&!(l instanceof Player))mobs.add(l);}
        List<Target> out=new ArrayList<>();for(var mob:mobs){String name=mob.getName().getString();boolean protect=ZealotRangedModule.protectedName(name);var near=plates.stream().filter(a->a.getBoundingBox().inflate(.6,2,.6).intersects(mob.getBoundingBox())).toList();protect|=near.stream().anyMatch(a->ZealotRangedModule.protectedName(a.getName().getString()));
            var labels=near.stream().filter(a->CombatLoop.matches(a.getName().getString(),"Ghost")).toList();if(labels.size()==1){var plate=labels.getFirst();if(mobs.stream().filter(m->plate.getBoundingBox().inflate(.6,2,.6).intersects(m.getBoundingBox())).count()==1)name=plate.getName().getString();else protect=true;}
            if(CombatLoop.matches(name,"Ghost"))out.add(new Target(mob,name,protect));}
        return out;
    }
    private boolean fullInventory(){for(int i=0;i<36;i++)if(client.player.getInventory().getItem(i).isEmpty())return false;return true;}
    private int inventoryCount(String name){int count=0;for(int i=0;i<36;i++){var item=client.player.getInventory().getItem(i);if(clean(item.getHoverName().getString()).equals(name))count+=item.getCount();}return count;}
    private static String clean(String s){return s.replaceAll("§.","").strip();}
    private static Vec3 position(net.minecraft.world.entity.Entity e){return new Vec3(e.getX(),e.getY(),e.getZ());}
    @Override public String diagnostics(){return status+" | source preflight="+(preflight==null?"unbound grind-only":preflight.state())+" | owned-hit kills="+ledger.kills()+" | Sorrow notices="+ledger.sorrowNotices()+" | collected Sorrows="+ledger.collected("Sorrow")+" | collected drops="+ledger.collectedTotal()+" | heals="+heals+" | physical returns="+returns+" | waves="+ledger.waves();}
}
