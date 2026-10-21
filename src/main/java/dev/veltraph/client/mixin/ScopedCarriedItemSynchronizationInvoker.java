package dev.veltraph.client.mixin;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Invokes the real cache/packet producer, with no cache field setter. */
@Mixin(MultiPlayerGameMode.class)
public interface ScopedCarriedItemSynchronizationInvoker {
    @Invoker("ensureHasSentCarriedItem") void veltraph$ensureHasSentCarriedItem();
}
