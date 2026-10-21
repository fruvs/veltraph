package dev.veltraph.client.fishing.trash.mc;

import dev.veltraph.client.fishing.trash.FishingTrashCliffSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Recovered null-traversal-vector source predicates, using only actual loaded native blocks. */
public final class McFishingTrashCliffFacts implements FishingTrashCliffSelector.Facts {
    private final Minecraft client;
    private final Map<Block,Boolean> defaults=new IdentityHashMap<>();
    public McFishingTrashCliffFacts(Minecraft client){this.client=client;}
    private static BlockPos nativePos(FishingTrashCliffSelector.Pos p){return new BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(FishingTrashCliffSelector.Pos p){return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private boolean defaultEmpty(Block block){return defaults.computeIfAbsent(block,b->{try{return b.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty();}catch(RuntimeException e){return false;}});}
    public boolean passable(FishingTrashCliffSelector.Pos p) {
        if(!loaded(p))return false;var state=client.level.getBlockState(nativePos(p));var block=state.getBlock();
        if(block instanceof FenceGateBlock)return state.getValue(FenceGateBlock.OPEN);
        if(block instanceof SnowLayerBlock)return state.getValue(SnowLayerBlock.LAYERS)==1;
        return block==Blocks.AIR||defaultEmpty(block);
    }
    public boolean standable(FishingTrashCliffSelector.Pos p) {
        var below=p.offset(0,-1,0);
        if(!loaded(p)||!loaded(below)||!passable(p)||!passable(p.offset(0,1,0)))return false;
        var state=client.level.getBlockState(nativePos(below));if(!state.getFluidState().isEmpty())return false;
        var block=state.getBlock();
        if(block instanceof CarpetBlock||block instanceof MossyCarpetBlock||block==Blocks.SNOW||block==Blocks.LILY_PAD)return true;
        if(block instanceof FenceBlock||block instanceof FenceGateBlock||block instanceof WallBlock||block instanceof WebBlock||block instanceof StandingSignBlock)return false;
        return !defaultEmpty(block);
    }
    public FishingTrashCliffSelector.Kind kind(FishingTrashCliffSelector.Pos p) {
        if(!loaded(p))return FishingTrashCliffSelector.Kind.OTHER;var state=client.level.getBlockState(nativePos(p));
        return state.is(Blocks.LAVA)?FishingTrashCliffSelector.Kind.LAVA:state.is(Blocks.SOUL_SAND)?FishingTrashCliffSelector.Kind.SOUL_SAND:FishingTrashCliffSelector.Kind.OTHER;
    }
    public double yawDifferenceToCenter(FishingTrashCliffSelector.Pos p) {
        if(client.player==null)return 0;
        return net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(p.z()+.5-client.player.getZ(),p.x()+.5-client.player.getX()))-90-client.player.getYRot());
    }
    /** Recheck the source face after walking and before the destructive request. */
    public boolean stillEligible(FishingTrashCliffSelector.Candidate candidate) {
        if(candidate==null||!passable(candidate.position())||!standable(candidate.position()))return false;
        for(var direction:candidate.clearDirections()) {
            int dx=switch(direction){case EAST->1;case WEST->-1;default->0;},dz=switch(direction){case SOUTH->1;case NORTH->-1;default->0;};
            for(int dy=2;dy>-6;dy--)if(!passable(candidate.position().offset(dx,dy,dz))||!passable(candidate.position().offset(dx*2,dy,dz*2)))return false;
        }
        return true;
    }
}
