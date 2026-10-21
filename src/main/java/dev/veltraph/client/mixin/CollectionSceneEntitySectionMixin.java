package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntitySection.class)
public abstract class CollectionSceneEntitySectionMixin implements CollectionSceneNativeAccess.EntitySectionObserver {
    @Inject(method="updateChunkStatus(Lnet/minecraft/world/level/entity/Visibility;)Lnet/minecraft/world/level/entity/Visibility;",at=@At("HEAD"),require=1)
    private void veltraph$sceneVisibility(Visibility visibility,CallbackInfoReturnable<Visibility> cir) {
        if (!CollectionSceneHistory.nativeScenesWatched()) return;
        // Includes HIDDEN contents; revision happens before native visibility can reveal any collider.
        ((EntitySection<?>)(Object)this).getEntities().forEach(CollectionSceneHistory::nativeEntityWrite);
    }
    @Inject(method="add(Lnet/minecraft/world/level/entity/EntityAccess;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneAdd(EntityAccess entity,CallbackInfo ci) { CollectionSceneHistory.nativeEntityWrite(entity); }
    @Inject(method="remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z",at=@At("HEAD"),require=1)
    private void veltraph$sceneRemove(EntityAccess entity,CallbackInfoReturnable<Boolean> cir) { CollectionSceneHistory.nativeEntityWrite(entity); }
}
