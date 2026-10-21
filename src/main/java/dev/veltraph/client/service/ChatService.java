package dev.veltraph.client.service;

/**
 * Emits client-side chat messages (local only — never sent to the server). Used for activation
 * errors and status notices.
 */
public interface ChatService {

    /** Append a plain local message to the client chat HUD. */
    void send(String message);
}
