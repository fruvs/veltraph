package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LevelChunkSection.class)
public interface CollectionSceneSectionAccessor extends CollectionSceneNativeAccess.Section {
    @Accessor("states") PalettedContainer<BlockState> veltraph$sceneStates();
}
