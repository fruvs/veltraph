package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientChunkCache.class)
public abstract class CollectionSceneChunkSourceMixin implements CollectionSceneNativeAccess.SourceObserver {
    @Inject(method="drop(Lnet/minecraft/world/level/ChunkPos;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneDrop(ChunkPos pos,CallbackInfo ci) {
        CollectionSceneHistory.nativeChunkLifecycle((ClientChunkCache)(Object)this,pos.x(),pos.z(),false);
    }
    @Inject(method="replaceWithPacketData(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)Lnet/minecraft/world/level/chunk/LevelChunk;",at=@At("HEAD"),require=1)
    private void veltraph$sceneReplace(int x,int z,ClientboundLevelChunkPacketData data,CallbackInfoReturnable<LevelChunk> cir) {
        CollectionSceneHistory.nativeChunkLifecycle((ClientChunkCache)(Object)this,x,z,false);
    }
    @Inject(method="updateViewCenter(II)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneView(int x,int z,CallbackInfo ci) {
        CollectionSceneHistory.nativeChunkLifecycle((ClientChunkCache)(Object)this,0,0,true);
    }
    @Inject(method="updateViewRadius(I)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneStorage(int radius,CallbackInfo ci) {
        CollectionSceneHistory.nativeChunkLifecycle((ClientChunkCache)(Object)this,0,0,true);
    }
}
