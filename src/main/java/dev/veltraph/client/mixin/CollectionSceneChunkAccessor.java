package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChunkAccess.class)
public interface CollectionSceneChunkAccessor extends CollectionSceneNativeAccess.Chunk {
    @Accessor("sections") LevelChunkSection[] veltraph$sceneSections();
}
