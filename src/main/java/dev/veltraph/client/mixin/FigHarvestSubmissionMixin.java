package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigHarvestPickupBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only after the actual native Connection API returned. No client-common redirect collision. */
@Mixin(Connection.class)
public abstract class FigHarvestSubmissionMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("RETURN"),require=1)
    private void fig$submitted(Packet<?> packet,CallbackInfo ci){McFigHarvestPickupBridge.observeSafely(()->McFigHarvestPickupBridge.submitted((Connection)(Object)this,packet));}
}
