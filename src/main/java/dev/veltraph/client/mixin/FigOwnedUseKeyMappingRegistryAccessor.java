package dev.veltraph.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only collision proof: vanilla click fans out to every mapping sharing the physical key. */
@Mixin(KeyMapping.class)
public interface FigOwnedUseKeyMappingRegistryAccessor {
    @Accessor("MAP")
    static Map<InputConstants.Key,List<KeyMapping>> veltraph$figMappings(){throw new AssertionError("Mixin accessor unavailable");}
}
