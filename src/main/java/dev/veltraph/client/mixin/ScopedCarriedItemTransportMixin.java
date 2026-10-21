package dev.veltraph.client.mixin;
import dev.veltraph.client.service.mc.McScopedCarriedItemSynchronization;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Exact inherited listener body identity; nested ordinary calls receive unclaimed stack capsules. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ScopedCarriedItemTransportMixin {
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
    private void carry$listenerBody(Packet<?> packet,Operation<Void> original) {
        var call=McScopedCarriedItemSynchronization.enterListener(this,packet);
        try{original.call(packet);}finally{McScopedCarriedItemSynchronization.closeListener(call);}
    }
}
