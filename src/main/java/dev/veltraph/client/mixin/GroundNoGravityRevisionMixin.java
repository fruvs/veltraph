package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.GroundNoGravityRevisionTracked;
import dev.veltraph.client.navigation.mc.GroundSharedFlagsMutationBridge;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class GroundNoGravityRevisionMixin implements GroundNoGravityRevisionTracked {
    @Inject(method="setNoGravity(Z)V",at=@At("HEAD"),require=1)
    private void veltraph$noGravityAttempt(boolean value,CallbackInfo ci){
        GroundSharedFlagsMutationBridge.noGravityEntityAttempt(this,value);}
}
