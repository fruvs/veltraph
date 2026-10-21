package dev.veltraph.client.mixin;

import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundPhysicsRevisionTracked;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GroundPhysicsLivingEffectRevisionMixin implements GroundPhysicsRevisionTracked {
    @Inject(method = {
        "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
        "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
        "removeEffect(Lnet/minecraft/core/Holder;)Z",
        "removeAllEffects()Z"
    }, at = @At("HEAD"), require = 1)
    private void veltraph$effectMutation(CallbackInfoReturnable<Boolean> ci) {
        GroundPhysicsNativeRevisions.mutation(this);
    }
    @Inject(method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), require = 1)
    private void veltraph$effectReplace(CallbackInfo ci) {
        GroundPhysicsNativeRevisions.mutation(this);
    }
    @Inject(method = "removeEffectNoUpdate(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;",
        at = @At("HEAD"), require = 1)
    private void veltraph$effectRemove(CallbackInfoReturnable<MobEffectInstance> ci) {
        GroundPhysicsNativeRevisions.mutation(this);
    }
}
