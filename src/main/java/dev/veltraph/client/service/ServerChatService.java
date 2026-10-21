package dev.veltraph.client.service;

/**
 * Sends an intentional in-game chat message to the connected server. Kept separate from {@link ChatService}
 * (which only appends to the local chat HUD) and from {@link ServerCommandService} (commands) so any
 * server-bound chat use is explicit and auditable.
 *
 * <p>This is the vanilla in-game chat channel — the same client path a player typing in chat uses, and the
 * same category as the recovered rewarp command. It is NOT a network/transport addition (no
 * {@code java.net}, no sockets, no backend/C2). Its only caller is the opt-in local Hydra/Anti-Staff
 * staff-reply layer.
 */
public interface ServerChatService {

    /**
     * Send a plain chat message to the server, e.g. {@code "nice check"}.
     *
     * @return true when a network handler was available and the message was submitted
     */
    boolean sendChat(String message);
}
