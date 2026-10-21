package dev.veltraph.client.galatea;

import dev.veltraph.client.mixin.FigJumpPowerAccessor;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** Productive native selection adapter; caller supplies observed descriptor/region provenance. */
public final class McFigSourceSelector {
    public record Descriptor(BlockPos base,List<BlockPos> blocks,String provenance) {
        public Descriptor {
            blocks=FigNativeDescriptorOrder.observe(blocks);
            if(base==null||blocks.isEmpty()||provenance==null||provenance.isBlank())
                throw new IllegalArgumentException("Observed Fig descriptor base, blocks and provenance required");
        }
        public static Descriptor observedLoadedComponent(GalateaPlanner.Tree tree) {
            return new Descriptor(tree.root(),tree.logs(),"Automatically observed complete loaded component; minimum root/base is a local adaptation, original global atlas base unproven");
        }
    }
    private final Minecraft client;
    private final FigSourceSelector selector;
    private final McFigSourceWorld world;
    private final Descriptor descriptor;
    private final FigSourceGeometry geometry;
    private Vec3 preparedStand;
    private BlockPos scaffoldSeed;
    private final int observedJumpMaximum;
    public McFigSourceSelector(Minecraft client,FigTargetPassability passability,Descriptor descriptor,
                               int observedScaffoldHeight,Supplier<McFigSourceWorld.RayContext> rayContext) {
        this.client=client;this.descriptor=descriptor;
        preparedStand=FigSourceGeometry.center(descriptor.base());
        world=new McFigSourceWorld(client,passability,rayContext);
        observedJumpMaximum=nativeJumpHeight();
        geometry=new FigSourceGeometry(descriptor.base(),descriptor.blocks(),world,observedJumpMaximum,observedScaffoldHeight);
        selector=new FigSourceSelector(geometry,world);
    }
    private int nativeJumpHeight() {
        if(!(client.player instanceof FigJumpPowerAccessor nativePower))throw new IllegalStateException("Native jump-power accessor unavailable");
        return FigSourceGeometry.jumpHeight(nativePower.veltraph$sourceJumpPower(1f));
    }
    public void enterJump(){selector.enterJump(preparedStand,nativeJumpHeight());}
    public Optional<Vec3> prepareStem(BlockPos target) {
        var next=FigSourceStandPolicy.stem(geometry,world,target,position(),client.player.getYRot(),true);
        next.ifPresent(p->preparedStand=p);return next;
    }
    public Optional<Vec3> prepareThrow(BlockPos target) {
        var eye=client.player.getEyePosition();
        var box=client.player.getBoundingBox();
        var support=net.minecraft.core.BlockPos.containing(box.minX+(box.maxX-box.minX)/2,box.minY-.001,box.minZ+(box.maxZ-box.minZ)/2);
        boolean scaffold=client.level.getBlockState(support).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
        var next=FigSourceStandPolicy.throwing(geometry,world,target,position(),new Vec3(eye.x,eye.y,eye.z),client.player.getYRot(),scaffold);
        next.ifPresent(p->preparedStand=p);return next;
    }
    /** Null-feet source callback omits horizontal and same-column terms, then central yaw is added. */
    public Optional<Vec3> prepareScaffold(boolean aimHigher,BlockPos previousTarget){
        var player=position();var eye=client.player.getEyePosition();float yaw=client.player.getYRot();
        scaffoldSeed=FigScaffoldCentralSeed.select(FigScaffoldCentralSeed.liveModeList(geometry.candidates(GalateaPlanner.AxeMode.SCAFFOLD,Set.of()),world::log),
                new Vec3(descriptor.base().x(),descriptor.base().y(),descriptor.base().z()),yaw,p->{
                    var center=FigSourceGeometry.center(p);
                    double angle=Math.toDegrees(Math.atan2(center.z()-player.z(),center.x()-player.x()))-90-yaw;
                    double delta=((angle+180)%360+360)%360-180;
                    return Math.abs(p.y()-(descriptor.base().y()+(aimHigher?1:0)))*7.5+Math.abs(delta)/15
                            +FigSourceCandidateScore.localPenalty(p,previousTarget,world.passable(previousTarget),false)+geometry.leafCost(p);
                }).orElse(null);
        if(scaffoldSeed==null)return Optional.empty();
        var pad=FigSourceCentralStandPolicy.choose(descriptor.base(),scaffoldSeed,world,player,yaw,
                ()->java.util.concurrent.ThreadLocalRandom.current().nextDouble(0,.5));
        // Original Fig callback supplies block-center pose; native walk caller separately projects support.
        preparedStand=FigSourceGeometry.center(pad);return Optional.of(preparedStand);
    }
    public int scaffoldReachableCount(Vec3 actualEye){
        return FigSourceCentralStandPolicy.reachableCount(actualEye,
                FigScaffoldCentralSeed.liveModeList(geometry.candidates(GalateaPlanner.AxeMode.SCAFFOLD,Set.of()),world::log));
    }
    public Optional<BlockPos> scaffoldSeed(){return Optional.ofNullable(scaffoldSeed);}
    public boolean scaffoldSeedVisible(){var eye=client.player.getEyePosition();return scaffoldSeed!=null&&world.sourceRay(new Vec3(eye.x,eye.y,eye.z),scaffoldSeed,true);}
    public boolean scaffoldPassable(BlockPos p){return world.passable(p);}
    /** Capture current feet center; never writes a native pose or substitutes eye height. */
    public Vec3 captureCenteredStand(){var feet=client.player.blockPosition();preparedStand=new Vec3(feet.getX()+.5,feet.getY(),feet.getZ()+.5);return preparedStand;}
    public String descriptorProvenance(){return descriptor.provenance();}
    public Optional<FigSelectionReceipt> lastReceipt(){return selector.lastReceipt();}
    public List<FigSelectionReceipt> recentReceipts(){return selector.recentReceipts();}
    public int descriptorJumpMaximum(){return observedJumpMaximum;}
    public Optional<BlockPos> select(GalateaPlanner.AxeMode mode,List<BlockPos> remaining,Predicate<BlockPos> canAttempt,
                                     boolean aimHigher,BlockPos previousTarget) {
        var feet=client.player.blockPosition();var eye=client.player.getEyePosition();
        if(mode==GalateaPlanner.AxeMode.THROWING) {
            var nativeSet=new it.unimi.dsi.fastutil.longs.LongOpenHashSet(remaining.size());
            for(BlockPos p:remaining)nativeSet.add(net.minecraft.core.BlockPos.asLong(p.x(),p.y(),p.z()));
            var ordered=new ArrayList<BlockPos>();var iterator=nativeSet.iterator();
            while(iterator.hasNext()){var p=net.minecraft.core.BlockPos.of(iterator.nextLong());ordered.add(new BlockPos(p.getX(),p.getY(),p.getZ()));}
            remaining=ordered;
        }
        return selector.select(mode,remaining,canAttempt,new FigSourceSelector.View(new BlockPos(feet.getX(),feet.getY(),feet.getZ()),
                position(),new Vec3(eye.x,eye.y,eye.z),client.player.getYRot()),aimHigher,previousTarget);
    }
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
}
