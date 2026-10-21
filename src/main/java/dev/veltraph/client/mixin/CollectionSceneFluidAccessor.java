package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Fluid.class)
public interface CollectionSceneFluidAccessor extends CollectionSceneNativeAccess.FluidFields {
    @Accessor("builtInRegistryHolder") Holder.Reference<Fluid> veltraph$sceneFluidHolder();
}
