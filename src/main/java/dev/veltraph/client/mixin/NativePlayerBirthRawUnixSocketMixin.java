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
@Mixin(targets="io.netty.channel.unix.FileDescriptor",remap=false)
public abstract class NativePlayerBirthRawUnixSocketMixin {
 @WrapOperation(method="read(Ljava/nio/ByteBuffer;II)I",at=@At(value="INVOKE",target="Lio/netty/channel/unix/FileDescriptor;read(ILjava/nio/ByteBuffer;II)I",remap=false),require=1)
 private int veltraph$birthRawUnixPrimitive(int fd,ByteBuffer view,int start,int end,Operation<Integer> original){
  NativePlayerBirthWireSource.RawPrimitive phase=null;try{phase=NativePlayerBirthWireSource.beginRawUnixPrimitive(this);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;
  try{result=original.call(fd,view,start,end);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.returnedRawUnixPrimitive(phase,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="readAddress(JII)I",at=@At(value="INVOKE",target="Lio/netty/channel/unix/FileDescriptor;readAddress(IJII)I",remap=false),require=1)
 private int veltraph$birthRawUnixPrimitive(int fd,long address,int start,int end,Operation<Integer> original){
  NativePlayerBirthWireSource.RawPrimitive phase=null;try{phase=NativePlayerBirthWireSource.beginRawUnixPrimitive(this);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;
  try{result=original.call(fd,address,start,end);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.returnedRawUnixPrimitive(phase,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
