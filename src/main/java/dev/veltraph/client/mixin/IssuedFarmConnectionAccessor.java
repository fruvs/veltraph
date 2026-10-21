package dev.veltraph.client.mixin;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Raw native receiver identity only; no callback or send permission. */
@Mixin(Connection.class)
public interface IssuedFarmConnectionAccessor {@Accessor("channel") Channel veltraph$issuedFarmChannel();}
