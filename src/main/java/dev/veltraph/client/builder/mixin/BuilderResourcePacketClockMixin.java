package dev.veltraph.client.builder.mixin;

import dev.veltraph.client.builder.BuilderResourcePacketClock;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact receive-age/post-gap witness; no packet contents, sending or cancellation. */
@Mixin(Connection.class)
public abstract class BuilderResourcePacketClockMixin {
    @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void veltraph$builderReceive(ChannelHandlerContext context,Packet<?> packet,CallbackInfo ci){
        BuilderResourcePacketClock.received(this,System.currentTimeMillis());
    }
}
