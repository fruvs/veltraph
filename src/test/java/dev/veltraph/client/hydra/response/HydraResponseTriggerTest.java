package dev.veltraph.client.hydra.response;

import dev.veltraph.client.hydra.HydraEvent;
import dev.veltraph.client.hydra.HydraSeverity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HydraResponseTrigger#fromEvent}: rarity is two independent, check-class-based predicates
 * ({@code forceReactRare} / {@code thresholdRare}), never derived from {@link HydraSeverity}.
 */
class HydraResponseTriggerTest {

    private static HydraEvent event(HydraEvent.Type type, HydraSeverity sev) {
        return new HydraEvent(type, sev, "msg", null, Double.NaN, false);
    }

    @Test
    void staffNearbyIsStaffCheckAndBothRare() {
        HydraResponseTrigger t = HydraResponseTrigger.fromEvent(
                event(HydraEvent.Type.STAFF_NEARBY, HydraSeverity.POSSIBLE));
        assertTrue(t.staffCheck());
        assertFalse(t.evacuation());
        assertFalse(t.disconnect());
        assertTrue(t.forceReactRare());
        assertTrue(t.thresholdRare());
    }

    @Test
    void teleportIntoBlockIsThresholdRareOnlyAndParityResponseEligible() {
        // The block/environment confinement analogue: thresholdRare, NOT forceReactRare, and a
        // parity-only response check (drives REACT in parity mode; passive-alert outside parity).
        HydraResponseTrigger t = HydraResponseTrigger.fromEvent(
                event(HydraEvent.Type.TELEPORT_INTO_BLOCK, HydraSeverity.DANGEROUS));
        assertFalse(t.staffCheck());
        assertTrue(t.parityResponseCheck());
        assertTrue(t.any());
        assertFalse(t.forceReactRare());
        assertTrue(t.thresholdRare());
    }

    @Test
    void floorChangeIsParityResponseCheckAndBothRare() {
        // The floor/block-change check analogue (original llIIIIIl…): both rare predicates set, and a
        // parity-only response check.
        HydraResponseTrigger t = HydraResponseTrigger.fromEvent(
                event(HydraEvent.Type.FLOOR_CHANGE, HydraSeverity.DANGEROUS));
        assertFalse(t.staffCheck());
        assertTrue(t.parityResponseCheck());
        assertTrue(t.any());
        assertTrue(t.forceReactRare());
        assertTrue(t.thresholdRare());
    }

    @Test
    void routingTextIsEvacuationOnlyAndNotRare() {
        HydraResponseTrigger t = HydraResponseTrigger.fromEvent(
                event(HydraEvent.Type.ROUTING_TEXT, HydraSeverity.DANGEROUS));
        assertFalse(t.staffCheck());
        assertTrue(t.evacuation());
        assertFalse(t.disconnect());
        assertFalse(t.forceReactRare());
        assertFalse(t.thresholdRare());
    }

    @Test
    void limboRoutingIsDisconnectOnlyAndNotRare() {
        HydraResponseTrigger t = HydraResponseTrigger.fromEvent(
                event(HydraEvent.Type.LIMBO_ROUTING, HydraSeverity.DANGEROUS));
        assertFalse(t.staffCheck());
        assertFalse(t.evacuation());
        assertTrue(t.disconnect());
        assertFalse(t.forceReactRare());
        assertFalse(t.thresholdRare());
    }

    @Test
    void dangerousAnomaliesAreNotRareBySeverity() {
        // FORCED_ADVENTURE + WORLD_ANOMALY carry DANGEROUS/POSSIBLE severity but neither rare predicate:
        // rarity is check-class based, not severity based, and they are passive-alert only.
        for (HydraEvent.Type type : new HydraEvent.Type[]{
                HydraEvent.Type.FORCED_ADVENTURE, HydraEvent.Type.WORLD_ANOMALY}) {
            HydraResponseTrigger t = HydraResponseTrigger.fromEvent(event(type, HydraSeverity.DANGEROUS));
            assertFalse(t.forceReactRare(), type + " must not be forceReactRare by severity");
            assertFalse(t.thresholdRare(), type + " must not be thresholdRare by severity");
            assertFalse(t.any(), type + " is passive-alert only");
        }
    }

    @Test
    void nullEventIsInert() {
        HydraResponseTrigger t = HydraResponseTrigger.fromEvent(null);
        assertFalse(t.any());
        assertFalse(t.forceReactRare());
        assertFalse(t.thresholdRare());
    }
}
