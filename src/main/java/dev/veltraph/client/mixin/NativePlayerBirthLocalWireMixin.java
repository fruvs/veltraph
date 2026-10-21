package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.buffer.*;
import io.netty.channel.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets="io.netty.channel.local.LocalChannel",remap=false)
public abstract class NativePlayerBirthLocalWireMixin {
 @WrapMethod(method="readInbound()V",require=1)
 private void veltraph$birthWireTransport(Operation<Void> original) {
  NativePlayerBirthWireSource.LocalReadBody body=null;
  try{body=NativePlayerBirthWireSource.enterLocalReadBody(this);}catch(RuntimeException|Error unavailable){}
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginTransport(this);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call();normal=true;}
  finally{
   try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}
   finally{try{NativePlayerBirthWireSource.exitLocalReadBody(body);}catch(RuntimeException|Error unavailable){}}
  }
 }
 @WrapOperation(method="doBeginRead()V",at=@At(value="INVOKE",target="Lio/netty/channel/local/LocalChannel;readInbound()V",remap=false),require=1,allow=1)
 private void veltraph$birthWireNestedRead(io.netty.channel.local.LocalChannel originalReceiver,Operation<Void> original){
  NativePlayerBirthWireSource.LocalReadEdge edge=null;
  try{edge=NativePlayerBirthWireSource.enterLocalReadEdge(this,originalReceiver);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{original.call(originalReceiver);normal=true;}
  finally{try{NativePlayerBirthWireSource.exitLocalReadEdge(edge,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="readInbound()V",at=@At(value="INVOKE",target="Lio/netty/channel/ChannelPipeline;fireChannelRead(Ljava/lang/Object;)Lio/netty/channel/ChannelPipeline;",remap=false),require=1)
 private ChannelPipeline veltraph$birthWireFire(ChannelPipeline pipeline,Object message,Operation<ChannelPipeline> original) {
  NativePlayerBirthWireSource.Phase phase=null;
  try{phase=NativePlayerBirthWireSource.beginTransportDelivery(pipeline,message);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;
  try{ChannelPipeline result=original.call(pipeline,message);normal=true;return result;}
  finally{try{NativePlayerBirthWireSource.finish(phase,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="readInbound()V",at=@At(value="INVOKE",target="Ljava/util/Queue;poll()Ljava/lang/Object;",remap=false),require=1)
 private Object veltraph$birthWirePoll(Queue<?> queue,Operation<Object> original) {
  Object result=original.call(queue);
  try{NativePlayerBirthWireSource.localPolled(queue,result);}catch(RuntimeException|Error unavailable){}
  return result;
 }
}
