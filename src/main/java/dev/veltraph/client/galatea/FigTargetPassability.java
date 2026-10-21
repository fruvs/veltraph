package dev.veltraph.client.galatea;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Source registry-derived passable set, plus its actual gate/snow state exceptions. */
public final class FigTargetPassability {
    private final Set<Block> defaults=new HashSet<>();
    public FigTargetPassability() {
        for(Block block:BuiltInRegistries.BLOCK) {
            try {if(block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty())defaults.add(block);}
            catch(Exception unavailable) { /* Source leaves a failed default-state probe out of the set. */ }
        }
        defaults.remove(Blocks.SNOW);
    }
    public boolean passable(BlockState state) {
        Block block=state.getBlock();
        if(block instanceof FenceGateBlock)return state.getValue(FenceGateBlock.OPEN);
        if(block instanceof SnowLayerBlock)return state.getValue(SnowLayerBlock.LAYERS)==1;
        return block==Blocks.AIR||defaults.contains(block);
    }
}
