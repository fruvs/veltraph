package dev.veltraph.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** MC26.3 protected native method exists in the independently inspected mapped client jar. */
@Mixin(LivingEntity.class)
public interface FigJumpPowerAccessor {
    @Invoker("getJumpPower") float veltraph$sourceJumpPower(float multiplier);
}
