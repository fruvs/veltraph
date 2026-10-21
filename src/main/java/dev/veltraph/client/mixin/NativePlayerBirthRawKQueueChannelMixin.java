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
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(targets="io.netty.channel.kqueue.AbstractKQueueChannel",remap=false)
public abstract class NativePlayerBirthRawKQueueChannelMixin {
 @WrapMethod(method="doReadBytes(Lio/netty/buffer/ByteBuf;)I",require=1)
 private int veltraph$birthRawRead(ByteBuf target,Operation<Integer> original){
  NativePlayerBirthWireSource.RawRead phase=null;
  try{phase=NativePlayerBirthWireSource.beginRawRead(this,target,null);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;
  try{result=original.call(target);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finishRawRead(phase,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="doReadBytes(Lio/netty/buffer/ByteBuf;)I",at=@At(value="INVOKE",target="Lio/netty/channel/kqueue/BsdSocket;readAddress(JII)I",remap=false),require=1)
 private int veltraph$birthRawSocketCall(@Coerce Object socket,long address,int start,int end,Operation<Integer> original){try{NativePlayerBirthWireSource.rawKQueueSocket(this,socket);}catch(RuntimeException|Error unavailable){}return original.call(socket,address,start,end);}
 @WrapOperation(method="doReadBytes(Lio/netty/buffer/ByteBuf;)I",at=@At(value="INVOKE",target="Lio/netty/channel/kqueue/BsdSocket;read(Ljava/nio/ByteBuffer;II)I",remap=false),require=1)
 private int veltraph$birthRawSocketCall(@Coerce Object socket,ByteBuffer view,int start,int end,Operation<Integer> original){try{NativePlayerBirthWireSource.rawKQueueSocket(this,socket);}catch(RuntimeException|Error unavailable){}return original.call(socket,view,start,end);}
}
