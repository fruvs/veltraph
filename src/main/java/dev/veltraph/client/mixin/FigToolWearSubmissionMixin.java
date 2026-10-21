package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigToolWearBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class FigToolWearSubmissionMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("RETURN"),require=1)
    private void wear$sent(Packet<?> p,CallbackInfo ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.submitted((Connection)(Object)this,p));}
}
