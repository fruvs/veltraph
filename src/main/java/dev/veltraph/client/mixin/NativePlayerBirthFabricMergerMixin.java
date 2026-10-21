package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireSource;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exactly the original ordinary-packet List.add; split Merger.add is a different opcode. */
@Mixin(targets = "net.fabricmc.fabric.impl.networking.splitter.FabricPacketMerger", remap = false)
public abstract class NativePlayerBirthFabricMergerMixin {
    @WrapOperation(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Ljava/util/List;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), require = 1, allow = 1)
    private boolean veltraph$birthFabricPassThroughAdd(List<?> addedList, Object addedObject, Operation<Boolean> original,
                                                       ChannelHandlerContext context, Packet<?> input, List<?> originalList) {
        NativePlayerBirthWireSource.FabricPassThrough token = null;
        try { token = NativePlayerBirthWireSource.beginFabricPassThrough(this, context, input, originalList, addedObject, addedList); }
        catch (RuntimeException | Error observerUnavailable) { /* No substitute receipt; original add still executes once. */ }
        boolean normal = false, added = false;
        try { added = original.call(addedList, addedObject); normal = true; return added; }
        finally {
            try { NativePlayerBirthWireSource.finishFabricPassThrough(token, this, context, input, originalList, addedObject, addedList, normal, added); }
            catch (RuntimeException | Error observerUnavailable) { /* Preserve original SDK result or exact original exception. */ }
        }
    }
}
