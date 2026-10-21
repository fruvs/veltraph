package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigPacketGapObservation;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Vanilla connection ingress, timestamp-only. Does not configure handlers or limit packets. */
@Mixin(Connection.class)
public abstract class FigScaffoldPacketIngressMixin {
    @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"),require=1)
    private void veltraph$figScaffoldPacketArrival(ChannelHandlerContext context,Packet<?> packet,CallbackInfo ci){
        McFigPacketGapObservation.received((Connection)(Object)this,System.currentTimeMillis());
    }
}
