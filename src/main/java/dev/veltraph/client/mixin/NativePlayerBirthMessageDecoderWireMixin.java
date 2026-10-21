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
@Mixin(targets="io.netty.handler.codec.MessageToMessageDecoder",remap=false)
public abstract class NativePlayerBirthMessageDecoderWireMixin {
 @WrapMethod(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",require=1)
 private void veltraph$birthWireChannelRead(ChannelHandlerContext context,Object input,Operation<Void> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginRead(this,context,input);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call(context,input);normal=true;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Lio/netty/handler/codec/MessageToMessageDecoder;decode(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;Ljava/util/List;)V",remap=false),require=1)
 private void veltraph$birthWireDecode(@Coerce Object decoder,ChannelHandlerContext context,Object buffer,List<?> list,Operation<Void> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginDecode(decoder,context,buffer,list);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call(decoder,context,buffer,list);normal=true;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method={"channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V"},at=@At(value="INVOKE",target="Lio/netty/channel/ChannelHandlerContext;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;",remap=false),require=1)
 private ChannelHandlerContext veltraph$birthWireOutputFire(ChannelHandlerContext context,Object object,Operation<ChannelHandlerContext> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginOutputDelivery(context,object);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{ChannelHandlerContext result=original.call(context,object);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
