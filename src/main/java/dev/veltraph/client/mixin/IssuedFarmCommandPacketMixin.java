package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.rewarp.mc.IssuedFarmCommandFence;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class IssuedFarmCommandPacketMixin {
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V", require=1)
    private void veltraph$farmReturnPacket(Packet<?> packet, Operation<Void> original) {
        var invocation=IssuedFarmCommandFence.packetEntered(this,packet);
        boolean normal=false;
        try { original.call(packet); normal=true; }
        finally { IssuedFarmCommandFence.exited(invocation,this,packet,normal); }
    }
}
