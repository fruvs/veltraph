package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigHarvestPickupBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Observes the actual serverVerifiedState argument on acknowledged prediction retirement. */
@Mixin(BlockStatePredictionHandler.class)
public abstract class FigHarvestPredictionAckMixin {
    @Redirect(method="endPredictionsUpTo",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;syncBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"),require=1)
    private void fig$verified(ClientLevel level,BlockPos pos,BlockState state,Vec3 originalPlayerPosition){
        level.syncBlockState(pos,state,originalPlayerPosition);McFigHarvestPickupBridge.observeSafely(()->McFigHarvestPickupBridge.serverVerifiedApplied(level,pos,state));
    }
}
