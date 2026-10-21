package dev.veltraph.client.hydra.mc;

import dev.veltraph.client.hydra.HydraAlertSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Minecraft-backed {@link HydraAlertSink}: shows a local title/subtitle via {@code InGameHud} and plays
 * a single local sound ping via the local player. Both are <b>client-side only</b> — nothing is sent to
 * the server. Every call is wrapped in try/catch so a UI/audio signature change degrades to a no-op
 * rather than crashing the client (project hard rule).
 *
 * <p>Verified yarn signatures (1.21.11 build.6):
 * <ul>
 *   <li>{@code InGameHud#setTitle(Text)} = {@code method_34004}, {@code InGameHud#setSubtitle(Text)} =
 *       {@code method_34002} (class {@code net/minecraft/client/gui/hud/InGameHud}).</li>
 *   <li>{@code Entity#playSound(SoundEvent, float, float)} = {@code method_5783} (inherited by
 *       {@code ClientPlayerEntity}).</li>
 *   <li>{@code SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP} = {@code field_14627} — a short benign ping.</li>
 * </ul>
 */
public final class McHydraAlertSink implements HydraAlertSink {

    private final Minecraft client;

    public McHydraAlertSink(Minecraft client) {
        this.client = client;
    }

    @Override
    public void showTitle(String title, String subtitle) {
        try {
            if (client.gui != null) {
                client.gui.hud.setTitle(Component.literal(title));
                client.gui.hud.setSubtitle(Component.literal(subtitle));
            }
        } catch (Throwable ignored) {
            // UI unavailable / signature drift — degrade silently (the chat line still carried the alert)
        }
    }

    @Override
    public void playPing() {
        try {
            LocalPlayer player = client.player;
            if (player != null) {
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        } catch (Throwable ignored) {
            // audio unavailable — degrade silently
        }
    }
}
