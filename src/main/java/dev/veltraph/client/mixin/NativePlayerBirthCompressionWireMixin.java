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
@Mixin(targets="net.minecraft.network.CompressionDecoder")
public abstract class NativePlayerBirthCompressionWireMixin {
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lio/netty/buffer/ByteBuf;readBytes(I)Lio/netty/buffer/ByteBuf;"),require=1)
 private ByteBuf veltraph$birthWireProduced(ByteBuf buffer,int count,Operation<ByteBuf> original){
  NativePlayerBirthWireSource.Copy copy=null;try{copy=NativePlayerBirthWireSource.beforeCopy(buffer,count);}catch(RuntimeException|Error unavailable){}
  ByteBuf result=original.call(buffer,count);
  try{NativePlayerBirthWireSource.copied(copy,result);}catch(RuntimeException|Error unavailable){}
  return result;
 }
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/CompressionDecoder;inflate(Lio/netty/channel/ChannelHandlerContext;I)Lio/netty/buffer/ByteBuf;"),require=1)
 private ByteBuf veltraph$birthWireProducedInflated(@Coerce Object decoder,ChannelHandlerContext context,int count,Operation<ByteBuf> original,@Local(argsOnly=true) ByteBuf input){
  ByteBuf result=null;boolean normal=false;
  try{result=original.call(decoder,context,count);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.producedTransformed(input,result,decoder,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z"),require=1)
 private boolean veltraph$birthWireAdded(List<?> list,Object object,Operation<Boolean> original){boolean result=original.call(list,object);try{NativePlayerBirthWireSource.outputAdded(list,object,result);}catch(RuntimeException|Error unavailable){}return result;}
}
