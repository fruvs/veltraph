package dev.veltraph.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only pending-click check. No consumption or clearing of manual events. */
@Mixin(KeyMapping.class)
public interface FigScaffoldKeyMappingAccessor {
    @Accessor("clickCount") int veltraph$figScaffoldClickCount();
}
