package dev.veltraph.client.scheduling.mc;

import dev.veltraph.client.service.mc.McHeldKeys;
import net.minecraft.client.Minecraft;

/** Captured movement/posture only. Attack/use are intentionally absent and reacquired by the engine. */
public record ScheduledMovementKeys(boolean forward,boolean back,boolean left,boolean right,boolean jump,boolean sneak,boolean sprint){
    public static ScheduledMovementKeys capture(Minecraft client){var o=client.options;return new ScheduledMovementKeys(o.keyUp.isDown(),o.keyDown.isDown(),o.keyLeft.isDown(),o.keyRight.isDown(),o.keyJump.isDown(),o.keyShift.isDown(),o.keySprint.isDown());}
    public void restore(Minecraft client){var o=client.options;McHeldKeys.setHeld(o.keyUp,forward);McHeldKeys.setHeld(o.keyDown,back);McHeldKeys.setHeld(o.keyLeft,left);McHeldKeys.setHeld(o.keyRight,right);McHeldKeys.setHeld(o.keyJump,jump);McHeldKeys.setHeld(o.keyShift,sneak);McHeldKeys.setHeld(o.keySprint,sprint);}
    public static void clearInteractions(Minecraft client){McHeldKeys.setHeld(client.options.keyAttack,false);McHeldKeys.setHeld(client.options.keyUse,false);}
}
