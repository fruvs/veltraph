package dev.veltraph.client.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only original native grid field; it issues no world, movement or shape permission. */
@Mixin(VoxelShape.class)
public interface GroundSupportVoxelShapeAccessor {
    @Accessor("shape") DiscreteVoxelShape veltraph$groundSupportDiscreteShape();
}
