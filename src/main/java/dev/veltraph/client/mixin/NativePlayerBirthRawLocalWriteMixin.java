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
@Mixin(targets="io.netty.channel.local.LocalChannel",remap=false)
public abstract class NativePlayerBirthRawLocalWriteMixin {
 @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lio/netty/util/internal/PlatformDependent;newSpscQueue()Ljava/util/Queue;",remap=false),require=1)
 private Queue<Object> veltraph$birthRawQueueCreated(Operation<Queue<Object>> original){var queue=original.call();try{NativePlayerBirthWireSource.rawQueueCreated(this,queue);}catch(RuntimeException|Error unavailable){}return queue;}
 @Inject(method="<init>",at=@At("RETURN"),require=1)
 private void veltraph$birthRawQueueNormal(CallbackInfo ci){try{NativePlayerBirthWireSource.rawQueueNormal(this,((NativePlayerBirthRawLocalAccessor)this).veltraph$rawInbound());}catch(RuntimeException|Error unavailable){}}
 @WrapMethod(method="doWrite(Lio/netty/channel/ChannelOutboundBuffer;)V",require=1)
 private void veltraph$birthRawLocalWrite(io.netty.channel.ChannelOutboundBuffer outbound,Operation<Void> original){
  NativePlayerBirthWireSource.RawLocalWrite phase=null;
  try{var peer=((NativePlayerBirthRawLocalAccessor)this).veltraph$rawPeer();var queue=peer==null?null:((NativePlayerBirthRawLocalAccessor)peer).veltraph$rawInbound();phase=NativePlayerBirthWireSource.beginRawLocalWrite(this,peer,queue,outbound);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(outbound);normal=true;}finally{try{NativePlayerBirthWireSource.finishRawLocalWrite(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="doWrite(Lio/netty/channel/ChannelOutboundBuffer;)V",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelOutboundBuffer;current()Ljava/lang/Object;",remap=false),require=1)
 private Object veltraph$birthRawLocalCurrent(io.netty.channel.ChannelOutboundBuffer outbound,Operation<Object> original){var message=original.call(outbound);try{NativePlayerBirthWireSource.rawLocalCurrent(outbound,message);}catch(RuntimeException|Error unavailable){}return message;}
 @WrapOperation(method="doWrite(Lio/netty/channel/ChannelOutboundBuffer;)V",at=@At(value="INVOKE",target="Lio/netty/util/ReferenceCountUtil;retain(Ljava/lang/Object;)Ljava/lang/Object;",remap=false),require=1)
 private Object veltraph$birthRawLocalRetained(Object input,Operation<Object> original){var result=original.call(input);try{NativePlayerBirthWireSource.rawLocalRetained(input,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="doWrite(Lio/netty/channel/ChannelOutboundBuffer;)V",at=@At(value="INVOKE",target="Ljava/util/Queue;add(Ljava/lang/Object;)Z",remap=false),require=1)
 private boolean veltraph$birthRawLocalAdded(Queue<Object> queue,Object wrapper,Operation<Boolean> original){
  NativePlayerBirthWireSource.RawLocalAdd phase=null;
  try{var contents=wrapper instanceof NativePlayerBirthRawHiddenBufferAccessor hidden?hidden.veltraph$rawContents():null;phase=NativePlayerBirthWireSource.beginRawLocalAdd(queue,wrapper,contents);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;boolean added=false;try{added=original.call(queue,wrapper);normal=true;return added;}finally{try{NativePlayerBirthWireSource.returnedRawLocalAdd(phase,added,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
