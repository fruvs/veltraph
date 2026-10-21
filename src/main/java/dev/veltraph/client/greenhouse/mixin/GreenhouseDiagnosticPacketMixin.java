package dev.veltraph.client.greenhouse.mixin;

import dev.veltraph.client.greenhouse.McGreenhouseDiagnosticAdapter;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pending inspection only: observe the actual native submission boundary, with no packet mutation. */
@Mixin(Connection.class)
public abstract class GreenhouseDiagnosticPacketMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"),require=1)
    private void veltraph$greenhouseActualUse(Packet<?> packet,ChannelFutureListener listener,boolean flush,CallbackInfo ci){McGreenhouseDiagnosticAdapter.beforeSend(this,packet);}
}
