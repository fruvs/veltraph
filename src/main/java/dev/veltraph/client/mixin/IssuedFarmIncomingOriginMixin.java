package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmIncomingOrigin;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Actual channelRead0 native dispatch opcode, never an inferred active window. */
@Mixin(Connection.class)
public abstract class IssuedFarmIncomingOriginMixin {
 @WrapOperation(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"),require=1)
 private void veltraph$farmNativeIngress(Packet<?> packet,PacketListener listener,Operation<Void> original,ChannelHandlerContext context,Packet<?> originalPacket){
  try{if(packet==originalPacket)IssuedFarmIncomingOrigin.observe((Connection)(Object)this,context,packet,listener);}catch(RuntimeException unavailable){/* Missing origin denies admission, never changes native packet delivery. */}
  original.call(packet,listener);
 }
}
