package dev.veltraph.client.fishing;

/**
 * Pure bite predicate: decides whether {@link BobberFacts} indicate a bite under the configured
 * {@link FishingDetectionMode}. This is the deterministic equivalent of the recovered
 * {@code FishingBitePredicate.test332} bobber-state read (`reports/_spec_parts/FishingBitePredicate.md`).
 * No Minecraft — unit-testable. The min-cast-time gate ("too soon") lives in {@link FishingRuntime},
 * mirroring the original {@code floatValue13} timer.
 */
public final class BiteDetector {

    private BiteDetector() {
    }

    /**
     * @param facts        this tick's bobber snapshot
     * @param mode         which signal(s) count as a bite
     * @param dipThreshold the (negative) Y-velocity below which a {@link FishingDetectionMode#BOBBER_DIP}
     *                     counts as a bite (e.g. {@code -0.15})
     */
    public static boolean isBite(BobberFacts facts, FishingDetectionMode mode, double dipThreshold) {
        if (facts == null || !facts.present()) {
            return false;
        }
        boolean dip = Double.isFinite(facts.velocityY()) && Double.isFinite(dipThreshold)
                && dipThreshold < 0 && facts.velocityY() <= dipThreshold;
        boolean hooked = facts.hasHookedEntity();
        return switch (mode) {
            case TRACKED_BITE -> facts.caughtFish();
            case BOBBER_DIP -> dip;
            case HOOKED_ENTITY -> hooked;
            case ANY -> facts.caughtFish() || dip || hooked;
        };
    }
}
