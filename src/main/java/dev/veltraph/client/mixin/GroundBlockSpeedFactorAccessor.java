package dev.veltraph.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only invocation of native speed-factor interpolation, including movement efficiency. */
@Mixin(LivingEntity.class)
public interface GroundBlockSpeedFactorAccessor {
    @Invoker("getBlockSpeedFactor") float veltraph$profileBlockSpeedFactor();
}
