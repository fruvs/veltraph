package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class IssuedFarmPositionProcessorMixin {
 @WrapOperation(method="handle()V",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),require=1)
 private void veltraph$issuedFarmPositionProcess(Packet<?> packet,PacketListener listener,Operation<Void> original){var token=IssuedFarmAppliedLanding.processorEntered(packet,listener);try{original.call(packet,listener);}finally{IssuedFarmAppliedLanding.processorExited(token);}}
}
