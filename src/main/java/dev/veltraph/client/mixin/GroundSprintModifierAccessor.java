package dev.veltraph.client.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LivingEntity.class)
public interface GroundSprintModifierAccessor {
    @Accessor("SPEED_MODIFIER_SPRINTING")
    static AttributeModifier veltraph$nativeSprintModifier(){throw new AssertionError("unwoven sprint accessor");}
}
