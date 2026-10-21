package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinNativeWearBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class VeinWearSubmissionMixin {
 @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("RETURN"),require=1)
 private void vein$sent(Packet<?> packet,CallbackInfo ci){VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.submitted((Connection)(Object)this,packet));}
}
