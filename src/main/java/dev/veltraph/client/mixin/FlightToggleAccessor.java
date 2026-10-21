package dev.veltraph.client.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Scripted ascent adjustments are not a user's double-tap request to disable flight. */
@Mixin(Player.class)
public interface FlightToggleAccessor {
    @Accessor("jumpTriggerTime")
    void veltraph$clearFlightToggleWindow(int ticks);
}
