package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof;
import io.netty.channel.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Passive first-init ACK witnesses. Private refusal never cancels ordinary native delivery. */
@Mixin(Connection.class)
public abstract class NativePlayerBirthAckSubmissionMixin {
 @WrapOperation(method="sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"),require=1)
 private void veltraph$birthDirect(Connection c,Packet<?> p,ChannelFutureListener listener,boolean flush,Operation<Void> original){try{NativePlayerBirthProof.ackBodyEntered(c,p,false,null);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(c,p,listener,flush);normal=true;}finally{try{NativePlayerBirthProof.ackBodyReturned(c,p,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/EventLoop;execute(Ljava/lang/Runnable;)V"),require=1)
 private void veltraph$birthScheduled(EventLoop loop,Runnable task,Operation<Void> original,Packet<?> p,ChannelFutureListener listener,boolean flush){var c=(Connection)(Object)this;try{NativePlayerBirthProof.ackBodyEntered(c,p,true,loop);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{Runnable submitted=task;try{submitted=NativePlayerBirthProof.bindScheduledTask(c,p,loop,task);}catch(RuntimeException|Error unavailable){}original.call(loop,submitted);normal=true;}finally{try{NativePlayerBirthProof.ackBodyReturned(c,p,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;write(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"),require=1)
 private ChannelFuture veltraph$birthWrite(Channel channel,Object p,Operation<ChannelFuture> original){boolean normal=false;try{var result=original.call(channel,p);normal=result!=null;return result;}finally{try{NativePlayerBirthProof.ackWriteReturned((Connection)(Object)this,channel,p,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"),require=1)
 private ChannelFuture veltraph$birthFlush(Channel channel,Object p,Operation<ChannelFuture> original){boolean normal=false;try{var result=original.call(channel,p);normal=result!=null;return result;}finally{try{NativePlayerBirthProof.ackWriteReturned((Connection)(Object)this,channel,p,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;write(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;"),require=1)
 private ChannelFuture veltraph$birthWritePromise(Channel channel,Object p,ChannelPromise promise,Operation<ChannelFuture> original){boolean normal=false;try{var result=original.call(channel,p,promise);normal=result!=null;return result;}finally{try{NativePlayerBirthProof.ackWriteReturned((Connection)(Object)this,channel,p,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;"),require=1)
 private ChannelFuture veltraph$birthFlushPromise(Channel channel,Object p,ChannelPromise promise,Operation<ChannelFuture> original){boolean normal=false;try{var result=original.call(channel,p,promise);normal=result!=null;return result;}finally{try{NativePlayerBirthProof.ackWriteReturned((Connection)(Object)this,channel,p,normal);}catch(RuntimeException|Error unavailable){}}}
}
