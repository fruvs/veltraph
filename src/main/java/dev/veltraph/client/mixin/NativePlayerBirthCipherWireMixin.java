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
@Mixin(targets="net.minecraft.network.CipherDecoder")
public abstract class NativePlayerBirthCipherWireMixin {
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/CipherBase;decipher(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf;"),require=1)
 private ByteBuf veltraph$birthWireProduced(@Coerce Object cipher,ChannelHandlerContext context,ByteBuf buffer,Operation<ByteBuf> original){
  ByteBuf result=null;boolean normal=false;
  try{result=original.call(cipher,context,buffer);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.producedTransformed(buffer,result,cipher,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z"),require=1)
 private boolean veltraph$birthWireAdded(List<?> list,Object object,Operation<Boolean> original){boolean result=original.call(list,object);try{NativePlayerBirthWireSource.outputAdded(list,object,result);}catch(RuntimeException|Error unavailable){}return result;}
}
