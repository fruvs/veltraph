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
@Mixin(targets={"io.netty.buffer.PooledByteBuf","io.netty.buffer.UnpooledDirectByteBuf","io.netty.buffer.UnpooledHeapByteBuf"},remap=false)
public abstract class NativePlayerBirthRawNioBufferMixin {
 @WrapOperation(method="setBytes(ILjava/nio/channels/ScatteringByteChannel;I)I",at=@At(value="INVOKE",target="Ljava/nio/channels/ScatteringByteChannel;read(Ljava/nio/ByteBuffer;)I",remap=false),require=1)
 private int veltraph$birthRawNioPrimitive(ScatteringByteChannel socket,ByteBuffer view,Operation<Integer> original){
  NativePlayerBirthWireSource.RawPrimitive phase=null;
  try{phase=NativePlayerBirthWireSource.beginRawNioPrimitive(this,socket,view);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;int result=0;
  try{result=original.call(socket,view);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.returnedRawNioPrimitive(phase,view,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
