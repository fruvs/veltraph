package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.farming.movement.FarmCropAutoJumpGuard;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses vanilla's automatic intent during owned crop work; user options and timers stay native. */
@Mixin(LocalPlayer.class)
public abstract class GroundedCropAutoJumpMixin {
    @Inject(method="isAutoJumpEnabled()Z",at=@At("HEAD"),cancellable=true,require=1)
    private void veltraph$groundedCropScheduling(CallbackInfoReturnable<Boolean> result) {
        if(FarmCropAutoJumpGuard.suppress((LocalPlayer)(Object)this))result.setReturnValue(false);
    }

    @WrapOperation(method="aiStep()V",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/player/ClientInput;makeJump()V"),require=1)
    private void veltraph$groundedCropQueuedJump(ClientInput input,Operation<Void> original) {
        // Vanilla already decremented autoJumpTime before this call. Refuse only makeJump so a
        // pre-activation queue expires normally and cannot remain pending until the macro stops.
        if(!FarmCropAutoJumpGuard.suppress((LocalPlayer)(Object)this))original.call(input);
    }
}
