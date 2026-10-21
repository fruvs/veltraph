package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.ServerCommandService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Minecraft-backed server-command sender. Used for the recovered rewarp command
 * ({@code /warp garden}). No chat text is sent through this class; it only calls the vanilla client
 * command path.
 */
public final class McServerCommandService implements ServerCommandService {

    private final Minecraft client;

    public McServerCommandService(Minecraft client) {
        this.client = client;
    }

    @Override
    public boolean sendCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            return false;
        }
        handler.sendCommand(stripLeadingSlash(command));
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
