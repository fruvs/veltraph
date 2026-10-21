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
@Mixin(targets="net.minecraft.network.protocol.BundlerInfo$1$1")
public abstract class NativePlayerBirthBundlerBodyMixin {
 @Shadow @Final private List<Packet<?>> bundlePackets;
 @Inject(method="<init>",at=@At("RETURN"),require=1)
 private void veltraph$birthWireBundlerCtor(@Coerce Object info,BundleDelimiterPacket<?> delimiter,Function<?,?> constructor,CallbackInfo ci){try{NativePlayerBirthBundleSource.bundlerConstructed(info,this,bundlePackets);}catch(RuntimeException|Error unavailable){}}
 @WrapOperation(method="addPacket(Lnet/minecraft/network/protocol/Packet;)Lnet/minecraft/network/protocol/Packet;",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z"),require=1)
 private boolean veltraph$birthWireBundlerMember(List<?> list,Object packet,Operation<Boolean> original){boolean result=original.call(list,packet);try{NativePlayerBirthBundleSource.memberAdded(list,packet,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="addPacket(Lnet/minecraft/network/protocol/Packet;)Lnet/minecraft/network/protocol/Packet;",at=@At(value="INVOKE",target="Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"),require=1)
 private Object veltraph$birthWireBundlerCreate(Function<?,?> constructor,Object list,Operation<Object> original){
  NativePlayerBirthBundleSource.Create c=null;try{c=NativePlayerBirthBundleSource.beginCreate(list);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;Object result=null;try{result=original.call(constructor,list);normal=true;return result;}finally{try{NativePlayerBirthBundleSource.finishCreate(c,result,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
