package dev.veltraph.client.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only provenance of the vanilla remote receiving-level screen. */
@Mixin(LevelLoadingScreen.class)
public interface ScheduledLevelLoadingScreenAccessor {
    @Accessor("loadTracker") LevelLoadTracker veltraph$loadTracker();
}
