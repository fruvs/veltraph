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
@Mixin(targets={"net.minecraft.network.LocalFrameDecoder","net.minecraft.network.MonitoredLocalFrameDecoder"})
public abstract class NativePlayerBirthLocalFrameWireMixin {
 @WrapMethod(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",require=1)
 private void veltraph$birthWireChannelRead(ChannelHandlerContext context,Object input,Operation<Void> original){
  NativePlayerBirthWireSource.Phase phase=null;try{phase=NativePlayerBirthWireSource.beginRead(this,context,input);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(context,input);normal=true;}finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/HiddenByteBuf;unpack(Ljava/lang/Object;)Ljava/lang/Object;"),require=1)
 private Object veltraph$birthWireUnpack(Object input,Operation<Object> original){Object result=original.call(input);try{NativePlayerBirthWireSource.localUnpacked(input,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelHandlerContext;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelHandlerContext;"),require=1)
 private ChannelHandlerContext veltraph$birthWireOutputFire(ChannelHandlerContext context,Object object,Operation<ChannelHandlerContext> original){
  NativePlayerBirthWireSource.Phase phase=null;try{phase=NativePlayerBirthWireSource.beginOutputDelivery(context,object);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{ChannelHandlerContext result=original.call(context,object);normal=true;return result;}finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
