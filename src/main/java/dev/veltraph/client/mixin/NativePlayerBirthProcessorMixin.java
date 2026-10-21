package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthIngress;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class NativePlayerBirthProcessorMixin {
 @WrapOperation(method="handle()V",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),require=1)
 private void veltraph$birthProcess(Packet<?> packet,PacketListener listener,Operation<Void> original){NativePlayerBirthIngress.Dispatch d=null;try{d=NativePlayerBirthIngress.processEntered(packet,listener);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(packet,listener);normal=true;}finally{try{NativePlayerBirthIngress.processExited(d,normal);}catch(RuntimeException|Error unavailable){}}}
}
