package dev.veltraph.client.mixin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.spongepowered.asm.mixin.Mixin;

/** Marker for the pinned exact loop and subscriber opcode post-apply transformation. */
@Mixin(value = ClientTickEvents.class, priority = 500, remap = false)
public abstract class RootNativeFabricEndTickMixin {}
