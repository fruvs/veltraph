package dev.veltraph.client.service;

/**
 * Sends an intentional gameplay command to the connected server. This is separate from
 * {@link ChatService}, which is local-only, so any server-bound command use is explicit and auditable.
 */
public interface ServerCommandService {

    /**
     * Send a server command without a leading slash, e.g. {@code "warp garden"}.
     *
     * @return true when a network handler was available and the command was submitted
     */
    boolean sendCommand(String command);
}
