package dev.veltraph.client.mixin;

import dev.veltraph.client.VeltraphBootstrap;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original boolean-true attack ingress, after native HEAD denial; useItemOn is deliberately absent. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CropSpeciesAttackObservationMixin {
    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"), require = 1)
    private void crop$attack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        try { VeltraphBootstrap.onSourceCropAttack((MultiPlayerGameMode) (Object) this, pos); }
        catch (Throwable unavailable) { /* A read-only observer cannot interrupt vanilla interaction. */ }
    }
}
