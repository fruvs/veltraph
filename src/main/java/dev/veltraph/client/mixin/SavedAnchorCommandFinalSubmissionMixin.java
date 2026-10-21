package dev.veltraph.client.mixin;
import dev.veltraph.client.rewarp.mc.SavedAnchorCommandFence;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Final synchronous saved-command refusal after native queued consumers, never an ACK or wire witness. */
@Mixin(Connection.class)
public abstract class SavedAnchorCommandFinalSubmissionMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"),cancellable=true,require=1)
    private void veltraph$savedPlotBeforeSubmit(Packet<?> packet,ChannelFutureListener listener,boolean flush,CallbackInfo ci){if(!SavedAnchorCommandFence.allowSubmission((Connection)(Object)this,packet))ci.cancel();}
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at=@At(value="INVOKE",target="Ljava/util/Queue;add(Ljava/lang/Object;)Z"),cancellable=true,require=1)
    private void veltraph$savedPlotBeforeQueue(Packet<?> packet,ChannelFutureListener listener,boolean flush,CallbackInfo ci){if(!SavedAnchorCommandFence.allowEnqueue((Connection)(Object)this,packet))ci.cancel();}
}
