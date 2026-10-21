package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthIngress;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class NativePlayerBirthBundleHandlerMixin {
 @WrapMethod(method="handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",require=1)
 private void veltraph$birthWireBundleHandler(ClientboundBundlePacket packet,Operation<Void> original){
  NativePlayerBirthIngress.BundleInvocation b=null;try{b=NativePlayerBirthIngress.beginBundle(this,packet);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(packet);normal=true;}finally{try{NativePlayerBirthIngress.finishBundle(b,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",at=@At(value="INVOKE",target="Ljava/lang/Iterable;iterator()Ljava/util/Iterator;"),require=1)
 private Iterator<?> veltraph$birthWireBundleIterator(Iterable<?> list,Operation<Iterator<?>> original){var result=original.call(list);try{NativePlayerBirthIngress.bundleIterator(list,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",at=@At(value="INVOKE",target="Ljava/util/Iterator;next()Ljava/lang/Object;"),require=1)
 private Object veltraph$birthWireBundleNext(Iterator<?> iterator,Operation<Object> original){var result=original.call(iterator);try{NativePlayerBirthIngress.bundleNext(iterator,result);}catch(RuntimeException|Error unavailable){}return result;}
 @WrapOperation(method="handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),require=1)
 private void veltraph$birthWireBundleMember(Packet<?> packet,PacketListener listener,Operation<Void> original){
  NativePlayerBirthIngress.Dispatch d=null;try{d=NativePlayerBirthIngress.beginBundleMember(packet,listener);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(packet,listener);normal=true;}finally{try{NativePlayerBirthIngress.finishBundleMember(d,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
