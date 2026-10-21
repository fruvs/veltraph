package dev.veltraph.client.fishing;

/**
 * One tick of input to the pure {@link FishingRuntime}. Everything the FSM needs is passed in (no
 * Minecraft, no clock read) so the whole loop is unit-testable.
 *
 * @param enabled       whether the fishing macro is enabled
 * @param available     whether a world + player are present
 * @param hasRod        whether a fishing rod is held
 * @param bobberPresent whether a bobber is currently out
 * @param biting        whether {@link BiteDetector} reported a bite this tick
 * @param nowMillis     wall clock in epoch millis (for the min-cast and post-catch timers)
 */
public record FishingInputs(
        boolean enabled,
        boolean available,
        boolean hasRod,
        boolean bobberPresent,
        boolean biting,
        long nowMillis) {
}
