package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SynchedEntityData.class)
public abstract class CollectionSceneEntityDataMixin implements CollectionSceneNativeAccess.EntityDataObserver {
    @Shadow @Final private SyncedDataHolder entity;
    @Inject(method={"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
        "assignValues(Ljava/util/List;)V"},at=@At("HEAD"),require=1)
    private void veltraph$sceneData(CallbackInfo ci) { CollectionSceneHistory.nativeEntityWrite(entity); }
}
