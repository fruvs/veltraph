package dev.veltraph.client.foraging.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.foraging.*;
import dev.veltraph.client.foraging.mc.McForagingAbilitySource;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Source-region SEARCH → WALK → BREAK → COLLECT loop using loaded collision and vanilla input.
 * Region/density/checkpoints: recovered TreeType and WaypointRoute constants. Score and failure policy:
 * ForagingParkController / ForagingHubFindTree / TreeBreaker. No packet-assisted block removal. */
public final class AutoForagingModule extends AbstractModule implements dev.veltraph.client.foraging.scheduling.ForagingRestOwner {
    private final Minecraft client; private final ConfigService config; private final FarmServices services;
    private final McTravelNavigator navigation; private final McNavigator terrain; private final GameArea area;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final TreeBlacklist blacklist=new TreeBlacklist();
    private final dev.veltraph.client.foraging.hub.McHubSourceLoop hub;
    private dev.veltraph.client.foraging.hub.HubSourceLoop.Saved pausedHub;
    private final ParkCrowdTracker parkCrowd=new ParkCrowdTracker();
    private final java.util.random.RandomGenerator parkRandom=new Random();
    private int parkCandidates,parkRouteAttempts;
    private double parkSelectedCost=Double.NaN;
    private final Map<BlockPos,Long> visitedCheckpoints=new HashMap<>();
    private TreeDiscovery.Tree target; private BlockPos aimedLog;
    private final Set<BlockPos> disappeared=new HashSet<>();
    private long nextSearch,started,lastProgress,nextAxe,settleUntil;
    private int broken,harvested,collected,inventoryBefore,ghostRollbacks,activationTotal,largestBatchObserved,multiBatches;
    private int previousSlot=-1,ownedAxeSlot=-1;
    private String phase="IDLE",lastReason="",lastTravelFailure="";
    private final ForagingHarvestCycle cycle=new ForagingHarvestCycle();
    private boolean usingAbilityKey;
    private boolean scopedHarvest,scopedFinished,scopedFailed;
    private final dev.veltraph.client.foraging.scheduling.ForagingPauseGate rest=new dev.veltraph.client.foraging.scheduling.ForagingPauseGate();
    private net.minecraft.world.phys.HitResult blockedRestHit;
    private net.minecraft.client.player.LocalPlayer nativeOwner;
    private net.minecraft.client.multiplayer.ClientLevel nativeWorld;
    private Object nativeConnection;
    public AutoForagingModule(String id,String name,GameArea area,Minecraft client,ConfigService config,FarmServices services) {
        this(id,name,area,client,config,services,null,null);
    }
    /** Fixture/explicit-port seam: area and warp observations remain owned by the supplied native port. */
    public AutoForagingModule(String id,String name,GameArea area,Minecraft client,ConfigService config,FarmServices services,
                              java.util.function.BooleanSupplier hubAreaObservation,dev.veltraph.client.foraging.hub.McHubSourceLoop.WarpAccess hubWarp) {
        this(id,name,area,client,config,services,hubAreaObservation,hubWarp,null);
    }
    public AutoForagingModule(String id,String name,GameArea area,Minecraft client,ConfigService config,FarmServices services,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){this(id,name,area,client,config,services,null,null,parent);}
    private AutoForagingModule(String id,String name,GameArea area,Minecraft client,ConfigService config,FarmServices services,java.util.function.BooleanSupplier hubAreaObservation,dev.veltraph.client.foraging.hub.McHubSourceLoop.WarpAccess hubWarp,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){
        super(id,name,ModuleCategory.FORAGING);interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,parent==null?()->this:parent);this.area=area;this.client=client;this.config=config;this.services=services;
        navigation=new McTravelNavigator(client,services,config);terrain=new McNavigator(client,services.input(),config);
        java.util.function.BiPredicate<BlockPos,Long> blocked=(p,now)->blacklist.isActive(coord(p),now,Math.max(1000,config.get(ForagingConfig.FORAGING_BLACKLIST_TTL_MS)));
        hub=area!=GameArea.HUB?null:hubAreaObservation==null
                ?new dev.veltraph.client.foraging.hub.McHubSourceLoop(client,config,services,blocked)
                :new dev.veltraph.client.foraging.hub.McHubSourceLoop(client,config,services,blocked,hubAreaObservation,()->false,hubWarp==null?dev.veltraph.client.foraging.hub.McHubSourceLoop.WarpAccess.UNBOUND:hubWarp);
    }
    @Override public boolean requiresSkyBlock() { return true; }
    @Override public Optional<GameArea> requiredArea() { return Optional.of(area); }
    @Override public Optional<String> preStartCheck() {
        var names=services.inventory().hotbarItemNames();
        for(int i=0;i<names.size();i++) if(ForagingAxePolicy.isAxe(names.get(i))) {
            if(area==GameArea.THE_PARK&&config.get(ForagingConfig.FORAGING_REQUIRE_SWEEP)&&!(McForagingAbilitySource.profile(client,config,i).sweep()>0))continue;
            if(config.get(ForagingConfig.FORAGING_AXE_MODE)==ForagingConfig.AxeMode.THROWING&&!McForagingAbilitySource.hasThrowingAbility(client,i))continue;
            ownedAxeSlot=i;return Optional.empty();
        }
        return Optional.of(area==GameArea.THE_PARK&&config.get(ForagingConfig.FORAGING_REQUIRE_SWEEP)?"Put an axe with an observed Sweep stat in the hotbar.":"Put an axe or Treecapitator in the hotbar.");
    }
    @Override public void onEnable() {
        rest.clear();blockedRestHit=null;pausedHub=null;
        nativeOwner=client.player;nativeWorld=client.level;nativeConnection=client.getConnection();
        previousSlot=services.inventory().selectedSlot();if(ownedAxeSlot>=0)services.inventory().selectSlot(ownedAxeSlot);
        target=null;aimedLog=null;blacklist.clear();visitedCheckpoints.clear();disappeared.clear();parkCrowd.clear();parkCandidates=parkRouteAttempts=0;parkSelectedCost=Double.NaN;
        cycle.reset();activationTotal=largestBatchObserved=multiBatches=0;nextSearch=0;nextAxe=0;broken=harvested=collected=ghostRollbacks=0;phase="SEARCH_TREE";lastReason="";scopedHarvest=scopedFinished=scopedFailed=false;
        if(hub!=null)hub.loop().start();
    }
    @Override public void onDisable() {
        rest.clear();blockedRestHit=null;pausedHub=null;
        if(hub!=null)hub.cancel();
        releaseAbilityKey();interactionRotation.release(navigation);services.breaking().setBreaking(false);
        if(client.player==nativeOwner&&client.level==nativeWorld&&previousSlot>=0&&services.inventory().selectedSlot()==ownedAxeSlot)services.inventory().selectSlot(previousSlot);
        nativeOwner=null;nativeWorld=null;nativeConnection=null;
        previousSlot=-1;target=null;aimedLog=null;disappeared.clear();phase="STOPPED";
    }
    private static BlockCoord coord(BlockPos p) { return new BlockCoord(p.x(),p.y(),p.z()); }
    private static net.minecraft.core.BlockPos mc(BlockPos p) { return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()); }
    private boolean loaded(BlockPos p) { return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4); }
    private String logId(BlockPos p) {
        if(!loaded(p)) return "";
        var state=client.level.getBlockState(mc(p));
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return state.is(BlockTags.LOGS)&&LogFilter.matches(id,config.get(ForagingConfig.FORAGING_LOG_KEYWORDS))?id:"";
    }
    private boolean log(BlockPos p) { return target!=null&&target.species().equals(ForagingRegion.speciesOf(logId(p))); }
    private void abandon(long now,String reason) {
        if(scopedHarvest&&!scopedFinished)scopedFailed=true;
        if(target!=null) blacklist.blacklistTree(target.logs().stream().map(AutoForagingModule::coord).toList(),now);
        if(hub!=null)hub.loop().treeFinished();
        target=null;aimedLog=null;releaseAbilityKey();navigation.stop();interactionRotation.cancelRotation();services.breaking().setBreaking(false);
        disappeared.clear();phase="SEARCH_TREE";lastReason=reason;nextSearch=now+500;
    }
    private ForagingRegion region() { return area==GameArea.HUB?ForagingRegion.OAK:config.get(ForagingConfig.FORAGING_PARK_REGION); }
    private void releaseAbilityKey() {
        if(usingAbilityKey&&client.options!=null)McHeldKeys.setHeld(client.options.keyUse,false);usingAbilityKey=false;
    }
    @Override public void onTick() {
        if(rest.paused())return;
        // The Hub port already released keys/navigation/rotation on terminal failure. Do not
        // retick it or clear later manual input while its visible diagnostic remains enabled.
        if(!scopedHarvest&&hub!=null&&hub.loop().phase()==dev.veltraph.client.foraging.hub.HubSourceLoop.Phase.BLOCKED)return;
        releaseAbilityKey();if(!isEnabled())return;
        if(scopedHarvest&&(scopedFinished||scopedFailed))return;
        if(client.player!=nativeOwner||client.level!=nativeWorld) {
            var transition=hub==null?dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.DENIED:hub.expectedOwnerTransition();
            if(transition==dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.DENIED){setEnabled(false);return;}
            releaseAbilityKey();navigation.stop();interactionRotation.cancelRotation();services.breaking().setBreaking(false);services.input().releaseAllMovement();
            if(transition==dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.WAITING)return;
            // Only the issued UUID/connection/destination/receipt contract permits this replacement.
            if(!commitHubOwnerTransition()){setEnabled(false);return;}
        }
        if(client.player==null||client.level==null||client.gui.screen()!=null) { setEnabled(false);return; }
        var names=services.inventory().hotbarItemNames();int slot=services.inventory().selectedSlot();
        if(slot<0||slot>=names.size()||!ForagingAxePolicy.isAxe(names.get(slot))) { lastReason="axe unavailable";setEnabled(false);return; }
        var ability=McForagingAbilitySource.profile(client,config);
        if(ability.activation()==ForagingAxePolicy.Activation.USE&&!McForagingAbilitySource.hasThrowingAbility(client,slot)) {
            lastReason="held axe has no observed Throwing Axe ability";setEnabled(false);return;
        }
        if(area==GameArea.THE_PARK) {
            if(config.get(ForagingConfig.FORAGING_REQUIRE_SWEEP)&&!(ability.sweep()>0)) {
                lastReason="held axe no longer has observed Sweep";setEnabled(false);return;
            }
            Map<UUID,Vec3> positions=new HashMap<>();
            for(var player:List.copyOf(client.level.players()))positions.put(player.getUUID(),new Vec3(player.getX(),player.getY(),player.getZ()));
            parkCrowd.observe(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()),positions);
        }
        long window=Math.max(100,Math.min(5000,config.get(ForagingConfig.FORAGING_ABILITY_RESULT_WINDOW_MS)));
        long now=System.currentTimeMillis(),ttl=Math.max(1000,config.get(ForagingConfig.FORAGING_BLACKLIST_TTL_MS));
        blacklist.prune(now,ttl);visitedCheckpoints.entrySet().removeIf(e->now-e.getValue()>=ttl);
        if(phase.equals("WALK_TO_REGION")) {
            var state=navigation.tick();if(state!=McTravelNavigator.State.ACTIVE) { navigation.stop();phase="SEARCH_TREE";nextSearch=now+500; }
            return;
        }
        if(target==null) { if(hub!=null||now>=nextSearch) search(now);return; }
        if(target.logs().stream().anyMatch(p->!loaded(p))) { abandon(now,"tree chunk unloaded");return; }
        if(phase.equals("COLLECT_DROPS")) { collect(now);return; }
        if(phase.equals("WALK_TO_TREE")) {
            if(now-started>30_000) { if(hub!=null)hub.loop().treeWalkFailed();abandon(now,"tree travel timeout");return; }
            var state=navigation.tick();
            if(state==McTravelNavigator.State.ARRIVED) { phase="BREAK_TREE";started=lastProgress=now; }
            else if(state==McTravelNavigator.State.FAILED) {lastTravelFailure=navigation.status();if(hub!=null)hub.loop().treeWalkFailed();abandon(now,"tree path failed");}
            return;
        }
        List<BlockPos> remaining=target.logs().stream().filter(this::log).toList();
        boolean rollback=remaining.stream().anyMatch(disappeared::contains);
        if(rollback) {
            ghostRollbacks++;disappeared.removeAll(remaining);
            if(config.get(ForagingConfig.FORAGING_GHOST_BLOCK_CHECKER)&&config.get(ForagingConfig.FORAGING_STOP_ON_GHOST_BLOCK_SPAM)&&ghostRollbacks>=3) {
                lastReason="repeated ghost-block rollback";setEnabled(false);return;
            }
        }
        int before=disappeared.size();target.logs().stream().filter(p->!log(p)).forEach(disappeared::add);
        if(disappeared.size()>before) { lastProgress=now;nextAxe=Math.max(nextAxe,now+ability.cooldownMs());services.breaking().setBreaking(false); }
        int batchesBefore=cycle.multiLogBatches();cycle.observe(disappeared.size(),ability.multiLog(),now,window);
        multiBatches+=cycle.multiLogBatches()-batchesBefore;largestBatchObserved=Math.max(largestBatchObserved,cycle.largestBatch());
        if(remaining.isEmpty()) {
            services.breaking().setBreaking(false);interactionRotation.release(navigation);
            // Wait for normal server drops and client block corrections before finishing the tree.
            phase="COLLECT_DROPS";started=now;settleUntil=now+Math.max(750,ability.multiLog()?window:0);return;
        }
        if(now-lastProgress>Math.max(500,config.get(ForagingConfig.FORAGING_TREE_BREAK_TIMEOUT_MS))) { abandon(now,"took too long to break tree");return; }
        if(cycle.waiting(now)||now<nextAxe||McForagingAbilitySource.cooldownActive(client)) {
            services.breaking().setBreaking(false);phase="WAIT_ABILITY_OUTCOME";return;
        }
        phase="BREAK_TREE";
        BlockPos next=aimedLog!=null&&remaining.contains(aimedLog)?aimedLog:remaining.stream().min(Comparator.comparingDouble(this::eyeDistance)).orElseThrow();
        double reach=Math.min(4.5,Math.max(1,config.get(ForagingConfig.FORAGING_TREE_REACH)));
        if(eyeDistance(next)>reach) {
            services.breaking().setBreaking(false);
            if(!moving()) {
                if(approach(next)) { phase="WALK_TO_TREE";started=now; }
                else abandon(now,"not in reach of tree and not moving");
            }
            return;
        }
        aimedLog=next;aim(next);
        boolean aligned=client.hitResult!=blockedRestHit&&client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(mc(next));
        if(aligned) {
            boolean use=ability.activation()==ForagingAxePolicy.Activation.USE;
            if(use) {
                if(!cycle.active()) { McHeldKeys.setHeld(client.options.keyUse,true);usingAbilityKey=true;activationTotal++;cycle.activate(true,now,window);nextAxe=now+ability.cooldownMs(); }
                services.breaking().setBreaking(false);
            } else { if(!cycle.active())activationTotal++;cycle.activate(false,now,window);services.breaking().setBreaking(true); }
        } else services.breaking().setBreaking(false);
    }
    private void search(long now) {
        if(area!=GameArea.THE_PARK){searchHub(now);return;}
        nextSearch=now+1000;
        var origin=client.player.blockPosition();
        Vec3 player=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        float yaw=client.player.getYRot();
        int radius=Math.max(2,Math.min(24,config.get(ForagingConfig.FORAGING_SEARCH_RADIUS)));
        var selected=region();Map<BlockPos,String> logs=new HashMap<>();
        boolean bounds=config.get(ForagingConfig.FORAGING_USE_REGION_BOUNDS);
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)for(int y=-2;y<=24;y++) {
            BlockPos p=new BlockPos(origin.getX()+x,origin.getY()+y,origin.getZ()+z);String id=logId(p);
            if(!id.isEmpty()&&selected.matches(id))logs.put(p,id);
        }
        List<ParkTreePlanner.Candidate> candidates=new ArrayList<>();
        long ttl=Math.max(1000,config.get(ForagingConfig.FORAGING_BLACKLIST_TTL_MS));
        for(var tree:TreeDiscovery.discover(logs,256)) {
            // Atlas-free observations must include the complete connected component and its boundary.
            boolean complete=tree.logs().stream().noneMatch(p->Math.abs(p.x()-origin.getX())==radius
                    ||Math.abs(p.z()-origin.getZ())==radius||p.y()==origin.getY()+24||p.y()==origin.getY()-2
                    ||bounds&&!selected.contains(p)||touchesUnloadedNeighbor(p));
            boolean blocked=tree.logs().stream().anyMatch(p->blacklist.isActive(coord(p),now,ttl));
            // Source atlas approach coordinates are native-only. The observed root is explicit here.
            Vec3 anchor=new Vec3(tree.trunk().x()+.5,tree.trunk().y()+.5,tree.trunk().z()+.5);
            candidates.add(new ParkTreePlanner.Candidate(tree,anchor,true,complete,false,blocked));
        }
        // Actual visible remote player identities require a synchronized player-list entry; NPC-like
        // entities without one cannot contribute invented occupancy. The original NPC predicate is salted.
        List<Vec3> others=List.copyOf(client.level.players()).stream()
                .filter(p->p instanceof RemotePlayer&&!p.isInvisible()&&client.getConnection()!=null
                        &&client.getConnection().getPlayerInfo(p.getUUID())!=null)
                .map(p->new Vec3(p.getX(),p.getY(),p.getZ())).toList();
        var snapshot=new ParkTreePlanner.Snapshot(player,yaw,selected,config.get(ForagingConfig.FORAGING_LOG_DENSITY),bounds,parkCrowd.crowded(),others);
        var ranked=ParkTreePlanner.rank(candidates,snapshot,parkRandom);
        parkCandidates=ranked.size();parkRouteAttempts=0;parkSelectedCost=Double.NaN;
        var route=ParkTreePlanner.firstReachable(ranked,scored->{
            parkRouteAttempts++;
            var tree=scored.candidate().tree();
            var spot=parkApproach(tree.trunk(),player);
            if(spot.isPresent()&&startApproach(spot.get()))return spot;
            // Preserve aliases across a missing base so a failed whole tree cannot be immediately retried.
            blacklist.blacklistTree(tree.logs().stream().map(AutoForagingModule::coord).toList(),now);
            return Optional.empty();
        });
        if(route.isPresent()) {
            var plan=route.get();target=plan.scored().candidate().tree();parkSelectedCost=plan.scored().cost();
            cycle.reset();started=lastProgress=now;inventoryBefore=inventoryLogs(target.species());disappeared.clear();aimedLog=null;phase="WALK_TO_TREE";lastReason="";
            return;
        }
        // Source fallback chooses one region checkpoint at random from those farther than ten blocks.
        var checkpoints=selected.checkpoints().stream().filter(this::loaded).filter(p->!visitedCheckpoints.containsKey(p))
                .filter(p->new Vec3(p.x(),p.y(),p.z()).distanceTo(player)>10).toList();
        if(!checkpoints.isEmpty()) {
            BlockPos checkpoint=checkpoints.get(parkRandom.nextInt(checkpoints.size()));visitedCheckpoints.put(checkpoint,now);
            if(startApproach(checkpoint)){phase="WALK_TO_REGION";started=now;return;}
        }
        lastReason="no loaded complete Park tree passes region/density and first-five route checks";
    }
    private boolean touchesUnloadedNeighbor(BlockPos log) {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!loaded(log.add(x,0,z)))return true;
        return false;
    }
    private Optional<BlockPos> parkApproach(BlockPos log,Vec3 player) {
        return ParkTreePlanner.approach(log,log,player,client.player.getEyeHeight(),new ParkTreePlanner.ApproachTerrain() {
            public boolean standable(BlockPos spot){return terrain.standable(spot);}
            public Vec3 feet(BlockPos spot){return terrain.position(spot);}
            public boolean surroundingStandable(BlockPos spot){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!terrain.standable(spot.add(x,0,z)))return false;return true;}
            public boolean visible(Vec3 eye,BlockPos targetLog) {
                var start=new net.minecraft.world.phys.Vec3(eye.x(),eye.y(),eye.z());
                var result=client.level.clip(new ClipContext(start,net.minecraft.world.phys.Vec3.atCenterOf(mc(targetLog)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
                return result.getType()==HitResult.Type.BLOCK&&result.getBlockPos().equals(mc(targetLog));
            }
        },parkRandom);
    }
    private void searchHub(long now) {
        if(hub==null)return;
        var selection=hub.loop().tick(now);
        if(selection.isPresent()) {
            var selected=selection.get();
            if(startApproach(selected.standingPosition())) {
                target=selected.tree();cycle.reset();started=lastProgress=now;inventoryBefore=inventoryLogs(target.species());
                disappeared.clear();aimedLog=selected.targetLog();phase="WALK_TO_TREE";lastReason="";
                hub.loop().treeWalkStarted(selected.targetLog());return;
            }
            hub.loop().treeWalkFailed();
        }
        lastReason=hub.loop().reason();
        phase=hub.loop().phase()==dev.veltraph.client.foraging.hub.HubSourceLoop.Phase.SEARCH?"SEARCH_TREE":"HUB_"+hub.loop().phase();
    }
    private int inventoryLogs(String species) {
        int count=0;
        for(int i=0;i<client.player.getInventory().getContainerSize();i++) {
            var stack=client.player.getInventory().getItem(i);
            if(species.equals(ForagingRegion.speciesOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))) count+=stack.getCount();
        }
        return count;
    }
    private void collect(long now) {
        if(target.logs().stream().anyMatch(this::log)) { phase="BREAK_TREE";lastProgress=now;return; }
        services.breaking().setBreaking(false);
        var drops=client.level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(mc(target.trunk())).inflate(8),
                item->item.isAlive()&&target.species().equals(ForagingRegion.speciesOf(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString())));
        if(now-started>Math.max(1000,config.get(ForagingConfig.FORAGING_COLLECTION_TIMEOUT_MS))) {
            finish(now,"collection timeout; remaining drops="+drops.size());return;
        }
        if(!drops.isEmpty()) {
            var nearest=drops.stream().min(Comparator.comparingDouble(item->item.distanceToSqr(client.player))).orElseThrow();
            var state=navigation.tick();
            if(state!=McTravelNavigator.State.ACTIVE) {
                var p=nearest.blockPosition();startApproach(new BlockPos(p.getX(),p.getY(),p.getZ()));
            }
            settleUntil=now+500;return;
        }
        navigation.stop();if(now>=settleUntil) finish(now,"");
    }
    private void finish(long now,String reason) {
        broken++;harvested+=disappeared.size();collected+=Math.max(0,inventoryLogs(target.species())-inventoryBefore);
        if(scopedHarvest){scopedFinished=reason.isBlank();scopedFailed=!scopedFinished;}
        abandon(now,reason);
    }
    /** Private-island production supplies its observed grown tree. This scope never falls through
     * into region search or another tree after completion, rejection or cancellation. */
    public boolean beginOwnedHarvest(List<BlockPos> logs,String species,int axeSlot) {
        if(!isEnabled()||client.player==null||client.level==null||logs.isEmpty()||logs.size()>256||axeSlot<0||axeSlot>8
                ||!ForagingAxePolicy.isAxe(services.inventory().hotbarItemNames().get(axeSlot))
                ||logs.stream().anyMatch(p->!loaded(p)||!species.equals(ForagingRegion.speciesOf(logId(p)))))return false;
        scopedHarvest=true;scopedFinished=scopedFailed=false;ownedAxeSlot=axeSlot;services.inventory().selectSlot(axeSlot);
        var trunk=logs.stream().min(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z)).orElseThrow();
        target=new TreeDiscovery.Tree(trunk,species,logs);cycle.reset();disappeared.clear();inventoryBefore=inventoryLogs(species);started=lastProgress=System.currentTimeMillis();nextAxe=0;phase="BREAK_TREE";return true;
    }
    public boolean ownedHarvestComplete(){return scopedHarvest&&scopedFinished;}
    public boolean ownedHarvestFailed(){return scopedHarvest&&scopedFailed;}
    private boolean moving() { var v=client.player.getDeltaMovement();return Math.abs(v.x)>1e-3||Math.abs(v.z)>1e-3; }
    private double eyeDistance(BlockPos p) { return client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(mc(p))); }
    private boolean startApproach(BlockPos destination) {
        if(!navigation.startApproach(destination,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL)) {lastTravelFailure=navigation.status();return false;}
        // Issue ordinary takeoff/walk input before the next physics tick. Starting authorized flight
        // while grounded without its initial input lets vanilla cancel flight before the controller runs.
        if(navigation.tick()==McTravelNavigator.State.FAILED) {
            lastTravelFailure=navigation.status();navigation.stop();return false;
        }
        return true;
    }
    private List<BlockPos> approachSpots(BlockPos trunk) {
        List<BlockPos> spots=new ArrayList<>();
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int dy:new int[]{0,-1,1}) spots.add(trunk.add(d[0],dy,d[1]));
        spots.sort(Comparator.comparingDouble(p->net.minecraft.world.phys.Vec3.atCenterOf(mc(p)).distanceToSqr(client.player.position())));
        return spots;
    }
    private boolean approach(BlockPos trunk) {
        if(hub!=null){var spot=hub.standingPosition(trunk);return spot!=null&&startApproach(spot);}
        if(area==GameArea.THE_PARK){var spot=parkApproach(trunk,new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));return spot.isPresent()&&startApproach(spot.get());}
        for(BlockPos p:approachSpots(trunk)) if(startApproach(p)) return true;return false;
    }
    private void aim(BlockPos p) {
        var eye=client.player.getEyePosition();var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(p));
        double dx=point.x-eye.x,dy=point.y-eye.y,dz=point.z-eye.z;
        interactionRotation.track((float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
    }
    /** Read-only pre-identity scope: one enabled idle Hub parent and its actually issued warp. */
    public boolean hasIssuedHubWarp(){return isEnabled()&&!rest.paused()&&!scopedHarvest&&target==null&&hub!=null&&hub.hasIssuedWarp();}
    /** The Root transition hook owns the one advance on WAITING ticks and returns before onTick. */
    public dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition expectedHubOwnerTransition(){
        return hasIssuedHubWarp()?hub.expectedOwnerTransition():dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.DENIED;
    }
    /** Pure exact-arrival validation followed by one phase-only handoff; movement starts on the
     * next ordinary parent tick and the original issued warp proof remains bound. */
    public boolean commitHubOwnerTransition(){
        if(!hasIssuedHubWarp()||hub.confirmedOwnerTransition()!=dev.veltraph.client.foraging.hub.McHubSourceLoop.OwnerTransition.ARRIVED
                ||client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null||!hub.completeOwnerTransition())return false;
        releaseAbilityKey();navigation.stop();interactionRotation.cancelRotation();services.breaking().setBreaking(false);services.input().releaseAllMovement();
        nativeOwner=client.player;nativeWorld=client.level;nativeConnection=client.getConnection();aimedLog=null;disappeared.clear();return true;
    }
    public void onHubFeedback(String message){if(isEnabled()&&!rest.paused()&&hub!=null)hub.onFeedback(message);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){
        if(!isEnabled()||rest.paused()||!client.isSameThread()||nativeOwner==null||nativeConnection==null
                ||client.player!=nativeOwner||client.level!=nativeWorld||client.getConnection()!=nativeConnection)return false;
        return consumeHubExpectedTeleport(position,now)||navigation.consumeExpectedTeleport(position,now);
    }
    public boolean consumeHubExpectedTeleport(Vec3 position,long now){return isEnabled()&&!rest.paused()&&hub!=null&&hub.consumeExpectedTeleport(position,now);}
    public void onFeedback(String message) { onHubFeedback(message);if(isEnabled()&&!rest.paused()&&message.startsWith("You cannot damage a tree")) abandon(System.currentTimeMillis(),"unbreakable tree"); }
    static boolean hasGameplayInputAuthority(boolean paused,boolean scopedHarvest,dev.veltraph.client.foraging.hub.HubSourceLoop source){
        return !paused&&(scopedHarvest||source==null||source.permitsGameplayInput());
    }
    @Override public boolean ownsGameplayInput(){return hasGameplayInputAuthority(rest.paused(),scopedHarvest,hub==null?null:hub.loop());}
    @Override public Optional<String> scheduledRestReadiness(){
        if(!isEnabled()||scopedHarvest||!phase.equals("SEARCH_TREE")||target!=null||usingAbilityKey||cycle.active())return Optional.of("Foraging requires its idle between-tree search boundary");
        if(!rest.paused()&&(client.player!=nativeOwner||client.level!=nativeWorld))return Optional.of("Foraging original native owner changed");
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Foraging requires a healthy closed native menu");
        if(McForagingAbilitySource.cooldownActive(client)||System.currentTimeMillis()<nextAxe)return Optional.of("Axe cooldown or ability batch is still active");
        var names=services.inventory().hotbarItemNames();if(ownedAxeSlot<0||ownedAxeSlot>=names.size()||!ForagingAxePolicy.isAxe(names.get(ownedAxeSlot)))return Optional.of("Owned foraging axe is unavailable");
        if(area==GameArea.THE_PARK&&config.get(ForagingConfig.FORAGING_REQUIRE_SWEEP)&&!(McForagingAbilitySource.profile(client,config,ownedAxeSlot).sweep()>0))return Optional.of("Owned axe no longer has observed Sweep");
        if(config.get(ForagingConfig.FORAGING_AXE_MODE)==ForagingConfig.AxeMode.THROWING&&!McForagingAbilitySource.hasThrowingAbility(client,ownedAxeSlot))return Optional.of("Owned Throwing Axe ability is unavailable");
        return Optional.empty();
    }
    @Override public boolean pauseForScheduledRest(Object ticket){return rest.pause(ticket,scheduledRestReadiness().isEmpty(),System.currentTimeMillis(),()->{if(hub!=null)pausedHub=hub.loop().save();releaseAbilityKey();services.breaking().setBreaking(false);services.input().releaseAllMovement();interactionRotation.release(navigation);});}
    @Override public boolean scheduledRestOwnedBy(Object ticket){return isEnabled()&&rest.owns(ticket);}
    @Override public boolean rebindForScheduledRest(Object ticket){
        if(!scheduledRestOwnedBy(ticket)||scheduledRestReadiness().isPresent())return false;
        nativeOwner=client.player;nativeWorld=client.level;nativeConnection=client.getConnection();aimedLog=null;blockedRestHit=client.hitResult;navigation.stop();if(hub!=null&&(pausedHub==null||!hub.loop().restore(pausedHub)))return false;return rest.rebound(ticket);
    }
    @Override public boolean resumeFromScheduledRest(Object ticket){if(!scheduledRestOwnedBy(ticket))return false;long elapsed=rest.resume(ticket,System.currentTimeMillis());if(elapsed<0)return false;nextSearch+=elapsed;pausedHub=null;return true;}
    public Object scheduledEngineIdentity(){return cycle;}
    public int largestObservedBatch() {return largestBatchObserved;}
    public int abilityActivations() {return activationTotal;}
    public int brokenTrees() { return broken; }
    public int harvestedLogs() { return harvested; }
    public int collectedItems() { return collected; }
    public String phase() { return phase; }
    @Override public String diagnostics() {
        return id()+" | "+phase+" | region="+region()+" | target="+(target==null?null:target.trunk())+" | aimed="+aimedLog
                +" | parkCandidates="+parkCandidates+" | parkRouteAttempts="+parkRouteAttempts+" | parkSelectedCost="+parkSelectedCost+" | parkCrowded="+parkCrowd.crowded()
                +" | remaining="+(target==null?0:target.logs().size()-disappeared.size())+" | broken="+broken+" | harvested="+harvested
                +" | collected="+collected+" | largestObservedBatch="+largestBatchObserved+" | multiLogBatches="+multiBatches+" | abilityActivations="+activationTotal+" | travelFailure="+lastTravelFailure+" | blacklistEntries="+blacklist.size()+" | ghostRollbacks="+ghostRollbacks+" | "+lastReason;
    }
}
