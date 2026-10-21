package dev.veltraph.client.fishing;

/**
 * A read-only snapshot of the player's fishing bobber this tick, supplied by the MC layer to the pure
 * {@link BiteDetector}. No Minecraft types here so the detection logic stays unit-testable.
 *
 * @param present         whether a bobber entity exists ({@code player.fishHook != null})
 * @param velocityY       the bobber's vertical velocity (blocks/tick); {@code NaN} when no bobber
 * @param hasHookedEntity whether the bobber reports a hooked entity (vanilla bite signal)
 */
public record BobberFacts(boolean present, double velocityY, boolean hasHookedEntity, boolean caughtFish) {

    public BobberFacts(boolean present, double velocityY, boolean hasHookedEntity) {
        this(present, velocityY, hasHookedEntity, false);
    }

    /** No bobber out. */
    public static BobberFacts none() {
        return new BobberFacts(false, Double.NaN, false);
    }
}
