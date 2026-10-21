package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelChunk.class)
public abstract class CollectionSceneChunkBlockEntityMixin implements CollectionSceneNativeAccess.ChunkObserver {
    @Inject(method="setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneBlockEntitySet(BlockEntity entity,CallbackInfo ci) {
        CollectionSceneHistory.nativeChunkWrite(this,entity.getBlockPos());
    }
    @Inject(method="removeBlockEntity(Lnet/minecraft/core/BlockPos;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneBlockEntityRemove(BlockPos pos,CallbackInfo ci) { CollectionSceneHistory.nativeChunkWrite(this,pos); }
    @Inject(method="clearAllBlockEntities()V",at=@At("HEAD"),require=1)
    private void veltraph$sceneBlockEntityClear(CallbackInfo ci) { CollectionSceneHistory.nativeChunkWrite(this,null); }
}
