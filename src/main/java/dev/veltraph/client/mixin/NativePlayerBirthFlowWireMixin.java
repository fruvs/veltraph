package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.buffer.*;
import io.netty.channel.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets="io.netty.handler.flow.FlowControlHandler",remap=false)
public abstract class NativePlayerBirthFlowWireMixin {
 @WrapMethod(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",require=1)
 private void veltraph$birthWireChannelRead(ChannelHandlerContext context,Object input,Operation<Void> original){
  NativePlayerBirthWireSource.Phase phase=null;try{phase=NativePlayerBirthWireSource.beginRead(this,context,input);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(context,input);normal=true;}finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapMethod(method="dequeue(Lio/netty/channel/ChannelHandlerContext;)Z",require=1)
 private boolean veltraph$birthWireDrain(ChannelHandlerContext context,Operation<Boolean> original){
  NativePlayerBirthWireSource.Phase phase=null;try{phase=NativePlayerBirthWireSource.beginDrain(this,context);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{boolean result=original.call(context);normal=true;return result;}finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Lio/netty/handler/flow/FlowControlHandler$RecyclableArrayDeque;offer(Ljava/lang/Object;)Z",remap=false),require=1)
 private boolean veltraph$birthWireOffer(@Coerce Object queue,Object object,Operation<Boolean> original){boolean result=original.call(queue,object);try{NativePlayerBirthWireSource.offered(queue,object,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="dequeue(Lio/netty/channel/ChannelHandlerContext;)Z",at=@At(value="INVOKE",target="Lio/netty/handler/flow/FlowControlHandler$RecyclableArrayDeque;poll()Ljava/lang/Object;",remap=false),require=1)
 private Object veltraph$birthWireQueuePoll(@Coerce Object queue,Operation<Object> original){Object result=original.call(queue);try{NativePlayerBirthWireSource.polled(queue,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="dequeue(Lio/netty/channel/ChannelHandlerContext;)Z",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelHandlerContext;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;",remap=false),require=1)
 private ChannelHandlerContext veltraph$birthWireOutputFire(ChannelHandlerContext context,Object object,Operation<ChannelHandlerContext> original){
  NativePlayerBirthWireSource.Phase phase=null;try{phase=NativePlayerBirthWireSource.beginOutputDelivery(context,object);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{ChannelHandlerContext result=original.call(context,object);normal=true;return result;}finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
