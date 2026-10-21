package dev.veltraph.client.service.mc;

import dev.veltraph.client.service.ChatService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft-backed {@link ChatService}. Appends messages straight to the local chat HUD via
 * {@code ChatHud.addMessage} — this is purely client-side and never transmits to the server.
 */
public final class McChatService implements ChatService {

    private final Minecraft client;

    public McChatService(Minecraft client) {
        this.client = client;
    }

    @Override
    public void send(String message) {
        if (client.gui != null) {
            client.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        }
    }
}
