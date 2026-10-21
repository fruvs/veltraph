package dev.veltraph.client.rewarp;

import java.util.Optional;

/**
 * Pure rule for the {@code REWARP_REQUIRED} pre-start gate. Mirrors the original
 * {@code ReturnToLocationController} "No Return Location" guard: if the return-to-location failsafe is
 * required but there is nowhere to return to, the farm must not start.
 *
 * <p>Kept separate + pure so the rule is unit-testable and so {@code FarmModule.preStartCheck()} stays
 * thin (it mirrors how the tool-missing check returns a chat-ready error to block the start).
 */
public final class RewarpStartGate {

    private RewarpStartGate() {
    }

    /**
     * @param required   the {@code REWARP_REQUIRED} config flag
     * @param hasPoints  whether at least one rewarp point is saved
     * @param moduleName the module display name, for the chat error
     * @return a chat-ready error to block the start, or empty to allow it
     */
    public static Optional<String> check(boolean required, boolean hasPoints, String moduleName) {
        if (required && !hasPoints) {
            return Optional.of("Veltraph: " + moduleName
                    + " needs a saved rewarp point (REWARP_REQUIRED is on). "
                    + "Run /veltraph farming rewarp add at your start spot first.");
        }
        return Optional.empty();
    }
}
