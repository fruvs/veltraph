package dev.veltraph.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface FigOwnedUseMinecraftInvoker {
    @Invoker("startUseItem") void veltraph$figVanillaUse();
}
