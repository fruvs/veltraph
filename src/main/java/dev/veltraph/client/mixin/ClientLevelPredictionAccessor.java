package dev.veltraph.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only bridge across the native package-private prediction-handler visibility boundary. */
@Mixin(ClientLevel.class)
public interface ClientLevelPredictionAccessor {
    @Accessor("blockStatePredictionHandler")
    BlockStatePredictionHandler veltraph$blockStatePredictionHandler();
}
