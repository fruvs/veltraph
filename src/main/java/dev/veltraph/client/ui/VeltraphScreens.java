package dev.veltraph.client.ui;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleActivationGuard;

/**
 * Small factory that builds the Veltraph screens from the bootstrap's shared instances, so the
 * keybind, commands, and pause-menu button all open the screens the same way.
 */
public final class VeltraphScreens {

    private VeltraphScreens() {
    }

    public static AutomationsScreen automations(ModuleActivationGuard guard) {
        var screen=new AutomationsScreen(
                VeltraphBootstrap.moduleManager(), VeltraphBootstrap.config(),
                VeltraphBootstrap.farmRegistry(), guard);
        screen.observeOriginalFactoryCompletion();
        return screen;
    }

    public static SettingsScreen settings(ModuleActivationGuard guard) {
        return new SettingsScreen(
                VeltraphBootstrap.moduleManager(), VeltraphBootstrap.config(),
                VeltraphBootstrap.farmRegistry(), guard);
    }
}
