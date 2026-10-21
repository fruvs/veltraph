package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigHarvestPickupBridge;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A preprediction capture is only an attempt; actual outgoing submission is a separate observer. */
@Mixin(MultiPlayerGameMode.class)
public abstract class FigHarvestBreakAttemptMixin {
    @Inject(method={"startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z","continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"},at=@At("HEAD"),require=1)
    private void fig$attempt(BlockPos pos,Direction direction,CallbackInfoReturnable<Boolean> ci){McFigHarvestPickupBridge.observeSafely(()->McFigHarvestPickupBridge.attempted((MultiPlayerGameMode)(Object)this,pos));}
}
