package dev.veltraph.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only bridge for the native pending-coordinate receipt snapshot; never mutates this map. */
@Mixin(BlockStatePredictionHandler.class)
public interface BlockStatePredictionHandlerAccessor {
    @Accessor("serverVerifiedStates")
    Long2ObjectOpenHashMap<?> veltraph$serverVerifiedStates();
}
