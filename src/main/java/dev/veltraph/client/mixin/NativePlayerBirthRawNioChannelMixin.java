package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.buffer.ByteBuf;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.Coerce;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Queue;
@Mixin(targets="io.netty.channel.socket.nio.NioSocketChannel",remap=false)
public abstract class NativePlayerBirthRawNioChannelMixin {
 @Shadow protected abstract SocketChannel javaChannel();
 @WrapMethod(method="doReadBytes(Lio/netty/buffer/ByteBuf;)I",require=1)
 private int veltraph$birthRawRead(ByteBuf target,Operation<Integer> original){
  NativePlayerBirthWireSource.RawRead phase=null;
  try{phase=NativePlayerBirthWireSource.beginRawRead(this,target,javaChannel());}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;
  try{result=original.call(target);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finishRawRead(phase,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
