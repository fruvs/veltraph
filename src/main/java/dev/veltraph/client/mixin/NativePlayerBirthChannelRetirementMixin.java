package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import org.spongepowered.asm.mixin.Mixin;
/** Negative retirement before close callbacks; never grants a new channel/player history. */
@Mixin(targets={"io.netty.channel.local.LocalChannel","io.netty.channel.socket.nio.NioSocketChannel","io.netty.channel.kqueue.AbstractKQueueChannel"},remap=false)
public abstract class NativePlayerBirthChannelRetirementMixin {
 @WrapMethod(method="doClose()V",require=1)
 private void veltraph$birthWireRetire(Operation<Void> original){try{NativePlayerBirthWireSource.retireChannel(this);}catch(RuntimeException|Error unavailable){}original.call();}
}
