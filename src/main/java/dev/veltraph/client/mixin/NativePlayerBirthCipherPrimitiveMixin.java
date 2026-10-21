package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.ByteBuffer;
import javax.crypto.Cipher;
import java.util.zip.Inflater;
@Mixin(targets="net.minecraft.network.CipherBase")
public abstract class NativePlayerBirthCipherPrimitiveMixin {
 @WrapOperation(method="decipher(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf;",at=@At(value="INVOKE",target="Ljavax/crypto/Cipher;update([BII[BI)I"),require=1)
 private int veltraph$birthCipherUpdate(Cipher cipher,byte[] input,int offset,int count,byte[] output,int outputOffset,Operation<Integer> original)throws javax.crypto.ShortBufferException{
  NativePlayerBirthWireSource.CipherPrimitive token=null;try{token=NativePlayerBirthWireSource.beforeCipherUpdate(this,cipher,input,offset,count,output,outputOffset);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;
  try{result=original.call(cipher,input,offset,count,output,outputOffset);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.afterCipherUpdate(token,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
