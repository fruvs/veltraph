package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmTransportOrigin;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class IssuedFarmTransportPacketProcessMixin {
 @WrapOperation(method="handle()V",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),require=1)
 private void veltraph$issuedFarmContextPacket(Packet<?> packet,PacketListener listener,Operation<Void> original){var i=IssuedFarmTransportOrigin.incomingEntered(packet,listener);boolean normal=false;try{original.call(packet,listener);normal=true;}finally{IssuedFarmTransportOrigin.incomingReturned(i,packet,listener,normal);}}
}
