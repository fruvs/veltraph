package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The REWARP_REQUIRED-with-no-points start-block rule. */
class RewarpStartGateTest {

    @Test
    void requiredButNoPointsBlocksWithClearError() {
        Optional<String> r = RewarpStartGate.check(true, false, "Wart/Crops");
        assertTrue(r.isPresent());
        assertTrue(r.get().contains("Wart/Crops"), r.get());
        assertTrue(r.get().contains("REWARP_REQUIRED"), r.get());
        assertTrue(r.get().contains("rewarp add"), r.get());
    }

    @Test
    void requiredWithPointsAllows() {
        assertTrue(RewarpStartGate.check(true, true, "Wart/Crops").isEmpty());
    }

    @Test
    void notRequiredAlwaysAllows() {
        assertTrue(RewarpStartGate.check(false, false, "Wart/Crops").isEmpty());
        assertTrue(RewarpStartGate.check(false, true, "Wart/Crops").isEmpty());
    }
}
