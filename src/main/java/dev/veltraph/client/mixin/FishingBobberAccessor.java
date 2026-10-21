package dev.veltraph.client.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the server-synchronized bite flag, rather than mistaking flight velocity for a fish. */
@Mixin(FishingHook.class)
public interface FishingBobberAccessor {
    @Accessor("biting") boolean veltraph$caughtFish();
}
