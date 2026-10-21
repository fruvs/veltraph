package dev.veltraph.client.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Read-only native capability; caller input/Boolean never substitutes for original key authority. */
@Mixin(LocalPlayer.class)
public interface GroundSprintCapabilityAccessor {
    @Invoker("isSprintingPossible") boolean veltraph$nativeSprintPossible(boolean flying);
}
