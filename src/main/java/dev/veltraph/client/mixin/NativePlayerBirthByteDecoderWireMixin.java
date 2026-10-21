package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.buffer.*;
import io.netty.channel.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets="io.netty.handler.codec.ByteToMessageDecoder",remap=false)
public abstract class NativePlayerBirthByteDecoderWireMixin {
 @WrapMethod(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",require=1)
 private void veltraph$birthWireChannelRead(ChannelHandlerContext context,Object input,Operation<Void> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginRead(this,context,input);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call(context,input);normal=true;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="decodeRemovalReentryProtection(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lio/netty/handler/codec/ByteToMessageDecoder;decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",remap=false),require=1)
 private void veltraph$birthWireDecode(@Coerce Object decoder,ChannelHandlerContext context,ByteBuf buffer,List<?> list,Operation<Void> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginDecode(decoder,context,buffer,list);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call(decoder,context,buffer,list);normal=true;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Lio/netty/handler/codec/ByteToMessageDecoder$Cumulator;cumulate(Lio/netty/buffer/ByteBufAllocator;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf;",remap=false),require=1)
 private ByteBuf veltraph$birthWireCumulate(@Coerce Object cumulator,ByteBufAllocator allocator,ByteBuf old,ByteBuf input,Operation<ByteBuf> original) {
  NativePlayerBirthWireSource.Cumulation before=null;
  try{before=NativePlayerBirthWireSource.beforeCumulate(cumulator,old,input);}catch(RuntimeException|Error unavailable){}
  ByteBuf result=original.call(cumulator,allocator,old,input);
  try{NativePlayerBirthWireSource.cumulated(before,result);}catch(RuntimeException|Error unavailable){}
  return result;
 }
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Ljava/util/Queue;offer(Ljava/lang/Object;)Z",remap=false),require=1)
 private boolean veltraph$birthWireOffer(Queue<Object> queue,Object object,Operation<Boolean> original){boolean result=original.call(queue,object);try{NativePlayerBirthWireSource.offered(queue,object,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Ljava/util/Queue;poll()Ljava/lang/Object;",remap=false),require=1)
 private Object veltraph$birthWireQueuePoll(Queue<?> queue,Operation<Object> original){Object result=original.call(queue);try{NativePlayerBirthWireSource.polled(queue,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method={"fireChannelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/util/List;I)V", "fireChannelRead(Lio/netty/channel/ChannelHandlerContext;Lio/netty/handler/codec/CodecOutputList;I)V"},at=@At(value="INVOKE",target="Lio/netty/channel/ChannelHandlerContext;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;",remap=false),require=1)
 private static ChannelHandlerContext veltraph$birthWireOutputFire(ChannelHandlerContext context,Object object,Operation<ChannelHandlerContext> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginOutputDelivery(context,object);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{ChannelHandlerContext result=original.call(context,object);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Lio/netty/handler/codec/ByteToMessageDecoder;discardSomeReadBytes()V",remap=false),require=1)
 private void veltraph$birthWireCompact(@Coerce Object decoder,Operation<Void> original){
  NativePlayerBirthWireSource.Compaction before=null;try{before=NativePlayerBirthWireSource.beforeCompact(decoder);}catch(RuntimeException|Error unavailable){}
  original.call(decoder);try{NativePlayerBirthWireSource.compacted(before);}catch(RuntimeException|Error unavailable){}
 }
}
