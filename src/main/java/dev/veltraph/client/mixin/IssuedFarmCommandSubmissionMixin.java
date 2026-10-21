package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import dev.veltraph.client.rewarp.mc.IssuedFarmCommandFence;
import dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding;
import dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class IssuedFarmCommandSubmissionMixin {
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", require=1)
    private void veltraph$farmReturnTransport(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        var self=(Connection)(Object)this;
        var invocation=IssuedFarmCommandFence.transportEntered(self,packet);
        boolean normal=false;
        try { original.call(packet,listener,flush); normal=true; }
        finally { IssuedFarmCommandFence.exited(invocation,self,packet,normal); }
    }
    @WrapMethod(method="sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", require=1)
    private void veltraph$farmReturnConnectedLeaf(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        var self=(Connection)(Object)this;
        if (!IssuedFarmCommandFence.allowLeaf(self,packet)) return;
        boolean normal=false;
        try { original.call(packet,listener,flush); normal=true; }
        finally { IssuedFarmCommandFence.dispatchExited(self,packet,normal); }
    }
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at=@At(value="INVOKE", target="Ljava/util/Queue;add(Ljava/lang/Object;)Z"), cancellable=true, require=1)
    private void veltraph$farmReturnDisconnectedQueue(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!IssuedFarmCommandFence.allowQueue((Connection)(Object)this,packet)) ci.cancel();
    }
    @WrapOperation(method="sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"),require=1)
    private void veltraph$farmActualDirectBody(Connection receiver,Packet<?> packet,ChannelFutureListener listener,boolean flush,Operation<Void> original){
        boolean ack=IssuedFarmAppliedLanding.activeAck();if(ack)IssuedFarmAppliedLanding.ackBodyEntered(receiver,packet,false,null);var body=IssuedFarmCommandFence.bodyEntered(receiver,packet,false,null);if(IssuedFarmCommandFence.scopedBodyRefused(body))return;boolean normal=false;
        var device=NativeDeviceTeleportReceipt.captureIssuedReturnSendBody(receiver,packet,false,null);
        try{original.call(receiver,packet,listener,flush);normal=true;}finally{NativeDeviceTeleportReceipt.issuedReturnSendBodyReturned(device,receiver,packet,normal);IssuedFarmCommandFence.bodyExited(body,receiver,packet,normal);if(ack)IssuedFarmAppliedLanding.ackBodyReturned(receiver,packet,normal);}
    }
    @WrapOperation(method="sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/EventLoop;execute(Ljava/lang/Runnable;)V"),require=1)
    private void veltraph$farmActualScheduledBody(EventLoop loop,Runnable task,Operation<Void> original,Packet<?> packet,ChannelFutureListener listener,boolean flush){
        var self=(Connection)(Object)this;boolean ack=IssuedFarmAppliedLanding.activeAck();if(ack)IssuedFarmAppliedLanding.ackBodyEntered(self,packet,true,loop);var body=IssuedFarmCommandFence.bodyEntered(self,packet,true,loop);if(IssuedFarmCommandFence.scopedBodyRefused(body))return;boolean normal=false;
        var device=NativeDeviceTeleportReceipt.captureIssuedReturnSendBody(self,packet,true,loop);
        try{original.call(loop,task);normal=true;}finally{NativeDeviceTeleportReceipt.issuedReturnSendBodyReturned(device,self,packet,normal);IssuedFarmCommandFence.bodyExited(body,self,packet,normal);if(ack)IssuedFarmAppliedLanding.ackBodyReturned(self,packet,normal);}
    }
    @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;write(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"),require=1)
    private ChannelFuture veltraph$farmActualWrite(Channel channel,Object packet,Operation<ChannelFuture> original){return veltraph$farmWriteBody(channel,packet,null,false,false,original);}
    @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"),require=1)
    private ChannelFuture veltraph$farmActualWriteFlush(Channel channel,Object packet,Operation<ChannelFuture> original){return veltraph$farmWriteBody(channel,packet,null,true,false,original);}
    @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;write(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;"),require=1)
    private ChannelFuture veltraph$farmActualWritePromise(Channel channel,Object packet,ChannelPromise promise,Operation<ChannelFuture> original){return veltraph$farmWriteBody(channel,packet,promise,false,true,original);}
    @WrapOperation(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;"),require=1)
    private ChannelFuture veltraph$farmActualWriteFlushPromise(Channel channel,Object packet,ChannelPromise promise,Operation<ChannelFuture> original){return veltraph$farmWriteBody(channel,packet,promise,true,true,original);}
    private ChannelFuture veltraph$farmWriteBody(Channel channel,Object packet,ChannelPromise promise,boolean flush,boolean hasPromise,Operation<ChannelFuture> original){
        var self=(Connection)(Object)this;if(!IssuedFarmCommandFence.allowDirectWrite(self,channel,packet))return channel.newFailedFuture(new IllegalStateException("Issued farm command write refused"));boolean normal=false;
        try{var result=hasPromise?original.call(channel,packet,promise):original.call(channel,packet);normal=result!=null;return result;}finally{NativeDeviceTeleportReceipt.issuedReturnSendWriteReturned(self,channel,packet,normal);IssuedFarmCommandFence.directWriteReturned(self,channel,packet,normal);if(IssuedFarmAppliedLanding.activeAck())IssuedFarmAppliedLanding.ackWriteReturned(self,channel,packet,normal);}
    }
}
