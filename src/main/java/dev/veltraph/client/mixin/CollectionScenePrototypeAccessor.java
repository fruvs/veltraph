package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BlockBehaviour.class)
public interface CollectionScenePrototypeAccessor extends CollectionSceneNativeAccess.Prototype {
    @Accessor("friction") float veltraph$sceneFriction();
    @Accessor("speedFactor") float veltraph$sceneSpeedFactor();
    @Accessor("jumpFactor") float veltraph$sceneJumpFactor();
    @Accessor("bounceRestitution") float veltraph$sceneBounceRestitution();
    @Accessor("fallDistanceReduction") float veltraph$sceneFallDistanceReduction();
    @Accessor("hasCollision") boolean veltraph$sceneHasCollision();
    @Accessor("dynamicShape") boolean veltraph$sceneDynamicShape();
}
