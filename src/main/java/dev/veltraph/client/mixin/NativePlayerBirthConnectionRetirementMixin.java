package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(Connection.class)
public abstract class NativePlayerBirthConnectionRetirementMixin {
 @WrapMethod(method="channelInactive(Lio/netty/channel/ChannelHandlerContext;)V",require=1)
 private void veltraph$birthWireRetire(ChannelHandlerContext context,Operation<Void> original){try{NativePlayerBirthWireSource.retireChannel(((NativePlayerBirthConnectionAccessor)this).veltraph$birthChannel());}catch(RuntimeException|Error unavailable){}original.call(context);}
}
