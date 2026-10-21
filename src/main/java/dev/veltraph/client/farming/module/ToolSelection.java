package dev.veltraph.client.farming.module;

/**
 * Result of {@link CropToolSelector}: either a chosen hotbar slot or a clear, chat-ready failure
 * message (no tool found / configured slot empty).
 *
 * @param ok    whether a tool slot was resolved
 * @param slot  the chosen hotbar slot (0..8) when {@link #ok}; -1 otherwise
 * @param error the chat-ready error message when not {@link #ok}; "" otherwise
 */
public record ToolSelection(boolean ok, int slot, String error) {

    public static ToolSelection ok(int slot) {
        return new ToolSelection(true, slot, "");
    }

    public static ToolSelection fail(String error) {
        return new ToolSelection(false, -1, error);
    }
}
