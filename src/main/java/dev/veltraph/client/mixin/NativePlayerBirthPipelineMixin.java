package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthIngress;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.channel.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Actual verified Netty4.2 upstream native handler invocation, not a public Minecraft handler observation. */
@Mixin(targets="io.netty.channel.AbstractChannelHandlerContext")
public abstract class NativePlayerBirthPipelineMixin {
 @WrapOperation(method="fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelInboundHandler;channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V"),require=1)
 private void veltraph$birthUpstream(ChannelInboundHandler handler,ChannelHandlerContext context,Object message,Operation<Void> original){
  NativePlayerBirthWireSource.Incoming incoming=null;NativePlayerBirthIngress.PipelineInvocation token=null;
  try{incoming=NativePlayerBirthWireSource.actualHandlerEntered(this,handler,context,message);token=NativePlayerBirthIngress.pipelineEntered(handler,context,message);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(handler,context,message);normal=true;}
  finally{try{NativePlayerBirthIngress.pipelineExited(token,normal);}catch(RuntimeException|Error unavailable){}
   try{NativePlayerBirthWireSource.connectionDeliveryFinished(handler,context,message);NativePlayerBirthWireSource.actualHandlerExited(incoming,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelDuplexHandler;channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V"),require=1)
 private void veltraph$birthUpstreamDuplex(ChannelDuplexHandler handler,ChannelHandlerContext context,Object message,Operation<Void> original){
  NativePlayerBirthWireSource.Incoming incoming=null;try{incoming=NativePlayerBirthWireSource.actualHandlerEntered(this,handler,context,message);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(handler,context,message);normal=true;}finally{try{NativePlayerBirthWireSource.actualHandlerExited(incoming,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
