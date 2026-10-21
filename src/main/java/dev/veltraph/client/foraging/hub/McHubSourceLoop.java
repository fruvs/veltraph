package dev.veltraph.client.foraging.hub;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.foraging.ForagingRegion;
import dev.veltraph.client.foraging.TreeDiscovery;
import dev.veltraph.client.navigation.GroundPathfinder;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Loaded-world adapter for the Hub parent. Every approach is supported ordinary walking.
 * Native server warp metadata is intentionally supplied through a separate receipt-bearing port. */
public final class McHubSourceLoop implements HubSourceLoop.Port {
    public enum OwnerTransition { DENIED, WAITING, ARRIVED }
    public interface WarpAccess {
        boolean request();
        HubSourceLoop.Motion motion();
        void cancel();
        WarpAccess UNBOUND=new WarpAccess(){
            public boolean request(){return false;}
            public HubSourceLoop.Motion motion(){return HubSourceLoop.Motion.FAILED;}
            public void cancel(){}
        };
    }
    private final Minecraft client;
    private final ConfigService config;
    private final FarmServices services;
    private final McNavigator terrain;
    private final BiPredicate<BlockPos,Long> blacklist;
    private final BooleanSupplier inHub,stop;
    private final WarpAccess warp;
    private final HubSourceLoop loop;
    private BlockPos anchorGoal;private Vec3 anchorStart;
    private String anchorEvidence="no anchor navigation evaluated";
    private final java.util.random.RandomGenerator random;
    public McHubSourceLoop(Minecraft client,ConfigService config,FarmServices services,BiPredicate<BlockPos,Long> blacklist) {
        this(client,config,services,blacklist,()->new dev.veltraph.client.game.mc.McGameContextService(client).current().isArea(dev.veltraph.client.game.GameArea.HUB),()->false,new McHubWarpAccess(client,config));
    }
    public McHubSourceLoop(Minecraft client,ConfigService config,FarmServices services,BiPredicate<BlockPos,Long> blacklist,
                           BooleanSupplier inHub,BooleanSupplier stop,WarpAccess warp) {
        this(client,config,services,blacklist,inHub,stop,warp,java.util.random.RandomGenerator.getDefault());
    }
    public McHubSourceLoop(Minecraft client,ConfigService config,FarmServices services,BiPredicate<BlockPos,Long> blacklist,
                           BooleanSupplier inHub,BooleanSupplier stop,WarpAccess warp,java.util.random.RandomGenerator random) {
        this.client=client;this.config=config;this.services=services;this.blacklist=blacklist;this.inHub=inHub;this.stop=stop;this.warp=warp;this.random=random;
        terrain=new McNavigator(client,services.input(),config);loop=new HubSourceLoop(this);
    }
    public HubSourceLoop loop(){return loop;}
    public boolean inHub(){return inHub.getAsBoolean();}
    public boolean sourceStopRequested(){return stop.getAsBoolean()||client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null&&!(warp instanceof McHubWarpAccess nativeWarp&&nativeWarp.permittedPendingLoading());}
    public BlockPos playerBlock(){var p=client.player.blockPosition();return new BlockPos(p.getX(),p.getY(),p.getZ());}
    public Vec3 playerPosition(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private static net.minecraft.core.BlockPos mc(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(BlockPos p){return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private boolean taggedLog(BlockPos p){
        if(!loaded(p))return false;var state=client.level.getBlockState(mc(p));
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return state.is(BlockTags.LOGS)&&!ForagingRegion.speciesOf(id).isBlank()&&dev.veltraph.client.foraging.LogFilter.matches(id,config.get(dev.veltraph.client.config.ForagingConfig.FORAGING_LOG_KEYWORDS));
    }
    public TreeDiscovery.Tree treeAt(BlockPos checkpoint){
        if(!taggedLog(checkpoint))return null;
        var originalBlock=client.level.getBlockState(mc(checkpoint)).getBlock();
        String originalId=BuiltInRegistries.BLOCK.getKey(originalBlock).toString();
        Set<BlockPos> logs=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();logs.add(checkpoint);queue.add(checkpoint);
        while(!queue.isEmpty()) {
            var p=queue.remove();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++) {
                if(x==0&&y==0&&z==0)continue;var next=p.add(x,y,z);
                // Reject an unknown or oversized component rather than inventing a partial tree.
                if(!loaded(next))return null;
                if(!logs.contains(next)&&client.level.getBlockState(mc(next)).is(originalBlock)) {
                    if(HubTreePlanner.squared(next,checkpoint)>32*32||logs.size()>=256)return null;
                    logs.add(next);queue.add(next);
                }
            }
        }
        return TreeDiscovery.discover(logs.stream().collect(java.util.stream.Collectors.toMap(p->p,p->originalId)),256).stream().findFirst().orElse(null);
    }
    public boolean blacklisted(BlockPos checkpoint,long now){return blacklist.test(checkpoint,now);}
    public boolean pathExists(BlockPos target){return GroundPathfinder.find(terrain,GroundPathfinder.node(playerPosition()),target,5000,50).found();}
    public boolean startAnchorWalk(BlockPos target){
        anchorGoal=target;anchorStart=playerPosition();boolean accepted=terrain.start(terrain.position(target),5000,50);
        anchorEvidence="anchor="+target+" start="+anchorStart+" search=5000/50 accepted="+accepted+" status="+terrain.status();return accepted;
    }
    public HubSourceLoop.Motion anchorMotion(){
        var state=terrain.tick();anchorEvidence="anchor="+anchorGoal+" start="+anchorStart+" search=5000/50 | "+terrain.diagnostics();
        return switch(state){case WALKING -> HubSourceLoop.Motion.ACTIVE;case ARRIVED -> HubSourceLoop.Motion.ARRIVED;default -> HubSourceLoop.Motion.FAILED;};
    }
    /** Last native navigator observation, retained after failure releases input; no tick or game read. */
    public String anchorDiagnostics(){return anchorEvidence;}
    public boolean requestHubWarp(){return warp.request();}
    public HubSourceLoop.Motion hubWarpMotion(){return warp.motion();}
    public void release(){terrain.stop();services.input().releaseAllMovement();services.breaking().setBreaking(false);services.rotation().cancelRotation();}
    public void cancel(){loop.stop();warp.cancel();}
    public boolean hasIssuedWarp(){return loop.phase()==HubSourceLoop.Phase.WAIT_WARP&&warp instanceof McHubWarpAccess nativeWarp&&nativeWarp.hasIssuedWarp();}
    public OwnerTransition confirmedOwnerTransition(){return loop.phase()==HubSourceLoop.Phase.WAIT_WARP&&warp instanceof McHubWarpAccess nativeWarp?nativeWarp.confirmedOwnerTransition():OwnerTransition.DENIED;}
    public boolean completeOwnerTransition(){return confirmedOwnerTransition()==OwnerTransition.ARRIVED&&loop.confirmedWarpArrival();}
    public OwnerTransition expectedOwnerTransition(){return loop.phase()==HubSourceLoop.Phase.WAIT_WARP&&warp instanceof McHubWarpAccess nativeWarp?nativeWarp.expectedOwnerTransition():OwnerTransition.DENIED;}
    public void onFeedback(String message){if(warp instanceof McHubWarpAccess nativeWarp)nativeWarp.feedback(message);}
    public boolean consumeExpectedTeleport(Vec3 position,long now){return loop.phase()==HubSourceLoop.Phase.WAIT_WARP&&warp instanceof McHubWarpAccess nativeWarp&&nativeWarp.consumeExpectedTeleport(position,now);}
    /** Readable stand search: radius 1..2.5 and vertical -3..1, then nearby visible positions
     * within 3.5 eye blocks, retaining the source [0,1.25) score perturbation. */
    public BlockPos standingPosition(BlockPos log){
        var player=playerPosition();List<BlockPos> base=new ArrayList<>();
        for(int x=-3;x<=3;x++)for(int y=-3;y<=1;y++)for(int z=-3;z<=3;z++) {
            var p=log.add(x,y,z);double distance=Math.sqrt(HubTreePlanner.squared(p,log));
            if(distance>=1&&distance<=2.5&&terrain.standable(p))base.add(p);
        }
        base.sort(Comparator.comparingDouble(p->terrain.position(p).distanceTo(player)+Math.abs(1-Math.sqrt(HubTreePlanner.squared(p,log)))));
        if(base.isEmpty())return null;
        var initial=base.getFirst();BlockPos best=null;double score=Double.MAX_VALUE;
        for(int x=-3;x<=3;x++)for(int y=-1;y<=1;y++)for(int z=-3;z<=3;z++) {
            var p=initial.add(x,y,z);if(!terrain.standable(p)||!visibleFrom(p,log))continue;
            var pos=terrain.position(p);double next=pos.distanceTo(player)+Math.abs(pos.distanceTo(new Vec3(log.x()+.5,log.y()+.5,log.z()+.5))-3)+random.nextDouble(1.25);
            boolean openNeighborhood=true;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!terrain.standable(p.add(dx,0,dz)))openNeighborhood=false;
            if(!openNeighborhood)next+=2;
            if(next<score){score=next;best=p;}
        }
        // Source's refinement falls back to the initial stand when no refinement is visible.
        return best==null?initial:best;
    }
    public double checkpointJitter(){return random.nextDouble(6);}
    public BlockPos targetLog(BlockPos checkpoint,BlockPos stand){
        if(random.nextDouble()>=.25)return checkpoint;
        var alternatives=new ArrayList<BlockPos>();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            var p=checkpoint.add(x,y,z);if(taggedLog(p)&&visibleFrom(stand,p,4.5))alternatives.add(p);
        }
        return alternatives.isEmpty()?checkpoint:alternatives.get(random.nextInt(alternatives.size()));
    }
    private boolean visibleFrom(BlockPos stand,BlockPos log){return visibleFrom(stand,log,3.5);}
    private boolean visibleFrom(BlockPos stand,BlockPos log,double reach){
        var pos=terrain.position(stand);var eye=new net.minecraft.world.phys.Vec3(pos.x(),pos.y()+client.player.getEyeHeight(),pos.z());
        var target=net.minecraft.world.phys.Vec3.atCenterOf(mc(log));if(eye.distanceTo(target)>reach)return false;
        var hit=client.level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(mc(log));
    }
}
