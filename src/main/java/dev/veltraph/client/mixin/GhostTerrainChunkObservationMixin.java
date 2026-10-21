package dev.veltraph.client.mixin;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.veltraph.client.combat.ghost.GhostSourceWalkTerrainEpochs;
@Mixin(ClientChunkCache.class)
public abstract class GhostTerrainChunkObservationMixin {
    @Shadow @Final private ClientLevel level;
    @Inject(method="<init>",at=@At("RETURN"))private void veltraph$chunkObserved(CallbackInfo ci){GhostSourceWalkTerrainEpochs.observedChunkHook(level);}
    @Inject(method="drop",at=@At("HEAD"))private void veltraph$chunkDrop(net.minecraft.world.level.ChunkPos pos,CallbackInfo ci){GhostSourceWalkTerrainEpochs.changedChunk(level,pos.x(),pos.z());}
    @Inject(method="replaceWithPacketData",at=@At("HEAD"))private void veltraph$chunkReplacement(int x,int z,net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData data,CallbackInfoReturnable<LevelChunk> cir){GhostSourceWalkTerrainEpochs.changedChunk(level,x,z);}
}
