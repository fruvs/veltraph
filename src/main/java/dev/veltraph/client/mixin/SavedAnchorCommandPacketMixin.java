package dev.veltraph.client.mixin;
import dev.veltraph.client.rewarp.mc.SavedAnchorCommandFence;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Binds only the exact packet of an active private saved-plot command; unscoped sends are unchanged. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class SavedAnchorCommandPacketMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"),require=1)
    private void veltraph$savedPlotPacket(Packet<?> packet,CallbackInfo ci){SavedAnchorCommandFence.commandEntered(this,packet);}
}
