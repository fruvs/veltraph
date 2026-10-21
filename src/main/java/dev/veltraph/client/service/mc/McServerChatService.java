package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.ServerChatService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Minecraft-backed {@link ServerChatService}. Sends a plain chat message over the vanilla client path via
 * {@code ClientPlayNetworkHandler#sendChatMessage} — exactly what happens when a player types in chat.
 * Used only by the opt-in local Hydra/Anti-Staff staff-reply layer; no other Veltraph feature sends chat.
 *
 * <p>Verified yarn signature (1.21.11 build.6): {@code ClientPlayNetworkHandler#sendChatMessage(String)}.
 */
public final class McServerChatService implements ServerChatService {

    private final Minecraft client;

    public McServerChatService(Minecraft client) {
        this.client = client;
    }

    @Override
    public boolean sendChat(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            return false;
        }
        // A leading slash would be interpreted as a command; chat lines must not start with one. The
        // recovered templates may include command-shaped entries (e.g. "/chat a") — route those as
        // commands so they behave as intended; everything else is a plain chat message.
        String trimmed = message.trim();
        if (trimmed.startsWith("/")) {
            handler.sendCommand(stripLeadingSlash(trimmed));
        } else {
            handler.sendChat(trimmed);
        }
        return true;
    }

    private static String stripLeadingSlash(String command) {
        String c = command.trim();
        while (c.startsWith("/")) {
            c = c.substring(1).trim();
        }
        return c;
    }
}
