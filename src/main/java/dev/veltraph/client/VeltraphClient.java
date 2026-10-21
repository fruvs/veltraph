package dev.veltraph.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint for Veltraph.
 *
 * <p>This class is intentionally thin: it only owns the Fabric lifecycle hook. All framework wiring
 * (module manager, config service, Minecraft-backed services, HUD, keybinds, tick loop) is performed
 * by {@link VeltraphBootstrap}, which is plain code that does not depend on the Fabric entrypoint.
 *
 * <p>Mod id is {@code veltraph}; package root is {@code dev.veltraph.client}; target is Minecraft
 * 1.21.11. No obfuscation, encryption, auth, telemetry, or native loaders are present anywhere in
 * this project.
 */
public final class VeltraphClient implements ClientModInitializer {

    public static final String MOD_ID = "veltraph";
    public static final Logger LOGGER = LoggerFactory.getLogger("Veltraph");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Veltraph] Client initializing (mod id '{}').", MOD_ID);
        VeltraphBootstrap.init();
        LOGGER.info("[Veltraph] Client initialized.");
    }
}
