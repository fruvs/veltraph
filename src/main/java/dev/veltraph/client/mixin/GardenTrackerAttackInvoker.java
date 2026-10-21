package dev.veltraph.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ordinary vanilla left-click entry point; the Garden owner must bind context/tool and MISS ray. */
@Mixin(Minecraft.class)
public interface GardenTrackerAttackInvoker {
    @Invoker("startAttack") boolean veltraph$startGardenTrackerAttack();
    @Accessor("missTime") int veltraph$gardenTrackerMissTime();
}
