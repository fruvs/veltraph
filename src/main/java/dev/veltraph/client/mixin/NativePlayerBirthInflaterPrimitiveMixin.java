package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.ByteBuffer;
import javax.crypto.Cipher;
import java.util.zip.Inflater;
@Mixin(targets="net.minecraft.network.CompressionDecoder")
public abstract class NativePlayerBirthInflaterPrimitiveMixin {
 @WrapOperation(method="setupInflaterInput(Lio/netty/buffer/ByteBuf;)V",at=@At(value="INVOKE",target="Ljava/util/zip/Inflater;setInput(Ljava/nio/ByteBuffer;)V"),require=1)
 private void veltraph$birthInflaterSetInput(Inflater inflater,ByteBuffer input,Operation<Void> original){
  NativePlayerBirthWireSource.InflatePrimitive token=null;try{token=NativePlayerBirthWireSource.beforeInflaterSetInput(this,inflater,input);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(inflater,input);normal=true;}finally{try{NativePlayerBirthWireSource.afterInflaterSetInput(token,inflater,input,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="inflate(Lio/netty/channel/ChannelHandlerContext;I)Lio/netty/buffer/ByteBuf;",at=@At(value="INVOKE",target="Ljava/util/zip/Inflater;inflate(Ljava/nio/ByteBuffer;)I"),require=1)
 private int veltraph$birthInflaterInflate(Inflater inflater,ByteBuffer output,Operation<Integer> original)throws java.util.zip.DataFormatException{
  NativePlayerBirthWireSource.InflatePrimitive token=null;try{token=NativePlayerBirthWireSource.beforeInflaterInflate(this,inflater,output);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;try{result=original.call(inflater,output);normal=true;return result;}finally{try{NativePlayerBirthWireSource.afterInflaterInflate(token,output,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
