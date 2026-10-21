package dev.veltraph.client.mixin;

import dev.veltraph.client.service.mc.McBlockBreakingService;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checks the actual target at each vanilla breaking call, including calls between module ticks. */
@Mixin(MultiPlayerGameMode.class)
public class BlockBreakingTargetGuardMixin {

    @Inject(method = {
            "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"
    }, at = @At("HEAD"), cancellable = true)
    private void veltraph$guardActualTarget(BlockPos target, Direction direction,
            CallbackInfoReturnable<Boolean> callback) {
        if (!McBlockBreakingService.allowsDestroyBlock((MultiPlayerGameMode) (Object) this, target)) {
            callback.setReturnValue(false);
        }
    }
}
