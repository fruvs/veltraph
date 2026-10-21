package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntitySectionStorage.class)
public abstract class CollectionSceneEntityStorageMixin implements CollectionSceneNativeAccess.EntityStorageObserver {
    @Shadow @Final private Long2ObjectMap<EntitySection<Entity>> sections;
    @Inject(method="remove(J)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneRemoveSection(long sectionKey,CallbackInfo ci) {
        if (!CollectionSceneHistory.nativeScenesWatched()) return;
        EntitySection<Entity> section=sections.get(sectionKey);
        if (section != null) section.getEntities().forEach(CollectionSceneHistory::nativeEntityWrite);
    }
}
