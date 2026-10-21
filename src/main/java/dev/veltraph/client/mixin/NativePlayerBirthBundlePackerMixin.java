package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthBundleSource;
import io.netty.channel.ChannelHandlerContext;
import java.util.*;
import java.util.function.Function;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.network.PacketBundlePacker")
public abstract class NativePlayerBirthBundlePackerMixin {
 @WrapMethod(method="decode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",require=1)
 private void veltraph$birthWireBundleDecode(ChannelHandlerContext context,Packet<?> packet,List<?> list,Operation<Void> original){
  NativePlayerBirthBundleSource.Decode d=null;try{d=NativePlayerBirthBundleSource.beginDecode(this,context,packet,list);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(context,packet,list);normal=true;}finally{try{NativePlayerBirthBundleSource.finishDecode(d,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/BundlerInfo;startPacketBundling(Lnet/minecraft/network/protocol/Packet;)Lnet/minecraft/network/protocol/BundlerInfo$Bundler;"),require=1)
 private BundlerInfo.Bundler veltraph$birthWireBundleStart(BundlerInfo info,Packet<?> packet,Operation<BundlerInfo.Bundler> original){
  NativePlayerBirthBundleSource.Start s=null;try{s=NativePlayerBirthBundleSource.beginStart(info,packet);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;BundlerInfo.Bundler result=null;try{result=original.call(info,packet);normal=true;return result;}finally{try{NativePlayerBirthBundleSource.finishStart(s,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/BundlerInfo$Bundler;addPacket(Lnet/minecraft/network/protocol/Packet;)Lnet/minecraft/network/protocol/Packet;"),require=1)
 private Packet<?> veltraph$birthWireBundleAdd(BundlerInfo.Bundler bundler,Packet<?> packet,Operation<Packet<?>> original){
  NativePlayerBirthBundleSource.Member m=null;try{m=NativePlayerBirthBundleSource.beginMember(bundler,packet);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;Packet<?> result=null;try{result=original.call(bundler,packet);normal=true;return result;}finally{try{NativePlayerBirthBundleSource.finishMember(m,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="decode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z"),require=1)
 private boolean veltraph$birthWireBundleAdded(List<?> list,Object packet,Operation<Boolean> original){boolean result=original.call(list,packet);try{NativePlayerBirthBundleSource.outputAdded(list,packet,result);}catch(RuntimeException|Error unavailable){}return result;}
}
