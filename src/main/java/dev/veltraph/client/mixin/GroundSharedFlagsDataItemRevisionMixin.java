package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.GroundPhysicsRevisionTracked;
import dev.veltraph.client.navigation.mc.GroundSharedFlagsMutationBridge;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SynchedEntityData.DataItem.class)
public abstract class GroundSharedFlagsDataItemRevisionMixin implements GroundPhysicsRevisionTracked {
    @Inject(method="setValue(Ljava/lang/Object;)V",at=@At("HEAD"),require=1)
    private void veltraph$rawItem(Object value,CallbackInfo ci){GroundSharedFlagsMutationBridge.attemptedItem(this,value);}
}
