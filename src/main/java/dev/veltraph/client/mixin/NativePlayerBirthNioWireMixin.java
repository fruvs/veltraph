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
@Mixin(targets="io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe",remap=false)
public abstract class NativePlayerBirthNioWireMixin {
 @WrapMethod(method="read()V",require=1)
 private void veltraph$birthWireTransport(Operation<Void> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginTransport(this);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call();normal=true;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="read()V",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelPipeline;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelPipeline;",remap=false),require=1)
 private ChannelPipeline veltraph$birthWireFire(ChannelPipeline pipeline,Object message,Operation<ChannelPipeline> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginTransportDelivery(pipeline,message);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{ChannelPipeline result=original.call(pipeline,message);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="read()V",at=@At(value="INVOKE",target="Lio/netty/channel/nio/AbstractNioByteChannel;doReadBytes(Lio/netty/buffer/ByteBuf;)I",remap=false),require=1)
 private int veltraph$birthWireRead(@Coerce Object channel,ByteBuf buffer,Operation<Integer> original) {
  int result=original.call(channel,buffer);
  try{NativePlayerBirthWireSource.readReturned(channel,buffer,result);}catch(RuntimeException|Error unavailable){}
  return result;
 }
}
