package dev.veltraph.client.combat.ghost;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.TooltipFlag;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.GhostGrindConfig;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.movement.device.DeviceUseReceipt;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

/** Actual separately owned source sixth-failure child: native121-cell admission and privately produced2000 path. */
public final class McGhostSourceLeavingChild {
    public enum State { SCANNING, CAPTURING, WALKING, ETHERWARP_READY, FINISH_PARENT_UNRECOVERED, REVOKED }
    private final Minecraft client;private final GhostGrindModule parent;private final GhostSourceOwnedParentArea.Stamp stamp;
    private final GhostSourceJobCameraCustody camera;private final java.util.function.BooleanSupplier actualInstalled;
    private LegacyTravelPublicationLease.Leg etherLeg;private DeviceUseReceipt.Context etherContext;
    private GhostSourceJobCameraCustody etherCamera;private long etherSerial,etherGeneration;
    private final GhostSourceLeavingLifecycle lifecycle=new GhostSourceLeavingLifecycle();private final GhostSourceLeavingLifecycle.Policy policy;
    private final java.util.ArrayList<Cell> scan=new java.util.ArrayList<>(),admitted=new java.util.ArrayList<>();private int scanIndex;
    private final boolean jump,smoothing,tail;private final int rough;private final String forbidden;private final long override;
    private final GhostSourceLeavingPhaseObservation phase=new GhostSourceLeavingPhaseObservation();
    private McGhostSourceCoordinateTerrainSnapshot terrain;private McGhostSourceCoordinatePipeline pipeline;private McGhostSourceCoordinateCursor cursor;private State state=State.SCANNING;
    private McGhostSourceLeavingChild(Minecraft client,GhostGrindModule parent,GhostSourceLeavingLifecycle.Policy policy,java.util.function.BooleanSupplier actualInstalled){
        this.client=client;this.parent=parent;this.policy=policy;this.actualInstalled=actualInstalled;stamp=parent.sourceParentAreaStamp(parent).orElseThrow();
        camera=new GhostSourceJobCameraCustody(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
        var c=VeltraphBootstrap.config();jump=client.player.getEffect(MobEffects.JUMP_BOOST)!=null;smoothing=c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED);rough=c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE);tail=c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS);forbidden=c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS);override=GhostSourceGraphDeveloperAreaOverride.runtime().revision();
        var p=client.player.blockPosition();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)scan.add(new Cell(p.getX()+x,p.getY(),p.getZ()+z));
    }
    static McGhostSourceLeavingChild issue(Minecraft client,GhostGrindModule parent,GhostSourceLeavingLifecycle.Policy actualPolicy,java.util.function.BooleanSupplier actualInstalled){
        if(client==null||!client.isSameThread()||parent==null||actualPolicy==null||parent.sourceParentAreaStamp(parent).isEmpty()||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=parent)throw new IllegalStateException("Actual Root-owned leaving child issuance required");return new McGhostSourceLeavingChild(client,parent,actualPolicy,actualInstalled);
    }
    /** Exact parent/stamp/camera issuance match for the named sixth-child handoff; no installed-child assumption yet. */
    boolean handoffMatches(GhostGrindModule actualParent,GhostSourceOwnedParentArea.Stamp captured,GhostSourceJobCameraCustody priorCamera){
        return client.isSameThread()&&actualParent==parent&&stamp.same(captured)
            &&priorCamera.current(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client))
            &&camera.current(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
    }
    /** Bind once while the original child and actual registered Parent still own the original camera. */
    boolean bindOwnedEtherLeg(LegacyTravelPublicationLease.Leg leg){
        if(etherLeg!=null||leg==null||state!=State.ETHERWARP_READY||!current()||!leg.current()||leg.parent()!=parent)return false;
        var context=leg.deviceContext();long epoch=McOwnedRotation.epoch(client),serial=McOwnedRotation.publicationSerial(client);
        if(context==null||context.parent()!=parent||context.player()!=client.player||context.world()!=client.level||context.connection()!=client.getConnection()
                ||context.policy()!=VeltraphBootstrap.config()||context.journey()!=leg.journeyGeneration()||context.cameraEpoch()!=epoch
                ||leg.cameraEpoch()!=epoch||leg.actualTicket()!=0||serial<0||!current()||!leg.current())return false;
        etherLeg=leg;etherContext=context;etherGeneration=leg.requestGeneration();etherSerial=serial;
        etherCamera=new GhostSourceJobCameraCustody(parent,epoch);return current();
    }
    /** Accept only this bound helper's certified, synchronous next positive publication; never a raw epoch. */
    boolean acceptOwnedEtherPublication(LegacyTravelPublicationLease.Leg leg){
        if(leg==null||leg!=etherLeg||!leg.current()||leg.parent()!=parent||!currentChecks(false))return false;
        // No publication occurred: retaining the already captured camera needs no renewal.
        if(McOwnedRotation.publicationSerial(client)==etherSerial)return current();
        var context=leg.deviceContext();long ticket=leg.actualTicket(),serial=McOwnedRotation.publicationSerial(client),epoch=McOwnedRotation.epoch(client);
        if(context==null||etherContext==null||context.parent()!=etherContext.parent()||context.player()!=etherContext.player()
                ||context.world()!=etherContext.world()||context.connection()!=etherContext.connection()||context.policy()!=etherContext.policy()
                ||context.revision()!=etherContext.revision()||context.journey()!=etherContext.journey()||leg.journeyGeneration()!=etherContext.journey()
                ||leg.requestGeneration()!=etherGeneration||context.cameraEpoch()!=epoch||leg.cameraEpoch()!=epoch
                ||ticket<=0||ticket!=serial||serial!=etherSerial+1||epoch<etherContext.cameraEpoch()||epoch-etherContext.cameraEpoch()>1
                ||!leg.current()||!currentChecks(false))return false;
        etherCamera=new GhostSourceJobCameraCustody(parent,epoch);etherSerial=serial;etherContext=context;return current();
    }
    /** For an exact synchronous native arrival receipt only; caller must independently prove the sole owned release. */
    boolean contextCurrentExceptCameraEpoch(){return currentChecks(false);}
    public State state(){return state;}public Object actualChildIdentity(){return this;}
    boolean current(){return currentChecks(true);}
    private boolean currentChecks(boolean requireCamera){if(!client.isSameThread()||!actualInstalled.getAsBoolean())return false;var actual=parent.sourceParentAreaStamp(parent).orElse(null);var mm=VeltraphBootstrap.moduleManager();var c=VeltraphBootstrap.config();
        return actual!=null&&stamp.same(actual)&&parent.sourceNativeChildIdentity(parent)==this&&mm!=null&&mm.inputOwner().orElse(null)==parent&&mm.enabledControllers().size()==1&&mm.enabledControllers().getFirst()==parent
            &&(!requireCamera||(etherCamera==null?camera:etherCamera).current(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)))
            &&(etherLeg==null||etherLeg.current())&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==parent&&GhostSourceGraphDeveloperAreaOverride.runtime().revision()==override&&GhostSourceGraphDeveloperAreaOverride.runtime().selected().isEmpty()
            &&(client.player.getEffect(MobEffects.JUMP_BOOST)!=null)==jump&&c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED)==smoothing&&c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE)==rough&&c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS)==tail&&java.util.Objects.equals(c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS),forbidden);}
    public void pump(){pump(4_000_000L);}
    /** Shares the actual parent tick's remaining capture budget even while another owned cursor supplies movement. No input/camera writes. */
    public void pump(long remainingBudgetNanos){if(state!=State.SCANNING&&state!=State.CAPTURING)return;if(!current()){revoke();return;}
        long bounded=GhostSourceLeavingPhaseObservation.boundedBudget(remainingBudgetNanos);if(bounded==0)return;long began=System.nanoTime();
        try{if(state==State.SCANNING){terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,this::current,terrain);
            while(scanIndex<scan.size()&&System.nanoTime()-began<bounded){var cell=scan.get(scanIndex++);if(GhostSourceOrdinaryWalkTerrain.standable(terrain,cell,null)||GhostSourceOrdinaryWalkTerrain.standable(terrain,new Cell(cell.x(),cell.y()+1,cell.z()),null))admitted.add(cell);}
            if(scanIndex==scan.size()){var selected=lifecycle.begin(admitted,n->java.util.concurrent.ThreadLocalRandom.current().nextInt(n));if(selected==null){state=State.FINISH_PARENT_UNRECOVERED;return;}var p=client.player.blockPosition();pipeline=McGhostSourceCoordinatePipeline.begin(client,this::current,new Cell(p.getX(),p.getY(),p.getZ()),selected,2000,jump,smoothing,rough,tail,forbidden);state=State.CAPTURING;phase.capturing(client.player.tickCount);}}
            if(state==State.CAPTURING){long budget=bounded-(System.nanoTime()-began);if(budget>0)pipeline.pump(budget);if(pipeline.state()==McGhostSourceCoordinatePipeline.State.REVOKED){revoke();return;}
                if(pipeline.state()==McGhostSourceCoordinatePipeline.State.READY){var path=pipeline.path();var outcome=lifecycle.captured(path!=null,policy,findActualEtherSlot()>=0,client.player.tickCount,n->java.util.concurrent.ThreadLocalRandom.current().nextInt(n));
                    if(outcome.action()==GhostSourceLeavingLifecycle.Action.FINISH_PARENT){state=State.FINISH_PARENT_UNRECOVERED;return;}cursor=new McGhostSourceCoordinateCursor(client,path,this::current,pipeline);state=State.WALKING;phase.ready(client.player.tickCount);}}
        }catch(RuntimeException invalid){revoke();}}
    /** Cached real preparation phase; never a readiness override or permission to emit keys. */
    public GhostSourceLeavingPhaseObservation.Snapshot preparationObservation(){if(!current())throw new IllegalStateException("Actual leaving preparation custody required");return phase.snapshot();}
    /** Truly passive counterpart: cached real transition ticks; no current()/native/PWC/settings/authority queries. */
    public GhostSourceLeavingPhaseObservation.Snapshot cachedPreparationEvidence(){return phase.snapshot();}
    public record CachedEvidence(State state,int scanCount,int admittedCount,GhostSourceLeavingLifecycle.Policy policy,
            Cell selectedEndpoint,long toolAtTick,GhostSourceLeavingPhaseObservation.Snapshot preparation,
            GhostSourceCoordinateGraphEvidence graph) {}
    public CachedEvidence cachedEvidence(){return new CachedEvidence(state,scanIndex,admitted.size(),policy,lifecycle.chosen(),
            lifecycle.cachedToolAtTick(),phase.snapshot(),pipeline==null?null:pipeline.cachedGraphEvidence());}
    /** Canonical parent emits fresh validated camera/keys from this actual privately produced cursor. */
    public McGhostSourceCoordinateCursor.Steering observeWalk(){if(state!=State.WALKING||!current())return new McGhostSourceCoordinateCursor.Steering(McGhostSourceCoordinateCursor.Outcome.STALE,null,false,false);
        var observation=cursor.observe(client);if(observation.outcome()==McGhostSourceCoordinateCursor.Outcome.AT_DESTINATION){lifecycle.pathSucceeded();state=State.ETHERWARP_READY;}else if(observation.outcome()==McGhostSourceCoordinateCursor.Outcome.STALE){lifecycle.pathFailed();state=State.FINISH_PARENT_UNRECOVERED;}return observation;}
    private int lastActualEventTick=Integer.MIN_VALUE;
    /** Actual owned child event, once per actual client tick; native current position/slot/shape LOS are sampled here. */
    public GhostSourceLeavingLifecycle.Tick observeActualEvent(boolean actualQueuedJob,boolean actualPathActive){
        if(!current()||client.player.tickCount==lastActualEventTick)throw new IllegalStateException("Fresh actual owned leaving event required");
        if(state!=State.WALKING&&state!=State.ETHERWARP_READY)throw new IllegalStateException("Actual active leaving cursor/event state required");
        lastActualEventTick=client.player.tickCount;var endpoint=lifecycle.chosen();
        if(endpoint==null)throw new IllegalStateException("Actual privately produced leaving endpoint required");
        terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,this::current,terrain);
        var nativePoint=new net.minecraft.core.BlockPos(endpoint.x(),endpoint.y(),endpoint.z());
        var below=nativePoint.below();if(!client.level.isLoaded(nativePoint)||!client.level.isLoaded(below))throw new IllegalStateException("Native leaving aim terrain unavailable");
        var shape=client.level.getBlockState(below).getCollisionShape(client.level,below);
        var eye=client.player.getEyePosition();var aim=new net.minecraft.world.phys.Vec3(endpoint.x()+.5,
            shape.isEmpty()?endpoint.y():shape.bounds().maxY+below.getY(),endpoint.z()+.5);
        // Qualified modern fresh native shape clip to the actual private support endpoint, never an always-true source helper.
        var hit=client.level.clip(new net.minecraft.world.level.ClipContext(eye,aim,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,client.player));
        boolean clear=hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS||hit.getBlockPos().equals(below);
        double distance=client.player.position().distanceTo(new net.minecraft.world.phys.Vec3(endpoint.x(),endpoint.y(),endpoint.z()));
        int slot=client.player.getInventory().getSelectedSlot();
        if(!current())throw new IllegalStateException("Leaving event scope changed during observation");
        var random=java.util.concurrent.ThreadLocalRandom.current();
        var action=lifecycle.event(actualQueuedJob,actualPathActive,clear,distance<10,slot,random::nextInt,random::nextDouble,random::nextBoolean);
        if(!current())throw new IllegalStateException("Leaving event action custody revoked");
        if(action.action()==GhostSourceLeavingLifecycle.Action.FINISH_PARENT)state=State.FINISH_PARENT_UNRECOVERED;
        return action;
    }
    public enum EventGate { ALLOWED, REFUSED, SOURCE_LIMITER_UNRECOVERED }
    /** Original left-click helper includes shared history/limit numerics. Their native lifetime producer is not fabricated. */
    public EventGate actualLeftClickGate(){return current()?EventGate.ALLOWED:EventGate.REFUSED;}
    /** Canonical parent invokes the actual shared source limiter+normal attack-key pulse,never emits direct attack here. */
    public McGhostSourceLeavingClick.Result emitActualLeftClick(){return McGhostSourceLeavingClick.emit(client,this);}
    /** Source JUMP helper requires client-ready + actual ground-capable + not already held; modern swept body safeguard is added. */
    public EventGate actualJumpGate(){if(!current()||!client.player.onGround()||client.options.keyJump.isDown())return EventGate.REFUSED;
        var box=client.player.getBoundingBox().move(0,.42,0);return client.level.noCollision(client.player,box)&&current()?EventGate.ALLOWED:EventGate.REFUSED;}
    public boolean etherSlotDue(){return current()&&lifecycle.toolDue(client.player.tickCount);}
    public int findActualEtherSlot(){if(!current())return -1;try{for(int i=0;i<9;i++){var stack=client.player.getInventory().getItem(i);var lines=stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,null,TooltipFlag.NORMAL);if(lines.stream().anyMatch(line->line.getString().replaceAll("§.","").contains("Ether Transmission")))return i;}}catch(RuntimeException unavailable){return -1;}return -1;}
    public void observedEtherSlotSelection(int slot){if(!current()||slot!=findActualEtherSlot()||client.player.getInventory().getSelectedSlot()!=slot)throw new IllegalStateException("Actual source Ether slot selection required");lifecycle.toolSelected();}
    /** Native ether aim/use producer and unreadable parent's transfer command remain separate; no invented command dispatched. */
    public Cell actualLeavingEndpoint(){return lifecycle.chosen();}
    public void revoke(){state=State.REVOKED;lifecycle.revoke();if(pipeline!=null)pipeline.revoke();}
}
