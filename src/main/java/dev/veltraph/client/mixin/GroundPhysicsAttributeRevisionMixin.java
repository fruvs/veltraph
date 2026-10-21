package dev.veltraph.client.mixin;

import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundPhysicsRevisionTracked;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive mutation lineage only. Does not alter any native return or attribute. */
@Mixin(AttributeInstance.class)
public abstract class GroundPhysicsAttributeRevisionMixin implements GroundPhysicsRevisionTracked {
    @Inject(method = {
        "setBaseValue(D)V",
        "addOrUpdateTransientModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",
        "addOrReplacePermanentModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",
        "addPermanentModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",
        "addPermanentModifiers(Ljava/util/Collection;)V",
        "removeModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",
        "removeModifiers()V",
        "replaceFrom(Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;)V",
        "apply(Lnet/minecraft/world/entity/ai/attributes/AttributeInstance$Packed;)V"
    }, at = @At("HEAD"), require = 1)
    private void veltraph$attributeMutation(CallbackInfo ci) {
        GroundPhysicsNativeRevisions.mutation(this);
    }
    @Inject(method = "removeModifier(Lnet/minecraft/resources/Identifier;)Z", at = @At("HEAD"), require = 1)
    private void veltraph$attributeRemoval(net.minecraft.resources.Identifier id, CallbackInfoReturnable<Boolean> ci) {
        GroundPhysicsNativeRevisions.mutation(this, GroundPhysicsNativeRevisions.Kind.REMOVE_ID, id);
    }
    @Inject(method="addTransientModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",at=@At("HEAD"),require=1)
    private void veltraph$transient(net.minecraft.world.entity.ai.attributes.AttributeModifier modifier,CallbackInfo ci){
        GroundPhysicsNativeRevisions.mutation(this,GroundPhysicsNativeRevisions.Kind.ADD_TRANSIENT,modifier);}
    @Inject(method="addModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V",at=@At("HEAD"),require=1)
    private void veltraph$modifier(net.minecraft.world.entity.ai.attributes.AttributeModifier modifier,CallbackInfo ci){
        GroundPhysicsNativeRevisions.mutation(this,GroundPhysicsNativeRevisions.Kind.ADD_MODIFIER,modifier);}
}

