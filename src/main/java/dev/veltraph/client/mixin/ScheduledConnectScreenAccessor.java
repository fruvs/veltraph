package dev.veltraph.client.mixin;

import io.netty.channel.ChannelFuture;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access only to an already-owned vanilla connector's exact native cancellation state. */
@Mixin(ConnectScreen.class)
public interface ScheduledConnectScreenAccessor {
    @Accessor("aborted") void veltraph$setAborted(boolean aborted);
    @Accessor("channelFuture") ChannelFuture veltraph$getChannelFuture();
    @Accessor("channelFuture") void veltraph$setChannelFuture(ChannelFuture future);
    @Accessor("connection") Connection veltraph$getConnection();
}
