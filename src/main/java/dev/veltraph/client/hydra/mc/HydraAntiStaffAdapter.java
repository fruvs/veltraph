package dev.veltraph.client.hydra.mc;

import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.hydra.BarrierWatcher;
import dev.veltraph.client.hydra.GameModeKind;
import dev.veltraph.client.hydra.FloorChangeWatcher;
import dev.veltraph.client.hydra.HydraAntiStaff;
import dev.veltraph.client.hydra.HydraSettings;
import dev.veltraph.client.hydra.HydraTick;
import dev.veltraph.client.hydra.NearbyPlayer;
import dev.veltraph.client.hydra.RoutingTextMatcher;
import dev.veltraph.client.log.VeltraphLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Minecraft adapter that feeds the pure {@link HydraAntiStaff} one {@link HydraTick} per client tick. It
 * performs all the Minecraft reads — world player enumeration + distances, dimension, area, gamemode,
 * block passability — and tracks the previous-tick position (for the teleport delta + lagback) and
 * previous world (for the dimension-change anomaly). It contains no decision logic; that all lives in
 * the pure detector.
 *
 * <p>Wired from {@code VeltraphBootstrap.onClientTick} after the module tick (so {@code scripting}
 * reflects this tick's run state). Cheap when the watcher is disabled: it early-returns before touching
 * the player list. <b>Local-only — no network, no reporting, and it never sends/replies to chat.</b> The
 * routing-text trigger only READS received lines (pushed in via {@link #acceptChatLine(String)} from a
 * Fabric {@code ClientReceiveMessageEvents.GAME} listener).
 *
 * <p>Verified yarn signatures (1.21.11 build.6):
 * <ul>
 *   <li>{@code ClientWorld#getPlayers()} = {@code method_18456}; {@code PlayerEntity#getGameProfile()}
 *       = {@code method_7334}; {@code GameProfile#name()} (authlib 7.x record accessor).</li>
 *   <li>{@code Entity#getX/getY/getZ} = {@code method_23317/23318/23321}.</li>
 *   <li>{@code World#getRegistryKey()} = {@code method_27983}.</li>
 *   <li>{@code ClientPlayerInteractionManager#getCurrentGameMode()} returns
 *       {@code net.minecraft.world.GameMode} ({@code SURVIVAL/CREATIVE/ADVENTURE/SPECTATOR}).</li>
 *   <li>{@code World#getBlockState(BlockPos).isSolidBlock(World, BlockPos)} for the enclosed check
 *       (try/catch-degraded so signature drift returns "not enclosed", never crashing).</li>
 * </ul>
 */
public final class HydraAntiStaffAdapter {

    private final Minecraft client;
    private final HydraAntiStaff feature;
    private final GameContextService gameContext;
    private final ModuleManager moduleManager;
    private final RoutingTextMatcher routingMatcher = RoutingTextMatcher.withDefaults();

    private double lastX, lastY, lastZ;
    private boolean hasLastPos;
    private Identifier lastDimension;
    private final FloorChangeWatcher floorChangeWatcher = new FloorChangeWatcher();
    private final dev.veltraph.client.hydra.SourceFloorProducer sourceFloor=new dev.veltraph.client.hydra.SourceFloorProducer();
    private java.util.function.LongSupplier sourceFloorGeneration=()->0;
    public record ExternalFloorOwner(Object owner,boolean ownsNativeControl){}
    private java.util.function.Supplier<ExternalFloorOwner> sourceFloorExternalOwner=()->null;
    public void sourceFloorExternalOwner(java.util.function.Supplier<ExternalFloorOwner> owner){sourceFloorExternalOwner=java.util.Objects.requireNonNull(owner);}
    public dev.veltraph.client.hydra.SourceFloorProducer.Receipt sourceFloorReceipt(){return sourceFloor.detectedReceipt();}
    public java.util.List<dev.veltraph.client.hydra.SourceFloorProducer.Append> sourceFloorAppends(){return sourceFloor.detectedAppends();}
    private boolean sourceFloorGate;private long sourceFloorSettingGeneration;
    public void sourceFloorGeneration(java.util.function.LongSupplier generation){sourceFloorGeneration=java.util.Objects.requireNonNull(generation);}
    private dev.veltraph.client.hydra.SourceFloorProducer.Scope sourceFloorScope(){
        var settings=feature.settings();var registered=moduleManager==null?null:moduleManager.inputOwner().orElse(null);
        Object registeredOwner=registered!=null&&registered.isEnabled()&&registered.ownsGameplayInput()&&!registered.isPassiveObserver()
                &&moduleManager.get(registered.id()).orElse(null)==registered?registered:null;
        boolean externalActive=feature.observingExternalControl();Object externalOwner=null;
        if(externalActive){var external=sourceFloorExternalOwner.get();if(external!=null&&external.owner()!=null&&external.ownsNativeControl())externalOwner=external.owner();}
        Object owner=dev.veltraph.client.hydra.SourceFloorCustody.select(externalActive,externalOwner,registeredOwner);
        boolean active=owner!=null&&client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.isAlive();
        boolean gate=active&&settings.enabled()&&settings.detectFloorChange();if(gate!=sourceFloorGate){sourceFloorGate=gate;sourceFloorSettingGeneration++;}
        return new dev.veltraph.client.hydra.SourceFloorProducer.Scope(client.player,client.level,client.getConnection(),owner,31*sourceFloorGeneration.getAsLong()+sourceFloorSettingGeneration,gate);
    }
    public void verifiedFloorBlock(ClientLevel eventWorld,BlockPos pos,net.minecraft.world.level.block.state.BlockState incoming){
        if(!client.isSameThread()||client.level!=eventWorld||client.player==null||client.getConnection()==null||pos==null||incoming==null)return;
        var p=client.player;var old=eventWorld.getBlockState(pos);
        sourceFloor.accept(sourceFloorScope(),incoming.getBlock()==net.minecraft.world.level.block.Blocks.BEDROCK,old.getBlock()==net.minecraft.world.level.block.Blocks.BEDROCK,p.getX(),p.getY(),p.getZ(),pos.getX(),pos.getY(),pos.getZ(),p.tickCount);
    }

    private Map<BlockPos,String> priorFloorFacts=Map.of();
    private String evaluatedFloorDiagnostic="not sampled",lastFloorTrigger="none";
    public String evaluatedFloorDiagnostic(){return evaluatedFloorDiagnostic;}
    public String lastFloorTrigger(){return lastFloorTrigger;}
    private final BarrierWatcher barrierWatcher = new BarrierWatcher();
    private Object barrierOwner;private long barrierGeneration;
    private java.util.function.LongSupplier barrierRunGeneration=()->0;
    public void barrierRunGeneration(java.util.function.LongSupplier generation){barrierRunGeneration=java.util.Objects.requireNonNull(generation);}
    private String barrierDiagnostic="not sampled";
    private String lastBarrierTrigger="none";
    public String barrierDiagnostic(){return barrierDiagnostic;}
    public String lastBarrierTrigger(){return lastBarrierTrigger;}
    private java.util.function.BiPredicate<dev.veltraph.client.world.Vec3,Long> expectedRecoveryTeleport=(position,now)->false;
    private java.util.function.BiPredicate<dev.veltraph.client.world.Vec3,Long> expectedHubTeleport=(position,now)->false;
    /** Hub supplies a fresh native packet witness, including an explicitly bound dimension transition. */
    public void expectedHubTeleport(java.util.function.BiPredicate<dev.veltraph.client.world.Vec3,Long> predicate) {
        expectedHubTeleport=java.util.Objects.requireNonNull(predicate);
    }

    /** Optional interrupted-farm recovery owner, subject to the same world and landing guards. */
    public void expectedRecoveryTeleport(java.util.function.BiPredicate<dev.veltraph.client.world.Vec3,Long> predicate) {
        expectedRecoveryTeleport=java.util.Objects.requireNonNull(predicate);
    }

    public HydraAntiStaffAdapter(Minecraft client,
                                 HydraAntiStaff feature,
                                 GameContextService gameContext,
                                 ModuleManager moduleManager) {
        this.client = client;
        this.feature = feature;
        this.gameContext = gameContext;
        this.moduleManager = moduleManager;
    }

    /**
     * Feed one RECEIVED game/system chat line into the read-only routing matcher. Called from the Fabric
     * chat-receive listener registered in {@code VeltraphBootstrap}. Never sends or replies — it only
     * inspects what the client already received. Safe to call off the tick loop.
     */
    public void acceptChatLine(String line) {
        try {
            routingMatcher.offer(line);
        } catch (Throwable ignored) {
            // never let a malformed line break the listener
        }
    }

    /** Drive one tick of the anti-staff watcher. Safe to call every client tick. */
    public void tick(long nowMillis) {
        HydraSettings settings;
        try {
            settings = feature.settings();
        } catch (Throwable t) {
            return; // config not ready — never crash the tick loop
        }
        boolean scripting = moduleManager != null && !moduleManager.enabledControllers().isEmpty();
        boolean observing = scripting || feature.observingExternalControl();

        LocalPlayer self = client.player;
        ClientLevel world = client.level;
        boolean worldPresent = self != null && world != null;

        // Always consume any pending routing match so it never goes stale across ticks (and so a routing
        // line received while disabled doesn't fire the instant the watcher is re-enabled).
        String routingMatch = routingMatcher.pollMatch();

        if (!settings.enabled()) {
            // Config can also change through persistence/commands, bypassing setEnabled's UI cleanup.
            // Feed the disabled gate before returning so queued reactions release their ownership.
            feature.tick(new HydraTick(settings, feature.whitelist(), nowMillis, List.of(),
                    scripting, worldPresent, GameArea.UNKNOWN, Double.NaN, false, Double.NaN,
                    false, currentGameMode(), null));
            // Disabled: keep position tracking primed so re-enabling mid-flight doesn't false-fire a
            // teleport on the first tick, but do no detection/reaction work.
            resetFloorWatcher();
            barrierWatcher.reset();
            if (worldPresent) {
                primePosition(self);
            } else {
                hasLastPos = false;
                lastDimension = null;
            }
            return;
        }

        if (!worldPresent) {
            // World/player gone: feed a routing tick (only meaningful while scripting per the detector).
            hasLastPos = false;
            resetFloorWatcher();
            barrierWatcher.reset();
            HydraTick t = new HydraTick(settings, feature.whitelist(), nowMillis, List.of(),
                    scripting, false, GameArea.UNKNOWN, Double.NaN, false,
                    /*lagback*/ Double.NaN, /*enclosed*/ false, GameModeKind.UNKNOWN, routingMatch);
            feature.tick(t);
            lastDimension = null;
            return;
        }

        // --- position delta (teleport) + backward component (lagback) ---
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        double move = Double.NaN;
        double lagback = Double.NaN;
        if (hasLastPos) {
            double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
            move = Math.sqrt(dx * dx + dy * dy + dz * dz);
            lagback = backwardComponent(self, dx, dy, dz);
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLastPos = true;

        // --- dimension change ---
        Identifier dim = dimensionId(world);
        boolean dimensionChanged = lastDimension != null && dim != null && !dim.equals(lastDimension);
        lastDimension = dim;

        // --- enclosed destination (only relevant when a teleport-sized jump occurred) ---
        boolean enclosed = false;
        if (!Double.isNaN(move) && move > settings.teleportDistanceBlocks()) {
            enclosed = isEnclosed(world, self);
        }
        // An own-issued etherwarp may account for one matching open-space position jump. Do not
        // suppress other destinations, dimension changes, enclosed landings or unrelated detectors.
        boolean hubExpected=scripting&&!isEnclosed(world,self)
                &&expectedHubTeleport.test(new dev.veltraph.client.world.Vec3(x,y,z),nowMillis);
        if(hubExpected){
            move=0;lagback=0;enclosed=false;dimensionChanged=false;resetFloorWatcher();barrierWatcher.reset();
            VeltraphLog.details("Travel","Confirmed the issued Hub command's fresh native teleport receipt");
        }
        if(!hubExpected && scripting && !dimensionChanged && move>1 && !isEnclosed(world,self)) {
            var position=new dev.veltraph.client.world.Vec3(x,y,z);
            boolean recoveryExpected=expectedRecoveryTeleport.test(position,nowMillis);
            if(recoveryExpected) {
                move=0;lagback=0;enclosed=false;resetFloorWatcher();barrierWatcher.reset();
                VeltraphLog.details("Travel", "Confirmed own-issued interrupted-farm plot return");
            }
            for(var module:moduleManager.enabledModules()) if(!recoveryExpected && module.consumeExpectedTeleport(position,nowMillis)) {
                move=0;lagback=0;enclosed=false;resetFloorWatcher();barrierWatcher.reset();
                VeltraphLog.details("Travel", "Confirmed own-issued teleport for "+module.id());
                break;
            }
        }
        boolean floorChanged = false;
        if (!observing || dimensionChanged) {
            resetFloorWatcher();
        } else if (settings.detectFloorChange()) {
            floorChanged = sourceFloor.tick(sourceFloorScope(),self.tickCount,System::currentTimeMillis);
        } else {
            resetFloorWatcher();
        }

        // --- BARRIER: a solid block newly appears in the forward path while held in place ---
        boolean barrierDetected = false;
        if (!observing || dimensionChanged) {
            barrierWatcher.reset();
        } else if (settings.detectBarrier()) {
            boolean stationary = !Double.isNaN(move) && move <= settings.barrierStationarySpeed();
            var prior=barrierWatcher.previousSample();var sample=forwardSample(world,self);
            barrierDetected = barrierWatcher.observe(sample,stationary);
            barrierDiagnostic="prior="+diagnosticSample(prior)+" current="+diagnosticSample(sample)+" pose="+self.position()+" yaw="+self.getYRot()+" direction="+self.getDirection()+" move="+move+" eligible="+stationary+" fire="+barrierDetected;
            if(barrierDetected){lastBarrierTrigger=barrierDiagnostic;VeltraphLog.basic("Barrier",barrierDiagnostic);}
        } else {
            barrierWatcher.reset();
        }

        // --- gamemode ---
        GameModeKind gameMode = currentGameMode();

        // --- look angle (for the LOCAL_INCLUDE fix-angle response only) ---
        double lookPitch = currentPitch(self);
        double lookYaw = currentYaw(self);

        // --- nearby players ---
        List<NearbyPlayer> nearby = enumerateNearby(self, world);

        GameArea area = gameContext == null ? GameArea.UNKNOWN : safeArea();

        HydraTick t = new HydraTick(settings, feature.whitelist(), nowMillis, nearby,
                scripting, true, area, move, dimensionChanged,
                lagback, enclosed, gameMode, routingMatch, floorChanged, lookPitch, lookYaw, barrierDetected);
        feature.tick(t);
    }

    /** Reset transient tracking (called on world stop so the next world doesn't false-fire a teleport). */
    public void onWorldStop() {
        hasLastPos = false;
        lastDimension = null;
        resetFloorWatcher();
        barrierWatcher.reset();
        routingMatcher.reset();
        try {
            feature.resetResponses();
        } catch (Throwable ignored) {
            // never let response cleanup break world-stop handling
        }
    }

    private void primePosition(LocalPlayer self) {
        try {
            lastX = self.getX();
            lastY = self.getY();
            lastZ = self.getZ();
            hasLastPos = true;
            if (client.level != null) {
                lastDimension = dimensionId(client.level);
            }
        } catch (Throwable ignored) {
            hasLastPos = false;
        }
    }

    /**
     * Local FLOOR_CHANGE watcher. The original receives block-change callbacks; this clean rebuild polls a
     * small absolute-position snapshot around the stationary player's feet/body and records repeated collision
     * changes. Moving to a new block resets the baseline, so normal walking over different terrain cannot
     * accumulate floor-change events.
     */
    private boolean observeFloorChanges(ClientLevel world,LocalPlayer self,long nowMillis){
        BlockPos anchor;Map<BlockPos,Boolean> sample;boolean fired;
        try{anchor=self.blockPosition();sample=floorSnapshot(world,anchor);fired=floorChangeWatcher.observe(anchor,sample,nowMillis);}
        catch(Throwable decisionFailure){resetFloorWatcher();return false;}
        // All supplemental native queries, formatting, allocations and logging occur after the
        // original36-cell detector decision and cannot change its return or rolling state.
        try{
            Object owner=moduleManager==null?null:moduleManager.inputOwner().orElse(null);
            var guard=dev.veltraph.client.hydra.FloorDiagnosticReporting.read(()->dev.veltraph.client.service.mc.McBlockBreakingService.floorObservationGuard(owner,sample.keySet()),null);
            var details=new HashMap<BlockPos,String>();for(var entry:sample.entrySet()){
                var pos=entry.getKey();boolean evaluated=entry.getValue();
                String supplemental=dev.veltraph.client.hydra.FloorDiagnosticReporting.read(()->{
                    boolean loaded=world.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);var state=world.getBlockState(pos);var shape=state.getCollisionShape(world,pos);
                    var boxes=shape.toAabbs().stream().map(v->List.of(v.minX,v.minY,v.minZ,v.maxX,v.maxY,v.maxZ)).toList();
                    return "loaded="+loaded+",supplementalState="+state+",supplementalCollision="+boxes+",fluid="+state.getFluidState().getType()+",guardFacts="+(guard==null?"unavailable":guard.cell(pos));
                },"supplemental=unavailable");
                details.put(pos,"evaluatedSolid="+evaluated+","+supplemental);
            }
            var d=floorChangeWatcher.diagnostic();if(d==null)return fired;
            var changed=new ArrayList<String>();for(var value:d.changedCells())if(value instanceof BlockPos pos)changed.add(pos+" before="+d.before().get(pos)+" after="+d.after().get(pos)+" priorSupplemental="+priorFloorFacts.get(pos)+" currentSupplemental="+details.get(pos));
            String next="time="+nowMillis+" nativeTick="+self.tickCount+" player="+opaque(self)+" world="+opaque(world)+" connection="+opaque(client.getConnection())+" owner="+opaque(owner)+" anchor="+anchor+" priorAnchor="+d.priorAnchor()+" reason="+d.reason()+" pose="+self.position()+" view="+self.getYRot()+"/"+self.getXRot()+" velocity="+self.getDeltaMovement()+" water="+self.isInWater()+" onGround="+self.onGround()+" windowCount="+d.windowCount()+" changed="+changed+" sampleFacts="+details+" fire="+fired;
            priorFloorFacts=Map.copyOf(details);evaluatedFloorDiagnostic=next;if(fired)lastFloorTrigger=next;
            if(!changed.isEmpty()||fired)dev.veltraph.client.hydra.FloorDiagnosticReporting.run(()->VeltraphLog.basic("FloorObservation",next),failure->{});
        }catch(Throwable failure){try{VeltraphLog.basic("FloorObservation","Diagnostic unavailable: "+failure.getClass().getSimpleName());}catch(Throwable ignored){}}
        return fired;
    }

    private void resetFloorWatcher() {
        floorChangeWatcher.reset();priorFloorFacts=Map.of();sourceFloor.clear();if(sourceFloorGate){sourceFloorGate=false;sourceFloorSettingGeneration++;}
    }

    private static Map<BlockPos, Boolean> floorSnapshot(ClientLevel world, BlockPos anchor) {
        Map<BlockPos, Boolean> out = new HashMap<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Two layers under/at feet plus head: enough to catch floor swaps and encasement changes.
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos p = anchor.offset(dx, dy, dz);
                    out.put(p, isSolid(world, p));
                }
            }
        }
        return out;
    }

    private GameArea safeArea() {
        try {
            return gameContext.current().area();
        } catch (Throwable t) {
            return GameArea.UNKNOWN;
        }
    }

    /** Map the live gamemode to the MC-free {@link GameModeKind}; UNKNOWN if unreadable. */
    private GameModeKind currentGameMode() {
        try {
            if (client.gameMode == null) {
                return GameModeKind.UNKNOWN;
            }
            GameType gm = client.gameMode.getPlayerMode();
            if (gm == null) {
                return GameModeKind.UNKNOWN;
            }
            return switch (gm) {
                case SURVIVAL -> GameModeKind.SURVIVAL;
                case CREATIVE -> GameModeKind.CREATIVE;
                case ADVENTURE -> GameModeKind.ADVENTURE;
                case SPECTATOR -> GameModeKind.SPECTATOR;
            };
        } catch (Throwable t) {
            return GameModeKind.UNKNOWN;
        }
    }

    /** The local player's current look pitch in degrees, or {@code NaN} if unreadable. */
    private static double currentPitch(LocalPlayer self) {
        try {
            return self.getXRot();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /** The local player's current look yaw in degrees, or {@code NaN} if unreadable. */
    private static double currentYaw(LocalPlayer self) {
        try {
            return self.getYRot();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Magnitude of the movement component that opposed the player's intended look/forward direction —
     * i.e. how far the server pushed the player BACKWARD this tick. Forward walking yields ~0 (the
     * delta is along, not against, the facing), so normal movement never trips the lagback trigger.
     * Returns {@code NaN} on any read failure.
     */
    private static double backwardComponent(LocalPlayer self, double dx, double dy, double dz) {
        try {
            float yawDeg = self.getYRot();
            double yaw = Math.toRadians(yawDeg);
            // Minecraft forward unit vector on the XZ plane: (-sin yaw, +cos yaw).
            double fwdX = -Math.sin(yaw);
            double fwdZ = Math.cos(yaw);
            // Project the horizontal delta onto the forward axis. Negative = moved backward.
            double along = dx * fwdX + dz * fwdZ;
            return along < 0 ? -along : 0.0; // only the backward magnitude counts
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * Whether the player's destination is enclosed in solid blocks (feet + head both solid → trapped in
     * a box). Best-effort; any read failure returns false ("not enclosed") so a signature drift can only
     * suppress the trigger, never crash or false-fire.
     */
    private static boolean isEnclosed(ClientLevel world, LocalPlayer self) {
        try {
            BlockPos feet = self.blockPosition();
            BlockPos head = feet.above();
            return isSolid(world, feet) && isSolid(world, head);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether a solid block occupies the player's forward feet/body path right now: the block one step in
     * the player's horizontal facing direction, at feet OR head level, has a non-empty collision shape.
     * Best-effort; any read failure returns false ("not blocked") so signature drift can only suppress the
     * BARRIER trigger, never crash or false-fire. The rising-edge + stationary gating live in
     * {@link BarrierWatcher} / the caller, so this only reports the instantaneous "blocked in front" state.
     */
    private BarrierWatcher.Sample forwardSample(ClientLevel world,LocalPlayer self){
        Object owner=moduleManager==null?null:moduleManager.inputOwner().orElse(null);
        if(owner==null&&feature.observingExternalControl())owner=feature;
        if(owner!=barrierOwner){barrierOwner=owner;barrierGeneration++;}
        long generation=31*barrierGeneration+barrierRunGeneration.getAsLong();
        var feet=self.blockPosition().relative(self.getDirection());var head=feet.above();
        boolean loaded=world.getChunkSource().hasChunk(feet.getX()>>4,feet.getZ()>>4)&&world.getChunkSource().hasChunk(head.getX()>>4,head.getZ()>>4);
        if(!loaded)return new BarrierWatcher.Sample(self,world,client.getConnection(),owner,generation,cell(feet),cell(head),null,null,null,null,false,false);
        try{var footState=world.getBlockState(feet);var headState=world.getBlockState(head);var footShape=footState.getCollisionShape(world,feet);var headShape=headState.getCollisionShape(world,head);
            return new BarrierWatcher.Sample(self,world,client.getConnection(),owner,generation,cell(feet),cell(head),footState,headState,shapeKey(footShape),shapeKey(headShape),true,!footShape.isEmpty()||!headShape.isEmpty());
        }catch(RuntimeException unavailable){return new BarrierWatcher.Sample(self,world,client.getConnection(),owner,generation,cell(feet),cell(head),null,null,null,null,false,false);}
    }
    private static String opaque(Object value){return value==null?"none":value.getClass().getSimpleName()+"@"+Integer.toHexString(System.identityHashCode(value));}
    private static String diagnosticSample(BarrierWatcher.Sample s){return s==null?"none":"{player="+opaque(s.player())+",world="+opaque(s.world())+",connection="+opaque(s.connection())+",owner="+opaque(s.owner())+",run="+s.generation()+",feet="+s.feet()+",head="+s.head()+",feetState="+s.feetState()+",headState="+s.headState()+",feetShape="+s.feetShape()+",headShape="+s.headShape()+",loaded="+s.loaded()+",blocked="+s.blocked()+"}";}
    private static java.util.List<java.util.List<Double>> shapeKey(net.minecraft.world.phys.shapes.VoxelShape shape){return shape.toAabbs().stream().map(b->java.util.List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();}
    private static BarrierWatcher.Cell cell(BlockPos p){return new BarrierWatcher.Cell(p.getX(),p.getY(),p.getZ());}
    private static boolean isForwardBlocked(ClientLevel world, LocalPlayer self) {
        try {
            BlockPos feet = self.blockPosition();
            BlockPos front = feet.relative(self.getDirection());
            return isSolid(world, front) || isSolid(world, front.above());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether the block at {@code pos} would block the player's body — i.e. it has a non-empty collision
     * shape (the same passability notion {@code McWorldService} uses). A non-passable feet AND head block
     * means the player cannot fit, so the destination is an enclosed trap.
     */
    private static boolean isSolid(ClientLevel world, BlockPos pos) {
        try {
            return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static Identifier dimensionId(ClientLevel world) {
        try {
            return world.dimension().identifier();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Enumerate other players + their distance to the local player (excludes self by reference). */
    private static List<NearbyPlayer> enumerateNearby(LocalPlayer self, ClientLevel world) {
        List<NearbyPlayer> out = new ArrayList<>();
        try {
            double sx = self.getX(), sy = self.getY(), sz = self.getZ();
            for (Player p : world.players()) {
                if (p == self || p == null) {
                    continue;
                }
                String name = nameOf(p);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                double dx = p.getX() - sx, dy = p.getY() - sy, dz = p.getZ() - sz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                out.add(new NearbyPlayer(name, dist));
            }
        } catch (Throwable t) {
            VeltraphLog.details("HydraAntiStaffAdapter.enumerateNearby",
                    "player enumeration failed: " + t.getClass().getSimpleName());
        }
        return out;
    }

    private static String nameOf(Player p) {
        try {
            // authlib 7.x GameProfile is a record-style class: the accessor is name(), not getName().
            return p.getGameProfile().name();
        } catch (Throwable t1) {
            try {
                return p.getName().getString();
            } catch (Throwable t2) {
                return null;
            }
        }
    }
}
