package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Block.class)
public interface CollectionSceneBlockAccessor {
    @Accessor("builtInRegistryHolder") Holder.Reference<Block> veltraph$sceneRawBlockHolder();
}
