package dev.veltraph.client.mixin;

import dev.veltraph.client.galatea.McFigOwnedUseBridge;
import dev.veltraph.client.service.mc.McScopedCarriedItemSynchronization;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Synchronous submission fence; never a server-acceptance/effect/ACK witness. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class FigOwnedUsePacketMixin {
    @Redirect(method="send",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1)
    private void veltraph$figNativeSend(Connection connection,Packet<?> packet){
        var permit=McFigOwnedUseBridge.beforeSend(this,packet);
        if(permit!=null&&permit.ticket()==null)return;
        var nativeCarry=McScopedCarriedItemSynchronization.beforeTransport(this,connection,packet);
        boolean nativeReturned=false;
        try{connection.send(packet);nativeReturned=true;} // A rejected attempt never reaches this actual submission call.
        finally{McScopedCarriedItemSynchronization.transportReturned(nativeCarry,nativeReturned);}
        McFigOwnedUseBridge.submitted(permit); // Local permit survives any nested synchronous send.
    }
}
