package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientLevel.class)
public interface CollectionSceneWorldAccessor extends CollectionSceneNativeAccess.World {
    @Accessor("worldBorder") WorldBorder veltraph$sceneBorder();
    @Accessor("chunkSource") ClientChunkCache veltraph$sceneChunkSource();
    @Accessor("entityStorage") TransientEntitySectionManager<Entity> veltraph$sceneEntityStorage();
}
