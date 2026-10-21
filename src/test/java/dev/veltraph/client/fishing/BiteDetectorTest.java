package dev.veltraph.client.fishing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure bite-detection tests for each {@link FishingDetectionMode}. */
class BiteDetectorTest {

    private static final double THR = -0.15;

    @Test
    void bobberDipFiresOnSharpDownwardVelocity() {
        assertTrue(BiteDetector.isBite(new BobberFacts(true, -0.2, false), FishingDetectionMode.BOBBER_DIP, THR));
        assertFalse(BiteDetector.isBite(new BobberFacts(true, -0.1, false), FishingDetectionMode.BOBBER_DIP, THR));
        assertFalse(BiteDetector.isBite(new BobberFacts(true, 0.05, false), FishingDetectionMode.BOBBER_DIP, THR));
    }

    @Test
    void hookedEntityModeUsesTheHookFlagNotVelocity() {
        assertTrue(BiteDetector.isBite(new BobberFacts(true, 0.0, true), FishingDetectionMode.HOOKED_ENTITY, THR));
        assertFalse(BiteDetector.isBite(new BobberFacts(true, -0.5, false), FishingDetectionMode.HOOKED_ENTITY, THR));
    }

    @Test
    void anyModeFiresOnEitherSignal() {
        assertTrue(BiteDetector.isBite(new BobberFacts(true, -0.2, false), FishingDetectionMode.ANY, THR));
        assertTrue(BiteDetector.isBite(new BobberFacts(true, 0.0, true), FishingDetectionMode.ANY, THR));
        assertFalse(BiteDetector.isBite(new BobberFacts(true, 0.0, false), FishingDetectionMode.ANY, THR));
    }

    @Test
    void noBobberNeverBites() {
        assertFalse(BiteDetector.isBite(BobberFacts.none(), FishingDetectionMode.BOBBER_DIP, THR));
        assertFalse(BiteDetector.isBite(BobberFacts.none(), FishingDetectionMode.ANY, THR));
        assertFalse(BiteDetector.isBite(null, FishingDetectionMode.ANY, THR));
    }
}
