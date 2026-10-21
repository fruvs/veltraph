package dev.veltraph.client.mixin;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Holder.Reference.class)
public interface CollectionSceneHolderAccessor {
    @Accessor("tags") Set<TagKey<Block>> veltraph$sceneRawHolderTags();
    @Accessor("value") Object veltraph$sceneRawHolderValue();
}
