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
@Mixin(targets="io.netty.channel.kqueue.AbstractKQueueStreamChannel$KQueueStreamUnsafe",remap=false)
public abstract class NativePlayerBirthKQueueWireMixin {
 @WrapMethod(method="readReady(Lio/netty/channel/kqueue/KQueueRecvByteAllocatorHandle;)V",require=1)
 private void veltraph$birthWireTransport(@Coerce Object handle,Operation<Void> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginTransport(this);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call(handle);normal=true;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="readReady(Lio/netty/channel/kqueue/KQueueRecvByteAllocatorHandle;)V",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelPipeline;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelPipeline;",remap=false),require=1)
 private ChannelPipeline veltraph$birthWireFire(ChannelPipeline pipeline,Object message,Operation<ChannelPipeline> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginTransportDelivery(pipeline,message);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{ChannelPipeline result=original.call(pipeline,message);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="readReady(Lio/netty/channel/kqueue/KQueueRecvByteAllocatorHandle;)V",at=@At(value="INVOKE",target="Lio/netty/channel/kqueue/AbstractKQueueStreamChannel;doReadBytes(Lio/netty/buffer/ByteBuf;)I",remap=false),require=1)
 private int veltraph$birthWireRead(@Coerce Object channel,ByteBuf buffer,Operation<Integer> original) {
  int result=original.call(channel,buffer);
  try{NativePlayerBirthWireSource.readReturned(channel,buffer,result);}catch(RuntimeException|Error unavailable){}
  return result;
 }
}
