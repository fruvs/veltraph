package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Native observation adapter, no movement, attack, inventory or camera actions. */
public final class McFigSourceWorld implements FigSourceGeometry.World {
    public record RayContext(boolean moongladeMarsh,boolean specialAzaleaTask,boolean regionBound) {
        public RayContext(boolean moongladeMarsh,boolean specialAzaleaTask){this(moongladeMarsh,specialAzaleaTask,true);}
    }
    private final Minecraft client;
    private final ClientLevel level;
    private final FigTargetPassability passability;
    private final Supplier<RayContext> context;
    private final Set<Block> support=new HashSet<>();

    public McFigSourceWorld(Minecraft client,FigTargetPassability passability,RayContext context) {
        this(client,passability,()->context);
    }
    public McFigSourceWorld(Minecraft client,FigTargetPassability passability,Supplier<RayContext> context) {
        this.client=client;this.level=client.level;this.passability=passability;this.context=context;
        if(level==null||client.player==null)throw new IllegalStateException("Missing native Fig world");
        for(Block block:BuiltInRegistries.BLOCK) {
            boolean solid=false;
            try {solid=!block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).isEmpty();}
            catch(Exception unavailable){/* Source failed default probe is absent from its passable set. */solid=true;}
            if(block instanceof FenceBlock||block instanceof FenceGateBlock||block instanceof WallBlock
                    ||block instanceof WebBlock||block instanceof StandingSignBlock)solid=false;
            if(block instanceof CarpetBlock||block instanceof MossyCarpetBlock||block==Blocks.SNOW||block==Blocks.LILY_PAD)solid=true;
            if(solid)support.add(block);
        }
    }
    private void valid(){if(client.level!=level||client.player==null)throw new IllegalStateException("Fig native context changed");}
    private static net.minecraft.core.BlockPos mc(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private BlockState state(BlockPos p){valid();if(!loaded(p))throw new IllegalStateException("Fig descriptor probe requires a loaded native chunk");return level.getBlockState(mc(p));}
    private boolean loaded(BlockPos p){return level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private boolean fluid(BlockPos p){return state(p).getFluidState().getType()!=Fluids.EMPTY;}
    @Override public boolean passable(BlockPos p){return passability.passable(state(p));}
    @Override public boolean log(BlockPos p){return state(p).is(BlockTags.LOGS);}
    @Override public boolean leaves(BlockPos p){return state(p).getBlock() instanceof LeavesBlock;}
    @Override public Object blockType(BlockPos p){return state(p).getBlock();}
    @Override public boolean standing(BlockPos p) {
        valid();if(!loaded(p))return false;
        if(!passable(p)||!passable(p.up()))return false;
        BlockState below=state(p.down());Block block=below.getBlock();
        if(support.contains(block))return true;
        if(block instanceof FenceBlock||block instanceof WallBlock
                ||block instanceof FenceGateBlock&&!below.getValue(FenceGateBlock.OPEN))return false;
        return fluid(p)&&fluid(p.down());
    }
    @Override public boolean dryStanding(BlockPos p){return !fluid(p.down())&&standing(p);}
    @Override public Vec3 standingEye(Vec3 supplied) {
        BlockPos below=supplied.toBlockPos().down();BlockState state=state(below);
        var shape=state.getCollisionShape(level,mc(below));
        double height=shape.isEmpty()?(fluid(below)?1:0):shape.bounds().maxY;
        return new Vec3(supplied.x(),below.y()+height+1.62,supplied.z());
    }
    @Override public boolean sourceRay(Vec3 origin,BlockPos candidate,boolean leavesAllowed) {
        valid();
        Vec3 endpoint=FigSourceGeometry.center(candidate);
        int steps=(int)Math.ceil(Math.max(Math.abs(endpoint.x()-origin.x()),Math.abs(endpoint.z()-origin.z()))*2)+1;
        for(int i=0;i<=steps;i++) {
            double t=(double)i/steps;
            var sample=new Vec3(origin.x()+(endpoint.x()-origin.x())*t,origin.y()+(endpoint.y()-origin.y())*t,origin.z()+(endpoint.z()-origin.z())*t);
            if(!loaded(sample.toBlockPos()))throw new IllegalStateException("Fig ray traversal requires loaded native chunks");
        }
        var from=new net.minecraft.world.phys.Vec3(origin.x(),origin.y(),origin.z());
        var hit=level.clip(new ClipContext(from,net.minecraft.world.phys.Vec3.atCenterOf(mc(candidate)),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,CollisionContext.empty()));
        // The original true flag disables its entity picker entirely. Only this block ray applies.
        if(hit.getType()!=HitResult.Type.BLOCK)return false;
        var nativeHit=hit.getBlockPos();BlockState hitState=state(new BlockPos(nativeHit.getX(),nativeHit.getY(),nativeHit.getZ()));
        RayContext context=this.context.get();
        boolean leaf=leavesAllowed&&hitState.getBlock() instanceof LeavesBlock;
        boolean accepted=hitState.is(BlockTags.LOGS)||leaf;
        if(leaf&&!context.regionBound())return false; // Typed unbound preference; never disable unambiguous LOG hits.
        if(leaf&&context.moongladeMarsh()&&hitState.getBlock()!=Blocks.AZALEA_LEAVES)accepted=false;
        if(context.specialAzaleaTask())accepted=hitState.getBlock()==Blocks.FLOWERING_AZALEA||hitState.getBlock()==Blocks.AZALEA;
        return accepted&&nativeHit.distSqr(mc(candidate))<9;
    }
}
