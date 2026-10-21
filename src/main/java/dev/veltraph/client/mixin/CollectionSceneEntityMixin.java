package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class CollectionSceneEntityMixin implements CollectionSceneNativeAccess.EntityObserver {
    @Inject(method={"setBoundingBox(Lnet/minecraft/world/phys/AABB;)V","setPosRaw(DDD)V",
        "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V","unsetRemoved()V",
        "setLevel(Lnet/minecraft/world/level/Level;)V"},at=@At("HEAD"),require=1)
    private void veltraph$sceneBeforeEntityWrite(CallbackInfo ci) { CollectionSceneHistory.nativeEntityWrite(this); }
    @Inject(method={"setBoundingBox(Lnet/minecraft/world/phys/AABB;)V","setPosRaw(DDD)V",
        "setLevel(Lnet/minecraft/world/level/Level;)V"},at=@At("RETURN"),require=1)
    private void veltraph$sceneAfterEntityWrite(CallbackInfo ci) { CollectionSceneHistory.nativeEntityWrite(this); }
}
