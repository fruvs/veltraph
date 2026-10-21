package dev.veltraph.client.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Entity.class)
public interface GroundNoGravityAccessor {
    @Accessor("DATA_NO_GRAVITY")
    static EntityDataAccessor<Boolean> veltraph$nativeNoGravity(){throw new AssertionError("unwoven NoGravity accessor");}
}
