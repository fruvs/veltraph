package dev.veltraph.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only tracker that the exact new native listener supplies to its loading screen. */
@Mixin(ClientPacketListener.class)
public interface ScheduledClientLevelLoadAccessor {
    @Accessor("levelLoadTracker") LevelLoadTracker veltraph$levelLoadTracker();
}
