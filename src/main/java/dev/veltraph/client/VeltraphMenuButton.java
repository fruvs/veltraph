package dev.veltraph.client;

import dev.veltraph.client.ui.VeltraphScreens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds a "Veltraph" button to the vanilla pause menu ({@link PauseScreen}). Clicking it closes/
 * unpauses the pause menu and opens the <b>Settings</b> UI (not Automations). The Settings screen does
 * not pause the game, so in singleplayer the world resumes.
 */
public final class VeltraphMenuButton {

    private VeltraphMenuButton() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) {
                return;
            }
            Button button = Button.builder(Component.literal("Veltraph"), b -> openSettings())
                    .bounds(8, 8, 80, 20)
                    .build();
            Screens.getWidgets(screen).add(button);
        });
    }

    private static void openSettings() {
        Minecraft client = Minecraft.getInstance();
        // Replacing the GameMenuScreen with the (non-pausing) Settings screen closes/unpauses the menu.
        client.gui.setScreen(VeltraphScreens.settings(VeltraphBootstrap.guard()));
    }
}
