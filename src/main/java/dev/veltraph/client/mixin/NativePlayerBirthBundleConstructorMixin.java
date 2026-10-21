package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthBundleSource;
import io.netty.channel.ChannelHandlerContext;
import java.util.*;
import java.util.function.Function;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BundlePacket.class)
public abstract class NativePlayerBirthBundleConstructorMixin {
 @Inject(method="<init>(Ljava/lang/Iterable;)V",at=@At("RETURN"),require=1)
 private void veltraph$birthWireBundleCtor(Iterable<?> originalList,CallbackInfo ci){try{NativePlayerBirthBundleSource.bundleConstructed(this,originalList);}catch(RuntimeException|Error unavailable){}}
}
