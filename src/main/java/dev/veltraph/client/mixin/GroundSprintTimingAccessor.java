package dev.veltraph.client.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Read-only actual native double-tap timer. No native flag, key, option or tick setter exists. */
@Mixin(LocalPlayer.class)
public interface GroundSprintTimingAccessor {
    @Accessor("sprintTriggerTime") int veltraph$actualSprintTriggerTime();
}
