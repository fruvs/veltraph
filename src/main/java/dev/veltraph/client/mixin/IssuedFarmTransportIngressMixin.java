package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmTransportOrigin;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Connection.class)
public abstract class IssuedFarmTransportIngressMixin {
 @WrapOperation(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"),require=1)
 private void veltraph$issuedFarmContextIngress(Packet<?> packet,PacketListener listener,Operation<Void> original,ChannelHandlerContext context,Packet<?> nativePacket){try{if(packet==nativePacket)IssuedFarmTransportOrigin.observeIngress((Connection)(Object)this,context,packet,listener);}catch(RuntimeException unavailable){}original.call(packet,listener);}
}
