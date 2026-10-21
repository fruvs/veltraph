package dev.veltraph.client.mixin;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Read only actual pending vanilla click count required by recovered source key pulse. */
@Mixin(KeyMapping.class)
public interface GhostSourceClickCountAccessor {
    @Accessor("clickCount") int veltraph$ghostPendingClickCount();
}
