package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** All attempts remain visible, including foreign calls and same-value ABA writes. */
@Mixin(LivingEntity.class)
public abstract class GroundPhysicsSprintRevisionMixin {
    @Inject(method="setSprinting(Z)V",at=@At("HEAD"),require=1)
    private void veltraph$sprint(boolean value,CallbackInfo ci){GroundPhysicsNativeRevisions.mutation(this,GroundPhysicsNativeRevisions.Kind.SPRINT_METHOD,value);}
    @Inject(method="setSpeed(F)V",at=@At("HEAD"),require=1)
    private void veltraph$speed(float value,CallbackInfo ci){GroundPhysicsNativeRevisions.mutation(this,GroundPhysicsNativeRevisions.Kind.SPEED_FIELD,value);}
}
