package dev.veltraph.client.mixin;

import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundPhysicsRevisionTracked;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ordinary tickDownDuration is deliberately excluded; refresh/hidden replacement invalidates custody. */
@Mixin(MobEffectInstance.class)
public abstract class GroundPhysicsEffectInstanceRevisionMixin implements GroundPhysicsRevisionTracked {
    @Inject(method = "setDetailsFrom(Lnet/minecraft/world/effect/MobEffectInstance;)V",
        at = @At("HEAD"), require = 1)
    private void veltraph$effectDetails(CallbackInfo ci) { GroundPhysicsNativeRevisions.mutation(this); }
    @Inject(method = "update(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
        at = @At("HEAD"), require = 1)
    private void veltraph$effectUpdate(CallbackInfoReturnable<Boolean> ci) { GroundPhysicsNativeRevisions.mutation(this); }
}
