package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthDecodedOutput;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.channel.ChannelHandlerContext;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(PacketDecoder.class)
public abstract class NativePlayerBirthDecoderMixin {
 @WrapMethod(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",require=1)
 private void veltraph$birthDecode(ChannelHandlerContext context,ByteBuf buffer,List<?> list,Operation<Void> original){NativePlayerBirthDecodedOutput.Decode d=null;try{d=NativePlayerBirthDecodedOutput.begin(this,context,buffer,list);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(context,buffer,list);normal=true;}finally{try{NativePlayerBirthDecodedOutput.finish(d,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"),require=1)
 private Object veltraph$birthCodecOutput(StreamCodec<?,?> codec,Object buffer,Operation<Object> original){try{NativePlayerBirthWireSource.verifyCodecInput(buffer);}catch(RuntimeException|Error unavailable){}Object packet=original.call(codec,buffer);try{NativePlayerBirthWireSource.verifyCodecInput(buffer);NativePlayerBirthWireSource.produced(buffer,packet);NativePlayerBirthDecodedOutput.decoded(packet,true);}catch(RuntimeException|Error unavailable){}return packet;}
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z"),require=1)
 private boolean veltraph$birthDecodedListAdd(List<?> list,Object packet,Operation<Boolean> original){boolean result=original.call(list,packet);try{NativePlayerBirthWireSource.outputAdded(list,packet,result);NativePlayerBirthDecodedOutput.added(list,packet,result);}catch(RuntimeException|Error unavailable){}return result;}
}
